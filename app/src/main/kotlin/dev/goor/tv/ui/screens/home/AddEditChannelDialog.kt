package dev.goor.tv.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.goor.tv.data.model.Channel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditChannelDialog(
    channel: Channel? = null,
    groups: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, logoUrl: String?, group: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val nameFocus = remember { FocusRequester() }
    var name by remember { mutableStateOf(channel?.name ?: "") }
    var url by remember { mutableStateOf(channel?.url ?: "") }
    // Land focus on the first field so the D-pad / hardware keyboard can type
    // immediately without an extra navigation step.
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
    val urlValid = remember(url) {
        val t = url.trim()
        t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)
    }
    var logoUrl by remember { mutableStateOf(channel?.logoUrl ?: "") }
    var group by remember { mutableStateOf(channel?.group ?: "") }
    var groupExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Channel") },
            text = { Text("Delete \"${channel?.name}\"?") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (channel == null) "Add Channel" else "Edit Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = url.isNotBlank() && !urlValid,
                    supportingText = {
                        if (url.isNotBlank() && !urlValid) Text("Must start with http:// or https://")
                    },
                )
                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("Logo URL (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = { groupExpanded = it },
                ) {
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it; groupExpanded = true },
                        label = { Text("Group (optional)") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                    )
                    val filtered = groups.filter { it.contains(group, ignoreCase = true) }
                    if (filtered.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = groupExpanded,
                            onDismissRequest = { groupExpanded = false },
                        ) {
                            filtered.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g) },
                                    onClick = { group = g; groupExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        url.trim(),
                        logoUrl.trim().takeIf { it.isNotBlank() },
                        group.trim().takeIf { it.isNotBlank() },
                    )
                },
                enabled = name.isNotBlank() && urlValid,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
