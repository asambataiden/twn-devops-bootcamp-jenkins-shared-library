import com.asambataiden.devops.container.DockerImageRef

/** Builds a Docker image from a fixed root context. Does not log in or push. */
def call(Map args = [:]) {
    if (!(args.repository instanceof String) || !(args.tag instanceof String)) {
        error('dockerBuild requires repository and tag strings')
    }
    String image = new DockerImageRef(args.repository, args.tag).toString()
    if (!fileExists('Dockerfile')) error('Dockerfile missing from workspace root')
    sh "docker build --pull --tag ${image} --file Dockerfile ."
    return image
}
