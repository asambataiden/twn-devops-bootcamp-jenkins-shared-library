/**
 * Build the Maven application after verification; no Docker or Git side effects.
 * CI should normally run this only after successful mavenVerify().
 */
def call(Map args = [:]) {
    if (!fileExists('pom.xml')) error('Cannot build Maven project: pom.xml missing')
    if (!args.keySet().every { it == 'skipTests' }) {
        error('mavenBuild only supports optional skipTests: boolean')
    }
    if (args.containsKey('skipTests') && !(args.skipTests instanceof Boolean)) {
        error('skipTests must be boolean')
    }
    if (args.skipTests == true) {
        // Test skipping should only be permitted after an earlier verified test stage.
        sh 'mvn -B -ntp -DskipTests package'
    } else {
        sh 'mvn -B -ntp package'
    }
    archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: false, fingerprint: true
}
