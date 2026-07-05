package org.pipeline.config

class YamlConfigLoader implements Serializable {
    private static final String WORKFLOW_DIR         = '.jenkins/workflows'
    private static final String DEFAULT_WORKFLOW_FILE = 'ci.yaml'

    private final def steps

    YamlConfigLoader(steps) {
        this.steps = steps
    }

    Map load(Map params = [:]) {
        def workflowFile = params.workflowFile ?: DEFAULT_WORKFLOW_FILE
        def workflowPath = "${WORKFLOW_DIR}/${workflowFile}"

        Map userConfig = [:]
        if (steps.fileExists(workflowPath)) {
            steps.echo("[Config] Loading: ${workflowPath}")
            userConfig = steps.readYaml(file: workflowPath) as Map ?: [:]
        } else {
            steps.echo("[Config] ${workflowPath} not found — using defaults")
        }

        def merged = deepMerge(PipelineConfig.DEFAULTS, userConfig)

        if (params.overrides instanceof Map) {
            merged = deepMerge(merged, params.overrides as Map)
        }

        steps.echo("[Config] Pipeline: ${merged.name}")
        return merged
    }

    @NonCPS
    private static Map deepMerge(Map base, Map override) {
        def result = new LinkedHashMap(base)
        override?.each { k, v ->
            if (v instanceof Map && result[k] instanceof Map) {
                result[k] = deepMerge(result[k] as Map, v as Map)
            } else if (v != null) {
                result[k] = v
            }
        }
        return result
    }
}
