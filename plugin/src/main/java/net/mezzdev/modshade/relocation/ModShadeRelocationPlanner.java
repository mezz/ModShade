package net.mezzdev.modshade.relocation;

import net.mezzdev.modshade.RelocationRule;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Computes the final relocation rules used by ModShade jar tasks.
 */
public final class ModShadeRelocationPlanner {
    private static final String RULE_SEPARATOR = " -> ";

    private ModShadeRelocationPlanner() {
    }

    public static List<RelocationRule> planRelocations(
            String relocationBase,
            List<RelocationRule> explicitRules,
            Collection<File> dependencyFiles
    ) {
        List<RelocationRule> rules = new ArrayList<>();
        if (!explicitRules.isEmpty()) {
            rules.addAll(explicitRules);
            return rules;
        }

        Set<String> inferredPackageRoots = PackageRootScanner.inferPackageRoots(dependencyFiles);
        String sanitizedRelocationBase = PackageNameSanitizer.sanitize(relocationBase, "modshade");
        for (String packageRoot : inferredPackageRoots) {
            rules.add(new RelocationRule(packageRoot, sanitizedRelocationBase + "." + packageRoot));
        }
        return rules;
    }

    public static String formatRule(RelocationRule rule) {
        return rule.fromPackage() + RULE_SEPARATOR + rule.toPackage();
    }

    public static RelocationRule parseRule(String formattedRule) {
        int separator = formattedRule.indexOf(RULE_SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("Invalid relocation rule: " + formattedRule);
        }
        String fromPackage = formattedRule.substring(0, separator);
        String toPackage = formattedRule.substring(separator + RULE_SEPARATOR.length());
        return new RelocationRule(fromPackage, toPackage);
    }
}
