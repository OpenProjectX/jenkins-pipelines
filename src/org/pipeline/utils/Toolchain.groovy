package org.pipeline.utils

/**
 * Selects the toolchain context for build steps depending on the agent type.
 *
 * On a Kubernetes pod agent (detected via KUBERNETES_SERVICE_HOST, which is
 * injected into every pod) with stages.build.container configured, the body
 * runs inside that pod container — the container image provides the JDK/tools.
 *
 * On a classic (static/VM) agent, it falls back to the Jenkins tool
 * installation "jdk-<version>" when a jdkVersion is configured.
 *
 * This lets the same workflow YAML run on both agent types:
 *
 *   build:
 *     container: jdk17      # used on Kubernetes pod agents
 *     gradle:
 *       jdkVersion: "17"    # used on classic agents (Jenkins JDK tool "jdk-17")
 */
class Toolchain implements Serializable {

    static boolean useContainer(def steps, Map config) {
        containerName(config) && steps.env.KUBERNETES_SERVICE_HOST
    }

    static String containerName(Map config) {
        config.stages?.build?.container
    }

    static void withJdk(def steps, Map config, String jdkVersion, Closure body) {
        if (useContainer(steps, config)) {
            steps.container(containerName(config)) {
                body()
            }
        } else if (jdkVersion) {
            def jdkHome = steps.tool(name: "jdk-${jdkVersion}", type: 'jdk')
            steps.withEnv(["JAVA_HOME=${jdkHome}", "PATH+JDK=${jdkHome}/bin"]) {
                body()
            }
        } else {
            body()
        }
    }
}
