package org.pipeline.build

class MavenBuilder implements BuildTool, Serializable {
    private final def steps

    MavenBuilder(steps) {
        this.steps = steps
    }

    @Override
    void build(Map config) {
        def mc       = config.stages?.build?.maven ?: [:]
        def goals    = mc.goals ?: 'clean package -DskipTests'
        def opts     = mc.mavenOpts ?: '-Xmx2g'
        def profiles = mc.profiles ? "-P${(mc.profiles as List).join(',')}" : ''
        def settings = mc.settingsId ? "--settings ${mc.settingsId}" : ''

        steps.withEnv(["MAVEN_OPTS=${opts}"]) {
            withMavenSettings(mc.settingsId) {
                steps.sh(label: 'Maven Build', script: "mvn ${goals} ${profiles} ${settings}".trim())
            }
        }
    }

    @Override
    void test(Map config) {
        def tc    = config.stages?.'unit-test'?.maven ?: [:]
        def mc    = config.stages?.build?.maven ?: [:]
        def goals = tc.goals ?: 'test'
        def opts  = mc.mavenOpts ?: '-Xmx2g'

        steps.withEnv(["MAVEN_OPTS=${opts}"]) {
            withMavenSettings(mc.settingsId) {
                steps.sh(label: 'Maven Test', script: "mvn ${goals}")
            }
        }
    }

    private void withMavenSettings(String settingsId, Closure body) {
        if (settingsId) {
            steps.configFileProvider([steps.configFile(fileId: settingsId, variable: 'MAVEN_SETTINGS')]) {
                steps.withEnv(["MAVEN_ARGS=-s ${steps.env.MAVEN_SETTINGS}"]) {
                    body()
                }
            }
        } else {
            body()
        }
    }
}
