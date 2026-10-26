package com.asambataiden.devops.versioning
final class MavenVersion implements Serializable {
    static String validate(String value) {
        return SemanticVersion.parse(value).toString()
    }
    static String next(String current, String type, boolean snapshot = false) {
        def nextVersion = SemanticVersion.parse(validate(current)).bump(type)
        return (snapshot ? nextVersion.asSnapshot() : nextVersion).toString()
    }
    static void requireExpected(String actual, String expected) {
        if (validate(actual) != validate(expected)) {
            throw new IllegalStateException('Maven version differs from expected version')
        }
    }
}
