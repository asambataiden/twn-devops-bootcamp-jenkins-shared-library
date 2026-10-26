package com.asambataiden.devops.cloud

import com.asambataiden.devops.container.DockerImageRef

/** AWS commercial-partition ECR image coordinates. Does not obtain credentials. */
final class EcrImageRef implements Serializable {
    final String accountId
    final String region
    final String repository
    final String tag

    EcrImageRef(String accountId, String region, String repository, String tag) {
        if (!(accountId ==~ /[0-9]{12}/)) {
            throw new IllegalArgumentException('AWS account ID must contain 12 digits')
        }
        if (!(region ==~ /(?:us|eu|ap|sa|ca|me|af|il)-(?:north|south|east|west|central|northeast|northwest|southeast|southwest)-[1-9][0-9]*/)) {
            throw new IllegalArgumentException('Unsupported AWS commercial region')
        }
        if (!(repository ==~ /[a-z][a-z0-9._-]*(?:\/[a-z0-9][a-z0-9._-]*)*/)) {
            throw new IllegalArgumentException('Invalid ECR repository')
        }
        DockerImageRef validated = new DockerImageRef(repository, tag)
        this.accountId = accountId
        this.region = region
        this.repository = repository
        this.tag = tag
    }

    String registry() { "${accountId}.dkr.ecr.${region}.amazonaws.com".toString() }
    String image() { "${registry()}/${repository}:${tag}".toString() }
    String digestRef(String digest) {
        if (!(digest ==~ /sha256:[a-f0-9]{64}/)) {
            throw new IllegalArgumentException('Invalid ECR image digest')
        }
        return "${registry()}/${repository}@${digest}".toString()
    }
}
