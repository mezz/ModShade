package net.mezzdev.modshade.task;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;

/**
 * A shaded Minecraft mod runtime jar built from another archive task.
 */
@CacheableTask
@SuppressWarnings("unused")
public abstract class ModShadeJar extends ShadowJar {
    private final RegularFileProperty sourceArchiveFile;

    @Inject
    public ModShadeJar(ObjectFactory objects) {
        this.sourceArchiveFile = objects.fileProperty();
    }

    public void fromJar() {
        fromArchive(getProject().getTasks().named("jar", Jar.class));
    }

    public void fromArchive(TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask) {
        setDescription("Builds a mod jar with relocated modShade dependencies from " + sourceArchiveTask.getName() + ".");
        ArchiveCoordinates.copyFrom(sourceArchiveTask, this);
        dependsOn(sourceArchiveTask);
        sourceArchiveFile.set(sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveFile));
        from(getProject().zipTree(sourceArchiveFile));
    }

    @Internal
    public RegularFileProperty getSourceArchiveFile() {
        return sourceArchiveFile;
    }
}
