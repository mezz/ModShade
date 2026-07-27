package net.mezzdev.modshade.task;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModShadeJarTest {
    @Test
    void sourceArchiveCanOnlyBeConfiguredOnce() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        ModShadeJar modShadeJar = project.getTasks().register("modShadeJar", ModShadeJar.class).get();
        TaskProvider<Jar> jar = project.getTasks().named("jar", Jar.class);

        modShadeJar.fromArchive(jar);

        assertThrows(InvalidUserDataException.class, () -> modShadeJar.fromArchive(jar));
    }
}
