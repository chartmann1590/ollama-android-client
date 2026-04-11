// Top-level build file
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("com.google.dagger.hilt.android") version "2.56.2" apply false
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
    // Add the dependency for the Performance Monitoring Gradle plugin
    id("com.google.firebase.firebase-perf") version "2.0.2" apply false
}

