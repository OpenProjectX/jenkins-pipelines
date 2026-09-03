package org.pipeline.deploy

import org.pipeline.utils.EnvTemplate

class HelmDeployer implements Deployer, Serializable {
    private final def steps

    HelmDeployer(steps) {
        this.steps = steps
    }

    @Override
    void deploy(Map environment, Map config) {
        def hc         = environment.helm ?: [:]
        def release    = EnvTemplate.resolve(hc.release ?: environment.name, steps)
        def namespace  = EnvTemplate.resolve(hc.namespace ?: 'default', steps)
        def chart      = EnvTemplate.resolve(hc.chart ?: './chart', steps)
        def extraArgs  = hc.extraArgs  ?: '--wait --timeout 10m'
        def kubeCtx    = EnvTemplate.resolve(hc.kubeContext, steps)
        def ctxArg     = kubeCtx ? "--kube-context ${kubeCtx}" : ''
        def valuesArgs = EnvTemplate.resolveList(hc.values ?: [], steps).collect { "-f ${shellQuote(it)}" }.join(' ')
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
        def release   = EnvTemplate.resolve(hc.release ?: environment.name, steps)
        def namespace = EnvTemplate.resolve(hc.namespace ?: 'default', steps)
        def kubeCtx   = EnvTemplate.resolve(hc.kubeContext, steps)
        def ctxArg    = kubeCtx ? "--kube-context ${kubeCtx}" : ''
        def credId    = environment.kubeCredentialsId ?: config.stages?.deploy?.kubeCredentialsId

        withKubeCredentials(credId) {
            steps.sh(label: "Helm Rollback [${environment.name}]", script:
                "helm rollback ${release} 0 --namespace ${namespace} ${ctxArg}".trim()
            )
        }
    }

    private String buildSetArgs(Map setMap) {
        EnvTemplate.resolveMap(setMap, steps)?.collect { k, v -> "--set ${k}=${shellQuote(v as String)}" }?.join(' ') ?: ''
    }

    private static String shellQuote(String value) {
        "'${value.replace("'", "'\"'\"'")}'"
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
