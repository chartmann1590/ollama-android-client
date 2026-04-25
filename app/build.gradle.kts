import java.util.Properties
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

// Resolution order for AdMob configuration (and the same precedence works for
// the ads kill switch): environment variable (CI secrets) → local.properties
// (developer/fork override) → default. Defaults below are Google's documented
// AdMob *test* IDs so an unconfigured fork still builds and runs without
// pulling real ad inventory.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

fun adProp(envName: String, propName: String, default: String): String {
    val fromEnv = System.getenv(envName)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromLocal = localProperties.getProperty(propName)
    if (!fromLocal.isNullOrBlank()) return fromLocal
    return default
}

fun adsEnabled(): Boolean {
    val env = System.getenv("ADS_ENABLED")
    if (!env.isNullOrBlank()) return env.equals("true", ignoreCase = true)
    val prop = localProperties.getProperty("ads.enabled")
    if (!prop.isNullOrBlank()) return prop.equals("true", ignoreCase = true)
    return true
}

// Git commit epoch seconds, used to drive the in-app "new release available"
// check against the GitHub release `published_at` field. Using the commit
// timestamp (not `System.currentTimeMillis()`) keeps `buildConfigField` values
// stable across incremental builds so Gradle's up-to-date checks still work.
fun gitCommitEpochSeconds(): Long {
    return try {
        val out = ByteArrayOutputStream()
        exec {
            commandLine("git", "log", "-1", "--format=%ct")
            standardOutput = out
            isIgnoreExitValue = true
        }
        out.toString(Charsets.UTF_8.name()).trim().toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    }
}

fun gitShortSha(): String {
    return try {
        val out = ByteArrayOutputStream()
        exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = out
            isIgnoreExitValue = true
        }
        out.toString(Charsets.UTF_8.name()).trim()
    } catch (_: Exception) {
        ""
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    // Add the Performance Monitoring Gradle plugin
    id("com.google.firebase.firebase-perf")
}

android {
    namespace = "com.charles.ollama.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.charles.ollama.client"
        minSdk = 24
        targetSdk = 35

        // Play Store requires versionCode to monotonically increase per upload.
        // In CI we offset GITHUB_RUN_NUMBER by 1000 so it can never collide
        // with any manually-uploaded build (the previous manual versionCode
        // was 3) — the offset gives plenty of headroom even if the workflow
        // ever resets. Local builds keep a stable baseline so incremental
        // gradle outputs aren't churned on every commit.
        val baseVersionCode = 3
        val baseVersionName = "1.2"
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = if (ciRunNumber != null) 1000 + ciRunNumber else baseVersionCode
        versionName = if (ciRunNumber != null) "$baseVersionName.$ciRunNumber" else baseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "GITHUB_OWNER", "\"chartmann1590\"")
        buildConfigField("String", "GITHUB_REPO", "\"ollama-android-client\"")
        buildConfigField("long", "BUILD_COMMIT_EPOCH_SECONDS", "${gitCommitEpochSeconds()}L")
        buildConfigField("String", "BUILD_GIT_SHA", "\"${gitShortSha()}\"")

        // AdMob — defaults are Google's public test IDs, so a freshly-cloned
        // fork builds and runs without ad inventory or a real account. Real
        // IDs come from CI secrets or a developer's local.properties.
        // See https://developers.google.com/admob/android/test-ads
        val admobAppId = adProp(
            envName = "ADMOB_APP_ID",
            propName = "admob.appId",
            default = "ca-app-pub-3940256099942544~3347511713"
        )
        val admobBannerId = adProp(
            envName = "ADMOB_BANNER_AD_UNIT_ID",
            propName = "admob.bannerAdUnitId",
            default = "ca-app-pub-3940256099942544/6300978111"
        )
        val admobInterstitialId = adProp(
            envName = "ADMOB_INTERSTITIAL_AD_UNIT_ID",
            propName = "admob.interstitialAdUnitId",
            default = "ca-app-pub-3940256099942544/1033173712"
        )
        val admobNativeId = adProp(
            envName = "ADMOB_NATIVE_AD_UNIT_ID",
            propName = "admob.nativeAdUnitId",
            default = "ca-app-pub-3940256099942544/2247696110"
        )
        val admobAppOpenId = adProp(
            envName = "ADMOB_APP_OPEN_AD_UNIT_ID",
            propName = "admob.appOpenAdUnitId",
            default = "ca-app-pub-3940256099942544/9257395921"
        )
        val adsEnabled = adsEnabled()

        buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"$admobInterstitialId\"")
        buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"$admobNativeId\"")
        buildConfigField("String", "ADMOB_APP_OPEN_AD_UNIT_ID", "\"$admobAppOpenId\"")

        // Substituted into AndroidManifest.xml's
        // <meta-data com.google.android.gms.ads.APPLICATION_ID/> entry.
        manifestPlaceholders["admobAppId"] = admobAppId
    }

    signingConfigs {
        create("release") {
            // Load keystore properties from keystore.properties file
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing if keystore.properties exists, otherwise fall back to debug
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Fallback to debug signing if keystore not configured
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlinOptions {
        jvmTarget = "21"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Let JVM unit tests call android.util.Log / TextUtils without
            // exploding — they return defaults instead of throwing.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    javaCompiler.set(
        javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
}

kapt {
    correctErrorTypes = true
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    kapt("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.8.0")
    implementation("androidx.room:room-ktx:2.8.0")
    ksp("androidx.room:room-compiler:2.8.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // LiteRT-LM (on-device Gemma / .litertlm)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")
    
    // AdMob
    implementation("com.google.android.gms:play-services-ads:22.6.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

