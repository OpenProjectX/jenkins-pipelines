/**
 * Main CI pipeline entry point for the shared library.
 *
 * Usage in downstream Jenkinsfile:
 *
 *   @Library('jenkins-pipelines') _
 *   ciPipeline()
 *
 * With custom workflow file or runtime overrides:
 *
 *   ciPipeline(
 *     workflowFile: 'custom.yaml',
 *     overrides: [agent: 'linux-large']
 *   )
 *
 * Configuration is loaded from .jenkins/workflows/ci.yaml in the consuming repo.
 */

import org.pipeline.config.YamlConfigLoader
import org.pipeline.prgate.PrGateFactory
import org.pipeline.utils.Logger

def call(Map params = [:]) {
    def log = new Logger(this)
    def config

    node(params.agent ?: 'any') {
        try {
            stage('Checkout') {
                checkout scm
                config = new YamlConfigLoader(this).load(params)

                properties([
                    buildDiscarder(logRotator(
                        numToKeepStr         : "${config.options?.buildsToKeep ?: 20}",
                        artifactNumToKeepStr : "${config.options?.artifactsToKeep ?: 5}"
                    )),
                    disableConcurrentBuilds(
                        abortPrevious: config.options?.abortPreviousBuilds ?: false
                    )
                ])

                checkoutStage(config)
            }

            timeout(time: config.options?.timeout ?: 60, unit: 'MINUTES') {
                ansiColor('xterm') {

                    if (config.stages?.build?.enabled != false) {
                        stage('Build') {
                            buildStage(config)
                        }
                    }

                    if (config.stages?.'unit-test'?.enabled != false) {
                        stage('Unit Test') {
                            unitTestStage(config)
                        }
                    }

                    def sc = config.stages?.scan
                    if (sc && (sc.sonar?.enabled != false || sc.trivy?.enabled == true)) {
                        stage('Scan') {
                            scanStage(config)
                        }
                    }

                    if (config.stages?.deploy?.enabled != false) {
                        stage('Deploy') {
                            deployStage(config)
                        }
                    }

                    if (config.stages?.'integration-test'?.enabled == true) {
                        stage('Integration Test') {
                            integrationTestStage(config)
                        }
                    }

                    if (config.stages?.'pr-gate'?.enabled != false && env.CHANGE_ID) {
                        stage('PR Gate') {
                            prGateStage(config)
                        }
                    }

                }
            }

            currentBuild.result = currentBuild.result ?: 'SUCCESS'

        } catch (e) {
            currentBuild.result = 'FAILURE'
            log.error("Pipeline FAILED — ${env.JOB_NAME} #${env.BUILD_NUMBER} — ${env.BUILD_URL}")
            throw e
        } finally {
            if (config?.stages?.'pr-gate'?.enabled != false && env.CHANGE_ID) {
                def result   = currentBuild.result ?: 'SUCCESS'
                def provider = config.stages.'pr-gate'.provider ?: 'github'
                try {
                    new PrGateFactory(this).create(provider).notify(result, config)
                } catch (notifyErr) {
                    log.warn("PR gate notification failed: ${notifyErr.message}")
                }
            }
            cleanWs notFailBuild: true
        }
    }
}
