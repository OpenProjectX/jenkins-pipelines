package org.pipeline.utils

/**
 * Runtime ${VAR} / ${VAR:-fallback} interpolation for YAML values, resolved
 * against the Jenkins environment.
 *
 * Everything here is @NonCPS on purpose: a CPS-transformed closure handed to
 * String.replaceAll never executes its body synchronously (the JDK regex
 * machinery cannot drive the CPS interpreter), so every ${VAR} would silently
 * resolve to the empty string. NonCPS keeps the closure plain Java, and the
 * only pipeline interaction inside is reading the env map, which is safe.
 */
class EnvTemplate implements Serializable {

    @NonCPS
    static String resolve(Object value, steps) {
        if (value == null) {
            return null
        }

        def envMap = steps.env
        def text = value as String
        text.replaceAll(/\$\{([A-Za-z_][A-Za-z0-9_]*)(:-([^}]*))?\}/) { match, name, fallbackExpr, fallback ->
            def envValue = envMap[name as String]
            envValue ? envValue as String : (fallback ?: '')
        }
    }

    @NonCPS
    static Map resolveMap(Map values, steps) {
        def resolved = [:]
        values?.each { k, v ->
            resolved[k] = resolve(v, steps)
        }
        resolved
    }

    @NonCPS
    static List resolveList(Object values, steps) {
        if (!values) {
            return []
        }
        (values instanceof List ? values : [values]).collect { resolve(it, steps) }
    }
}
