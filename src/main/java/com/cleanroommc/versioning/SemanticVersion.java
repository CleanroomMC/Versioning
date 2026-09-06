package com.cleanroommc.versioning;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A strict numeric semantic version used by release tags.
 *
 * @param major major component
 * @param minor minor component
 * @param patch patch component
 */
public record SemanticVersion(long major, long minor, long patch) implements Comparable<SemanticVersion>, Serializable {

    // Tags may carry a leading "v", but built versions won't include it
    private static final Pattern NUMERIC_VERSION = Pattern.compile("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
    // A development branch names the version it leads to, the patch is optional
    private static final Pattern TARGET_VERSION = Pattern.compile("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:\\.(0|[1-9]\\d*))?$");

    /**
     * The baseline used before the first tag exists.
     */
    public static final SemanticVersion INITIAL = new SemanticVersion(0, 0, 0);

    /**
     * Parses a strict numeric version.
     *
     * @param value the text to parse
     * @return the parsed version
     * @throws IllegalArgumentException if the text is not {@code <major>.<minor>.<patch>}, with an optional {@code v} prefix
     */
    public static SemanticVersion parse(String value) {
        Matcher matcher = NUMERIC_VERSION.matcher(value == null ? "" : value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Version tag must be numeric SemVer in the form <major>.<minor>.<patch> or v<major>.<minor>.<patch> (got '" + value + "')");
        }
        return new SemanticVersion(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)), Long.parseLong(matcher.group(3)));
    }

    /**
     * Parses the version a development branch leads to. The patch component may be omitted and has to be zero when
     * present, because a patch is a fix on top of a release rather than something a line is developed towards.
     *
     * @param value the text to parse
     * @return the parsed target
     * @throws IllegalArgumentException if the text names no target, or names one carrying a patch
     */
    public static SemanticVersion parseTarget(String value) {
        Matcher matcher = TARGET_VERSION.matcher(value == null ? "" : value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Development branch must be named <prefix>/<major>.<minor> or <prefix>/<major>.<minor>.0 (got '" + value + "')");
        }
        if (matcher.group(3) != null && !"0".equals(matcher.group(3))) {
            throw new IllegalArgumentException("Development branch '" + value + "' carries a patch component; patch versions are cut on the release branch");
        }
        return new SemanticVersion(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)), 0);
    }

    /**
     * @param value the text to test
     * @return whether the text is a strict numeric version, with or without a {@code v} prefix
     */
    public static boolean isValid(String value) {
        return value != null && NUMERIC_VERSION.matcher(value).matches();
    }

    /**
     * @return the start of the next patch line
     */
    public SemanticVersion nextPatch() {
        return new SemanticVersion(this.major, this.minor, this.patch + 1);
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int result = Long.compare(this.major, other.major);
        if (result != 0) {
            return result;
        }
        result = Long.compare(this.minor, other.minor);
        return result != 0 ? result : Long.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return this.major + "." + this.minor + "." + this.patch;
    }

}
