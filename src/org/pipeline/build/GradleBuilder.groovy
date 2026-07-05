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
        runGradle(config, 'Gradle Build', tasks)
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
     */
    private void runGradle(Map config, String label, String tasks) {
        def gc         = config.stages?.build?.gradle ?: [:]
        def opts       = gc.gradleOpts ?: ''
        def proxyCli   = ProxySettings.gradleCliArgs(steps, config)
        def gradleOpts = "${opts} ${ProxySettings.gradleJvmOpts(steps, config)}".trim()

        Toolchain.withJdk(steps, config, gc.jdkVersion as String) {
            steps.withEnv(gradleOpts ? ["GRADLE_OPTS=${gradleOpts}"] : []) {
                steps.sh(label: label, script: "./gradlew ${tasks} ${proxyCli}".trim())
            }
        }
    }
}
