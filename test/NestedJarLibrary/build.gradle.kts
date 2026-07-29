plugins {
    `java-library`
}

base {
    archivesName.set("nested-jar-library")
}

java {
    withSourcesJar()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Automatic-Module-Name" to "net.mezzdev.modshade.integration.nestedjar")
    }
}
