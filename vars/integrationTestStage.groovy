def call(Map config) {
    def tc      = config.stages?.'integration-test' ?: [:]
    def command = tc.command ?: 'make integration-test'
    def timeoutM = tc.timeout ?: 30

    echo("[Integration Test] command=${command}")
    try {
        timeout(time: timeoutM, unit: 'MINUTES') {
            sh(label: 'Integration Test', script: command)
        }
    } finally {
        def junitPattern = tc.reports?.junit
        if (junitPattern) {
            junit allowEmptyResults: true, testResults: junitPattern
        }
        if (tc.archiveArtifacts) {
            archiveArtifacts artifacts: tc.archiveArtifacts, allowEmptyArchive: true
        }
    }
}
