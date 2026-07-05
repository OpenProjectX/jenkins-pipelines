package org.pipeline.scan

class TrivyScanner implements Serializable {
    private final def steps

    TrivyScanner(steps) {
        this.steps = steps
    }

    void scan(Map config) {
        def tc          = config.stages?.scan?.trivy ?: [:]
        def image       = tc.image      ?: "${steps.env.IMAGE_NAME}:${steps.env.IMAGE_TAG}"
        def severity    = tc.severity   ?: 'CRITICAL,HIGH'
        def exitCode    = tc.exitCode   != null ? tc.exitCode : 1
        def format      = tc.format     ?: 'table'
        def output      = tc.output     ?: 'trivy-report.txt'
        def ignoreFile  = tc.ignoreFile ?: ''
        def extraArgs   = tc.extraArgs  ?: ''
        def ignoreArg   = ignoreFile ? "--ignorefile ${ignoreFile}" : ''

        steps.sh(label: 'Trivy Image Scan', script: """
            trivy image \\
              --severity ${severity} \\
              --exit-code ${exitCode} \\
              --format ${format} \\
              --output ${output} \\
              ${ignoreArg} ${extraArgs} \\
              ${image}
        """.stripIndent().trim())

        steps.archiveArtifacts artifacts: output, allowEmptyArchive: true
    }
}
