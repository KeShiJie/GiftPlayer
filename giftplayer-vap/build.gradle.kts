import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.keke.giftplayer.vap"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

dependencies {
    api(project(":giftplayer"))
    compileOnly(libs.vap)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "giftplayer-vap"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
