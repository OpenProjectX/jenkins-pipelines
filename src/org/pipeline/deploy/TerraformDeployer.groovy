package org.pipeline.deploy

import org.pipeline.utils.EnvTemplate

/**
 * Terraform deployments: init + command against a working directory whose
 * state is managed by its configured backend (S3, Terraform Cloud, ...).
 * The terraform CLI ships on the recommended agent image.
 *
 *   deploy:
 *     environments:
 *       - name: infra
 *         branches: ["master", "v*"]
 *         tool: terraform
 *         terraform:
 *           dir: deploy/terraform        # -chdir target, default: repo root
 *           command: apply               # apply|plan|destroy|validate (default: apply)
 *           vars:                        # -var k=v, ${VAR}-expanded
 *             image_tag: "${IMAGE_TAG}"
 *           varFiles: ["prod.tfvars"]    # -var-file=...
 *           initArgs: "-backend-config=..."   # appended to terraform init
 *           extraArgs: ""                # appended to the main command
 *
 * apply/destroy always run with -auto-approve (CI is non-interactive);
 * destroy additionally requires autoApprove: true as a safety gate.
 * Rollback is git-based: revert the configuration and redeploy — state
 * history remains inspectable via the backend.
 */
class TerraformDeployer implements Deployer, Serializable {
    private final def steps

    TerraformDeployer(steps) {
        this.steps = steps
    }

    @Override
    void deploy(Map environment, Map config) {
        run(environment, 'apply')
    }

    @Override
    void rollback(Map environment, Map config) {
        steps.echo('[Deploy] terraform rollback: revert the configuration in git and redeploy')
    }

    private void run(Map environment, String defaultCommand) {
        def tc = environment.terraform ?: [:]
        def dir = tc.dir ?: '.'
        def command = (tc.command ?: defaultCommand) as String

        def initCmd = "terraform -chdir='${dir}' init -input=false"
        if (tc.initArgs) {
            initCmd += " ${tc.initArgs}"
        }

        def cmdArgs = ["terraform -chdir='${dir}' ${command} -input=false"]
        if (command in ['apply', 'plan', 'destroy']) {
            if (command != 'plan') {
                if (command == 'destroy' && tc.autoApprove != true) {
                    steps.error("terraform destroy requires terraform.autoApprove: true (safety gate)")
                }
                cmdArgs << '-auto-approve'
            }
            EnvTemplate.resolveMap(tc.vars ?: [:], steps).each { k, v ->
                cmdArgs << "-var='${k}=${v}'"
            }
            EnvTemplate.resolveList(tc.varFiles ?: [], steps).each { f ->
                cmdArgs << "-var-file='${f}'"
            }
        }
        if (tc.extraArgs) {
            cmdArgs << (tc.extraArgs as String)
        }

        steps.echo("[Deploy] terraform dir=${dir} command=${command}")
        steps.sh(label: "Terraform init [${environment.name}]", script: initCmd)
        steps.sh(label: "Terraform ${command} [${environment.name}]", script: cmdArgs.join(' '))
    }
}
