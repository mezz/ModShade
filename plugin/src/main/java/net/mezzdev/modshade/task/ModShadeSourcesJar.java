package net.mezzdev.modshade.task;

import org.gradle.api.Action;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A sources jar whose available shaded-library sources are relocated to match
 * the ModShade runtime jar.
 */
@CacheableTask
@SuppressWarnings("unused")
public abstract class ModShadeSourcesJar extends Jar {
    private final List<Action<? super FileCopyDetails>> sourceRelocationActions = new ArrayList<>();

    public void fromSourcesJar() {
        fromArchive(getProject().getTasks().named("sourcesJar", AbstractArchiveTask.class));
    }

    public void fromArchive(TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask) {
        setDescription("Builds a sources jar with relocated modShade dependency sources from " + sourceArchiveTask.getName() + ".");
        ArchiveCoordinates.copyFrom(sourceArchiveTask, this);
        dependsOn(sourceArchiveTask);
        from(
                getProject().zipTree(sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveFile)),
                copySpec -> copySpec.eachFile(new CompositeFileCopyDetailsAction(sourceRelocationActions))
        );
    }

    public void relocateSourcesWith(Action<? super FileCopyDetails> action) {
        sourceRelocationActions.add(action);
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class CompositeFileCopyDetailsAction implements Action<FileCopyDetails>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final List<Action<? super FileCopyDetails>> actions;

        private CompositeFileCopyDetailsAction(List<Action<? super FileCopyDetails>> actions) {
            this.actions = actions;
        }

        @Override
        public void execute(FileCopyDetails details) {
            for (Action<? super FileCopyDetails> action : actions) {
                action.execute(details);
            }
        }
    }
}
