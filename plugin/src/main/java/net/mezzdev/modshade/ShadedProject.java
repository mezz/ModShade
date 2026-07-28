package net.mezzdev.modshade;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import net.mezzdev.modshade.task.ModShadeJar;
import net.mezzdev.modshade.task.ModShadeSourcesJar;

import java.util.List;
import java.util.Map;

/**
 * Consumer-side handle for embedding another project's ModShade outputs.
 */
@SuppressWarnings("unused")
public final class ShadedProject {
    private static final List<String> SOURCES_MANIFEST_EXCLUDES = List.of(
            "META-INF/MANIFEST.MF",
            "MANIFEST.MF"
    );

    private final Project consumerProject;
    private final Project producerProject;

    ShadedProject(Project consumerProject, Project producerProject) {
        this.consumerProject = consumerProject;
        this.producerProject = producerProject;
    }

    public ProjectDependency runtimeDependency() {
        requireRuntimeJarTasks();
        ProjectDependency dependency = (ProjectDependency) consumerProject.getDependencies().project(Map.of(
                "path",
                producerProject.getPath()
        ));
        ObjectFactory objects = consumerProject.getObjects();
        dependency.attributes(attributes -> attributes.attribute(
                Bundling.BUNDLING_ATTRIBUTE,
                objects.named(Bundling.class, Bundling.SHADOWED)
        ));
        return dependency;
    }

    public FileCollection runtimeContents() {
        return contentsFrom(requireRuntimeJarTasks(), List.of());
    }

    public FileCollection sourcesContents() {
        return contentsFrom(requireSourcesJarTasks(), SOURCES_MANIFEST_EXCLUDES);
    }

    public void addRuntimeDependencyTo(Configuration configuration) {
        configuration.getDependencies().add(runtimeDependency());
    }

    public void addRuntimeDependencyTo(NamedDomainObjectProvider<? extends Configuration> configuration) {
        configuration.configure(this::addRuntimeDependencyTo);
    }

    public void runtimeInto(AbstractCopyTask task) {
        task.from(runtimeContents());
    }

    public void runtimeInto(TaskProvider<? extends AbstractCopyTask> task) {
        task.configure(this::runtimeInto);
    }

    public void sourcesInto(AbstractCopyTask task) {
        task.from(sourcesContents());
    }

    public void sourcesInto(TaskProvider<? extends AbstractCopyTask> task) {
        task.configure(this::sourcesInto);
    }

    private List<TaskProvider<ModShadeJar>> requireRuntimeJarTasks() {
        List<TaskProvider<ModShadeJar>> runtimeJarTasks = producerExtension().getRuntimeJarTasks();
        if (runtimeJarTasks.isEmpty()) {
            throw new InvalidUserDataException(
                    "Project '" + producerProject.getPath() + "' has no ModShade runtime output registered. " +
                            "Call modShade.shadeJar() or modShade.shadeJar(\"taskName\", archiveTask) in that project before embedding it."
            );
        }
        return runtimeJarTasks;
    }

    private List<TaskProvider<ModShadeSourcesJar>> requireSourcesJarTasks() {
        List<TaskProvider<ModShadeSourcesJar>> sourcesJarTasks = producerExtension().getSourcesJarTasks();
        if (sourcesJarTasks.isEmpty()) {
            throw new InvalidUserDataException(
                    "Project '" + producerProject.getPath() + "' has no ModShade sources output registered. " +
                            "Call modShade.shadeSourcesJar() or modShade.shadeSourcesJar(\"taskName\", archiveTask) in that project before embedding it."
            );
        }
        return sourcesJarTasks;
    }

    private ModShadeExtension producerExtension() {
        ModShadeExtension extension = producerProject.getExtensions().findByType(ModShadeExtension.class);
        if (extension == null) {
            throw new InvalidUserDataException(
                    "Project '" + producerProject.getPath() + "' does not apply the ModShade plugin. " +
                            "Apply plugin 'net.mezzdev.modshade' in that project before embedding it."
            );
        }
        return extension;
    }

    private FileCollection contentsFrom(
            List<? extends TaskProvider<? extends AbstractArchiveTask>> archiveTasks,
            List<String> excludes
    ) {
        ConfigurableFileCollection contents = consumerProject.files();
        for (TaskProvider<? extends AbstractArchiveTask> archiveTask : archiveTasks) {
            FileTree archiveContents = consumerProject.zipTree(archiveTask.flatMap(AbstractArchiveTask::getArchiveFile));
            if (!excludes.isEmpty()) {
                archiveContents = archiveContents.matching(patterns -> patterns.exclude(excludes));
            }
            contents.from(archiveContents);
            contents.builtBy(archiveTask);
        }
        return contents;
    }
}
