package com.games.playNewAdventure.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Common ──────────────────────────────────────────────────────────────────

internal val OVERLAY_ALPHA_LOADING  = 0.25f
internal val OVERLAY_ALPHA_FANTIC   = 0.20f
internal val BOTTOM_GRADIENT_HEIGHT = 0.48f  // fraction of screen height
internal val BOTTOM_GRADIENT_ALPHA  = 0xD8   // ~85 % opaque at the very bottom

// ─── LoadingScreen ───────────────────────────────────────────────────────────

internal val LOADING_LOGO_WIDTH = 295.dp
internal val LOADING_LOGO_HEIGHT = 305.dp

internal val LOADING_LOGO_SPINNER_GAP    = 150.dp
internal val LOADING_SPINNER_STROKE      = 3.dp

// ─── FanticScreen ────────────────────────────────────────────────────────────

internal val FANTIC_TOP_BAR_H_PADDING    = 20.dp

internal val FANTIC_LOGO_WIDTH = 450.dp
internal val FANTIC_LOGO_HEIGHT = 280.dp

internal val FANTIC_START_BUTTON_WIDTH = 300.dp
internal val FANTIC_START_BUTTON_HEIGHT = 82.dp

internal val FANTIC_LEVELS_BUTTON_WIDTH = 300.dp
internal val FANTIC_LEVELS_BUTTON_HEIGHT = 82.dp

internal val FANTIC_SCORES_BUTTON_WIDTH = 300.dp
internal val FANTIC_SCORES_BUTTON_HEIGHT = 82.dp
internal val FANTIC_TOP_BAR_V_PADDING    = 12.dp
internal val FANTIC_TOP_ICON_SIZE        = 56.dp
internal val FANTIC_CONTENT_H_PADDING    = 32.dp

internal val FANTIC_CONTENT_OFFSET_Y = 20.dp
internal val FANTIC_LOGO_BUTTON_GAP      = 40.dp
internal val FANTIC_BUTTON_GAP           = 20.dp

// ─── PushPermissionScreen — decorative elements ───────────────────────────────
// Note on offsets inside Box:
//   align(TopEnd) + offset(x > 0) → moves element further RIGHT (off-screen)
//   align(TopEnd) + offset(x < 0) → moves element LEFT (onto screen)
//   align(TopStart) + offset(x < 0) → moves element LEFT (off-screen)

internal val PUSH_BG_GRADIENT_HEIGHT = 0.48f

internal val PUSH_EGG_TR_WIDTH     = 180.dp
internal val PUSH_EGG_TR_HEIGHT    = 180.dp
internal val PUSH_EGG_TR_OFFSET_X  = 45.dp
internal val PUSH_EGG_TR_OFFSET_Y  = (-10).dp

internal val PUSH_EGG_L_WIDTH      = 150.dp
internal val PUSH_EGG_L_HEIGHT     = 150.dp
internal val PUSH_EGG_L_OFFSET_X   = (-30).dp
internal val PUSH_EGG_L_OFFSET_Y   = 400.dp

internal val PUSH_NEST_WIDTH       = 420.dp
internal val PUSH_NEST_HEIGHT      = 360.dp
internal val PUSH_NEST_OFFSET_X    = 100.dp
internal val PUSH_NEST_OFFSET_Y    = 200.dp

internal val PUSH_CHICKEN_WIDTH    = 400.dp
internal val PUSH_CHICKEN_HEIGHT   = 400.dp
internal val PUSH_CHICKEN_OFFSET_X = (-15).dp
internal val PUSH_CHICKEN_OFFSET_Y = 20.dp

// ─── PushPermissionScreen — bottom content ────────────────────────────────────

internal val PUSH_CONTENT_H_PADDING     = 20.dp
internal val PUSH_CONTENT_BOTTOM_PADDING= 28.dp
internal val PUSH_CONTENT_TITLE_SIZE    = 22.sp
internal val PUSH_CONTENT_TITLE_HEIGHT  = 30.sp
internal val PUSH_CONTENT_SUBTITLE_SIZE = 14.sp
internal val PUSH_CONTENT_SUBTITLE_HEIGHT = 20.sp
internal val PUSH_CONTENT_TITLE_SUBTITLE_GAP = 10.dp
internal val PUSH_CONTENT_SUBTITLE_BUTTON_GAP = 22.dp
internal val PUSH_CONTENT_BUTTON_SKIP_GAP = 14.dp

internal val PUSH_ACCEPT_WIDTH_FRACTION = 0.82f
internal val PUSH_ACCEPT_MIN_HEIGHT     = 64.dp
internal val PUSH_ACCEPT_MAX_HEIGHT     = 104.dp

internal val PUSH_SKIP_SIZE             = 20.sp
internal val PUSH_SKIP_H_PADDING        = 32.dp
internal val PUSH_SKIP_V_PADDING        = 10.dp
