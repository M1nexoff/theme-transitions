plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":demo:shared"))
            // RenderPreviews builds transitions and reads theme colours itself.
            implementation(project(":theme-transitions"))
            implementation(libs.compose.material3)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.m1nexoff.themetransitions.demo.MainKt"
    }
}

// Renders docs/preview.png, the README's picture of every transition, off screen.
tasks.register<JavaExec>("renderPreviews") {
    group = "documentation"
    description = "Renders docs/preview.png"
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("io.github.m1nexoff.themetransitions.demo.RenderPreviewsKt")
    args(rootProject.layout.projectDirectory.file("docs/preview.png").asFile.path)
}

// Renders docs/showreel: every transition and its variants through all three themes, as showreel.mp4
// and a GIF per transition for the README. Needs ffmpeg: -Pffmpeg=<path>, or FFMPEG in the environment.
tasks.register<JavaExec>("renderShowreel") {
    group = "documentation"
    description = "Renders docs/showreel (needs ffmpeg)"
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider)
    classpath = files(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("io.github.m1nexoff.themetransitions.demo.RenderShowreelKt")
    val ffmpeg = providers.gradleProperty("ffmpeg").orElse(providers.environmentVariable("FFMPEG")).orElse("ffmpeg")
    args(
        ffmpeg.map { "--ffmpeg=$it" }.get(),
        "--out=" + rootProject.layout.projectDirectory.dir("docs/showreel").asFile.path,
    )
}
