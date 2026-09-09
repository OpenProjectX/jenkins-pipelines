package org.pipeline.deploy

import org.pipeline.utils.EnvTemplate

/**
 * Deploys to VMs with Ansible: runs the repo's playbook through the Jenkins
 * ansible plugin (ansible CLI lives on the agent image). The playbook owns
 * the on-VM contract — download the artifact from the release registry,
 * install, switch, restart, health-gate. Rollback re-runs the playbook with
 * rollback=true so repos can repoint to the previous release.
 *
 *   deploy:
 *     environments:
 *       - name: prod
 *         branches: ["master", "v*"]
 *         tool: ansible
 *         ansible:
 *           playbook: deploy/ansible/deploy.yml
 *           inventory: deploy/ansible/inventory/prod.ini
 *           credentialsId: vm-ssh-key      # Jenkins SSH private key
 *           limit: rlist                   # optional host/group subset
 *           extraVars:                     # ${VAR}-expanded
 *             artifact_url: "https://github.com/org/repo/releases/download/v${RELEASE_VERSION}/app"
 */
class AnsibleDeployer implements Deployer, Serializable {
    private final def steps

    AnsibleDeployer(steps) {
        this.steps = steps
    }

    @Override
    void deploy(Map environment, Map config) {
        run(environment, false)
    }

    @Override
    void rollback(Map environment, Map config) {
        run(environment, true)
    }

    private void run(Map environment, boolean rollback) {
        def ac = environment.ansible ?: [:]
        if (!ac.playbook) {
            steps.error('ansible deploy requires environments[].ansible.playbook')
        }

        def extraVars = EnvTemplate.resolveMap(ac.extraVars ?: [:], steps)
        if (!extraVars.artifact_version) {
            extraVars.artifact_version = steps.env.RELEASE_VERSION
        }
        if (rollback) {
            extraVars.rollback = 'true'
        }

        steps.echo("[Deploy] ansible playbook=${ac.playbook} inventory=${ac.inventory ?: '(default)'} rollback=${rollback}")

        def call = [
            playbook              : ac.playbook as String,
            inventory             : ac.inventory ? (ac.inventory as String) : null,
            credentialsId         : ac.credentialsId ? (ac.credentialsId as String) : null,
            extraVars             : extraVars,
            disableHostKeyChecking: true,
            colorized             : true,
        ]
        if (ac.limit) {
            call.limit = ac.limit as String
        }
        steps.ansiblePlaybook(call)
    }
}
