package dev.goor.tv.ui.util

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * TV focus primitives. Replace the ad-hoc
 * `var isFocused by remember { mutableStateOf(false) }` +
 * `onFocusChanged { isFocused = it.isFocused }` + inline border boilerplate
 * that was duplicated across HomeScreen, GuideScreen, and PlayerScreen.
 *
 * This is the pragmatic minimum — the full `androidx.tv.material3` swap is
 * tracked as a follow-up. Keeps the existing Material 3 components, just
 * consolidates the focus pattern.
 */

/** Allocates the focus-state holder. Read its `.value` to drive visuals. */
@Composable
fun rememberTvFocus(): MutableState<Boolean> = remember { mutableStateOf(false) }

/** Wires the state into the focus pipeline. Pair with [rememberTvFocus]. */
fun Modifier.trackTvFocus(state: MutableState<Boolean>): Modifier =
    onFocusChanged { state.value = it.isFocused }

/**
 * Standard 2 dp border focus indicator. Default [color] is the surface primary;
 * pass [Color.White] for overlay buttons drawn on video / dark fills where the
 * theme primary would disappear.
 */
@Composable
fun Modifier.focusBorder(
    focused: Boolean,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier = if (focused) this.border(2.dp, color, shape) else this
