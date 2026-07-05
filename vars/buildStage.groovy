import org.pipeline.build.BuildToolFactory

def call(Map config) {
    def tool = config.stages?.build?.tool ?: 'gradle'
    echo("[Build] tool=${tool}")
    BuildToolFactory.create(tool, this).build(config)

    def pattern = config.stages?.build?.archiveArtifacts
    if (pattern) {
        archiveArtifacts artifacts: pattern, allowEmptyArchive: true
    }
}
