package net.mezzdev.modshade;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

import net.mezzdev.modshade.relocation.ModShadeRelocationPlanner;
import net.mezzdev.modshade.relocation.PackageNameSanitizer;
import net.mezzdev.modshade.task.ModShadeJar;
import net.mezzdev.modshade.task.ModShadeSourcesJar;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Project-level ModShade configuration.
 */
@SuppressWarnings("unused")
public abstract class ModShadeExtension {
    private static final String JAVA_COMPONENT_NAME = "java";
    private static final String JAVA_RUNTIME_ELEMENTS_CONFIGURATION_NAME = "runtimeElements";
    private static final String JAVA_SOURCES_ELEMENTS_CONFIGURATION_NAME = "sourcesElements";
    private static final String JAR_JAR_TASK_NAME = "jarJar";
    private static final String JAR_JAR_CONFIGURATION_NAME = "jarJar";

    public static final List<String> DEFAULT_EXCLUDES = List.of(
            "META-INF/maven/**",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA"
    );

    private final Project project;
    private final Configuration modShadeRuntimeElements;
    private final Configuration modShadeSourcesElements;
    private final Property<String> relocationBase;
    private final ListProperty<String> excludes;
    private final ListProperty<String> relocationRules;
    private final Set<String> modShadeJarTaskNames = new LinkedHashSet<>();
    private final Set<String> publishedRuntimeTaskNames = new LinkedHashSet<>();
    private final Set<String> publishedSourcesTaskNames = new LinkedHashSet<>();
    private boolean javaRuntimePublicationConfigured;
    private boolean javaSourcesPublicationConfigured;

    @Inject
    public ModShadeExtension(
            Project project,
            Configuration modShadeRuntimeElements,
            Configuration modShadeSourcesElements
    ) {
        this.project = project;
        this.modShadeRuntimeElements = modShadeRuntimeElements;
        this.modShadeSourcesElements = modShadeSourcesElements;
        this.relocationBase = project.getObjects().property(String.class);
        this.relocationBase.convention(project.provider(() -> defaultRelocationBase(project)));
        this.excludes = project.getObjects().listProperty(String.class);
        this.excludes.convention(DEFAULT_EXCLUDES);
        this.relocationRules = project.getObjects().listProperty(String.class);
        this.relocationRules.convention(List.of());
    }

    public Property<String> getRelocationBase() {
        return relocationBase;
    }

    public ListProperty<String> getExcludes() {
        return excludes;
    }

    public List<RelocationRule> getRelocationRules() {
        return relocationRules.get().stream()
                .map(ModShadeRelocationPlanner::parseRule)
                .toList();
    }

    public void exclude(String pattern) {
        List<String> updatedExcludes = new ArrayList<>(excludes.get());
        updatedExcludes.add(pattern);
        excludes.set(List.copyOf(updatedExcludes));
    }

    public void relocate(String fromPackage, String toPackage) {
        relocationRules.add(ModShadeRelocationPlanner.formatRule(new RelocationRule(fromPackage, toPackage)));
    }

    public ListProperty<String> getFormattedRelocationRules() {
        return relocationRules;
    }

    public TaskProvider<ModShadeJar> shadeJar() {
        Optional<TaskProvider<? extends AbstractArchiveTask>> remapJar = findArchiveTask("remapJar");
        if (remapJar.isPresent()) {
            TaskProvider<? extends AbstractArchiveTask> archiveTask = remapJar.get();
            configureClassifier(archiveTask, "unshaded");
            return shadeJar("modShadeJar", archiveTask);
        }

        Optional<TaskProvider<? extends AbstractArchiveTask>> reobfJar = findArchiveTask("reobfJar");
        if (reobfJar.isPresent()) {
            TaskProvider<? extends AbstractArchiveTask> archiveTask = reobfJar.get();
            configureClassifier(archiveTask, "unshaded");
            return shadeJar("modShadeJar", archiveTask);
        }

        Optional<TaskProvider<? extends AbstractArchiveTask>> jarJar = findArchiveTask(JAR_JAR_TASK_NAME);
        if (jarJar.isPresent() && hasDeclaredDependencies(JAR_JAR_CONFIGURATION_NAME)) {
            TaskProvider<? extends AbstractArchiveTask> archiveTask = jarJar.get();
            configureIntermediateJarClassifierForJarJar();
            configureClassifier(archiveTask, "unshaded");
            TaskProvider<ModShadeJar> task = shadeJar("modShadeJar", archiveTask);
            configureDependsOnReobfTask(task, archiveTask.getName());
            return task;
        }

        TaskProvider<Jar> jar = project.getTasks().named("jar", Jar.class);
        configureClassifier(jar, "unshaded");
        TaskProvider<ModShadeJar> task = shadeJar("modShadeJar", jar);
        configureDependsOnReobfTask(task, jar.getName());
        return task;
    }

