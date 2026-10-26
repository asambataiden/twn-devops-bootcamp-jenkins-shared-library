import com.asambataiden.devops.git.GitConfig
import com.asambataiden.devops.versioning.ReleasePolicy

/** Create an annotated local release tag only. No remote push. */
def call(Map args = [:]) {
    GitConfig.requireReleaseBranch(args.branch as String)
    String tag = ReleasePolicy.tag(args.version as String)
    String name = GitConfig.requireIdentity(args.authorName as String)
    String email = GitConfig.requireIdentity(args.authorEmail as String)

    String currentBranch = sh(script: 'git symbolic-ref --quiet --short HEAD', returnStdout: true).trim()
    if (currentBranch != 'main') error('Tags require an attached main branch')
    String changes = sh(script: 'git status --porcelain --untracked-files=normal', returnStdout: true).trim()
    if (changes) error('Refuse release tag from a dirty workspace')
    int exists = sh(script: "git show-ref --verify --quiet refs/tags/${tag}", returnStatus: true)
    if (exists == 0) error('Tag already exists: ' + tag)
    if (exists != 1) error('Unable to check local tag')
    withEnv(["GIT_COMMITTER_NAME=${name}", "GIT_COMMITTER_EMAIL=${email}"]) {
        sh "git tag -a ${tag} -m 'Release ${tag}'"
    }
    return tag
}
