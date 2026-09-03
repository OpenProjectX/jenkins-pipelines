package org.pipeline.utils

class EnvTemplate implements Serializable {
    static String resolve(Object value, steps) {
        if (value == null) {
            return null
        }

        def text = value as String
        text.replaceAll(/\$\{([A-Za-z_][A-Za-z0-9_]*)(:-([^}]*))?\}/) { match, name, fallbackExpr, fallback ->
            def envValue = steps.env[name as String]
            envValue ? envValue as String : (fallback ?: '')
        }
    }

    static Map resolveMap(Map values, steps) {
        def resolved = [:]
        values?.each { k, v ->
            resolved[k] = resolve(v, steps)
        }
        resolved
    }

    static List resolveList(Object values, steps) {
        if (!values) {
            return []
        }
        (values instanceof List ? values : [values]).collect { resolve(it, steps) }
    }
}
