package com.asambataiden.devops.versioning

/** Pure SemVer core: MAJOR.MINOR.PATCH, optionally followed by -SNAPSHOT.
 * Future pre-release/build metadata support must be specified and tested before enabling.
 */
class SemanticVersion implements Serializable {
    final int major
    final int minor
    final int patch
    final boolean snapshot

    SemanticVersion(int major, int minor, int patch, boolean snapshot = false) {
        if ([major, minor, patch].any { it < 0 }) {
            throw new IllegalArgumentException('Version parts must be nonnegative')
        }
        this.major = major
        this.minor = minor
        this.patch = patch
        this.snapshot = snapshot
    }

    static SemanticVersion parse(String input) {
        if (input == null || !(input ==~ /(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)(?:-SNAPSHOT)?/)) {
            throw new IllegalArgumentException('Expected MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-SNAPSHOT')
        }
        boolean snapshot = input.endsWith('-SNAPSHOT')
        String numeric = snapshot ? input.substring(0, input.length() - 9) : input
        def parts = numeric.tokenize('.').collect { Integer.parseInt(it) }
        return new SemanticVersion(parts[0], parts[1], parts[2], snapshot)
    }

    SemanticVersion bump(String type) {
        switch (type) {
            case 'major': return new SemanticVersion(major + 1, 0, 0)
            case 'minor': return new SemanticVersion(major, minor + 1, 0)
            case 'patch': return new SemanticVersion(major, minor, patch + 1)
            default: throw new IllegalArgumentException('type must be major, minor, or patch')
        }
    }

    SemanticVersion asSnapshot() { new SemanticVersion(major, minor, patch, true) }
    SemanticVersion asRelease() { new SemanticVersion(major, minor, patch, false) }

    @Override String toString() { "${major}.${minor}.${patch}${snapshot ? '-SNAPSHOT' : ''}" }
}
