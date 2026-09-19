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
 *           credentialVars:                # any credential kind -> extra vars
 *             - id: cloudflare-api-key
 *               kind: usernamePassword
 *               usernameVar: cf_api_email
 *               passwordVar: cf_api_key
 *             - id: cloudflare
 *               kind: certificate
 *               keystoreVar: cf_keystore_path
 *               passwordVar: cf_keystore_password
 *
 * Playbooks that need a credential of their own - a DNS API token, a certificate,
 * a registry password - get it this way instead of having it committed or passed
 * on the command line.
 *
 * `secretVars` is the shorthand for the common case: extra var <- secret text.
 * `credentialVars` handles the rest, because a credential's KIND decides how it
 * can be bound: a username/password yields two vars, and a certificate yields a
 * PKCS#12 keystore path plus its password (the playbook extracts the PEMs).
 *
 * Values are bound with withCredentials, so Jenkins masks them in the log; the
 * playbook should still mark the consuming tasks `no_log: true`.
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
        def bindings = []
        def names = []

        (ac.secretVars ?: [:]).each { varName, credentialsId ->
            def envName = "SECRET_${names.size()}"
            names << [varName: varName as String, envName: envName]
            bindings << steps.string(credentialsId: credentialsId as String, variable: envName)
        }

        (ac.credentialVars ?: []).each { entry ->
            def id = entry.id as String
            if (!id) {
                steps.error('deploy: credentialVars[].id is required')
            }
            switch (entry.kind ?: 'secretText') {
                case 'secretText':
                    def envName = "SECRET_${names.size()}"
                    names << [varName: (entry.var ?: entry.variable) as String, envName: envName]
                    bindings << steps.string(credentialsId: id, variable: envName)
                    break
                case 'usernamePassword':
                    def userEnv = "CRED_${names.size()}_USER"
                    def passEnv = "CRED_${names.size()}_PASS"
                    names << [varName: entry.usernameVar as String, envName: userEnv]
                    names << [varName: entry.passwordVar as String, envName: passEnv]
                    bindings << steps.usernamePassword(
                        credentialsId  : id,
                        usernameVariable: userEnv,
                        passwordVariable: passEnv,
                    )
                    break
                case 'certificate':
                    // A certificate credential is a PKCS#12 keystore: Jenkins
                    // writes it to a temp file and gives us the path plus the
                    // keystore password. Turning it into PEM is the playbook's
                    // job, since only it knows what the service wants.
                    def storeEnv = "CRED_${names.size()}_STORE"
                    def passEnv = "CRED_${names.size()}_STOREPASS"
                    names << [varName: entry.keystoreVar as String, envName: storeEnv]
                    names << [varName: entry.passwordVar as String, envName: passEnv]
                    bindings << steps.certificate(
                        credentialsId   : id,
                        keystoreVariable: storeEnv,
                        passwordVariable: passEnv,
                    )
                    break
                default:
                    steps.error("deploy: unsupported credentialVars kind '${entry.kind}' for ${id}")
            }
        }

        if (bindings) {
            steps.withCredentials(bindings) {
                names.each { entry ->
                    if (entry.varName) {
                        extraVars[entry.varName] = steps.env[entry.envName]
                    }
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
