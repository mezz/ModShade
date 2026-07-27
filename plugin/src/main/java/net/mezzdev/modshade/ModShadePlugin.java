package net.mezzdev.modshade;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.util.GradleVersion;

import net.mezzdev.modshade.relocation.RelocateSourceFilesAction;
import net.mezzdev.modshade.shadow.ConfigureShadowRuntimeAction;
import net.mezzdev.modshade.shadow.RemoveDependencyExcludedEntriesAction;
import net.mezzdev.modshade.task.ModShadeJar;
import net.mezzdev.modshade.task.ModShadeReportTask;
import net.mezzdev.modshade.task.ModShadeSourcesJar;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

/**
 * Main Gradle plugin implementation.
 */
public final class ModShadePlugin implements Plugin<Project> {
    public static final String IMPLEMENTATION_CONFIGURATION_NAME = "modShadeImplementation";
    public static final String COMPILE_ONLY_CONFIGURATION_NAME = "modShadeCompileOnly";
    public static final String RUNTIME_ONLY_CONFIGURATION_NAME = "modShadeRuntimeOnly";
    public static final String EXTENSION_NAME = "modShade";
    private static final String CLASSPATH_CONFIGURATION_NAME = "modShadeClasspath";
    private static final String RUNTIME_ELEMENTS_CONFIGURATION_NAME = "modShadeRuntimeElements";
    private static final String SOURCES_ELEMENTS_CONFIGURATION_NAME = "modShadeSourcesElements";
    private static final String COMPONENT_NAME = "modShade";
    private static final GradleVersion MINIMUM_GRADLE_VERSION = GradleVersion.version("8.3");

    private final SoftwareComponentFactory softwareComponentFactory;

    @Inject
    public ModShadePlugin(SoftwareComponentFactory softwareComponentFactory) {
        this.softwareComponentFactory = softwareComponentFactory;
    }

    @Override
    public void apply(Project project) {
        requireSupportedGradleVersion();

        Configuration modShadeImplementationConfiguration = createModShadeImplementationConfiguration(project);
        Configuration modShadeCompileOnlyConfiguration = createModShadeCompileOnlyConfiguration(project);
        Configuration modShadeRuntimeOnlyConfiguration = createModShadeRuntimeOnlyConfiguration(project);
        Configuration modShadeClasspathConfiguration = createModShadeClasspathConfiguration(
                project,
                modShadeImplementationConfiguration,
                modShadeCompileOnlyConfiguration,
                modShadeRuntimeOnlyConfiguration
        );
        Configuration modShadeRuntimeElements = createModShadeRuntimeElementsConfiguration(project);
        Configuration modShadeSourcesElements = createModShadeSourcesElementsConfiguration(project);
        registerModShadeComponent(project, modShadeRuntimeElements, modShadeSourcesElements);
        ModShadeExtension extension = project.getExtensions().create(
                EXTENSION_NAME,
                ModShadeExtension.class,
                project,
                modShadeRuntimeElements,
                modShadeSourcesElements
        );

        project.getPlugins().withId("java", plugin -> {
            project.getConfigurations().named("compileOnly").configure(compileOnly ->
                    compileOnly.extendsFrom(modShadeImplementationConfiguration, modShadeCompileOnlyConfiguration)
            );
            project.getConfigurations().named("testCompileOnly").configure(testCompileOnly ->
                    testCompileOnly.extendsFrom(modShadeImplementationConfiguration, modShadeCompileOnlyConfiguration)
            );
            project.getConfigurations().named("runtimeClasspath").configure(runtimeClasspath ->
                    runtimeClasspath.extendsFrom(modShadeImplementationConfiguration, modShadeRuntimeOnlyConfiguration)
            );
            project.getConfigurations().named("testRuntimeClasspath").configure(testRuntimeClasspath ->
                    testRuntimeClasspath.extendsFrom(modShadeImplementationConfiguration, modShadeRuntimeOnlyConfiguration)
            );
        });

        configureModShadeJarTasks(project, modShadeClasspathConfiguration, extension);
        configureModShadeSourcesJarTasks(project, modShadeClasspathConfiguration, extension);
        registerReportTask(project, modShadeClasspathConfiguration, extension);
    }

    private static void requireSupportedGradleVersion() {
        GradleVersion currentVersion = GradleVersion.current();
        if (currentVersion.compareTo(MINIMUM_GRADLE_VERSION) < 0) {
            throw new GradleException("ModShade requires Gradle " + MINIMUM_GRADLE_VERSION.getVersion() + " or newer.");
        }
    }

