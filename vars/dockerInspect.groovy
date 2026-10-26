import com.asambataiden.devops.container.DockerImageRef

/** Returns local Docker image ID (sha256 digest); does not contact registry. */
def call(Map args = [:]) {
    if (!(args.repository instanceof String) || !(args.tag instanceof String)) {
        error('dockerInspect requires repository and tag strings')
    }
    String image = new DockerImageRef(args.repository, args.tag).toString()
    String id = sh(script: "docker image inspect --format '{{.Id}}' ${image}", returnStdout: true).trim()
    if (!(id ==~ /sha256:[a-f0-9]{64}/)) error('Docker returned an invalid image ID')
    return id
}
