package net.mezzdev.modshade.relocation;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.Nullable;

import net.mezzdev.modshade.RelocationRule;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

/**
 * Relocates Java source paths and text for ModShade sources jars.
 */
public final class RelocateSourceFilesAction implements Action<FileCopyDetails>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final FileCollection dependencyFiles;
    private final Property<String> relocationBase;
    private final List<RelocationRule> relocationRules;

    private transient @Nullable List<RelocationRule> finalRules;

    public RelocateSourceFilesAction(
            FileCollection dependencyFiles,
            Property<String> relocationBase,
            List<RelocationRule> relocationRules
    ) {
        this.dependencyFiles = dependencyFiles;
        this.relocationBase = relocationBase;
        this.relocationRules = relocationRules;
    }

    @Override
    public void execute(FileCopyDetails details) {
        List<RelocationRule> rules = finalRules();
        if (rules.isEmpty()) {
            return;
        }

        String originalPath = details.getPath();
        String relocatedPath = relocatePath(originalPath, rules);
        if (!originalPath.equals(relocatedPath)) {
            details.setPath(relocatedPath);
        }

        if (originalPath.endsWith(".java")) {
            details.filter(new RelocateSourceLineTransformer(rules));
        }
    }

    private List<RelocationRule> finalRules() {
        List<RelocationRule> rules = finalRules;
        if (rules == null) {
            rules = ModShadeRelocationPlanner.planRelocations(
                            relocationBase.get(),
                            relocationRules,
                            dependencyFiles.getFiles()
                    )
                    .stream()
                    .sorted(Comparator.comparingInt((RelocationRule rule) -> rule.fromPackage().length()).reversed())
                    .toList();
            finalRules = rules;
        }
        return rules;
    }

    private static String relocatePath(String path, List<RelocationRule> rules) {
        for (RelocationRule rule : rules) {
            String fromPath = rule.fromPackage().replace('.', '/') + "/";
            if (path.startsWith(fromPath)) {
                return rule.toPackage().replace('.', '/') + "/" + path.substring(fromPath.length());
            }
        }
        return path;
    }

    private record RelocateSourceLineTransformer(List<RelocationRule> rules) implements Transformer<String, String>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private RelocateSourceLineTransformer(List<RelocationRule> rules) {
            this.rules = List.copyOf(rules);
        }

        @Override
        public String transform(String line) {
            String result = line;
            for (RelocationRule rule : rules) {
                result = result.replace(rule.fromPackage(), rule.toPackage());
            }
            return result;
        }
    }
}
