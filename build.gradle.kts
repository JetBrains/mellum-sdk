group = "ai.jetbrains.code.mellum.sdk"
version = "0.2.2"

tasks.register("reportProjectVersionToTeamCity") {
    doLast {
        println("##teamcity[buildNumber '${project.version}']")
    }
}