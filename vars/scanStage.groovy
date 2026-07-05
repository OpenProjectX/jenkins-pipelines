import org.pipeline.scan.SonarScanner
import org.pipeline.scan.TrivyScanner

def call(Map config) {
    def sc           = config.stages?.scan ?: [:]
    def sonarEnabled = sc.sonar?.enabled != false
    def trivyEnabled = sc.trivy?.enabled == true

    if (sonarEnabled && trivyEnabled) {
        parallel(
            'SonarQube': { new SonarScanner(this).scan(config) },
            'Trivy'     : { new TrivyScanner(this).scan(config) }
        )
    } else if (sonarEnabled) {
        new SonarScanner(this).scan(config)
    } else if (trivyEnabled) {
        new TrivyScanner(this).scan(config)
    }
}