    public TaskProvider<ModShadeJar> shadeJar(TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask) {
        return shadeJar(defaultModShadeJarTaskName(sourceArchiveTask.getName()), sourceArchiveTask);
    }

    public TaskProvider<ModShadeJar> shadeJar(String taskName, TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask) {
        TaskProvider<ModShadeJar> task = registerModShadeJar(taskName, sourceArchiveTask);
        task.configure(modShadeJar -> modShadeJar.getArchiveClassifier().set(""));
        publishRuntimeArtifact(task);
        configureAssembleDependsOn(task);
        return task;
    }

    public TaskProvider<ModShadeSourcesJar> shadeSourcesJar() {
        TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask = findArchiveTask("remapSourcesJar")
                .orElseGet(() -> project.getTasks().named("sourcesJar", AbstractArchiveTask.class));
        configureClassifier(sourceArchiveTask, "sources-unshaded");
        return shadeSourcesJar("modShadeSourcesJar", sourceArchiveTask);
    }

    public TaskProvider<ModShadeSourcesJar> shadeSourcesJar(TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask) {
        return shadeSourcesJar("modShadeSourcesJar", sourceArchiveTask);
    }

    public TaskProvider<ModShadeSourcesJar> shadeSourcesJar(String taskName, TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask) {
        TaskProvider<ModShadeSourcesJar> task = registerModShadeSourcesJar(taskName, sourceArchiveTask);
        task.configure(sourcesJar -> sourcesJar.getArchiveClassifier().set("sources"));
        publishSourcesArtifact(task);
        configureAssembleDependsOn(task);
        return task;
    }

    void markModShadeJarTask(String taskName) {
        modShadeJarTaskNames.add(taskName);
    }

    List<String> getModShadeJarTaskNames() {
        return List.copyOf(modShadeJarTaskNames);
    }

    private void configureDependsOnReobfTask(TaskProvider<ModShadeJar> task, String sourceArchiveTaskName) {
        String reobfTaskName = "reobf" + capitalize(sourceArchiveTaskName);
        Task reobfTask = project.getTasks().findByName(reobfTaskName);
        if (reobfTask != null) {
            task.configure(modShadeJar -> modShadeJar.dependsOn(reobfTask));
            return;
        }

        project.afterEvaluate(evaluatedProject -> {
            Task lateReobfTask = evaluatedProject.getTasks().findByName(reobfTaskName);
            if (lateReobfTask != null) {
                task.configure(modShadeJar -> modShadeJar.dependsOn(lateReobfTask));
            }
        });
    }

    private void configureAssembleDependsOn(TaskProvider<? extends Task> archiveTask) {
        project.getTasks()
                .matching(task -> "assemble".equals(task.getName()))
                .configureEach(assemble -> assemble.dependsOn(archiveTask));
    }

    private void configureIntermediateJarClassifierForJarJar() {
        project.getTasks().named("jar", Jar.class).configure(jar -> {
            String classifier = jar.getArchiveClassifier().getOrElse("");
            if (classifier.isEmpty() || "unshaded".equals(classifier)) {
                jar.getArchiveClassifier().set("plain-unshaded");
            }
        });
    }

    private void publishRuntimeArtifact(TaskProvider<ModShadeJar> task) {
        if (publishedRuntimeTaskNames.add(task.getName())) {
            modShadeRuntimeElements.getOutgoing().artifact(task);
            configureJavaRuntimePublication();
        }
    }

    private void publishSourcesArtifact(TaskProvider<ModShadeSourcesJar> task) {
        if (publishedSourcesTaskNames.add(task.getName())) {
            modShadeSourcesElements.getOutgoing().artifact(task);
            configureJavaSourcesPublication();
        }
    }

