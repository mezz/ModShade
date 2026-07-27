package net.mezzdev.modshade;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
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
    public static final List<String> DEFAULT_EXCLUDES = List.of(
            "META-INF/maven/**",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA"
    );

    public static final List<String> MOD_METADATA_EXCLUDES = List.of(
            "fabric.mod.json",
            "quilt.mod.json",
            "META-INF/mods.toml",
            "META-INF/neoforge.mods.toml",
            "mcmod.info"
    );

    private final Project project;
    private final Configuration modShadeRuntimeElements;
    private final Configuration modShadeSourcesElements;
    private final Property<String> relocationBase;
    private final ListProperty<String> excludes;
    private final Property<Boolean> failOnModJars;
    private final ListProperty<String> relocationRules;
    private final Set<String> modShadeJarTaskNames = new LinkedHashSet<>();
    private final Set<String> publishedRuntimeTaskNames = new LinkedHashSet<>();
    private final Set<String> publishedSourcesTaskNames = new LinkedHashSet<>();

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
        this.failOnModJars = project.getObjects().property(Boolean.class);
        this.failOnModJars.convention(true);
        this.excludes = project.getObjects().listProperty(String.class);
        this.excludes.convention(project.provider(() -> defaultExcludes(failOnModJars.get())));
        this.relocationRules = project.getObjects().listProperty(String.class);
        this.relocationRules.convention(List.of());
    }

    public Property<String> getRelocationBase() {
        return relocationBase;
    }

    public ListProperty<String> getExcludes() {
        return excludes;
    }

    public Property<Boolean> getFailOnModJars() {
        return failOnModJars;
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

        TaskProvider<Jar> jar = project.getTasks().named("jar", Jar.class);
        configureClassifier(jar, "unshaded");
        TaskProvider<ModShadeJar> task = shadeJar("modShadeJar", jar);
        configureDependsOnReobfJar(task);
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

    private void configureDependsOnReobfJar(TaskProvider<ModShadeJar> task) {
        Task reobfJar = project.getTasks().findByName("reobfJar");
        if (reobfJar != null) {
            task.configure(modShadeJar -> modShadeJar.dependsOn(reobfJar));
            return;
        }

        project.afterEvaluate(evaluatedProject -> {
            Task lateReobfJar = evaluatedProject.getTasks().findByName("reobfJar");
            if (lateReobfJar != null) {
                task.configure(modShadeJar -> modShadeJar.dependsOn(lateReobfJar));
            }
        });
    }

    private void configureAssembleDependsOn(TaskProvider<? extends Task> archiveTask) {
        project.getTasks()
                .matching(task -> "assemble".equals(task.getName()))
                .configureEach(assemble -> assemble.dependsOn(archiveTask));
    }

    private void publishRuntimeArtifact(TaskProvider<ModShadeJar> task) {
        if (publishedRuntimeTaskNames.add(task.getName())) {
            modShadeRuntimeElements.getOutgoing().artifact(task);
        }
    }

    private void publishSourcesArtifact(TaskProvider<ModShadeSourcesJar> task) {
        if (publishedSourcesTaskNames.add(task.getName())) {
            modShadeSourcesElements.getOutgoing().artifact(task);
        }
    }

    private Optional<TaskProvider<? extends AbstractArchiveTask>> findArchiveTask(String taskName) {
        try {
            return Optional.of(project.getTasks().named(taskName, AbstractArchiveTask.class));
        } catch (UnknownTaskException | InvalidUserDataException e) {
            return Optional.empty();
        }
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

    private static List<String> defaultExcludes(boolean failOnModJars) {
        if (failOnModJars) {
            return DEFAULT_EXCLUDES;
        }

        List<String> excludes = new ArrayList<>(DEFAULT_EXCLUDES);
        excludes.addAll(MOD_METADATA_EXCLUDES);
        return List.copyOf(excludes);
    }
}
