package org.pipeline.deploy

import org.pipeline.utils.EnvTemplate

class KustomizeDeployer implements Deployer, Serializable {
    private final def steps

    KustomizeDeployer(steps) {
        this.steps = steps
    }

    @Override
    void deploy(Map environment, Map config) {
        def kc        = environment.kustomize ?: [:]
        def path      = EnvTemplate.resolve(kc.path ?: './k8s', steps)
        def namespace = EnvTemplate.resolve(kc.namespace ?: 'default', steps)
        def kubeCtx   = EnvTemplate.resolve(kc.kubeContext, steps)
        def ctxArg    = kubeCtx ? "--context ${kubeCtx}" : ''
        def credId    = environment.kubeCredentialsId ?: config.stages?.deploy?.kubeCredentialsId

        withKubeCredentials(credId) {
            steps.sh(label: "Kustomize Deploy [${environment.name}]", script: """
                kubectl apply -k ${path} --namespace ${namespace} ${ctxArg}
                kubectl rollout status deployment --namespace ${namespace} ${ctxArg} --timeout=10m
            """.stripIndent().trim())
        }
    }

    @Override
    void rollback(Map environment, Map config) {
        def kc        = environment.kustomize ?: [:]
        def namespace = EnvTemplate.resolve(kc.namespace ?: 'default', steps)
        def kubeCtx   = EnvTemplate.resolve(kc.kubeContext, steps)
        def ctxArg    = kubeCtx ? "--context ${kubeCtx}" : ''
        def credId    = environment.kubeCredentialsId ?: config.stages?.deploy?.kubeCredentialsId

        withKubeCredentials(credId) {
            steps.sh(label: "Kustomize Rollback [${environment.name}]", script:
                "kubectl rollout undo deployment --namespace ${namespace} ${ctxArg}".trim()
            )
        }
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
