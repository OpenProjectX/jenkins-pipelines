package org.pipeline.prgate

class GithubPrGate implements PrGate, Serializable {
    private final def steps

    GithubPrGate(steps) {
        this.steps = steps
    }

    @Override
    void check(Map config) {
        def gc        = config.stages?.'pr-gate'?.github ?: [:]
        def credId    = gc.credentialsId ?: 'github-token'
        def context   = gc.statusContext  ?: 'ci/jenkins'

        steps.githubNotify(
            context      : context,
            description  : 'Pipeline running',
            status       : 'PENDING',
            credentialsId: credId
        )

        def required = gc.requiredChecks ?: []
        if (required) {
            steps.echo("[PR Gate] Required checks: ${required.join(', ')}")
        }
    }

    @Override
    void notify(String result, Map config) {
        def gc      = config.stages?.'pr-gate'?.github ?: [:]
        def credId  = gc.credentialsId ?: 'github-token'
        def context = gc.statusContext  ?: 'ci/jenkins'
        def mapped  = mapResult(result)

        steps.githubNotify(
            context      : context,
            description  : mapped.description,
            status       : mapped.status,
            credentialsId: credId
        )
    }

    @NonCPS
    private static Map mapResult(String result) {
        switch (result?.toUpperCase()) {
            case 'SUCCESS':  return [status: 'SUCCESS', description: 'Pipeline passed']
            case 'UNSTABLE': return [status: 'FAILURE', description: 'Pipeline unstable']
            case 'ABORTED':  return [status: 'ERROR',   description: 'Pipeline aborted']
            default:         return [status: 'FAILURE', description: 'Pipeline failed']
        }
    }
}
