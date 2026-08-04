package org.pipeline.build

class DockerBuilder implements BuildTool, Serializable {
    private final def steps

    DockerBuilder(steps) {
        this.steps = steps
    }

    @Override
    void build(Map config) {
        def dc          = config.stages?.build?.docker ?: [:]
        def context     = dc.context ?: '.'
        def dockerfile  = dc.dockerfile ?: 'Dockerfile'
        def buildKit    = dc.buildKit != false
        def tags        = imageTags(dc)
        def buildArgs   = dockerBuildArgs(dc, dockerfile, tags, context)
        def envVars     = buildKit ? ['DOCKER_BUILDKIT=1'] : []

        withRegistryLogin(dc) {
            steps.withEnv(envVars) {
                steps.sh(label: 'Docker Build', script: "docker build ${buildArgs.join(' ')}")
            }

            if (dc.push == true) {
                tags.each { tag ->
                    steps.sh(label: "Docker Push ${tag}", script: "docker push ${shellQuote(tag)}")
                }
            }
        }
    }

    @Override
    void test(Map config) {
        def tc = config.stages?.'unit-test'?.docker ?: [:]
        if (tc.command) {
            steps.sh(label: 'Docker Test', script: tc.command as String)
        } else {
            steps.echo('[Docker Test] no unit-test.docker.command configured; skipping')
        }
    }

    private List<String> imageTags(Map dc) {
        if (dc.tags) {
            return (dc.tags instanceof List ? dc.tags : [dc.tags]).collect { it as String }
        }

        def image = dc.image ?: steps.env.IMAGE_NAME
        if (!image) {
            steps.error('stages.build.docker.image is required when docker.tags is not set')
        }

        def tag = dc.tag ?: steps.env.IMAGE_TAG ?: steps.env.BUILD_NUMBER ?: 'latest'
        return ["${image}:${tag}" as String]
    }

    private List<String> dockerBuildArgs(Map dc, String dockerfile, List<String> tags, String context) {
        def args = []
        args << '-f' << shellQuote(dockerfile)
        tags.each { tag ->
            args << '-t' << shellQuote(tag)
        }
        if (dc.pull == true) {
            args << '--pull'
        }
        if (dc.noCache == true) {
            args << '--no-cache'
        }
        if (dc.target) {
            args << '--target' << shellQuote(dc.target as String)
        }
        if (dc.platform) {
            args << '--platform' << shellQuote(dc.platform as String)
        }
        if (dc.extraArgs) {
            args << (dc.extraArgs as String)
        }
        (dc.buildArgs ?: [:]).each { k, v ->
            args << '--build-arg'
            args << (v == null ? shellQuote(k as String) : shellQuote("${k}=${v}" as String))
        }
        (dc.labels ?: [:]).each { k, v ->
            args << '--label'
            args << shellQuote("${k}=${v}" as String)
        }
        args << shellQuote(context)
        return args
    }

    private void withRegistryLogin(Map dc, Closure body) {
        def registry      = dc.registry ?: [:]
        def credentialsId = registry.credentialsId
        def url           = registry.url ?: registry.server ?: ''

        if (credentialsId) {
            steps.withCredentials([[
                '$class'        : 'UsernamePasswordMultiBinding',
                credentialsId   : credentialsId as String,
                usernameVariable: 'DOCKER_REGISTRY_USER',
                passwordVariable: 'DOCKER_REGISTRY_PASSWORD'
            ]]) {
                def loginTarget = url ? " ${shellQuote(url as String)}" : ''
                steps.sh(
                    label: 'Docker Login',
                    script: "printf '%s' \"\$DOCKER_REGISTRY_PASSWORD\" | docker login${loginTarget} -u \"\$DOCKER_REGISTRY_USER\" --password-stdin"
                )
                try {
                    body()
                } finally {
                    if (url) {
                        steps.sh(label: 'Docker Logout', script: "docker logout ${shellQuote(url as String)} || true")
                    }
                }
            }
        } else {
            body()
        }
    }

    private static String shellQuote(String value) {
        "'${value.replace("'", "'\"'\"'")}'"
    }
}
