package org.pipeline.deploy

class DeployerFactory implements Serializable {
    static Deployer create(String tool, def steps) {
        switch (tool?.toLowerCase()) {
            case 'helm':      return new HelmDeployer(steps)
            case 'helmfile':  return new HelmfileDeployer(steps)
            case 'kustomize': return new KustomizeDeployer(steps)
            case 'ansible':
            case 'vm':        return new AnsibleDeployer(steps)
            default:
                throw new IllegalArgumentException(
                    "Unsupported deploy tool: '${tool}'. Supported: helm, helmfile, kustomize, ansible"
                )
        }
    }
}
