package org.pipeline.utils

/**
 * Generic credential injection for user pipelines.
 *
 * Accepts a list of binding specs from the workflow YAML and wraps the stage
 * body in withCredentials, exporting each credential as masked environment
 * variables available to gradle/maven/nodejs/docker steps. Three shapes are
 * supported:
 *
 *   - credentialsId + envVar                  -> secret text, one env var
 *   - credentialsId + usernameVar/passwordVar -> username/password credential
 *   - credentialsId + variable                -> secret file, env var = file path
 *
 * Example workflow YAML:
 *
 *   build:
 *     credentials:
 *       - credentialsId: npm-token
 *         envVar: NPM_TOKEN
 *       - credentialsId: harbor-login
 *         usernameVar: REG_USER
 *         passwordVar: REG_PASS
 */
class Credentials implements Serializable {

    static void withCredentials(def steps, def specs, Closure body) {
        def list = normalize(specs)
        if (!list) {
            body()
            return
        }
        steps.withCredentials(bindings(steps, list)) {
            body()
        }
    }

    static List bindings(def steps, def specs) {
        return normalize(specs).findResults { entry ->
            def spec = entry as Map
            def id = spec.credentialsId as String
            if (!id) {
                steps.error("credentials entry missing credentialsId: ${spec}")
            }
            if (spec.usernameVar || spec.passwordVar) {
                if (!(spec.usernameVar && spec.passwordVar)) {
                    steps.error("credentials '${id}': usernameVar and passwordVar must be set together")
                }
                return [
                    '$class'        : 'UsernamePasswordMultiBinding',
                    credentialsId   : id,
                    usernameVariable: spec.usernameVar as String,
                    passwordVariable: spec.passwordVar as String
                ]
            }
            if (spec.variable) {
                return [
                    '$class'     : 'FileBinding',
                    credentialsId: id,
                    variable     : spec.variable as String
                ]
            }
            if (spec.envVar) {
                return [
                    '$class'     : 'StringCredentialsBinding',
                    credentialsId: id,
                    variable     : spec.envVar as String
                ]
            }
            steps.error("credentials '${id}': set envVar, usernameVar+passwordVar, or variable")
        }
    }

    private static List normalize(def specs) {
        if (specs == null) {
            return []
        }
        if (specs instanceof Map) {
            return [specs]
        }
        return specs as List
    }
}
