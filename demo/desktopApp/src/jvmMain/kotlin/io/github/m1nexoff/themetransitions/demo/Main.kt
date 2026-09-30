package io.github.m1nexoff.themetransitions.demo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// `--bench=<case>` runs a bench case instead of waiting for clicks.
fun main(args: Array<String>) = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Theme Transitions",
        state = rememberWindowState(size = DpSize(420.dp, 860.dp)),
    ) {
        DemoApp(bench = args.firstOrNull { it.startsWith("--bench=") }?.substringAfter('='))
    }
}
