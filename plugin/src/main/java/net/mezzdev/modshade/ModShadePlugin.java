package net.mezzdev.modshade;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import net.mezzdev.modshade.relocation.ModShadeRelocationPlanner;
import net.mezzdev.modshade.relocation.RelocateModShadeJarAction;
import net.mezzdev.modshade.relocation.RelocateSourceFilesAction;
import net.mezzdev.modshade.task.ModShadeJar;
import net.mezzdev.modshade.task.ModShadeReportTask;
import net.mezzdev.modshade.task.ModShadeSourcesJar;
import net.mezzdev.modshade.validation.ConfigureModShadeDependenciesAction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main Gradle plugin implementation.
 */
public final class ModShadePlugin implements Plugin<Project> {
    public static final String CONFIGURATION_NAME = "modShade";
    public static final String EXTENSION_NAME = "modShade";

    @Override
    public void apply(Project project) {
        Configuration modShadeConfiguration = createModShadeConfiguration(project);
        ModShadeExtension extension = project.getExtensions().create(
                EXTENSION_NAME,
                ModShadeExtension.class,
                project
        );

        project.getPlugins().withId("java", plugin -> {
            project.getConfigurations().named("compileOnly").configure(compileOnly ->
                    compileOnly.extendsFrom(modShadeConfiguration)
            );
        });

        configureModShadeJarTasks(project, modShadeConfiguration, extension);
        configureModShadeSourcesJarTasks(project, modShadeConfiguration, extension);
        registerReportTask(project, modShadeConfiguration, extension);
    }

    private static void configureModShadeJarTasks(
            Project project,
            Configuration modShadeConfiguration,
            ModShadeExtension extension
    ) {
        FileCollection modShadeDependencyFiles = project.files(modShadeConfiguration);
        List<ModShadeJar> modShadeJarTasks = new ArrayList<>();

        project.getTasks().withType(ModShadeJar.class).all(task -> {
            extension.markModShadeJarTask(task.getName());
            modShadeJarTasks.add(task);
            task.setGroup("build");
            task.setIncludeEmptyDirs(false);
            task.getInputs().property("modShade.relocationBase", extension.getRelocationBase());
            task.getInputs().property("modShade.relocationRules", project.provider(() ->
                    extension.getRelocationRules().stream()
                            .map(ModShadeRelocationPlanner::formatRule)
                            .toList()
            ));
            task.getInputs()
                    .files(modShadeDependencyFiles)
                    .withPropertyName("modShade.dependencyFiles")
                    .withPathSensitivity(PathSensitivity.RELATIVE);

            task.doLast(new RelocateModShadeJarAction(
                    modShadeDependencyFiles,
                    extension.getRelocationBase(),
                    extension.getRelocationRules()
            ));
        });

        project.afterEvaluate(evaluatedProject -> {
            for (ModShadeJar task : modShadeJarTasks) {
                TaskProvider<Jar> dependencyJarTask = evaluatedProject.getTasks().register(
                        dependencyJarTaskName(task.getName()),
                        Jar.class,
                        dependencyTask -> configureDependencyJarTask(
                                evaluatedProject,
                                dependencyTask,
                                task.getName(),
                                modShadeConfiguration,
                                extension
                        )
                );
                task.dependsOn(dependencyJarTask);
                task.from(evaluatedProject.provider(() ->
                        evaluatedProject.zipTree(dependencyJarTask.get().getArchiveFile().get().getAsFile())
                ));
            }
        });
    }

    private static void configureDependencyJarTask(
            Project project,
            Jar task,
            String modShadeJarTaskName,
            Configuration modShadeConfiguration,
            ModShadeExtension extension
    ) {
        task.setGroup("build");
        task.setDescription("Filters modShade dependencies for " + modShadeJarTaskName + ".");
        task.setIncludeEmptyDirs(false);
        task.setPreserveFileTimestamps(false);
        task.setReproducibleFileOrder(true);
        task.dependsOn(modShadeConfiguration);
        task.getArchiveBaseName().set(modShadeJarTaskName + "-dependencies");
        task.getArchiveVersion().set("");
        task.getArchiveClassifier().set("");
        task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("modshade/" + modShadeJarTaskName));
        task.getInputs().property("modShade.excludes", extension.getExcludes());
        task.getInputs().property("modShade.failOnModJars", extension.getFailOnModJars());
        task.from(project.provider(() ->
                modShadeConfiguration.getFiles().stream()
                        .map(project::zipTree)
                        .toList()
        ));
        for (String exclude : extension.getExcludes().get()) {
            task.exclude(exclude);
        }

        task.doFirst(new ConfigureModShadeDependenciesAction(
                project.files(modShadeConfiguration),
                extension.getFailOnModJars()
        ));
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
                extension.getRelocationRules()
        );

        project.getTasks().withType(ModShadeSourcesJar.class).configureEach(task -> {
            extension.markModShadeJarTask(task.getName());
            task.setGroup("build");
            task.relocateSourcesWith(relocateSourceFiles);
            task.getInputs().property("modShade.relocationBase", extension.getRelocationBase());
            task.getInputs().property("modShade.relocationRules", project.provider(() ->
                    extension.getRelocationRules().stream()
                            .map(ModShadeRelocationPlanner::formatRule)
                            .toList()
            ));
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
            task.getExplicitRelocations().set(project.provider(() ->
                    extension.getRelocationRules().stream()
                            .map(ModShadeRelocationPlanner::formatRule)
                            .toList()
            ));
            task.getExcludes().set(extension.getExcludes());
            task.getFailOnModJars().set(extension.getFailOnModJars());
            task.getModShadeJarTasks().set(project.provider(extension::getModShadeJarTaskNames));
            task.getReportFile().set(project.getLayout().getBuildDirectory().file("reports/modshade/modShadeReport.txt"));
        });
    }

    private static Configuration createModShadeConfiguration(Project project) {
        ObjectFactory objects = project.getObjects();
        return project.getConfigurations().create(CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setDescription("Plain libraries to relocate and shade into selected Minecraft mod jars.");
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

    private static String dependencyJarTaskName(String modShadeJarTaskName) {
        return modShadeJarTaskName + "Dependencies";
    }

    private static void configureProjectDependencySources(
            Project project,
            Configuration modShadeConfiguration,
            ModShadeSourcesJar sourcesJarTask,
            Action<? super FileCopyDetails> relocateSourceFiles
    ) {
        modShadeConfiguration.getDependencies().all(dependency -> {
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
