package net.mezzdev.modshade.validation;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Describes a dependency jar that appears to be a Minecraft mod instead of a
 * plain library.
 */
public record DetectedModJar(File file, List<String> markers, Set<String> loaders) {
    public DetectedModJar(File file, List<String> markers, Set<String> loaders) {
        this.file = Objects.requireNonNull(file, "file");
        this.markers = List.copyOf(markers);
        this.loaders = Collections.unmodifiableSet(new TreeSet<>(loaders));
    }

    public String getLoaderSummary() {
        return String.join("/", loaders);
    }
}
