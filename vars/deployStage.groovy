import org.pipeline.deploy.DeployerFactory

def call(Map config) {
    def dc            = config.stages?.deploy ?: [:]
    def environments  = dc.environments ?: []
    def currentBranch = env.BRANCH_NAME ?: env.GIT_BRANCH?.replaceFirst('origin/', '') ?: ''

    def targets = environments.findAll { e -> branchMatches(currentBranch, e.branches ?: []) }

    if (!targets) {
        echo("[Deploy] No environment matched branch '${currentBranch}'")
        return
    }

    targets.each { environment ->
        def tool = environment.tool ?: 'helm'
        echo("[Deploy] environment=${environment.name}, tool=${tool}, branch=${currentBranch}")
        DeployerFactory.create(tool, this).deploy(environment, config)
    }
}

@NonCPS
private boolean branchMatches(String branch, List patterns) {
    if (!patterns) return true
    return patterns.any { p ->
        branch ==~ p.replace('**', '.*').replace('*', '[^/]*')
    }
}
