package com.asambataiden.devops.git

/** Pure validation of release Git parameters. No I/O or Jenkins dependencies. */
final class GitConfig implements Serializable {
    static final List<String> VERSION_FILES = ['pom.xml'].asImmutable()

    static String requireBranch(String branch) {
        if (!(branch ==~ /[A-Za-z0-9][A-Za-z0-9._\/-]*/)) {
            throw new IllegalArgumentException('Invalid Git branch')
        }
        if (branch.contains('..') || branch.contains('//') || branch.endsWith('/') ||
            branch.endsWith('.') || branch.contains('@{') || branch.endsWith('.lock')) {
            throw new IllegalArgumentException('Unsafe Git branch')
        }
        return branch
    }

    static String requireReleaseBranch(String branch) {
        if (requireBranch(branch) != 'main') {
            throw new IllegalArgumentException('Release Git operations require main')
        }
        return branch
    }

    static List<String> requireVersionFiles(List<String> paths) {
        if (!paths || paths.any { !(it in VERSION_FILES) } || paths.size() != paths.unique(false).size()) {
            throw new IllegalArgumentException('Only distinct approved version files are allowed')
        }
        return paths.asImmutable()
    }

    static String requireIdentity(String value) {
        if (value == null || !value.trim() || value.contains('\n') || value.contains('\r') ||
            value.contains('\u0000') || value.startsWith('-')) {
            throw new IllegalArgumentException('Invalid Git identity')
        }
        return value
    }
}
