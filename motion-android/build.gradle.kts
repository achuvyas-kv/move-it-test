plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ninjapanda.motion.camera"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

dependencies {
    api(projects.motionCore)
    api(libs.camerax.core)
    api(libs.camerax.lifecycle)
    implementation(libs.camerax.camera2)
    implementation(libs.mediapipe.tasks.vision)
}
