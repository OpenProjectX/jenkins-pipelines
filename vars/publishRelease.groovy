/**
 * Publish build artifacts to the release registry — the source of truth for
 * deployments. Extensible per provider (github | jfrog | nexus); GitHub
 * Releases is the built-in implementation.
 *
 * Config (under stages.release.publish):
 *
 *   publish:
 *     provider: github            # github (only provider for now)
 *     credentialsId: github       # username/password credential (PAT)
 *     artifacts:                  # workspace paths, ${VAR}-expanded
 *       - dist/rlist-linux-amd64
 *
 * Runs on formal builds only (ciPipeline gates on RELEASE_TYPE): tag builds
 * (v*) publish a full release, branch builds (e.g. master) auto-create the
 * v<RELEASE_VERSION> tag and publish a prerelease.
 *
 * Requires the `gh` CLI on the agent (jenkins-build-agent has it).
 */

import org.pipeline.utils.EnvTemplate

def call(Map config) {
    def pc = config.stages?.release?.publish
    if (!pc) {
        return
    }
    def provider = (pc.provider ?: 'github').toString().toLowerCase()
    if (provider != 'github') {
        error("Unsupported release publisher: '${provider}'. Supported: github")
    }

    def artifacts = EnvTemplate.resolveList(pc.artifacts ?: [], this)
    if (!artifacts) {
        echo('[Publish] no artifacts configured; skipping')
        return
    }

    def tag = "v${env.RELEASE_VERSION}"
    def repo = githubRepo()
    def prerelease = !env.TAG_NAME

    echo("[Publish] ${repo} tag=${tag} artifacts=${artifacts.size()} prerelease=${prerelease}")

    withCredentials([usernamePassword(
        credentialsId   : pc.credentialsId ?: 'github',
        usernameVariable: 'GH_USER',
        passwordVariable: 'GH_TOKEN'
    )]) {
        // Branch builds have no tag yet — create and push it so the release
        // has something to attach to.
        sh(label: 'Ensure release tag', script:
            "git rev-parse -q --verify refs/tags/${tag} >/dev/null 2>&1 || " +
            "(git tag ${tag} && git push origin ${tag})")

        sh(label: 'Ensure GitHub release', script:
            "gh release view ${tag} -R ${repo} >/dev/null 2>&1 || " +
            "gh release create ${tag} -R ${repo} --title ${tag} --generate-notes" +
            "${prerelease ? ' --prerelease' : ''}")

        sh(label: 'Upload artifacts', script:
            "gh release upload ${tag} -R ${repo} --clobber ${artifacts.collect { "'${it}'" }.join(' ')}")
    }
}

private String githubRepo() {
    def url = (env.GIT_URL ?: '').replaceAll(/\.git$/, '')
    if (url.startsWith('git@github.com:')) {
        url = url.replace('git@github.com:', 'https://github.com/')
    }
    if (!url.startsWith('https://github.com/')) {
        error("publishRelease: GIT_URL is not a GitHub repository: ${env.GIT_URL}")
    }
    return url.substring('https://github.com/'.length())
}
