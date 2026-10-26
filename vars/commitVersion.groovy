import com.asambataiden.devops.git.GitConfig
import com.asambataiden.devops.versioning.ReleasePolicy

/**
 * Commit an already-validated root pom.xml version change locally.
 * Never pushes, never checks out branches, never stages unrelated files.
 * Call only from trusted pipelines with an authorized release workspace.
 */
def call(Map args = [:]) {
    String branch = GitConfig.requireReleaseBranch(args.branch as String)
    String version = ReleasePolicy.releaseVersion(args.version as String)
    String name = GitConfig.requireIdentity(args.authorName as String)
    String email = GitConfig.requireIdentity(args.authorEmail as String)
    GitConfig.requireVersionFiles(['pom.xml'])

    if (!fileExists('pom.xml')) error('pom.xml missing')
    String actualBranch = sh(script: 'git symbolic-ref --quiet --short HEAD', returnStdout: true).trim()
    if (actualBranch != branch) error('Git HEAD must be on main (not detached)')
    String porcelain = sh(script: 'git status --porcelain --untracked-files=normal', returnStdout: true).trim()
    if (!porcelain) error('No version changes to commit')
    // Deliberately fail if unrelated files have changes or were staged earlier.
    def changed = porcelain.readLines().collect { it.length() >= 4 ? it.substring(3) : '' }
    if (changed != ['pom.xml']) error('Workspace must contain only a pom.xml change')

    sh 'git add -- pom.xml'
    sh 'git diff --cached --quiet -- pom.xml && exit 1 || test "$?" -eq 1'
    withEnv(["GIT_AUTHOR_NAME=${name}", "GIT_AUTHOR_EMAIL=${email}",
             "GIT_COMMITTER_NAME=${name}", "GIT_COMMITTER_EMAIL=${email}"]) {
        sh "git commit -m 'chore(release): version ${version}' --only -- pom.xml"
    }
    return sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
}
