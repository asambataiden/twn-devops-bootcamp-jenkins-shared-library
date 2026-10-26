import com.asambataiden.devops.versioning.MavenVersion

/** Changes only the root pom.xml project version. Does not commit, tag, push or deploy.
 * Requires Maven and Maven Central access/cached pinned Maven plugins.
 */
def call(Map args = [:]) {
    if (!(args.expectedVersion instanceof String) || !(args.newVersion instanceof String)) {
        error('updateMavenVersion requires expectedVersion and newVersion strings')
    }
    String expected = MavenVersion.validate(args.expectedVersion)
    String next = MavenVersion.validate(args.newVersion)
    if (expected == next) error('Target version must differ from current version')
    if (!fileExists('pom.xml')) error('pom.xml missing')

    String readCmd = 'mvn -B -ntp -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout'
    MavenVersion.requireExpected(sh(script: readCmd, returnStdout: true).trim(), expected)
    sh 'mvn -B -ntp org.codehaus.mojo:versions-maven-plugin:2.19.1:set -DnewVersion=' + next + ' -DgenerateBackupPoms=false -DprocessAllModules=false'
    MavenVersion.requireExpected(sh(script: readCmd, returnStdout: true).trim(), next)
    return next
}
