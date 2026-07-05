/**
 * Performs git checkout with optional submodule/LFS support.
 * Called by ciPipeline after the initial scm checkout.
 */
def call(Map config) {
    def cc = config.stages?.checkout ?: [:]

    if (cc.submodules) {
        sh(label: 'Init Submodules', script: 'git submodule update --init --recursive')
    }

    if (cc.lfs) {
        sh(label: 'Git LFS Pull', script: 'git lfs pull')
    }

    if (cc.printCommitInfo != false) {
        sh(label: 'Git Info', script: 'git log -1 --format="Commit: %H%nAuthor: %an <%ae>%nDate:   %ad%nMsg:    %s"')
    }
}
