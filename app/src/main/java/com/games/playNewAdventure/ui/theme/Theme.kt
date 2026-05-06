package com.games.playNewAdventure.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.games.playNewAdventure.R

@Composable
fun TrafficRushTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = lightColorScheme(
        primary = colorResource(id = R.color.accent_color),
        secondary = colorResource(id = R.color.secondary_background),
        background = colorResource(id = R.color.primary_background),
        surface = colorResource(id = R.color.secondary_background),
        onPrimary = colorResource(id = R.color.button_text),
        onSecondary = colorResource(id = R.color.primary_text),
        onBackground = colorResource(id = R.color.primary_text),
        onSurface = colorResource(id = R.color.primary_text),
        onSurfaceVariant = colorResource(id = R.color.secondary_text)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
