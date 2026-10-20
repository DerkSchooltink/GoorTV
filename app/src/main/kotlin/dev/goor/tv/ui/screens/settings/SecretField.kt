package dev.goor.tv.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.goor.tv.R

/**
 * Password field that is masked by default but can be revealed on demand.
 *
 * The reveal is part of the fix, not a nicety: a trailing space pasted into a
 * masked field is invisible, and it produces a 401 that reads like a server
 * fault. Lives in its own file because SettingsScreen.kt already sits at its
 * detekt file-level function threshold.
 */
@Composable
internal fun SecretField(
    value: String,
    @StringRes labelRes: Int,
    onValueChange: (String) -> Unit,
) {
    val visible = remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        visualTransformation = if (visible.value) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = { visible.value = !visible.value }) {
                Text(stringResource(if (visible.value) R.string.common_hide else R.string.common_show))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
