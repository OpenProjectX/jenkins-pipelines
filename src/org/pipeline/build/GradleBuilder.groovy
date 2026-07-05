package org.pipeline.build

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
        def opts  = gc.gradleOpts ?: ''

        Toolchain.withJdk(steps, config, gc.jdkVersion as String) {
            steps.sh(label: 'Gradle Build', script: "./gradlew ${tasks} ${opts}".trim())
        }
    }

    @Override
    void test(Map config) {
        def tc    = config.stages?.'unit-test'?.gradle ?: [:]
        def gc    = config.stages?.build?.gradle ?: [:]
        def tasks = tc.tasks ?: 'test'
        def opts  = gc.gradleOpts ?: ''

        Toolchain.withJdk(steps, config, gc.jdkVersion as String) {
            steps.sh(label: 'Gradle Test', script: "./gradlew ${tasks} ${opts}".trim())
        }
    }
}
