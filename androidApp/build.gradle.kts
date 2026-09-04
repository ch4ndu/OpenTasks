import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val releaseSigningInputs = mapOf(
    "storeFile" to (
        providers.gradleProperty("opentasks.android.signing.storeFile").orNull?.takeIf { it.isNotBlank() }
            ?: providers.environmentVariable("OPENTASKS_ANDROID_KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }
        ),
    "storePassword" to (
        providers.gradleProperty("opentasks.android.signing.storePassword").orNull?.takeIf { it.isNotBlank() }
            ?: providers.environmentVariable("OPENTASKS_ANDROID_KEYSTORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
        ),
    "keyAlias" to (
        providers.gradleProperty("opentasks.android.signing.keyAlias").orNull?.takeIf { it.isNotBlank() }
            ?: providers.environmentVariable("OPENTASKS_ANDROID_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() }
        ),
    "keyPassword" to (
        providers.gradleProperty("opentasks.android.signing.keyPassword").orNull?.takeIf { it.isNotBlank() }
            ?: providers.environmentVariable("OPENTASKS_ANDROID_KEY_PASSWORD").orNull?.takeIf { it.isNotBlank() }
        ),
)
val isReleaseSigningConfigured = releaseSigningInputs.values.all { it != null }
if (releaseSigningInputs.values.any { it != null } && !isReleaseSigningConfigured) {
    val missingInputs = releaseSigningInputs.filterValues { it == null }.keys.joinToString(", ")
    throw GradleException("Incomplete Android release signing configuration; missing inputs: $missingInputs")
}
val releaseStoreFile = releaseSigningInputs.getValue("storeFile")?.let(::file)

kotlin {
    jvmToolchain(17)

    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

android {
    namespace = "com.udnahc.opentasks"
    //noinspection GradleDependency
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.udnahc.opentasks"
        minSdk = libs.versions.android.minSdk.get().toInt()
        //noinspection OldTargetApi
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = "1.2.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
    if (isReleaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = requireNotNull(releaseStoreFile)
                storePassword = requireNotNull(releaseSigningInputs.getValue("storePassword"))
                keyAlias = requireNotNull(releaseSigningInputs.getValue("keyAlias"))
                keyPassword = requireNotNull(releaseSigningInputs.getValue("keyPassword"))
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (isReleaseSigningConfigured) {
                signingConfigs.getByName("release")
            } else {
                // Keep local minified release builds installable when production signing is unavailable.
                signingConfigs.getByName("debug")
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(platform(libs.koin.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    debugImplementation(libs.compose.uiTooling)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.components.resources)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.slf4j.simple)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutines.test)
}
