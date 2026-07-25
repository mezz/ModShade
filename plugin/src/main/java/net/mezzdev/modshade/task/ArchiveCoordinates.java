package net.mezzdev.modshade.task;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jspecify.annotations.Nullable;

/**
 * Copies Gradle archive coordinates from source archive tasks to ModShade
 * archive tasks.
 */
final class ArchiveCoordinates {
    private ArchiveCoordinates() {
    }

    static void copyFrom(
            TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask,
            AbstractArchiveTask modShadeJarTask
    ) {
        modShadeJarTask.getArchiveBaseName().set(sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveBaseName));
        modShadeJarTask.getArchiveAppendix().set(sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveAppendix));
        modShadeJarTask.getArchiveVersion().set(sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveVersion));
        modShadeJarTask.getArchiveExtension().set(sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveExtension));
        Provider<String> sourceClassifier = sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveClassifier);
        modShadeJarTask.getArchiveClassifier().set(sourceClassifier.map(ArchiveCoordinates::modShadeClassifier));
        modShadeJarTask.getDestinationDirectory().set(sourceArchiveTask.flatMap(AbstractArchiveTask::getDestinationDirectory));
        modShadeJarTask.setPreserveFileTimestamps(false);
        modShadeJarTask.setReproducibleFileOrder(true);
    }

    private static String modShadeClassifier(@Nullable String sourceClassifier) {
        if (sourceClassifier == null || sourceClassifier.isBlank()) {
            return "modshade";
        }
        return sourceClassifier + "-modshade";
    }
}
