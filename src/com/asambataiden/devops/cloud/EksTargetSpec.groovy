package com.asambataiden.devops.cloud

/**
 * Validated AWS EKS destination. AWS identity and Kubernetes RBAC are
 * verified separately at runtime. This class never accesses AWS.
 */
final class EksTargetSpec implements Serializable {
    final String accountId
    final String region
    final String cluster

    EksTargetSpec(String accountId, String region, String cluster) {
        if (!(accountId ==~ /[0-9]{12}/)) {
            throw new IllegalArgumentException('Expected a 12-digit AWS account ID')
        }
        if (!(region ==~ /(?:us|eu|ap|sa|ca|me|af|il)-(?:north|south|east|west|central|northeast|northwest|southeast|southwest)-[1-9][0-9]*/)) {
            throw new IllegalArgumentException('Unsupported AWS region')
        }
        if (!(cluster ==~ /[A-Za-z0-9][A-Za-z0-9_-]{0,99}/)) {
            throw new IllegalArgumentException('Invalid EKS cluster name')
        }
        this.accountId = accountId
        this.region = region
        this.cluster = cluster
    }

    String arn() {
        return "arn:aws:eks:${region}:${accountId}:cluster/${cluster}".toString()
    }
}
