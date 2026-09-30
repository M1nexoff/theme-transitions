package io.github.m1nexoff.themetransitions.demo

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { DemoApp() }
