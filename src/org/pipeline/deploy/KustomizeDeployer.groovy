package org.pipeline.deploy

class KustomizeDeployer implements Deployer, Serializable {
    private final def steps

    KustomizeDeployer(steps) {
        this.steps = steps
    }

    @Override
    void deploy(Map environment, Map config) {
        def kc        = environment.kustomize ?: [:]
        def path      = kc.path      ?: './k8s'
        def namespace = kc.namespace ?: 'default'
        def ctxArg    = kc.kubeContext ? "--context ${kc.kubeContext}" : ''
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
        def namespace = kc.namespace ?: 'default'
        def ctxArg    = kc.kubeContext ? "--context ${kc.kubeContext}" : ''
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
