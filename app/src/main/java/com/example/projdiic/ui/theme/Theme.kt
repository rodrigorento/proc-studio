package com.example.projdiic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app always uses the same dark scheme as the overlay screens, so the
// setup screen and the gate look like one product.
private val ScrollGateColorScheme = darkColorScheme(
    primary = Leaf,
    onPrimary = Ink,
    primaryContainer = LeafContainer,
    onPrimaryContainer = Leaf,
    secondary = Leaf,
    onSecondary = Ink,
    secondaryContainer = Surface2,
    onSecondaryContainer = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Surface1,
    onSurface = Paper,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Line,
    outlineVariant = Line,
    error = Clay,
    onError = Ink,
)

@Composable
fun ProjDIICTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScrollGateColorScheme,
        typography = Typography,
        content = content
    )
}
