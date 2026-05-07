package com.games.playNewAdventure.ui

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.games.playNewAdventure.R

@Composable
internal fun FarmBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.farm_background),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
internal fun DarkOverlay(alpha: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
    )
}

@Composable
internal fun BoxScope.BottomGradient(heightFraction: Float = PUSH_BG_GRADIENT_HEIGHT) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .align(Alignment.BottomCenter)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xD8000000))
                )
            )
    )
}

@Composable
internal fun BoxScope.DecoImage(
    @DrawableRes resId: Int,
    width: Dp,
    height: Dp,
    alignment: Alignment,
    offsetX: Dp,
    offsetY: Dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .align(alignment)
            .size(width = width, height = height)
            .offset(x = offsetX, y = offsetY)
    )
}

@Composable
internal fun TopIconButton(
    @DrawableRes resId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(FANTIC_TOP_ICON_SIZE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick ?: {
                    Toast.makeText(context, R.string.coming_soon, Toast.LENGTH_SHORT).show()
                }
            )
    )
}

@Composable
internal fun GameMenuButton(
    @DrawableRes resId: Int,
    contentDescription: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .testTag(testTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    Toast.makeText(context, R.string.coming_soon, Toast.LENGTH_SHORT).show()
                }
            )
    )
}
