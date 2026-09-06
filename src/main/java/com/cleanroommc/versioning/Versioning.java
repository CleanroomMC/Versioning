package com.cleanroommc.versioning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Computes a tag-native project version from Git state and branch policy.
 */
public final class Versioning {

    // SemVer pre-release identifiers, and a numeric one may not carry a leading zero.
    private static final Pattern IDENTIFIER = Pattern.compile("[0-9A-Za-z-]+");
    private static final Pattern LEADING_ZERO = Pattern.compile("0\\d+");

    /**
     * Computes the version for the given state.
     *
     * @param git      Git state at the build commit
     * @param label    the pre-release label commits are counted under
     * @param metadata additional pre-release identifiers, rendered as {@code .<key>.<value>} in iteration order
     * @return the full version
     * @throws IllegalArgumentException if the label or any metadata entry is not a SemVer identifier, or if the branch
     *                                  leads to a version that has already been tagged
     */
    public static String compute(GitState git, String label, Map<String, String> metadata) {
        SemanticVersion baseline = git.latestTag() == null ? SemanticVersion.INITIAL : git.latestTag();
        SemanticVersion numeric = baseline;
        List<String> identifiers = new ArrayList<>();
        if (!git.exactTag()) {
            numeric = git.target() == null ? baseline.nextPatch() : requireUnreleased(git.target(), baseline);
            identifiers.add(requireLabel(label));
            identifiers.add(Long.toString(git.commits()));
            metadata.forEach((key, value) -> {
                identifiers.add(requireIdentifier("metadata key", key));
                identifiers.add(requireIdentifier("metadata value of '" + key + "'", value));
            });
        }
        // Sitting on a tag is itself evidence of publication
        if (git.dirty() || (!git.pushed() && !git.exactTag())) {
            identifiers.add("local");
            if (git.dirty()) {
                identifiers.add("dirty");
            }
        }
        return identifiers.isEmpty() ? numeric.toString() : numeric + "-" + String.join(".", identifiers);
    }

    private static SemanticVersion requireUnreleased(SemanticVersion target, SemanticVersion baseline) {
        if (target.compareTo(baseline) <= 0) {
            throw new IllegalArgumentException("Branch leads to " + target + ", which tag " + baseline
                    + " has already reached; retarget or delete the branch");
        }
        return target;
    }

    private static String requireLabel(String label) {
        // A numeric label would be ordered against the commit count of another line rather than against a name
        if (label != null && !label.isEmpty() && label.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("versioning.label must not be numeric (got '" + label + "')");
        }
        return requireIdentifier("versioning.label", label);
    }

    private static String requireIdentifier(String name, String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches() || LEADING_ZERO.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be alphanumerics or hyphens, with no leading zero on a "
                    + "number (got '" + value + "')");
        }
        return value;
    }

    private Versioning() { }

}
