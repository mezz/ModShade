package net.mezzdev.modshade.task;

import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

/**
 * A shaded Minecraft mod runtime jar built from another archive task.
 */
@CacheableTask
@SuppressWarnings("unused")
public abstract class ModShadeJar extends Jar {
    public void fromJar() {
        fromArchive(getProject().getTasks().named("jar", Jar.class));
    }

    public void fromArchive(TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask) {
        setDescription("Builds a mod jar with relocated modShade dependencies from " + sourceArchiveTask.getName() + ".");
        ArchiveCoordinates.copyFrom(sourceArchiveTask, this);
        dependsOn(sourceArchiveTask);
        from(getProject().zipTree(sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveFile)));
    }
}
