import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties

abstract class GenerateLocalSyncDefaultsTask : DefaultTask() {
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localProperties: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val pocketBaseUrl: Property<String>

    @TaskAction
    fun generate() {
        val props = Properties()
        val localPropertiesFile = localProperties.asFile.get()
        if (localPropertiesFile.isFile) {
            localPropertiesFile.inputStream().use(props::load)
        }
        val url = props.getProperty("opentasks.pocketbase.url")
            ?: pocketBaseUrl.get()
        val packageDir = outputDir.file("com/udnahc/opentasks").get().asFile
        packageDir.mkdirs()
        packageDir.resolve("LocalSyncDefaults.kt").writeText(
            """
            package com.udnahc.opentasks

            object LocalSyncDefaults {
                const val POCKETBASE_URL: String = ${url.kotlinLiteral()}
            }
            """.trimIndent()
        )
    }

    private fun String.kotlinLiteral(): String =
        buildString {
            append('"')
            this@kotlinLiteral.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}

val generateLocalSyncDefaults by tasks.registering(GenerateLocalSyncDefaultsTask::class) {
    localProperties.set(rootProject.layout.projectDirectory.file("local.properties"))
    pocketBaseUrl.set(
        providers.gradleProperty("opentasks.pocketbase.url")
            .orElse(providers.environmentVariable("OPENTASKS_POCKETBASE_URL"))
            .orElse("")
    )
    outputDir.set(layout.buildDirectory.dir("generated/source/localSyncDefaults/commonMain/kotlin"))
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "com.udnahc.opentasks.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    sourceSets {
        commonMain {
            kotlin.srcDir(generateLocalSyncDefaults)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.exifinterface)
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.jetbrains.material3.adaptive.navigation3)
            api(libs.kmlog)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Room
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)

            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Rich Text Editor
            implementation(libs.richeditor.compose)

            // PocketBase
            implementation(libs.pocketbase)

            // SLF4J API (required by Ktor / PocketBase SDK)
            implementation(libs.slf4j.api)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.logback.classic)
            implementation(libs.java.keyring)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    publicResClass = true
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*.ComposableSingletons*",
                    "*.MainActivity",
                    "*.OpenTasksApplication",
                    "*.Platform*",
                    "*.Res",
                    "*.Res\$*",
                    "*.ThemeKt",
                    "*.SystemThemeKt",
                    "*.Preview*",
                    "*.PlatformModule*",
                    "*.AppModuleKt",
                    "*.AppDatabaseConstructor*",
                )
                packages(
                    "com.udnahc.opentasks.di",
                    "com.udnahc.opentasks.ui",
                    "com.udnahc.opentasks.ui.*",
                    "com.udnahc.opentasks.widget",
                    "com.udnahc.opentasks.widget.*",
                    "opentasks.composeapp.generated.resources",
                )
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.udnahc.opentasks.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-desktop-release.pro"))
            optimize.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OpenTasks"
            packageVersion = "1.2.0"
            macOS {
                bundleID = "com.udnahc.opentasks"
                iconFile.set(project.file("src/jvmMain/resources/opentasks-macos.icns"))
            }
            windows {
                iconFile.set(project.file("src/jvmMain/resources/ic_launcher.png"))
            }
            linux {
                packageName = "opentasks"
                iconFile.set(project.file("src/jvmMain/resources/ic_launcher.png"))
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        jvmArgs("-Dopentasks.dev.debug=true")
    }
}
