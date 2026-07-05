package org.pipeline.scan

import org.pipeline.utils.Toolchain

class SonarScanner implements Serializable {
    private final def steps

    SonarScanner(steps) {
        this.steps = steps
    }

    void scan(Map config) {
        def sc          = config.stages?.scan?.sonar ?: [:]
        def serverName  = sc.serverName    ?: 'SonarQube'
        def projectKey  = sc.projectKey    ?: steps.env.JOB_BASE_NAME
        def extraProps  = sc.extraProperties ?: ''
        def qgWait      = sc.qualityGateWait != false
        def qgTimeout   = sc.timeout        ?: 5
        def buildTool   = config.stages?.build?.tool?.toLowerCase() ?: 'gradle'

        def jdkVersion = config.stages?.build?."${buildTool}"?.jdkVersion

        steps.withSonarQubeEnv(serverName) {
            Toolchain.withJdk(steps, config, jdkVersion as String) {
                switch (buildTool) {
                    case 'gradle':
                        steps.sh(label: 'SonarQube Analysis', script: """
                            ./gradlew sonarqube -Dsonar.projectKey=${projectKey} ${extraProps}
                        """.stripIndent().trim())
                        break
                    case 'maven':
                        steps.sh(label: 'SonarQube Analysis', script: """
                            mvn sonar:sonar -Dsonar.projectKey=${projectKey} ${extraProps}
                        """.stripIndent().trim())
                        break
                    default:
                        steps.sh(label: 'SonarQube Analysis', script: """
                            sonar-scanner -Dsonar.projectKey=${projectKey} ${extraProps}
                        """.stripIndent().trim())
                }
            }
        }

        if (qgWait) {
            steps.timeout(time: qgTimeout, unit: 'MINUTES') {
                def qg = steps.waitForQualityGate()
                if (qg.status != 'OK') {
                    steps.error("SonarQube Quality Gate failed: status=${qg.status}")
                }
            }
        }
    }
}
