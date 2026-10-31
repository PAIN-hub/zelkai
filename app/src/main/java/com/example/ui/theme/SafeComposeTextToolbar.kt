package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * Safe, pure-Compose implementation of [TextToolbar] that replaces Android's native
 * `FloatingActionMode` (`TYPE_FLOATING`) on `DecorView`.
 *
 * Prevents `E/DecorView[]: Destroying unexpected ActionMode instance of TYPE_FLOATING`
 * when pasting API keys, switching text fields, or dismissing Dialogs while text selection is active.
 */
@Stable
class SafeComposeTextToolbar : TextToolbar {
    private var _status by mutableStateOf(TextToolbarStatus.Hidden)
    override val status: TextToolbarStatus
        get() = _status

    var targetRect by mutableStateOf(Rect.Zero)
        private set

    var onCopyRequested by mutableStateOf<(() -> Unit)?>(null)
        private set
    var onPasteRequested by mutableStateOf<(() -> Unit)?>(null)
        private set
    var onCutRequested by mutableStateOf<(() -> Unit)?>(null)
        private set
    var onSelectAllRequested by mutableStateOf<(() -> Unit)?>(null)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        targetRect = rect
        this.onCopyRequested = onCopyRequested
        this.onPasteRequested = onPasteRequested
        this.onCutRequested = onCutRequested
        this.onSelectAllRequested = onSelectAllRequested
        _status = if (
            onCopyRequested != null ||
            onPasteRequested != null ||
            onCutRequested != null ||
            onSelectAllRequested != null
        ) {
            TextToolbarStatus.Shown
        } else {
            TextToolbarStatus.Hidden
        }
    }

    override fun hide() {
        _status = TextToolbarStatus.Hidden
        onCopyRequested = null
        onPasteRequested = null
        onCutRequested = null
        onSelectAllRequested = null
    }
}

@Composable
fun ProvideSafeTextToolbar(
    content: @Composable () -> Unit
) {
    val toolbar = remember { SafeComposeTextToolbar() }
    val density = LocalDensity.current

    DisposableEffect(toolbar) {
        onDispose {
            toolbar.hide()
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box {
            content()

            if (toolbar.status == TextToolbarStatus.Shown) {
                val actions = buildList<Pair<String, () -> Unit>> {
                    toolbar.onCutRequested?.let { cb ->
                        add("Cut" to {
                            toolbar.hide()
                            cb.invoke()
                        })
                    }
                    toolbar.onCopyRequested?.let { cb ->
                        add("Copy" to {
                            toolbar.hide()
                            cb.invoke()
                        })
                    }
                    toolbar.onPasteRequested?.let { cb ->
                        add("Paste" to {
                            toolbar.hide()
                            cb.invoke()
                        })
                    }
                    toolbar.onSelectAllRequested?.let { cb ->
                        add("Select all" to {
                            toolbar.hide()
                            cb.invoke()
                        })
                    }
                }

                if (actions.isNotEmpty()) {
                    val toolbarHeightPx = with(density) { 44.dp.toPx() }
                    val offsetX = toolbar.targetRect.left.roundToInt().coerceAtLeast(16)
                    val rawY = (toolbar.targetRect.top - toolbarHeightPx - 8f).roundToInt()
                    val offsetY = if (rawY > 12) rawY else (toolbar.targetRect.bottom + 8f).roundToInt()

                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(offsetX, offsetY),
                        onDismissRequest = { toolbar.hide() },
                        properties = PopupProperties(
                            focusable = false,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                            clippingEnabled = true
                        )
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                actions.forEachIndexed { idx, (label, action) ->
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = action)
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                    if (idx < actions.lastIndex) {
                                        VerticalDivider(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
