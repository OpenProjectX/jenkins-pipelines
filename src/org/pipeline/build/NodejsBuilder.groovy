package org.pipeline.build

class NodejsBuilder implements BuildTool, Serializable {
    private final def steps

    NodejsBuilder(steps) {
        this.steps = steps
    }

    @Override
    void build(Map config) {
        def nc          = config.stages?.build?.nodejs ?: [:]
        def pm          = nc.packageManager ?: 'npm'
        def buildScript = nc.buildScript ?: 'build'
        def installCmd  = pm == 'yarn' ? 'yarn install --frozen-lockfile' : 'npm ci'

        withNode(nc.nodeVersion) {
            steps.sh(label: 'Install Dependencies', script: installCmd)
            steps.sh(label: 'Node Build', script: "${pm} run ${buildScript}")
        }
    }

    @Override
    void test(Map config) {
        def nc         = config.stages?.build?.nodejs ?: [:]
        def tc         = config.stages?.'unit-test'?.nodejs ?: [:]
        def pm         = nc.packageManager ?: 'npm'
        def testScript = tc.testScript ?: 'test'

        withNode(nc.nodeVersion) {
            steps.sh(label: 'Node Test', script: "${pm} run ${testScript}")
        }
    }

    private void withNode(String version, Closure body) {
        if (version) {
            steps.nodejs(nodeJSInstallationName: "NodeJS-${version}") {
                body()
            }
        } else {
            body()
        }
    }
}
