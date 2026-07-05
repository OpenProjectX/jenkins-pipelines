package org.pipeline.utils

class Logger implements Serializable {
    private final def steps

    Logger(steps) {
        this.steps = steps
    }

    void info(String message) {
        steps.echo("\033[0;34m[INFO]\033[0m ${message}")
    }

    void warn(String message) {
        steps.echo("\033[0;33m[WARN]\033[0m ${message}")
    }

    void error(String message) {
        steps.echo("\033[0;31m[ERROR]\033[0m ${message}")
    }

    void debug(String message) {
        if (steps.env.PIPELINE_DEBUG == 'true') {
            steps.echo("\033[0;90m[DEBUG]\033[0m ${message}")
        }
    }

    void section(String title) {
        steps.echo("\033[1;36m===== ${title} =====\033[0m")
    }
}