    private static void configureModShadeJarTasks(
            Project project,
            Configuration modShadeConfiguration,
            ModShadeExtension extension
    ) {
        FileCollection modShadeDependencyFiles = project.files(modShadeConfiguration);

        project.getTasks().withType(ModShadeJar.class).all(task -> {
            extension.markModShadeJarTask(task.getName());
            task.setGroup("build");
            task.setIncludeEmptyDirs(false);
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
            List<FileCollection> shadowConfigurations = new ArrayList<>();
            shadowConfigurations.add(modShadeConfiguration);
            task.setConfigurations(shadowConfigurations);
            task.mergeServiceFiles();
            task.getInputs().property("modShade.relocationBase", extension.getRelocationBase());
            task.getInputs().property("modShade.relocationRules", extension.getFormattedRelocationRules());
            task.getDependencyExcludes().set(extension.getExcludes());
            task.getInputs().property("modShade.failOnModJars", extension.getFailOnModJars());
            task.getInputs()
                    .files(modShadeDependencyFiles)
                    .withPropertyName("modShade.dependencyFiles")
                    .withPathSensitivity(PathSensitivity.RELATIVE);

            task.doFirst(new ConfigureShadowRuntimeAction(
                    modShadeDependencyFiles,
                    extension.getFailOnModJars(),
                    extension.getRelocationBase(),
                    extension.getFormattedRelocationRules()
            ));
            task.doLast(new RemoveDependencyExcludedEntriesAction(
                    task.getSourceArchiveFile(),
                    modShadeDependencyFiles,
                    extension.getRelocationBase(),
                    extension.getFormattedRelocationRules()
            ));
        });
    }

    private static void configureModShadeSourcesJarTasks(
            Project project,
            Configuration modShadeConfiguration,
            ModShadeExtension extension
    ) {
        FileCollection modShadeDependencyFiles = project.files(modShadeConfiguration);
        RelocateSourceFilesAction relocateSourceFiles = new RelocateSourceFilesAction(
                modShadeDependencyFiles,
                extension.getRelocationBase(),
                extension.getFormattedRelocationRules()
        );

        project.getTasks().withType(ModShadeSourcesJar.class).all(task -> {
            extension.markModShadeJarTask(task.getName());
            task.setGroup("build");
            task.relocateSourcesWith(relocateSourceFiles);
            task.getInputs().property("modShade.relocationBase", extension.getRelocationBase());
            task.getInputs().property("modShade.relocationRules", extension.getFormattedRelocationRules());
            task.getInputs()
                    .files(modShadeDependencyFiles)
                    .withPropertyName("modShade.dependencyFiles")
                    .withPathSensitivity(PathSensitivity.RELATIVE);
            configureProjectDependencySources(project, modShadeConfiguration, task, relocateSourceFiles);
        });
    }

    private static void registerReportTask(Project project, Configuration modShadeConfiguration, ModShadeExtension extension) {
        project.getTasks().register("modShadeReport", ModShadeReportTask.class, task -> {
            task.setGroup("help");
            task.setDescription("Writes a report of the current ModShade configuration.");
            task.getDependencyFiles().from(modShadeConfiguration);
            task.getRelocationBase().set(extension.getRelocationBase());
            task.getExplicitRelocations().set(extension.getFormattedRelocationRules());
            task.getExcludes().set(extension.getExcludes());
            task.getFailOnModJars().set(extension.getFailOnModJars());
            task.getModShadeJarTasks().set(project.provider(extension::getModShadeJarTaskNames));
            task.getReportFile().set(project.getLayout().getBuildDirectory().file("reports/modshade/modShadeReport.txt"));
        });
    }

