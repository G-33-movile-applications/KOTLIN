package com.mobile.mymeds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// --- Light Theme Colors ---
private val LightColorScheme = lightColorScheme(
    primary = LightBluePrimary,      // #9FB3DF (Used for buttons, primary text, active elements)
    secondary = SkyBlueSecondary,    // #9EC6F3 (Used for secondary buttons, floating action buttons)
    tertiary = TealTertiary,         // #BDDDE4 (Used for accents, complementary elements)
    background = White,              // #FFFFFF (Main screen background)
    surface = CreamBackground,       // #FFF1D5 (Used for cards, text field backgrounds, dialogs)
    onPrimary = White,               // Content (text, icons) on primary
    onSecondary = White,
    onTertiary = DarkGrey,
    onBackground = Black,            // Content on background
    onSurface = DarkGrey             // Content on surface
)

// --- Dark Theme Colors ---
private val DarkColorScheme = darkColorScheme(
    primary = LightBluePrimary,
    secondary = SkyBlueSecondary,
    tertiary = TealTertiary,
    background = Black,
    surface = DarkGrey,
    onPrimary = Black,
    onSecondary = Black,
    onTertiary = White,
    onBackground = White,
    onSurface = White
)

// --- Theme Wrapper ---
@Composable
fun MyMedsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // 👈 Usa tu PoetsenOne definido en Typography.kt
        content = content
    )
}
