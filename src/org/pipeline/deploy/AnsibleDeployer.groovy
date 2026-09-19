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
 *           secretVars:                    # Jenkins secret text -> extra var
 *             cf_api_token: cloudflare-api-token
 *             btdig_tls_cert_content: cloudflare-origin-cert
 *
 * `secretVars` maps an extra-var name to a Jenkins **secret text** credential.
 * Playbooks that need a credential of their own - a DNS API token, a certificate,
 * a registry password - get it this way instead of having it committed or passed
 * on the command line. The values are bound with withCredentials, so Jenkins
 * masks them in the log, and the playbook should still use `no_log: true` on the
 * tasks that consume them.
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

        // Playbook-owned credentials: each becomes an extra var, and Jenkins
        // masks the values in the build log.
        def secretVars = (ac.secretVars ?: [:]) as Map
        if (secretVars) {
            def bindings = []
            def names = []
            secretVars.each { varName, credentialsId ->
                def envName = "SECRET_${names.size()}"
                names << [varName: varName as String, envName: envName]
                bindings << steps.string(credentialsId: credentialsId as String, variable: envName)
            }
            steps.withCredentials(bindings) {
                names.each { entry ->
                    extraVars[entry.varName] = steps.env[entry.envName]
                }
                runWithToken(ac, call, extraVars)
            }
            return
        }
        runWithToken(ac, call, extraVars)
    }

    private void runWithToken(Map ac, Map call, Map extraVars) {
        if (ac.tokenCredentialsId) {
            // Private release registries: expose the token to the playbook as
            // artifact_token (used e.g. as a GitHub download Authorization).
            steps.withCredentials([steps.usernamePassword(
                credentialsId   : ac.tokenCredentialsId as String,
                usernameVariable: 'ARTIFACT_TOKEN_USER',
                passwordVariable: 'ARTIFACT_TOKEN'
            )]) {
                extraVars.artifact_token = steps.env.ARTIFACT_TOKEN
                steps.ansiblePlaybook(call)
            }
            return
        }
        steps.ansiblePlaybook(call)
    }
}
