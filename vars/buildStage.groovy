import org.pipeline.build.BuildToolFactory
import org.pipeline.utils.Credentials

def call(Map config) {
    def tool = config.stages?.build?.tool ?: 'gradle'
    echo("[Build] tool=${tool}")
    Credentials.withCredentials(this, config.stages?.build?.credentials) {
        BuildToolFactory.create(tool, this).build(config)
    }

    def pattern = config.stages?.build?.archiveArtifacts
    if (pattern) {
        archiveArtifacts artifacts: pattern, allowEmptyArchive: true
    }
}
