import org.pipeline.build.BuildToolFactory
import org.pipeline.utils.Credentials

def call(Map config) {
    def tool = config.stages?.build?.tool ?: 'gradle'
    echo("[Unit Test] tool=${tool}")
    def credentials = config.stages?.'unit-test'?.credentials ?: config.stages?.build?.credentials
    try {
        Credentials.withCredentials(this, credentials) {
            BuildToolFactory.create(tool, this).test(config)
        }
    } finally {
        def tc      = config.stages?.'unit-test' ?: [:]
        def pattern = tc.reports?.junit ?: '**/build/test-results/**/*.xml'
        junit allowEmptyResults: true, testResults: pattern
        def tarName = tc.archiveTar ? (tc.archiveTar instanceof String ? tc.archiveTar : 'unit-test-reports') : null
        archiveStageArtifacts(tc.archiveArtifacts as String, tarName)
    }
}
