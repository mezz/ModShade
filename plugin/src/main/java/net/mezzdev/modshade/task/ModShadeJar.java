package net.mezzdev.modshade.task;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * A shaded Minecraft mod runtime jar built from another archive task.
 */
@CacheableTask
@SuppressWarnings("unused")
public abstract class ModShadeJar extends ShadowJar {
    private final RegularFileProperty sourceArchiveFile;
    private boolean sourceArchiveConfigured;

    @Inject
    public ModShadeJar(ObjectFactory objects) {
        this.sourceArchiveFile = objects.fileProperty();
    }

    public void fromJar() {
        fromArchive(getProject().getTasks().named("jar", Jar.class));
    }

    public void fromArchive(TaskProvider<? extends AbstractArchiveTask> sourceArchiveTask) {
        if (sourceArchiveConfigured) {
            throw new InvalidUserDataException(getPath() + " already has a source archive configured.");
        }
        sourceArchiveConfigured = true;
        setDescription("Builds a mod jar with relocated modShade dependencies from " + sourceArchiveTask.getName() + ".");
        ArchiveCoordinates.copyFrom(sourceArchiveTask, this);
        dependsOn(sourceArchiveTask);
        sourceArchiveFile.set(sourceArchiveTask.flatMap(AbstractArchiveTask::getArchiveFile));
        from(getProject().zipTree(sourceArchiveFile), copySpec -> copySpec.exclude("META-INF/MANIFEST.MF", "MANIFEST.MF"));
        doFirst(new MergeSourceManifestAction(sourceArchiveFile));
    }

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public RegularFileProperty getSourceArchiveFile() {
        return sourceArchiveFile;
    }

    private static final class MergeSourceManifestAction implements Action<Task>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Provider<RegularFile> sourceArchiveFile;

        private MergeSourceManifestAction(Provider<RegularFile> sourceArchiveFile) {
            this.sourceArchiveFile = sourceArchiveFile;
        }

        @Override
        public void execute(Task task) {
            if (!sourceArchiveFile.isPresent()) {
                return;
            }

            ModShadeJar modShadeJar = (ModShadeJar) task;
            Path archive = sourceArchiveFile.get().getAsFile().toPath();
            try (JarFile jarFile = new JarFile(archive.toFile())) {
                java.util.jar.Manifest sourceManifest = jarFile.getManifest();
                if (sourceManifest != null) {
                    mergeMissingAttributes(modShadeJar.getManifest(), sourceManifest);
                }
            } catch (IOException e) {
                throw new GradleException("Failed to read source archive manifest from " + archive, e);
            }
        }

        private static void mergeMissingAttributes(
                Manifest targetManifest,
                java.util.jar.Manifest sourceManifest
        ) {
            mergeMissingMainAttributes(targetManifest, sourceManifest.getMainAttributes());
            sourceManifest.getEntries().forEach((sectionName, attributes) ->
                    mergeMissingSectionAttributes(targetManifest, sectionName, attributes)
            );
        }

        private static void mergeMissingMainAttributes(
                Manifest targetManifest,
                java.util.jar.Attributes sourceAttributes
        ) {
            Map<String, Object> missingAttributes = missingAttributes(targetManifest.getAttributes(), sourceAttributes);
            if (missingAttributes.isEmpty()) {
                return;
            }
            targetManifest.attributes(missingAttributes);
        }

        private static void mergeMissingSectionAttributes(
                Manifest targetManifest,
                String sectionName,
                java.util.jar.Attributes sourceAttributes
        ) {
            Attributes targetAttributes = targetManifest.getSections().get(sectionName);
            Map<String, Object> missingAttributes = missingAttributes(targetAttributes, sourceAttributes);
            if (!missingAttributes.isEmpty()) {
                targetManifest.attributes(missingAttributes, sectionName);
            }
        }

        private static Map<String, Object> missingAttributes(
                @Nullable Attributes targetAttributes,
                java.util.jar.Attributes sourceAttributes
        ) {
            Map<String, Object> missingAttributes = new LinkedHashMap<>();
            sourceAttributes.forEach((name, value) -> {
                String attributeName = name.toString();
                if (targetAttributes == null || !targetAttributes.containsKey(attributeName)) {
                    missingAttributes.put(attributeName, value);
                }
            });
            return missingAttributes;
        }
    }
}
