/**
 * Archives stage artifacts on the Jenkins build page.
 *
 * With tarName set, everything matching the Ant glob is bundled into a single
 * <tarName>.tar.gz before archiving — one artifact instead of hundreds of
 * small files (HTML test reports), which archives much faster.
 */
def call(String pattern, String tarName = null) {
    if (!pattern) {
        return
    }
    if (tarName) {
        def files = findFiles(glob: pattern)
        if (files.length == 0) {
            echo("[Archive] nothing matches '${pattern}', skipping ${tarName}.tar.gz")
            return
        }
        def list = new StringBuilder()
        for (int i = 0; i < files.length; i++) {
            list.append(files[i].path).append('\n')
        }
        def listFile = ".${tarName}.files"
        writeFile(file: listFile, text: list.toString())
        sh(label: "Tar ${tarName}", script: "tar -czf ${tarName}.tar.gz -T ${listFile} && rm -f ${listFile}")
        archiveArtifacts artifacts: "${tarName}.tar.gz"
    } else {
        archiveArtifacts artifacts: pattern, allowEmptyArchive: true
    }
}