    private static Configuration createModShadeImplementationConfiguration(Project project) {
        return project.getConfigurations().create(IMPLEMENTATION_CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(false);
            configuration.setDescription(
                    "Plain libraries to shade and add to local Java compile/runtime classpaths."
            );
        });
    }

    private static Configuration createModShadeCompileOnlyConfiguration(Project project) {
        return project.getConfigurations().create(COMPILE_ONLY_CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(false);
            configuration.setDescription(
                    "Plain libraries to shade without adding them to local Java development runtime classpaths."
            );
        });
    }

    private static Configuration createModShadeRuntimeOnlyConfiguration(Project project) {
        return project.getConfigurations().create(RUNTIME_ONLY_CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(false);
            configuration.setDescription(
                    "Plain libraries to shade and add to local Java runtime classpaths only."
            );
        });
    }

    private static Configuration createModShadeClasspathConfiguration(
            Project project,
            Configuration modShadeImplementationConfiguration,
            Configuration modShadeCompileOnlyConfiguration,
            Configuration modShadeRuntimeOnlyConfiguration
    ) {
        ObjectFactory objects = project.getObjects();
        return project.getConfigurations().create(CLASSPATH_CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setDescription("Resolved classpath of plain libraries to relocate and shade into selected Minecraft mod jars.");
            configuration.extendsFrom(
                    modShadeImplementationConfiguration,
                    modShadeCompileOnlyConfiguration,
                    modShadeRuntimeOnlyConfiguration
            );
            configuration.getAttributes().attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage.class, Usage.JAVA_RUNTIME)
            );
            configuration.getAttributes().attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category.class, Category.LIBRARY)
            );
            configuration.getAttributes().attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.class, LibraryElements.JAR)
            );
            configuration.getAttributes().attribute(
                    Bundling.BUNDLING_ATTRIBUTE,
                    objects.named(Bundling.class, Bundling.EXTERNAL)
            );
        });
    }

    private static Configuration createModShadeRuntimeElementsConfiguration(Project project) {
        ObjectFactory objects = project.getObjects();
        return project.getConfigurations().create(RUNTIME_ELEMENTS_CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(true);
            configuration.setCanBeResolved(false);
            configuration.setDescription("Published ModShade runtime artifact with shaded dependencies already bundled.");
            configuration.getAttributes().attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage.class, Usage.JAVA_RUNTIME)
            );
            configuration.getAttributes().attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category.class, Category.LIBRARY)
            );
            configuration.getAttributes().attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.class, LibraryElements.JAR)
            );
            configuration.getAttributes().attribute(
                    Bundling.BUNDLING_ATTRIBUTE,
                    objects.named(Bundling.class, Bundling.SHADOWED)
            );
        });
    }

    private static Configuration createModShadeSourcesElementsConfiguration(Project project) {
        ObjectFactory objects = project.getObjects();
        return project.getConfigurations().create(SOURCES_ELEMENTS_CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(true);
            configuration.setCanBeResolved(false);
            configuration.setDescription("Published ModShade sources artifact with shaded dependency sources relocated.");
            configuration.getAttributes().attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage.class, Usage.JAVA_RUNTIME)
            );
            configuration.getAttributes().attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category.class, Category.DOCUMENTATION)
            );
            configuration.getAttributes().attribute(
                    DocsType.DOCS_TYPE_ATTRIBUTE,
                    objects.named(DocsType.class, DocsType.SOURCES)
            );
        });
    }

    private void registerModShadeComponent(
            Project project,
            Configuration modShadeRuntimeElements,
            Configuration modShadeSourcesElements
    ) {
        AdhocComponentWithVariants component = softwareComponentFactory.adhoc(COMPONENT_NAME);
        project.getComponents().add(component);
        component.addVariantsFromConfiguration(modShadeRuntimeElements, variant ->
                variant.mapToMavenScope("runtime")
        );
        component.addVariantsFromConfiguration(modShadeSourcesElements, variant ->
                variant.mapToMavenScope("runtime")
        );
    }

    private static void configureProjectDependencySources(
            Project project,
            Configuration modShadeConfiguration,
            ModShadeSourcesJar sourcesJarTask,
            Action<? super FileCopyDetails> relocateSourceFiles
    ) {
        modShadeConfiguration.getAllDependencies().all(dependency -> {
            if (dependency instanceof ProjectDependency projectDependency) {
                resolveProjectDependency(project, projectDependency).ifPresent(dependencyProject ->
                        dependencyProject.getPlugins().withId("java", plugin -> {
                            SourceSetContainer sourceSets = dependencyProject.getExtensions().getByType(SourceSetContainer.class);
                            sourcesJarTask.from(
                                    sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getAllSource),
                                    copySpec -> copySpec.eachFile(relocateSourceFiles)
                            );
                        }));
            }
        });
    }

    private static Optional<Project> resolveProjectDependency(Project project, ProjectDependency projectDependency) {
        // Gradle 7/8 exposes ProjectDependency#getDependencyProject(), while Gradle 9 exposes
        // ProjectDependency#getPath(). Use whichever API is present so the same plugin binary
        // works in both the current Gradle wrapper and older real loader integration builds.
        Optional<Project> dependencyProjectByPath = invokeNoArg(projectDependency, "getPath")
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .flatMap(path -> Optional.ofNullable(project.findProject(path)));
        if (dependencyProjectByPath.isPresent()) {
            return dependencyProjectByPath;
        }

        return invokeNoArg(projectDependency, "getDependencyProject")
                .filter(Project.class::isInstance)
                .map(Project.class::cast);
    }

    private static Optional<Object> invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to inspect project dependency with " + methodName + "().", e);
        }
    }
}
