package com.cleanroommc.versioning;

/**
 * Computes a SemVer project version from a declared numeric version, a {@link Stage},
 * {@code git describe} output, and optional publish/CI flags.
 *
 * <p>Three modes, in precedence order:
 * <ol>
 *     <li><b>Publish</b> ({@code publish} is {@code true}): the base version verbatim, e.g. {@code 0.6.10-alpha}.
 *     Requires the nearest tag to equal the base version at distance {@code 0}.</li>
 *     <li><b>CI</b> ({@code run} is non-{@code null}): base version plus {@code +build.<distance>.run.<run>}.</li>
 *     <li><b>Local</b> (neither): base version plus {@code +local.<distance>}.</li>
 * </ol>
 *
 * <p>The base version carries the stage as a SemVer prerelease identifier
 * ({@code 0.6.10-alpha}), except for {@link Stage#RELEASE}, which uses the numeric version as-is ({@code 0.6.10}).
 * Note that the stage is independent of the publish flag:
 * an {@code alpha} can be published, and a {@code release} stage can be built locally.
 */
public final class Versioning {

    /**
     * Convenience overload that parses the raw {@code stage} and {@code gitDescribe} strings.
     *
     * @param numeric     declared numeric version, e.g. {@code 0.6.10}
     * @param stage       stage id, one of {@code alpha}, {@code beta}, {@code rc}, {@code release}
     * @param gitDescribe raw {@code git describe --tags --long --always} output, e.g.
     *                    {@code 0.6.10-alpha-48-gf5e9227e}; unparseable or {@code null} input
     *                    is treated as no tag at distance {@code 0}
     * @param publish     {@code true} to emit the base version and validate tag and distance
     * @param run         CI run identifier, or {@code null} for a local build
     * @return the computed version
     * @throws VersioningException if an argument is invalid, or if {@code publish} is {@code true}
     *                             and the tag or distance does not match
     */
    public static ComputedVersion compute(String numeric, String stage, String gitDescribe, boolean publish, String run) {
        return compute(new VersionRequest(numeric, Stage.parse(stage), GitDescribe.parse(gitDescribe), publish, run));
    }

    /**
     * Computes the version for the given request.
     *
     * @param request the inputs; must not be {@code null}
     * @return the computed version
     * @throws VersioningException if {@code request} is {@code null}, or if the request is publishing
     *                             and the nearest tag does not equal {@link VersionRequest#baseVersion()}
     *                             at distance {@code 0}
     */
    public static ComputedVersion compute(VersionRequest request) {
        if (request == null) {
            throw new VersioningException("request is required");
        }
        String base = request.baseVersion();
        GitDescribe git = request.git();
        if (request.publish()) {
            if (!git.tag().equals(base)) {
                throw new VersioningException("Git tag '" + git.tag() + "' does not match gradle.properties version '" + base + "'");
            }
            if (git.distance() != 0) {
                throw new VersioningException("Release must be on a tagged commit (" + git.distance() + " commits ahead of '" + git.tag() + "')");
            }
            return new ComputedVersion(base, base, request.stage(), git, true);
        }
        if (request.run() != null) {
            String version = base + "+build." + git.distance() + ".run." + request.run();
            return new ComputedVersion(version, base, request.stage(), git, false);
        }
        String version = base + "+local." + git.distance();
        return new ComputedVersion(version, base, request.stage(), git, false);
    }

    private Versioning() { }

}
