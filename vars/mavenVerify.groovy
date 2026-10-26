/**
 * Verify Maven project with test reporting.
 * Caller supplies a trusted Jenkins agent with Maven and Java available.
 * No Git writes, registry login, deployment, or release operations.
 */
def call(Map args = [:]) {
    String pom = args.get('pom', 'pom.xml')
    if (pom != 'pom.xml') error('Only the root pom.xml is supported for now')
    if (!fileExists(pom)) error('Cannot verify Maven project: pom.xml missing')
    try {
        sh 'mvn -B -ntp verify'
    } finally {
        junit allowEmptyResults: false, testResults: '**/target/surefire-reports/TEST-*.xml,**/target/failsafe-reports/TEST-*.xml'
    }
}
