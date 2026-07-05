package org.pipeline.build

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

        withJdk(gc.jdkVersion) {
            steps.sh(label: 'Gradle Build', script: "./gradlew ${tasks} ${opts}".trim())
        }
    }

    @Override
    void test(Map config) {
        def tc    = config.stages?.'unit-test'?.gradle ?: [:]
        def gc    = config.stages?.build?.gradle ?: [:]
        def tasks = tc.tasks ?: 'test'
        def opts  = gc.gradleOpts ?: ''

        withJdk(gc.jdkVersion) {
            steps.sh(label: 'Gradle Test', script: "./gradlew ${tasks} ${opts}".trim())
        }
    }

    private void withJdk(String version, Closure body) {
        if (version) {
            def jdkHome = steps.tool(name: "jdk-${version}", type: 'jdk')
            steps.withEnv(["JAVA_HOME=${jdkHome}", "PATH+JDK=${jdkHome}/bin"]) {
                body()
            }
        } else {
            body()
        }
    }
}
