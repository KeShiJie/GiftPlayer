// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

allprojects {
    group = "com.github.KeShiJie.GiftPlayer"
    version = providers.gradleProperty("releaseVersion")
        .orElse(providers.environmentVariable("VERSION"))
        .getOrElse("v1.0.1")
}
