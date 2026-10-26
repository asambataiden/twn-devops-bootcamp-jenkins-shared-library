package com.asambataiden.devops.container

/** Conservative, shell-safe image coordinates for CI; tags must not be mutable aliases. */
final class DockerImageRef implements Serializable {
    final String repository
    final String tag

    DockerImageRef(String repository, String tag) {
        if (repository == null || !(repository ==~ /[a-z0-9]+(?:[._-][a-z0-9]+)*(?:\/[a-z0-9]+(?:[._-][a-z0-9]+)*)*(?::[0-9]{2,5})?(?:\/[a-z0-9]+(?:[._-][a-z0-9]+)*)*/)) {
            throw new IllegalArgumentException('Unsupported image repository format')
        }
        if (tag == null || !(tag ==~ /[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}/) ||
            tag in ['latest', 'stable', 'main', 'master', 'dev']) {
            throw new IllegalArgumentException('An explicit non-floating image tag is required')
        }
        this.repository = repository
        this.tag = tag
    }

    String toString() { "${repository}:${tag}" }
}
