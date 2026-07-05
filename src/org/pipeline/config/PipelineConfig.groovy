package org.pipeline.config

class PipelineConfig implements Serializable {

    static final Map DEFAULTS = [
        name   : 'jenkins-pipeline',
        agent  : 'any',
        options: [
            timeout                : 60,
            disableConcurrentBuilds: true,
            abortPreviousBuilds    : false,
            buildsToKeep           : 20,
            artifactsToKeep        : 5
        ],
        stages : [
            checkout          : [
                enabled        : true,
                submodules     : false,
                lfs            : false,
                shallow        : true,
                depth          : 10,
                printCommitInfo: true
            ],
            build             : [
                enabled : true,
                tool    : 'gradle',
                gradle  : [
                    tasks      : 'clean build -x test',
                    jdkVersion : '17',
                    gradleOpts : '-Xmx2g'
                ],
                maven   : [
                    goals      : 'clean package -DskipTests',
                    mavenOpts  : '-Xmx2g'
                ],
                nodejs  : [
                    packageManager: 'npm',
                    buildScript   : 'build'
                ]
            ],
            'unit-test'       : [
                enabled: true,
                gradle : [tasks: 'test'],
                maven  : [goals: 'test'],
                nodejs : [testScript: 'test'],
                reports: [
                    junit: '**/build/test-results/**/*.xml'
                ]
            ],
            scan              : [
                sonar: [
                    enabled         : false,
                    serverName      : 'SonarQube',
                    qualityGateWait : true,
                    timeout         : 5
                ],
                trivy: [
                    enabled  : false,
                    severity : 'CRITICAL,HIGH',
                    exitCode : 1,
                    format   : 'table',
                    output   : 'trivy-report.txt'
                ]
            ],
            deploy            : [
                enabled     : false,
                environments: []
            ],
            'integration-test': [
                enabled: false,
                timeout: 30
            ],
            'pr-gate'         : [
                enabled  : true,
                provider : 'github',
                github   : [
                    credentialsId: 'github-token',
                    statusContext: 'ci/jenkins'
                ],
                bitbucket: [
                    credentialsId: 'bitbucket-token',
                    buildKey     : 'jenkins-ci'
                ]
            ]
        ]
    ]
}
