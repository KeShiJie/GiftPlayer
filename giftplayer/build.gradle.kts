import org.gradle.api.publish.tasks.GenerateModuleMetadata

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

tasks.withType<Jar>().configureEach {
    if (name == "sourceReleaseJar") {
        include("com/keke/giftplayer/animation/AnimationInitializer.kt")
        include("com/keke/giftplayer/animation/core/AnimationLog.kt")
        include("com/keke/giftplayer/animation/core/AnimationLogListener.kt")
        include("com/keke/giftplayer/animation/download/AnimationDownloadConfig.kt")
        include("com/keke/giftplayer/animation/download/AnimationResourceManager.kt")
        include("com/keke/giftplayer/animation/widget/BaseAnimationPlayerView.kt")
        include("com/keke/giftplayer/animation/widget/QueuedAnimationPlayerView.kt")
        include("com/keke/giftplayer/animation/core/AnimationRequest.kt")
    }
}

// Use Maven's standard -sources.jar lookup to avoid JitPack source URL rewriting.
tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
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
            groupId = project.group.toString()
            artifactId = "giftplayer"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
