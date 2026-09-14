plugins {
    base
    `maven-publish`
}

val downloaderAar = rootProject.file("lib_download/libs/filedownloader.aar")
configurations.named("default") {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts.add("default", downloaderAar)

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "filedownloader"
            version = project.version.toString()
            artifact(downloaderAar)
        }
    }
}
