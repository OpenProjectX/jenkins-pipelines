import org.pipeline.utils.EnvTemplate

def call(Map config) {
    def rc = config.stages?.release ?: [:]

    def branch = currentBranch()
    def tag = env.TAG_NAME ?: ''
    def shortSha = sh(label: 'Resolve Short SHA', script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
    def branchSlug = slug(branch ?: tag ?: 'detached')
    def releaseType = classify(rc, branch, tag)
    def profile = releaseType == 'formal' ? (rc.formal ?: [:]) : (rc.snapshot ?: [:])
    def version = releaseVersion(releaseType, profile, branchSlug, shortSha)
    env.RELEASE_TYPE = releaseType
    env.RELEASE_VERSION = version
    env.BRANCH_SLUG = branchSlug
    env.SHORT_SHA = shortSha

    def imageTag = imageTag(profile, version)

    env.IMAGE_TAG = imageTag

    echo("[Release] type=${releaseType}, version=${version}, imageTag=${imageTag}, branch=${branch ?: '-'}, tag=${tag ?: '-'}")
}

private String currentBranch() {
    (env.BRANCH_NAME ?: env.GIT_BRANCH?.replaceFirst(/^origin\//, '') ?: '')
}

private String classify(Map rc, String branch, String tag) {
    def formal = rc.formal ?: [:]
    def snapshot = rc.snapshot ?: [:]

    if (tag && branchMatches(tag, formal.tags ?: [])) {
        return 'formal'
    }
    if (branchMatches(branch, formal.branches ?: [])) {
        return 'formal'
    }
    if (branchMatches(branch, snapshot.branches ?: [])) {
        return 'snapshot'
    }
    return rc.defaultType ?: 'snapshot'
}

private String releaseVersion(String releaseType, Map profile, String branchSlug, String shortSha) {
    def template = profile.version
    if (!template) {
        template = releaseType == 'formal' ? '${TAG_NAME}' : '0.0.0-${BRANCH_SLUG}.${BUILD_NUMBER}.${SHORT_SHA}'
    }

    def resolved = EnvTemplate.resolve(template, this)?.trim()
    if (resolved) {
        return normalizedFormalVersion(releaseType, resolved)
    }

    if (releaseType == 'formal') {
        return "${env.BUILD_NUMBER}.${shortSha}"
    }
    return "0.0.0-${branchSlug}.${env.BUILD_NUMBER}.${shortSha}"
}

private String imageTag(Map profile, String version) {
    EnvTemplate.resolve(profile.imageTag ?: version, this)
}

private String normalizedFormalVersion(String releaseType, String version) {
    if (releaseType == 'formal' && version.startsWith('v')) {
        return version.substring(1)
    }
    version
}

@NonCPS
private String slug(String value) {
    value.toLowerCase()
        .replaceAll(/[^a-z0-9._-]+/, '-')
        .replaceAll(/^-+|-+$/, '')
        .take(63)
}

@NonCPS
private boolean branchMatches(String branch, List patterns) {
    if (!branch || !patterns) return false
    patterns.any { p ->
        branch ==~ p.replace('**', '.*').replace('*', '[^/]*')
    }
}
