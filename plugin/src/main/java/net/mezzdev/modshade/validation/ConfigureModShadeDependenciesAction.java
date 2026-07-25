package net.mezzdev.modshade.validation;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Execution-time validation for the intermediate dependency jar.
 */
public final class ConfigureModShadeDependenciesAction implements Action<Task>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final FileCollection dependencyFiles;
    private final Property<Boolean> failOnModJars;

    public ConfigureModShadeDependenciesAction(
            FileCollection dependencyFiles,
            Property<Boolean> failOnModJars
    ) {
        this.dependencyFiles = dependencyFiles;
        this.failOnModJars = failOnModJars;
    }

    @Override
    public void execute(Task task) {
        List<DetectedModJar> detectedModJars = MinecraftModJarDetector.findModJars(dependencyFiles.getFiles());
        if (!detectedModJars.isEmpty() && failOnModJars.get()) {
            throw new GradleException(MinecraftModJarDetector.buildFailureMessage(detectedModJars));
        }
    }
}
