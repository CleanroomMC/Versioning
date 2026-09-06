package com.cleanroommc.versioning;

import java.io.Serializable;

/**
 * Git inputs used to calculate a version.
 *
 * @param latestTag the highest numeric tag reachable from {@code HEAD}, or {@code null} before the first tag
 * @param target    the version the current branch leads to, or {@code null} outside a development branch
 * @param commits   the number of commits reachable from {@code HEAD}
 * @param exactTag  whether {@code HEAD} is the baseline tag itself
 * @param dirty     whether the worktree has uncommitted changes
 * @param pushed    whether {@code HEAD} is contained in a remote-tracking ref
 */
public record GitState(SemanticVersion latestTag, SemanticVersion target, long commits, boolean exactTag,
                       boolean dirty, boolean pushed) implements Serializable { }
