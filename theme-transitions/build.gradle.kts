import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    // Held at the oldest Kotlin that Compose Multiplatform 1.11 supports. Built at the compiler's own
    // version, the library would demand that version, and its stdlib, of every app that uses it.
    coreLibrariesVersion = "2.2.20"
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
        languageVersion = KotlinVersion.KOTLIN_2_2
    }

    android {
        namespace = "io.github.m1nexoff.themetransitions"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.animationCore)
            implementation(libs.compose.foundationLayout)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        named("desktopTest").dependencies {
            // Renders the effect off screen, through Skia, to check what it actually draws.
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.foundation)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    // Only Maven Central needs signatures, and only its release job has the key.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
