package org.pipeline.deploy

import org.pipeline.utils.EnvTemplate

/**
 * Multi-release deploys with helmfile (the helmfile CLI ships on the
 * recommended agent image). One environment entry runs one helmfile state
 * file, which can declare any number of releases — use it when a deploy
 * fans out to more than one chart/release and per-release `helm` entries
 * would duplicate state.
 *
 *   deploy:
 *     environments:
 *       - name: k8s
 *         branches: ["master", "v*"]
 *         tool: helmfile
 *         helmfile:
 *           file: deploy/helmfile.yaml       # default: helmfile.yaml
 *           environment: production          # helmfile -e (optional)
 *           selector: app=rlist              # helmfile -l label (optional)
 *           command: apply                   # apply|sync|diff|template|destroy (default: apply)
 *           kubeCredentialsId: kube-config   # optional file credential -> KUBECONFIG
 *           stateValuesSet:                  # helmfile --state-values-set, ${VAR}-expanded
 *             image_tag: "${IMAGE_TAG}"
 *           extraArgs: ""                    # appended verbatim
 *
 * State files read these values as {{ .Values.image_tag }} etc. helm-diff is
 * auto-installed by helmfile on first apply. Rollback is not implemented:
 * versioning lives in git (revert + redeploy).
 */
class HelmfileDeployer implements Deployer, Serializable {
    private final def steps

    HelmfileDeployer(steps) {
        this.steps = steps
    }

    @Override
    void deploy(Map environment, Map config) {
        run(environment, 'apply')
    }

    @Override
    void rollback(Map environment, Map config) {
        steps.echo('[Deploy] helmfile rollback is not supported — revert the state file and redeploy')
    }

    private void run(Map environment, String defaultCommand) {
        def hc = environment.helmfile ?: [:]
        def file = hc.file ?: 'helmfile.yaml'

        def args = ["-f", file as String]
        if (hc.environment) {
            args << "-e" << (hc.environment as String)
        }
        if (hc.selector) {
            args << "-l" << (hc.selector as String)
        }
        EnvTemplate.resolveMap(hc.stateValuesSet ?: hc.set ?: [:], steps).each { k, v ->
            args << "--state-values-set" << "${k}=${v}" as String
        }
        if (hc.extraArgs) {
            args << (hc.extraArgs as String)
        }

        def command = (hc.command ?: defaultCommand) as String
        def credId = hc.kubeCredentialsId ?: config.stages?.deploy?.kubeCredentialsId
        def fullArgs = args.collect { quote(it) }.join(' ')

        steps.echo("[Deploy] helmfile file=${file} command=${command}")

        if (credId) {
            steps.withCredentials([steps.file(credentialsId: credId as String, variable: 'KUBECONFIG')]) {
                steps.sh(label: "Helmfile ${command} [${environment.name}]", script: "helmfile ${fullArgs} ${command}")
            }
        } else {
            steps.sh(label: "Helmfile ${command} [${environment.name}]", script: "helmfile ${fullArgs} ${command}")
        }
    }

    private static String quote(String value) {
        value ==~ /[-.\w=:\/]+/ ? value : "'${value.replace("'", "'\"'\"'")}'"
    }
}
