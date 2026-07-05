package org.pipeline.utils

/**
 * Resolves HTTP proxy settings for build tools.
 *
 * Explicit config wins:
 *
 *   build:
 *     gradle:
 *       proxy:
 *         host: 192.168.0.89
 *         port: 10809
 *         noProxy: "localhost,127.0.0.1,.svc.cluster.local"
 *
 * Otherwise falls back to the standard HTTP(S)_PROXY / NO_PROXY environment
 * variables on the agent, so cluster-wide proxy config (e.g. injected into
 * the agent pod template) is picked up without per-repo configuration.
 */
class ProxySettings implements Serializable {

    /**
     * JVM -D proxy flags to append to the Gradle command line; empty string
     * when no proxy is configured. Command-line -D properties are forwarded
     * by Gradle to the build/daemon JVM, unlike plain proxy env vars, which
     * the JVM ignores.
     */
    static String gradleArgs(def steps, Map config) {
        def pc = config.stages?.build?.gradle?.proxy
        String host
        String port
        String noProxy

        if (pc?.host) {
            host    = pc.host
            port    = "${pc.port ?: 80}"
            noProxy = pc.noProxy ?: ''
        } else {
            def url = firstEnv(steps, ['HTTPS_PROXY', 'https_proxy', 'HTTP_PROXY', 'http_proxy'])
            if (!url) {
                return ''
            }
            def hostPort = parseHostPort(url)
            host    = hostPort[0]
            port    = hostPort[1]
            noProxy = firstEnv(steps, ['NO_PROXY', 'no_proxy']) ?: ''
        }

        return jvmArgs(host, port, noProxy)
    }

    private static String firstEnv(def steps, List<String> names) {
        for (name in names) {
            def value = steps.env."${name}"
            if (value) {
                return value
            }
        }
        return null
    }

    @NonCPS
    private static List<String> parseHostPort(String url) {
        def uri = new URI(url.contains('://') ? url : "http://${url}")
        return [uri.host, "${uri.port > 0 ? uri.port : 80}"]
    }

    @NonCPS
    private static String jvmArgs(String host, String port, String noProxy) {
        if (!host) {
            return ''
        }
        def args = [
            "-Dhttp.proxyHost=${host}", "-Dhttp.proxyPort=${port}",
            "-Dhttps.proxyHost=${host}", "-Dhttps.proxyPort=${port}"
        ]
        // env-style "a,b,.c.com" -> JVM-style "a|b|*.c.com"; the JVM reads
        // only http.nonProxyHosts (applies to https too), and cannot express
        // CIDR ranges
        def nonProxyHosts = (noProxy ?: '').split(',')
            .collect { it.trim() }
            .findAll { it && !it.contains('/') }
            .collect { it.startsWith('.') ? '*' + it : it }
            .join('|')
        if (nonProxyHosts) {
            args << "'-Dhttp.nonProxyHosts=${nonProxyHosts}'"
        }
        return args.join(' ')
    }
}
