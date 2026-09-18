package io.github.m1nexoff.themerift.demo

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Theme Rift",
        state = rememberWindowState(size = DpSize(420.dp, 860.dp)),
    ) {
        DemoApp()
    }
}
