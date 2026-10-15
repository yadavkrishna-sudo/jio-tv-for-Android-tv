@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.example.opentv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * D-pad friendly text field. The outer surface is what D-pad navigation lands on; pressing OK
 * "enters" the field and opens the on-screen keyboard. Leaving (Down/Up, or the IME Done key)
 * returns to plain navigation. This avoids the keyboard popping up just because focus passed over it.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoEdit: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
) {
    val inputFocus = remember { FocusRequester() }
    val shellFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var editing by remember { mutableStateOf(autoEdit) }
    var inputWasFocused by remember { mutableStateOf(false) }
    var shellHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }   // let canFocus = true be applied first
            runCatching { inputFocus.requestFocus() }
            keyboard?.show()
        }
    }

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val focusedBorder = Border(BorderStroke(3.dp, colors.primary), shape = shape)
    val idleBorder = Border(BorderStroke(1.dp, colors.border), shape = shape)

    Surface(
        onClick = { editing = true },
        modifier = modifier
            .focusRequester(shellFocus)
            .onFocusChanged { shellHasFocus = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.surface,
            focusedContainerColor = colors.surfaceVariant,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = if (shellHasFocus) focusedBorder else idleBorder,
            focusedBorder = focusedBorder,
        ),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = {
                keyboard?.hide()
                editing = false
                runCatching { shellFocus.requestFocus() }
                onSubmit()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .focusProperties { canFocus = editing }
                .focusRequester(inputFocus)
                .onFocusChanged {
                    if (it.isFocused) inputWasFocused = true
                    else if (inputWasFocused) {
                        inputWasFocused = false
                        editing = false
                    }
                },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    inner()
                }
            },
        )
    }
}
