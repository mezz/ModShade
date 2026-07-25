package net.mezzdev.modshade.relocation;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sanitizes Gradle/user-provided strings into Java package-name fragments.
 */
public final class PackageNameSanitizer {
    private PackageNameSanitizer() {
    }

    public static String sanitize(@Nullable String packageName, String fallback) {
        if (packageName == null || packageName.isBlank() || "unspecified".equals(packageName)) {
            return fallback;
        }

        List<String> segments = new ArrayList<>();
        for (String rawSegment : packageName.split("\\.")) {
            String segment = sanitizePackageSegment(rawSegment);
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }

        if (segments.isEmpty()) {
            return fallback;
        }
        return String.join(".", segments);
    }

    private static String sanitizePackageSegment(String rawSegment) {
        StringBuilder sanitized = new StringBuilder();
        for (int i = 0; i < rawSegment.length(); i++) {
            char c = rawSegment.charAt(i);
            if (i == 0) {
                if (Character.isJavaIdentifierStart(c)) {
                    sanitized.append(c);
                } else {
                    sanitized.append('_');
                    if (Character.isJavaIdentifierPart(c)) {
                        sanitized.append(c);
                    }
                }
            } else {
                sanitized.append(Character.isJavaIdentifierPart(c) ? c : '_');
            }
        }

        String result = sanitized.toString().toLowerCase(Locale.ROOT);
        if (result.isBlank()) {
            return "";
        }
        if (!Character.isJavaIdentifierStart(result.charAt(0))) {
            return "_" + result;
        }
        return result;
    }
}
