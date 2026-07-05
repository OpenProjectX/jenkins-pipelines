def call(Map config) {
    def tc      = config.stages?.'integration-test' ?: [:]
    def command = tc.command ?: 'make integration-test'
    def timeoutM = tc.timeout ?: 30

    echo("[Integration Test] command=${command}")
    timeout(time: timeoutM, unit: 'MINUTES') {
        sh(label: 'Integration Test', script: command)
    }

    def junitPattern = tc.reports?.junit
    if (junitPattern) {
        junit allowEmptyResults: true, testResults: junitPattern
    }
}