    private void configureJavaRuntimePublication() {
        if (javaRuntimePublicationConfigured) {
            return;
        }

        javaRuntimePublicationConfigured = true;
        project.getPlugins().withId("java", plugin -> {
            AdhocComponentWithVariants javaComponent = javaComponent();
            javaComponent.addVariantsFromConfiguration(modShadeRuntimeElements, variant ->
                    variant.mapToMavenScope("runtime")
            );
            Configuration runtimeElements = project.getConfigurations().getByName(JAVA_RUNTIME_ELEMENTS_CONFIGURATION_NAME);
            javaComponent.withVariantsFromConfiguration(runtimeElements, variant ->
                    variant.skip()
            );
        });
    }

    private void configureJavaSourcesPublication() {
        if (javaSourcesPublicationConfigured) {
            return;
        }

        javaSourcesPublicationConfigured = true;
        project.getPlugins().withId("java", plugin -> {
            AdhocComponentWithVariants javaComponent = javaComponent();
            javaComponent.addVariantsFromConfiguration(modShadeSourcesElements, variant ->
                    variant.mapToMavenScope("runtime")
            );
            Configuration sourcesElements = project.getConfigurations().findByName(JAVA_SOURCES_ELEMENTS_CONFIGURATION_NAME);
            if (sourcesElements != null) {
                javaComponent.withVariantsFromConfiguration(sourcesElements, variant ->
                        variant.skip()
                );
            }
        });
    }

    private AdhocComponentWithVariants javaComponent() {
        return (AdhocComponentWithVariants) project.getComponents().getByName(JAVA_COMPONENT_NAME);
    }

    private Optional<TaskProvider<? extends AbstractArchiveTask>> findArchiveTask(String taskName) {
        try {
            return Optional.of(project.getTasks().named(taskName, AbstractArchiveTask.class));
        } catch (UnknownTaskException | InvalidUserDataException e) {
            return Optional.empty();
        }
    }

    private boolean hasDeclaredDependencies(String configurationName) {
        Configuration configuration = project.getConfigurations().findByName(configurationName);
        return configuration != null && !configuration.getAllDependencies().isEmpty();
    }

    private static void configureClassifier(TaskProvider<? extends AbstractArchiveTask> archiveTask, String classifier) {
        archiveTask.configure(task -> task.getArchiveClassifier().set(classifier));
    }

    private TaskProvider<ModShadeJar> registerModShadeJar(
            String taskName,
            TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask
    ) {
        if (project.getTasks().findByName(taskName) != null) {
            return project.getTasks().named(taskName, ModShadeJar.class);
        }

        return project.getTasks().register(taskName, ModShadeJar.class, task ->
                task.fromArchive(sourceArchiveTask)
        );
    }

    private TaskProvider<ModShadeSourcesJar> registerModShadeSourcesJar(
            String taskName,
            TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask
    ) {
        if (project.getTasks().findByName(taskName) != null) {
            return project.getTasks().named(taskName, ModShadeSourcesJar.class);
        }

        return project.getTasks().register(taskName, ModShadeSourcesJar.class, task ->
                task.fromArchive(sourceArchiveTask)
        );
    }

    private static String defaultModShadeJarTaskName(String sourceJarTaskName) {
        if ("jar".equals(sourceJarTaskName)) {
            return "modShadeJar";
        }
        return "modShade" + capitalize(sourceJarTaskName);
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }

        List<Integer> codePoints = new ArrayList<>();
        value.codePoints().forEach(codePoints::add);
        int first = Character.toTitleCase(codePoints.get(0));
        StringBuilder result = new StringBuilder();
        result.appendCodePoint(first);
        for (int i = 1; i < codePoints.size(); i++) {
            result.appendCodePoint(codePoints.get(i));
        }
        return result.toString();
    }

    static String defaultRelocationBase(Project project) {
        String groupName = project.getGroup().toString();
        String sanitizedGroup = PackageNameSanitizer.sanitize(groupName, "modshade");
        if ("modshade".equals(sanitizedGroup)) {
            return "modshade";
        }
        return sanitizedGroup + ".modshade";
    }

}
