import org.pipeline.build.BuildToolFactory

def call(Map config) {
    def tool = config.stages?.build?.tool ?: 'gradle'
    echo("[Unit Test] tool=${tool}")
    try {
        BuildToolFactory.create(tool, this).test(config)
    } finally {
        def tc      = config.stages?.'unit-test' ?: [:]
        def pattern = tc.reports?.junit ?: '**/build/test-results/**/*.xml'
        junit allowEmptyResults: true, testResults: pattern
    }
}
