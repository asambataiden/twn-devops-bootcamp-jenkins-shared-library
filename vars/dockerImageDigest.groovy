import com.asambataiden.devops.container.DockerImageRef

/**
 * Queries a registry manifest digest using buildx imagetools (not local image ID).
 * Caller must already be authenticated if the repository is private.
 */
def call(Map args = [:]) {
    if (!(args.repository instanceof String) || !(args.tag instanceof String)) {
        error('dockerImageDigest requires repository and tag strings')
    }
    String image = new DockerImageRef(args.repository, args.tag).toString()
    String result = sh(
        script: "docker buildx imagetools inspect --format '{{.Manifest.Digest}}' ${image}",
        returnStdout: true
    ).trim()
    if (!(result ==~ /sha256:[a-f0-9]{64}/)) {
        error('Registry did not return a valid sha256 manifest digest')
    }
    return "${args.repository}@${result}".toString()
}
