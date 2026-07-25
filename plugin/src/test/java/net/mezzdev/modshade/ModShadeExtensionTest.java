package net.mezzdev.modshade;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModShadeExtensionTest {
    @Test
    void defaultRelocationBaseUsesSanitizedProjectGroup() {
        Project project = ProjectBuilder.builder().build();
        project.setGroup("Com.Example-Config.123lib");

        assertEquals("com.example_config._123lib.modshade", ModShadeExtension.defaultRelocationBase(project));
    }

    @Test
    void defaultRelocationBaseFallsBackForUnspecifiedGroup() {
        Project project = ProjectBuilder.builder().build();

        assertEquals("modshade", ModShadeExtension.defaultRelocationBase(project));
    }
}
