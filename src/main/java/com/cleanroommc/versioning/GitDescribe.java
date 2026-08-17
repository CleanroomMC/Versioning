package com.cleanroommc.versioning;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed {@code git describe --tags --long --always} output.
 */
public final class GitDescribe {

    private static final Pattern GIT_DESCRIBE = Pattern.compile("^(.+)-(\\d+)-g([0-9a-f]+)$");

    private final String tag;
    private final int distance;

    /**
     * @param tag      nearest matching tag, or empty when describe did not produce {@code <tag>-<n>-g<hash>}
     * @param distance commits since that tag, or {@code 0} when the output could not be parsed
     */
    public GitDescribe(String tag, int distance) {
        this.tag = tag == null ? "" : tag;
        if (distance < 0) {
            throw new VersioningException("git distance must be >= 0 (got " + distance + ")");
        }
        this.distance = distance;
    }

    public static GitDescribe parse(String raw) {
        if (raw == null) {
            return missing();
        }
        raw = raw.trim();
        if (raw.isEmpty()) {
            return missing();
        }
        Matcher matcher = GIT_DESCRIBE.matcher(raw);
        if (!matcher.matches()) {
            return missing();
        }
        return new GitDescribe(matcher.group(1), Integer.parseInt(matcher.group(2)));
    }

    public static GitDescribe missing() {
        return new GitDescribe("", 0);
    }

    public String tag() {
        return this.tag;
    }

    public int distance() {
        return this.distance;
    }

    public boolean isMissing() {
        return this.tag.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GitDescribe that = (GitDescribe) o;
        return this.distance == that.distance && this.tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.tag, this.distance);
    }

    @Override
    public String toString() {
        return "GitDescribe[tag=" + this.tag + ", distance=" + this.distance + "]";
    }

}
