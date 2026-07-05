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
 *
 * The flags must be delivered on two channels because Gradle runs in two
 * JVMs: GRADLE_OPTS reaches the wrapper/launcher JVM (which downloads the
 * Gradle distribution and ignores CLI -D args), while CLI -D properties are
 * forwarded to the daemon JVM (which resolves dependencies and ignores the
 * launcher's system properties).
 */
class ProxySettings implements Serializable {

    /** Proxy flags for the ./gradlew command line, shell-quoted. */
    static String gradleCliArgs(def steps, Map config) {
        shellJoin(resolve(steps, config))
    }

    /** Proxy flags for the GRADLE_OPTS env var, unquoted. */
    static String gradleJvmOpts(def steps, Map config) {
        resolve(steps, config).join(' ')
    }

    private static List<String> resolve(def steps, Map config) {
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
                return []
            }
            def hostPort = parseHostPort(url)
            host    = hostPort[0]
            port    = hostPort[1]
            noProxy = firstEnv(steps, ['NO_PROXY', 'no_proxy']) ?: ''
        }

        return jvmFlags(host, port, noProxy)
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
    private static List<String> jvmFlags(String host, String port, String noProxy) {
        if (!host) {
            return []
        }
        def flags = [
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
            flags << "-Dhttp.nonProxyHosts=${nonProxyHosts}".toString()
        }
        return flags*.toString()
    }

    @NonCPS
    private static String shellJoin(List<String> flags) {
        // | and * must not hit the shell unquoted
        flags.collect { it ==~ /[-.\w=:]+/ ? it : "'${it}'" }.join(' ')
    }
}
