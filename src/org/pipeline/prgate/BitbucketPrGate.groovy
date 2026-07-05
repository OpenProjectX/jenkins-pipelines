package org.pipeline.prgate

class BitbucketPrGate implements PrGate, Serializable {
    private final def steps

    BitbucketPrGate(steps) {
        this.steps = steps
    }

    @Override
    void check(Map config) {
        def bc       = config.stages?.'pr-gate'?.bitbucket ?: [:]
        def credId   = bc.credentialsId ?: 'bitbucket-token'
        def buildKey = bc.buildKey      ?: 'jenkins-ci'

        steps.bitbucketStatusNotify(
            buildState      : 'INPROGRESS',
            buildKey        : buildKey,
            buildDescription: 'Pipeline running',
            credentialsId   : credId
        )
    }

    @Override
    void notify(String result, Map config) {
        def bc       = config.stages?.'pr-gate'?.bitbucket ?: [:]
        def credId   = bc.credentialsId ?: 'bitbucket-token'
        def buildKey = bc.buildKey      ?: 'jenkins-ci'
        def mapped   = mapResult(result)

        steps.bitbucketStatusNotify(
            buildState      : mapped.state,
            buildKey        : buildKey,
            buildDescription: mapped.description,
            credentialsId   : credId
        )
    }

    @NonCPS
    private static Map mapResult(String result) {
        switch (result?.toUpperCase()) {
            case 'SUCCESS':  return [state: 'SUCCESSFUL', description: 'Pipeline passed']
            case 'UNSTABLE': return [state: 'FAILED',     description: 'Pipeline unstable']
            default:         return [state: 'FAILED',     description: 'Pipeline failed']
        }
    }
}
