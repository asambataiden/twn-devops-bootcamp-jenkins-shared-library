package com.asambataiden.devops.versioning
final class ReleasePolicy implements Serializable {
    static String releaseVersion(String input) {
        def version = SemanticVersion.parse(input)
        if (version.snapshot) throw new IllegalArgumentException('Cannot release a SNAPSHOT version')
        return version.toString()
    }
    static String releaseBranch(String branch) {
        if (branch != 'main') throw new IllegalArgumentException('Releases must originate from main')
        return branch
    }
    static String tag(String version) { 'v' + releaseVersion(version) }
}
