import org.pipeline.prgate.PrGateFactory

def call(Map config) {
    def pc       = config.stages?.'pr-gate' ?: [:]
    def provider = pc.provider ?: 'github'

    echo("[PR Gate] provider=${provider}, PR=${env.CHANGE_ID}")
    new PrGateFactory(this).create(provider).check(config)
}
