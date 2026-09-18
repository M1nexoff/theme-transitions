package io.github.m1nexoff.themerift.demo

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.m1nexoff.themerift.RiftColors

// Three themes rather than a light/dark Boolean, to show that ThemeRift takes any state.
enum class DemoTheme(
    val label: String,
    val isDark: Boolean,
    val colorScheme: ColorScheme,
    private val lightning: Color,
) {
    Light(
        label = "Light",
        isDark = false,
        colorScheme = lightColorScheme(
            primary = Color(0xFF4F46E5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E7FF),
            onPrimaryContainer = Color(0xFF1E1B4B),
            background = Color(0xFFFAFAFC),
            onBackground = Color(0xFF1E213F),
            surface = Color.White,
            onSurface = Color(0xFF1E213F),
            surfaceVariant = Color(0xFFF1F1F6),
            onSurfaceVariant = Color(0xFF6F6F7E),
            outlineVariant = Color(0xFFE2E2EA),
        ),
        lightning = RiftColors.DefaultLightning,
    ),
    Dark(
        label = "Dark",
        isDark = true,
        colorScheme = darkColorScheme(
            primary = Color(0xFFA5B4FC),
            onPrimary = Color(0xFF1E1B4B),
            primaryContainer = Color(0xFF312E81),
            onPrimaryContainer = Color(0xFFE0E7FF),
            background = Color(0xFF15151F),
            onBackground = Color(0xFFECECF4),
            surface = Color(0xFF1E1E2A),
            onSurface = Color(0xFFECECF4),
            surfaceVariant = Color(0xFF272734),
            onSurfaceVariant = Color(0xFF9A9AAD),
            outlineVariant = Color(0xFF34344A),
        ),
        lightning = RiftColors.DefaultLightning,
    ),
    Dusk(
        label = "Dusk",
        isDark = true,
        colorScheme = darkColorScheme(
            primary = Color(0xFFFFB86C),
            onPrimary = Color(0xFF3B1F00),
            primaryContainer = Color(0xFF5B3A1A),
            onPrimaryContainer = Color(0xFFFFE3C4),
            background = Color(0xFF2A1B3D),
            onBackground = Color(0xFFF8E9FF),
            surface = Color(0xFF362450),
            onSurface = Color(0xFFF8E9FF),
            surfaceVariant = Color(0xFF442E63),
            onSurfaceVariant = Color(0xFFCDB8E6),
            outlineVariant = Color(0xFF55397A),
        ),
        // Red is lost against the violet; the rift picks a colour per theme.
        lightning = Color(0xFF18FFFF),
    ),
    ;

    fun riftColors(): RiftColors = RiftColors(
        background = colorScheme.background,
        glow = colorScheme.onBackground,
        lightning = lightning,
    )

    fun next(): DemoTheme = entries[(ordinal + 1) % entries.size]
}
