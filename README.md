# jenkins-pipelines

A [Jenkins Shared Library](https://www.jenkins.io/doc/book/pipeline/shared-libraries/) that provides a reusable, YAML-configured CI/CD pipeline for downstream repositories. Consuming repos get a full pipeline — checkout, build, unit test, scan, deploy, integration test, PR gate — from a two-line `Jenkinsfile`, and configure each stage declaratively in a workflow file, similar to GitHub Actions.

## Quick start

**1. Register the library** in Jenkins under *Manage Jenkins → System → Global Pipeline Libraries* with the name `jenkins-pipelines`, pointing at this repository.

**2. Add a `Jenkinsfile`** to your repo:

```groovy
@Library('jenkins-pipelines') _
ciPipeline()
```

**3. (Optional) Add a workflow file** at `.jenkins/workflows/ci.yaml` to override defaults. Every field is optional — with no file at all, the pipeline runs with sensible Gradle defaults (build + unit test + PR gate). See [`.jenkins/workflows/example.yaml`](.jenkins/workflows/example.yaml) for a fully annotated example.

```yaml
name: my-service
agent: linux

stages:
  build:
    tool: gradle
    gradle:
      tasks: "clean build -x test"
      jdkVersion: "17"
  scan:
    sonar:
      enabled: true
      projectKey: my-service
  deploy:
    enabled: true
    environments:
      - name: dev
        branches: ["develop", "feature/*"]
        tool: helm
        helm:
          chart: ./charts/my-service
          namespace: dev
```

### Runtime overrides

`ciPipeline()` accepts parameters that take precedence over the YAML file:

```groovy
ciPipeline(
    workflowFile: 'custom.yaml',          // file under .jenkins/workflows/ (default: ci.yaml)
    agent: 'linux-large',                 // node label (default: 'any')
    overrides: [stages: [deploy: [enabled: false]]]  // deep-merged over the loaded config
)
```

## Pipeline stages

Stages run in this order. Each is driven by its section under `stages:` in the workflow YAML.

| Stage | Enabled by default | What it does |
|---|---|---|
| Checkout | yes | `checkout scm`, optional submodules / Git LFS, prints commit info |
| Build | yes | Builds with the configured tool (`gradle` \| `maven` \| `nodejs`), optionally archives artifacts |
| Unit Test | yes | Runs tests with the same tool, always publishes JUnit results |
| Scan | no | SonarQube analysis (with quality-gate wait) and/or Trivy image scan; runs both in parallel when both are enabled |
| Deploy | no | Deploys to every environment whose `branches` patterns match the current branch, via `helm` or `kustomize` |
| Integration Test | no | Runs an arbitrary shell command with a timeout, publishes JUnit results |
| PR Gate | yes (PR builds only) | Reports build status back to GitHub or Bitbucket; final status is always sent, even on failure |

Notes on stage semantics:

- Most stages are opt-out (`enabled: false` to skip); **Trivy, deploy, and integration-test are opt-in** (`enabled: true` to run).
- The PR Gate only runs when `env.CHANGE_ID` is set, i.e. on multibranch PR builds.
- Deploy branch patterns support globs: `*` matches within a path segment, `**` matches across segments (e.g. `feature/*`, `release/**`).
- Environment variables like `${IMAGE_TAG}` in the YAML are resolved by `readYaml`/shell at runtime; make sure they are set on the build.

## Configuration

Configuration is resolved in three layers, later layers winning via deep merge:

1. **Library defaults** — `PipelineConfig.DEFAULTS` (mirrored in [`resources/default-workflow.yaml`](resources/default-workflow.yaml))
2. **Repo workflow file** — `.jenkins/workflows/ci.yaml` in the consuming repo
3. **Runtime `overrides`** — the map passed to `ciPipeline()`

### Top-level options

```yaml
name: my-service          # display name (default: jenkins-pipeline)
agent: linux              # node label (default: any)
options:
  timeout: 60             # whole-pipeline timeout, minutes
  abortPreviousBuilds: false  # cancel older builds of the same branch/PR
  buildsToKeep: 20
  artifactsToKeep: 5
```

### Build tools

Set `stages.build.tool` and configure the matching block:

```yaml
build:
  tool: gradle            # gradle | maven | nodejs
  gradle:
    tasks: "clean build -x test"
    jdkVersion: "17"      # uses the Jenkins JDK tool named "jdk-<version>"
    gradleOpts: "-Xmx2g"
  maven:
    goals: "clean package -DskipTests"
    mavenOpts: "-Xmx2g"
    profiles: ["ci"]
    settingsId: my-settings   # Config File Provider file id
  nodejs:
    nodeVersion: "20"     # uses the NodeJS installation named "NodeJS-<version>"
    packageManager: npm   # npm | yarn
    buildScript: build
  archiveArtifacts: "**/build/libs/*.jar"
```

Unit-test commands are configured separately under `stages.unit-test.<tool>` (`gradle.tasks`, `maven.goals`, `nodejs.testScript`).

### Scanning

```yaml
scan:
  sonar:
    enabled: true
    serverName: SonarQube       # Jenkins SonarQube server name
    projectKey: my-service      # default: JOB_BASE_NAME
    qualityGateWait: true       # fail the build if the quality gate fails
    timeout: 5                  # quality-gate wait, minutes
    extraProperties: "-Dsonar.coverage.exclusions=**/generated/**"
  trivy:
    enabled: true
    image: "registry/my-service:${IMAGE_TAG}"
    severity: "CRITICAL,HIGH"
    exitCode: 1                 # 0 = warn-only
    ignoreFile: .trivyignore
```

Sonar analysis uses the build tool's own plugin for Gradle/Maven and falls back to `sonar-scanner` for anything else. The Trivy report is archived as a build artifact.

### Deployment

Deploy is environment-driven: each entry in `environments` is matched against the current branch, and **all** matching environments are deployed.

```yaml
deploy:
  enabled: true
  kubeCredentialsId: kube-config    # file credential exported as KUBECONFIG (global fallback)
  environments:
    - name: dev
      branches: ["develop", "feature/*"]
      tool: helm                    # helm | kustomize
      kubeCredentialsId: kube-dev   # per-environment override
      helm:
        chart: ./charts/my-service
        release: my-service-dev     # default: environment name
        namespace: dev
        kubeContext: dev-cluster
        values: [values.yaml, values-dev.yaml]
        set:
          image.tag: "${IMAGE_TAG}"
        extraArgs: "--wait --timeout 5m"
    - name: staging
      branches: ["main"]
      tool: kustomize
      kustomize:
        path: ./k8s/overlays/staging
        namespace: staging
        kubeContext: staging-cluster
```

Helm deploys use `helm upgrade --install --create-namespace`; Kustomize uses `kubectl apply -k` followed by `kubectl rollout status`. Both deployers also implement `rollback()`.

### PR gate

```yaml
pr-gate:
  enabled: true
  provider: github          # github | bitbucket
  github:
    credentialsId: github-token
    statusContext: ci/jenkins
  bitbucket:
    credentialsId: bitbucket-token
    buildKey: jenkins-ci
```

At the start of a PR build the gate posts a *pending/in-progress* status; in the pipeline's `finally` block it posts the terminal status (`SUCCESS` / `FAILURE` / `ERROR`), so the PR check is updated even when the build fails or aborts.

## Repository layout

```
vars/                        # Pipeline steps (the library's public API)
  ciPipeline.groovy          #   Entry point: orchestrates all stages
  checkoutStage.groovy       #   One step per stage, each takes the merged config
  buildStage.groovy
  unitTestStage.groovy
  scanStage.groovy
  deployStage.groovy
  integrationTestStage.groovy
  prGateStage.groovy
src/org/pipeline/
  config/                    # PipelineConfig (defaults) + YamlConfigLoader (load & deep-merge)
  build/                     # BuildTool interface, factory, Gradle/Maven/Nodejs builders
  deploy/                    # Deployer interface, factory, Helm/Kustomize deployers
  scan/                      # SonarScanner, TrivyScanner
  prgate/                    # PrGate interface, factory, GitHub/Bitbucket gates
  utils/Logger.groovy        # ANSI-colored log helper (debug gated on PIPELINE_DEBUG=true)
resources/default-workflow.yaml   # Reference copy of the library defaults
.jenkins/workflows/example.yaml   # Fully annotated example workflow for consumers
Jenkinsfile                  # Runs the library's own validation build
```

### Extending the library

Each pluggable concern follows the same interface + factory pattern; to add a new implementation:

- **Build tool** — implement `org.pipeline.build.BuildTool` (`build`/`test`), register it in `BuildToolFactory`.
- **Deployer** — implement `org.pipeline.deploy.Deployer` (`deploy`/`rollback`), register it in `DeployerFactory`.
- **PR gate provider** — implement `org.pipeline.prgate.PrGate` (`check`/`notify`), register it in `PrGateFactory`.

Then add the corresponding defaults to `PipelineConfig.DEFAULTS` and document the new YAML block in `example.yaml`.

All classes take the pipeline `steps` object in their constructor and must implement `Serializable` (Jenkins CPS requirement). Pure helper methods that use non-serializable iteration should be annotated `@NonCPS`.

## Jenkins requirements

Controller/agent tooling assumed by the stages you enable:

- **Plugins**: Pipeline Utility Steps (`readYaml`), AnsiColor, JUnit, Workspace Cleanup; SonarQube Scanner (`withSonarQubeEnv`), GitHub Notify (`githubNotify`), Bitbucket Build Status Notifier (`bitbucketStatusNotify`), Config File Provider (Maven `settingsId`), NodeJS plugin — each only if the matching feature is used.
- **Agent tools**: `git` (plus `git-lfs` if enabled), the selected build tool (`./gradlew` wrapper, `mvn`, or node/npm/yarn), `trivy`, `helm`, `kubectl` as applicable.
- **Tool installations**: JDKs named `jdk-<version>` and NodeJS installations named `NodeJS-<version>` when `jdkVersion`/`nodeVersion` are set.
- **Credentials**: kubeconfig file credentials for deploy; GitHub/Bitbucket tokens for the PR gate.
