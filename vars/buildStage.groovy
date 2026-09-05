import org.pipeline.build.BuildToolFactory
import org.pipeline.utils.Credentials
import org.pipeline.utils.EnvTemplate

def call(Map config) {
    def tool = config.stages?.build?.tool ?: 'gradle'
    echo("[Build] tool=${tool}")
    Credentials.withCredentials(this, config.stages?.build?.credentials) {
        prePull(config)
        BuildToolFactory.create(tool, this).build(config)
    }

    def pattern = config.stages?.build?.archiveArtifacts
    if (pattern) {
        archiveArtifacts artifacts: pattern, allowEmptyArchive: true
    }
}

/**
 * Pre-pull images into the local Docker daemon before the build runs.
 *
 * Needed whenever the build expects images to already exist in the daemon —
 * e.g. a Quarkus Jib build with a docker:// base image, where Jib's daemon
 * mode only inspects, it never pulls. The pulls also warm the agent's
 * docker-graph PVC, so later builds skip the download.
 *
 *   build:
 *     prePull:
 *       - ghcr.io/openprojectx/dockerhub/library/eclipse-temurin:17-jdk
 */
private void prePull(Map config) {
    def images = config.stages?.build?.prePull
    if (!images) {
        return
    }
    EnvTemplate.resolveList(images, this).each { image ->
        sh(label: "Pre-pull ${image}", script: "docker pull ${image}")
    }
}
