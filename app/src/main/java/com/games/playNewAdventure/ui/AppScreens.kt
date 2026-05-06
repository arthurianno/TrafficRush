package com.games.playNewAdventure.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.games.playNewAdventure.R

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.LOADING_SCREEN),
        contentAlignment = Alignment.Center
    ) {
        FarmBackground()
        DarkOverlay(alpha = OVERLAY_ALPHA_LOADING)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_egg_flip),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(LOADING_LOGO_WIDTH_FRACTION)
                    .heightIn(max = LOADING_LOGO_MAX_HEIGHT)
            )
            Spacer(modifier = Modifier.height(LOADING_LOGO_SPINNER_GAP))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = LOADING_SPINNER_STROKE
            )
        }
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
fun FanticScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.FANTIC_SCREEN)
    ) {
        FarmBackground()
        DarkOverlay(alpha = OVERLAY_ALPHA_FANTIC)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(
                    horizontal = FANTIC_TOP_BAR_H_PADDING,
                    vertical = FANTIC_TOP_BAR_V_PADDING
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopIconButton(resId = R.drawable.btn_settings, contentDescription = "Settings")
            TopIconButton(resId = R.drawable.btn_home, contentDescription = "Home")
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = FANTIC_CONTENT_H_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_egg_flip),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(FANTIC_LOGO_WIDTH_FRACTION)
                    .heightIn(max = FANTIC_LOGO_MAX_HEIGHT)
            )
            Spacer(modifier = Modifier.height(FANTIC_LOGO_BUTTON_GAP))
            GameMenuButton(
                resId = R.drawable.btn_start,
                contentDescription = "Start",
                testTag = UiTestTags.FANTIC_START
            )
            Spacer(modifier = Modifier.height(FANTIC_BUTTON_GAP))
            GameMenuButton(
                resId = R.drawable.btn_levels,
                contentDescription = "Levels",
                testTag = UiTestTags.FANTIC_LEVELS
            )
            Spacer(modifier = Modifier.height(FANTIC_BUTTON_GAP))
            GameMenuButton(
                resId = R.drawable.btn_scores,
                contentDescription = "Scores",
                testTag = UiTestTags.FANTIC_SCORES
            )
        }
    }
}

@Composable
fun PushPermissionScreen(
    onAllowClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.PUSH_PERMISSION_SCREEN)
    ) {
        FarmBackground()
        BottomGradient(heightFraction = PUSH_BG_GRADIENT_HEIGHT)

        DecoImage(
            resId = R.drawable.push_egg_top_right,
            width = PUSH_EGG_TR_WIDTH,
            height = PUSH_EGG_TR_HEIGHT,
            alignment = Alignment.TopEnd,
            offsetX = PUSH_EGG_TR_OFFSET_X,
            offsetY = PUSH_EGG_TR_OFFSET_Y
        )
        DecoImage(
            resId = R.drawable.push_egg_left,
            width = PUSH_EGG_L_WIDTH,
            height = PUSH_EGG_L_HEIGHT,
            alignment = Alignment.TopStart,
            offsetX = PUSH_EGG_L_OFFSET_X,
            offsetY = PUSH_EGG_L_OFFSET_Y
        )
        DecoImage(
            resId = R.drawable.push_nest_eggs,
            width = PUSH_NEST_WIDTH,
            height = PUSH_NEST_HEIGHT,
            alignment = Alignment.TopEnd,
            offsetX = PUSH_NEST_OFFSET_X,
            offsetY = PUSH_NEST_OFFSET_Y
        )
        DecoImage(
            resId = R.drawable.chicken_character,
            width = PUSH_CHICKEN_WIDTH,
            height = PUSH_CHICKEN_HEIGHT,
            alignment = Alignment.TopCenter,
            offsetX = PUSH_CHICKEN_OFFSET_X,
            offsetY = PUSH_CHICKEN_OFFSET_Y
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    start = PUSH_CONTENT_H_PADDING,
                    end = PUSH_CONTENT_H_PADDING,
                    bottom = PUSH_CONTENT_BOTTOM_PADDING
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ALLOW NOTIFICATIONS ABOUT\nBONUSES AND PROMOS",
                color = Color(0xFFFFF0A0),
                fontSize = PUSH_CONTENT_TITLE_SIZE,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = PUSH_CONTENT_TITLE_HEIGHT,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(PUSH_CONTENT_TITLE_SUBTITLE_GAP))
            Text(
                text = "Stay tuned with best offers\nfrom our casino",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = PUSH_CONTENT_SUBTITLE_SIZE,
                fontWeight = FontWeight.Normal,
                lineHeight = PUSH_CONTENT_SUBTITLE_HEIGHT,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(PUSH_CONTENT_SUBTITLE_BUTTON_GAP))
            Image(
                painter = painterResource(id = R.drawable.btn_accept),
                contentDescription = "Accept",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(PUSH_ACCEPT_WIDTH_FRACTION)
                    .heightIn(min = PUSH_ACCEPT_MIN_HEIGHT, max = PUSH_ACCEPT_MAX_HEIGHT)
                    .testTag(UiTestTags.PUSH_ACCEPT)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAllowClick
                    )
            )
            Spacer(modifier = Modifier.height(PUSH_CONTENT_BUTTON_SKIP_GAP))
            Text(
                text = "Skip",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = PUSH_SKIP_SIZE,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .testTag(UiTestTags.PUSH_SKIP)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkipClick
                    )
                    .padding(
                        horizontal = PUSH_SKIP_H_PADDING,
                        vertical = PUSH_SKIP_V_PADDING
                    )
            )
        }
    }
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
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}