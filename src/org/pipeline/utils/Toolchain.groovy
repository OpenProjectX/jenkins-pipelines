package org.pipeline.utils

/**
 * Selects the toolchain context for build steps depending on the agent type.
 *
 * On a Kubernetes pod agent (detected via KUBERNETES_SERVICE_HOST, which is
 * injected into every pod) with stages.build.container configured, the body
 * runs inside that pod container. Without a sidecar container, the body stays
 * in the inbound agent container and uses JAVA<version>_HOME when available.
 *
 * On a classic (static/VM) agent, it falls back to the Jenkins tool
 * installation "jdk-<version>" when a jdkVersion is configured.
 *
 * This lets the same workflow YAML run on both agent types:
 *
 *   build:
 *     gradle:
 *       jdkVersion: "17"    # k8s: JAVA17_HOME; classic: Jenkins tool "jdk-17"
 */
class Toolchain implements Serializable {

    static boolean isKubernetesAgent(def steps) {
        steps.env.KUBERNETES_SERVICE_HOST
    }

    static boolean useContainer(def steps, Map config) {
        containerName(config) && isKubernetesAgent(steps)
    }

    static String containerName(Map config) {
        config.stages?.build?.container
    }

    static void withJdk(def steps, Map config, String jdkVersion, Closure body) {
        if (useContainer(steps, config)) {
            steps.container(containerName(config)) {
                body()
            }
        } else if (isKubernetesAgent(steps) && jdkVersion) {
            def javaHome = steps.env["JAVA${majorVersion(jdkVersion)}_HOME"]
            if (javaHome) {
                steps.withEnv(["JAVA_HOME=${javaHome}", "PATH+JDK=${javaHome}/bin"]) {
                    body()
                }
            } else {
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

    private static String majorVersion(String version) {
        version.tokenize('.')[0]
    }
}
