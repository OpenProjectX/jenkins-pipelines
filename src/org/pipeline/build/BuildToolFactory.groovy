package org.pipeline.build

class BuildToolFactory implements Serializable {
    static BuildTool create(String tool, def steps) {
        switch (tool?.toLowerCase()) {
            case 'gradle': return new GradleBuilder(steps)
            case 'maven':  return new MavenBuilder(steps)
            case 'nodejs':
            case 'npm':    return new NodejsBuilder(steps)
            default:
                throw new IllegalArgumentException(
                    "Unsupported build tool: '${tool}'. Supported: gradle, maven, nodejs"
                )
        }
    }
}
