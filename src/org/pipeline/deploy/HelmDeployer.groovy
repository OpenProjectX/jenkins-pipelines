package org.pipeline.deploy

class HelmDeployer implements Deployer, Serializable {
    private final def steps

    HelmDeployer(steps) {
        this.steps = steps
    }

    @Override
    void deploy(Map environment, Map config) {
        def hc         = environment.helm ?: [:]
        def release    = hc.release    ?: environment.name
        def namespace  = hc.namespace  ?: 'default'
        def chart      = hc.chart      ?: './chart'
        def extraArgs  = hc.extraArgs  ?: '--wait --timeout 10m'
        def kubeCtx    = hc.kubeContext
        def ctxArg     = kubeCtx ? "--kube-context ${kubeCtx}" : ''
        def valuesArgs = (hc.values ?: []).collect { "-f ${it}" }.join(' ')
        def setArgs    = buildSetArgs(hc.set ?: [:])
        def credId     = environment.kubeCredentialsId ?: config.stages?.deploy?.kubeCredentialsId

        withKubeCredentials(credId) {
            steps.sh(label: "Helm Deploy [${environment.name}]", script: """
                helm upgrade --install ${release} ${chart} \\
                  --namespace ${namespace} --create-namespace \\
                  ${ctxArg} ${valuesArgs} ${setArgs} ${extraArgs}
            """.stripIndent().trim())
        }
    }

    @Override
    void rollback(Map environment, Map config) {
        def hc        = environment.helm ?: [:]
        def release   = hc.release   ?: environment.name
        def namespace = hc.namespace ?: 'default'
        def ctxArg    = hc.kubeContext ? "--kube-context ${hc.kubeContext}" : ''
        def credId    = environment.kubeCredentialsId ?: config.stages?.deploy?.kubeCredentialsId

        withKubeCredentials(credId) {
            steps.sh(label: "Helm Rollback [${environment.name}]", script:
                "helm rollback ${release} 0 --namespace ${namespace} ${ctxArg}".trim()
            )
        }
    }

    @NonCPS
    private static String buildSetArgs(Map setMap) {
        setMap?.collect { k, v -> "--set ${k}=${v}" }?.join(' ') ?: ''
    }

    private void withKubeCredentials(String credId, Closure body) {
        if (credId) {
            steps.withCredentials([steps.file(credentialsId: credId, variable: 'KUBECONFIG')]) {
                body()
            }
        } else {
            body()
        }
    }
}
