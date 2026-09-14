plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.keke.giftplayer.library"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

dependencies {
    testImplementation(libs.junit)
    api(libs.androidx.appcompat)
    api(project(":lib_download"))
    api(libs.wire.runtime)
    api(libs.vap)
    api(libs.libpag)
    api(libs.androidx.work.runtime)
    api(libs.androidx.work.multiprocess)
    api(libs.utilcodex)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.keke"
            artifactId = "giftplayer"
            version = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
