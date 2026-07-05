package org.pipeline.build

import org.pipeline.utils.Toolchain

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

        withNode(config, nc.nodeVersion as String) {
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

        withNode(config, nc.nodeVersion as String) {
            steps.sh(label: 'Node Test', script: "${pm} run ${testScript}")
        }
    }

    private void withNode(Map config, String version, Closure body) {
        if (Toolchain.useContainer(steps, config)) {
            steps.container(Toolchain.containerName(config)) {
                body()
            }
        } else if (version) {
            steps.nodejs(nodeJSInstallationName: "NodeJS-${version}") {
                body()
            }
        } else {
            body()
        }
    }
}
