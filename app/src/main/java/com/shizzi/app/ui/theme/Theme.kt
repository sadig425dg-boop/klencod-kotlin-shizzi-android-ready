package com.shizzi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ShizziDarkColorScheme = darkColorScheme(
    primary = ShizziPink,
    onPrimary = ShizziTextPrimary,
    secondary = ShizziViolet,
    onSecondary = ShizziTextPrimary,
    background = ShizziBackground,
    onBackground = ShizziTextPrimary,
    surface = ShizziSurface,
    onSurface = ShizziTextPrimary,
    surfaceVariant = ShizziSurfaceElevated,
    onSurfaceVariant = ShizziTextSecondary,
    error = ShizziDanger,
)

@Composable
fun ShizziTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Shizzi ships a single, deliberately dark neon theme (see reference UI).
    MaterialTheme(
        colorScheme = ShizziDarkColorScheme,
        typography = ShizziTypography,
        content = content,
    )
}
