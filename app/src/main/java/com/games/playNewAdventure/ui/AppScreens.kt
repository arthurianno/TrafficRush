package com.games.playNewAdventure.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.games.playNewAdventure.R

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.LOADING_SCREEN)
            .gameScreenBackground()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // TODO: replace with a higher-resolution splash export, recommended 1080x2400 or larger.
        // Current exported PNG is 393x852; keep it fitted instead of fullscreen-upscaling it.
        CenteredPngFrame(drawableRes = R.drawable.splash_screen)
    }
}

@Composable
fun NoInternetScreen(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.NO_INTERNET_SCREEN)
            .background(colorResource(id = R.color.primary_background))
            .systemBarsPadding()
            .padding(
                horizontal = dimensionResource(id = R.dimen.screen_horizontal_padding),
                vertical = dimensionResource(id = R.dimen.screen_vertical_padding)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = dimensionResource(id = R.dimen.content_max_width)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.chicken_character),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.spacing_md)))
            Text(
                text = stringResource(id = R.string.no_internet_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colorResource(id = R.color.primary_text)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.spacing_sm)))
            Text(
                text = stringResource(id = R.string.no_internet_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = colorResource(id = R.color.secondary_text)
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.spacing_lg)))
            GameButton(
                text = stringResource(id = R.string.retry_button),
                onClick = onRetryClick,
                modifier = Modifier.testTag(UiTestTags.RETRY_BUTTON)
            )
        }
    }
}

@Composable
fun FanticScreen(
    modifier: Modifier = Modifier,
    showClickZones: Boolean = false
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.FANTIC_SCREEN)
    ) {
        FullScreenPngBackground(drawableRes = R.drawable.fantic_main)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            CropAwareClickZone(
                topFraction = FANTIC_START_TOP_FRACTION,
                heightFraction = FANTIC_START_HEIGHT_FRACTION,
                widthFraction = FANTIC_BUTTON_WIDTH_FRACTION,
                showOverlay = showClickZones,
                testTag = UiTestTags.FANTIC_START,
                onClick = {
                    Toast.makeText(context, R.string.coming_soon, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun PushPermissionScreen(
    onAllowClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
    showClickZones: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.PUSH_PERMISSION_SCREEN)
            .gameScreenBackground()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        CenteredPngFrame(drawableRes = R.drawable.push_permission_screen) {
            TransparentClickZone(
                topFraction = PUSH_ACCEPT_TOP_FRACTION,
                heightFraction = PUSH_ACCEPT_HEIGHT_FRACTION,
                widthFraction = PUSH_ACCEPT_WIDTH_FRACTION,
                showOverlay = showClickZones,
                testTag = UiTestTags.PUSH_ACCEPT,
                onClick = onAllowClick
            )
            TransparentClickZone(
                topFraction = PUSH_SKIP_TOP_FRACTION,
                heightFraction = PUSH_SKIP_HEIGHT_FRACTION,
                widthFraction = PUSH_SKIP_WIDTH_FRACTION,
                showOverlay = showClickZones,
                testTag = UiTestTags.PUSH_SKIP,
                onClick = onSkipClick
            )
        }
    }
}

@Composable
private fun Modifier.gameScreenBackground(): Modifier {
    return background(
        brush = Brush.verticalGradient(
            colors = listOf(
                colorResource(id = R.color.game_background_top),
                colorResource(id = R.color.game_background_bottom)
            )
        )
    )
}

@Composable
private fun FullScreenPngBackground(
    drawableRes: Int,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun CenteredPngFrame(
    drawableRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(CENTERED_IMAGE_SCREEN_PADDING),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val widthByHeight = availableHeight / PNG_DESIGN_ASPECT_RATIO
        val frameWidth = minOf(PNG_DESIGN_WIDTH, availableWidth, widthByHeight).coerceAtLeast(1.dp)
        val frameHeight = frameWidth * PNG_DESIGN_ASPECT_RATIO

        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.CropAwareClickZone(
    topFraction: Float,
    heightFraction: Float,
    widthFraction: Float,
    showOverlay: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val renderedHeightByWidth = maxWidth * PNG_DESIGN_ASPECT_RATIO
    val renderedWidthByHeight = maxHeight / PNG_DESIGN_ASPECT_RATIO
    val renderedWidth: Dp
    val renderedHeight: Dp

    if (renderedHeightByWidth >= maxHeight) {
        renderedWidth = maxWidth
        renderedHeight = renderedHeightByWidth
    } else {
        renderedWidth = renderedWidthByHeight
        renderedHeight = maxHeight
    }

    val imageTopOffset = (maxHeight - renderedHeight) / 2
    ClickZone(
        topOffset = imageTopOffset + renderedHeight * topFraction,
        zoneWidth = renderedWidth * widthFraction,
        zoneHeight = renderedHeight * heightFraction,
        showOverlay = showOverlay,
        testTag = testTag,
        onClick = onClick
    )
}

@Composable
private fun BoxWithConstraintsScope.TransparentClickZone(
    topFraction: Float,
    heightFraction: Float,
    widthFraction: Float,
    showOverlay: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    ClickZone(
        topOffset = maxHeight * topFraction,
        zoneWidth = maxWidth * widthFraction,
        zoneHeight = maxHeight * heightFraction,
        showOverlay = showOverlay,
        testTag = testTag,
        onClick = onClick
    )
}

@Composable
private fun BoxWithConstraintsScope.ClickZone(
    topOffset: Dp,
    zoneWidth: Dp,
    zoneHeight: Dp,
    showOverlay: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = topOffset)
            .width(zoneWidth)
            .height(zoneHeight)
            .testTag(testTag)
            .then(
                if (showOverlay) {
                    Modifier
                        .background(colorResource(id = R.color.debug_click_zone_overlay))
                        .border(1.dp, colorResource(id = R.color.button_text))
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(id = R.dimen.button_height)),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.corner_radius)),
        border = BorderStroke(
            width = dimensionResource(id = R.dimen.button_border_width),
            color = colorResource(id = R.color.button_secondary_border)
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorResource(id = R.color.button_background),
            contentColor = colorResource(id = R.color.button_text)
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold
        )
    }
}

// Click zones are tuned against exported portrait PNGs sized 393x852.
private const val FANTIC_START_TOP_FRACTION = 0.545f
private const val FANTIC_START_HEIGHT_FRACTION = 0.12f
private const val FANTIC_BUTTON_WIDTH_FRACTION = 0.76f

private const val PUSH_ACCEPT_TOP_FRACTION = 0.795f
private const val PUSH_ACCEPT_HEIGHT_FRACTION = 0.10f
private const val PUSH_ACCEPT_WIDTH_FRACTION = 0.76f
private const val PUSH_SKIP_TOP_FRACTION = 0.905f
private const val PUSH_SKIP_HEIGHT_FRACTION = 0.055f
private const val PUSH_SKIP_WIDTH_FRACTION = 0.46f

private const val PNG_DESIGN_ASPECT_RATIO = 852f / 393f
private val PNG_DESIGN_WIDTH = 393.dp
private val CENTERED_IMAGE_SCREEN_PADDING = 16.dp
