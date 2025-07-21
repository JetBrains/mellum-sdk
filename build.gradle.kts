group = "ai.jetbrains.code.mellum.sdk"
version = "0.0.1"

tasks.register("reportProjectVersionToTeamCity") {
    doLast {
        println("##teamcity[buildNumber '${project.version}']")
    }
}