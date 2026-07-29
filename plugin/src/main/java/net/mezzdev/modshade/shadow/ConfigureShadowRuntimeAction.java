package net.mezzdev.modshade.shadow;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import net.mezzdev.modshade.RelocationRule;
import net.mezzdev.modshade.relocation.ModShadeRelocationPlanner;
import net.mezzdev.modshade.task.ModShadeJar;
import net.mezzdev.modshade.validation.DetectedModJar;
import net.mezzdev.modshade.validation.MinecraftModJarDetector;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

/**
 * Validates ModShade runtime dependencies and configures Shadow relocators
 * immediately before the runtime jar is copied.
 */
public final class ConfigureShadowRuntimeAction implements Action<Task>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final FileCollection dependencyFiles;
    private final Property<String> relocationBase;
    private final Provider<List<String>> relocationRules;

    public ConfigureShadowRuntimeAction(
            FileCollection dependencyFiles,
            Property<String> relocationBase,
            Provider<List<String>> relocationRules
    ) {
        this.dependencyFiles = dependencyFiles;
        this.relocationBase = relocationBase;
        this.relocationRules = relocationRules;
    }

    @Override
    public void execute(Task task) {
        validatePlainLibraries();

        ModShadeJar modShadeJar = (ModShadeJar) task;
        for (RelocationRule rule : finalRules()) {
            modShadeJar.relocate(rule.fromPackage(), rule.toPackage());
        }
    }

    private void validatePlainLibraries() {
        List<DetectedModJar> detectedModJars = MinecraftModJarDetector.findModJars(dependencyFiles.getFiles());
        if (!detectedModJars.isEmpty()) {
            throw new GradleException(MinecraftModJarDetector.buildFailureMessage(detectedModJars));
        }
    }

    private List<RelocationRule> finalRules() {
        return ModShadeRelocationPlanner.planRelocations(
                        relocationBase.get(),
                        relocationRules.get().stream()
                                .map(ModShadeRelocationPlanner::parseRule)
                                .toList(),
                        dependencyFiles.getFiles()
                )
                .stream()
                .sorted(Comparator.comparingInt((RelocationRule rule) -> rule.fromPackage().length()).reversed())
                .toList();
    }
}
