package org.pipeline.prgate

class PrGateFactory implements Serializable {
    private final def steps

    PrGateFactory(steps) {
        this.steps = steps
    }

    PrGate create(String provider) {
        switch (provider?.toLowerCase()) {
            case 'github':    return new GithubPrGate(steps)
            case 'bitbucket': return new BitbucketPrGate(steps)
            default:
                throw new IllegalArgumentException(
                    "Unsupported PR gate provider: '${provider}'. Supported: github, bitbucket"
                )
        }
    }
}
