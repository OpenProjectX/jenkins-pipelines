package org.pipeline.build

import org.pipeline.utils.ProxySettings
import org.pipeline.utils.Toolchain

class GradleBuilder implements BuildTool, Serializable {
    private final def steps

    GradleBuilder(steps) {
        this.steps = steps
    }

    @Override
    void build(Map config) {
        def gc    = config.stages?.build?.gradle ?: [:]
        def tasks = gc.tasks ?: 'clean build -x test'
        withRegistryCredentials(config) {
            runGradle(config, 'Gradle Build', tasks)
        }
    }

    /**
     * Optional registry login for in-Gradle image publishing (e.g. Jib pushing
     * to Harbor). When stages.build.registry.credentialsId is set, the build
     * runs with DOCKER_REGISTRY_USER / DOCKER_REGISTRY_PASSWORD in the
     * environment - the same variable names DockerBuilder uses, so tasks can
     * reference them via shell expansion:
     *
     *   tasks: ":app:build -Dquarkus.container-image.push=true " +
     *          "-Dquarkus.container-image.username=\$DOCKER_REGISTRY_USER " +
     *          "-Dquarkus.container-image.password=\$DOCKER_REGISTRY_PASSWORD"
     *
     * Without the block the build runs unchanged.
     */
    private void withRegistryCredentials(Map config, Closure body) {
        def credentialsId = config.stages?.build?.registry?.credentialsId
        if (!credentialsId) {
            body()
            return
        }
        steps.withCredentials([[
            '$class'        : 'UsernamePasswordMultiBinding',
            credentialsId   : credentialsId as String,
            usernameVariable: 'DOCKER_REGISTRY_USER',
            passwordVariable: 'DOCKER_REGISTRY_PASSWORD'
        ]]) {
            body()
        }
    }

    @Override
    void test(Map config) {
        def tc    = config.stages?.'unit-test'?.gradle ?: [:]
        def tasks = tc.tasks ?: 'test'
        runGradle(config, 'Gradle Test', tasks)
    }

    /**
     * gradleOpts and proxy flags go into GRADLE_OPTS so the wrapper/launcher
     * JVM gets them (the Gradle distribution download happens there); proxy
     * flags are ALSO passed as CLI -D properties, which Gradle forwards to
     * the daemon JVM for dependency resolution.
     *
     * JENKINS_NODE_COOKIE=dontKillMe exempts the Gradle (and Kotlin) daemons
     * from Jenkins' ProcessTreeKiller at the end of the build, so they stay
     * warm on long-running agents and later builds reuse them.
     */
    private void runGradle(Map config, String label, String tasks) {
        def gc         = config.stages?.build?.gradle ?: [:]
        def opts       = gc.gradleOpts ?: ''
        def proxyCli   = ProxySettings.gradleCliArgs(steps, config)
        def gradleOpts = "${opts} ${ProxySettings.gradleJvmOpts(steps, config)}".trim()
        def envVars    = ['JENKINS_NODE_COOKIE=dontKillMe']
        if (gradleOpts) {
            envVars << "GRADLE_OPTS=${gradleOpts}"
        }

        Toolchain.withJdk(steps, config, gc.jdkVersion as String) {
            steps.withEnv(envVars) {
                steps.sh(label: label, script: "./gradlew ${tasks} ${proxyCli}".trim())
            }
        }
    }
}
