package net.mezzdev.modshade;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * User-provided package relocation override.
 */
public record RelocationRule(String fromPackage, String toPackage) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public RelocationRule(String fromPackage, String toPackage) {
        this.fromPackage = requirePackageName(fromPackage, "fromPackage");
        this.toPackage = requirePackageName(toPackage, "toPackage");
    }

    private static String requirePackageName(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return trimmed;
    }
}
