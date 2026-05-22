package dev.goor.tv.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.model.isEpgEligible
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = koinViewModel(),
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncingIds by vm.syncingIds.collectAsStateWithLifecycle()
    val epgSyncingIds by vm.epgSyncingIds.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<Source?>(null) }
    var groupsSource by remember { mutableStateOf<Source?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Focus restoration for the global Add Source button (only single-trigger
    // dialog on this screen; per-row edit/groups dialogs naturally restore via
    // Compose's focus tree since the row button stays composed).
    val addSourceFocus = remember { FocusRequester() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (syncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { showAddDialog = true },
                        enabled = !syncing,
                        modifier = Modifier.focusRequester(addSourceFocus),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add source")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(sources, key = { it.id }) { source ->
                SourceItem(
                    source = source,
                    isSyncing = source.id in syncingIds,
                    isEpgSyncing = source.id in epgSyncingIds,
                    onSync = { vm.syncSource(source) },
                    onEpgSync = { vm.syncEpg(source) },
                    onEdit = { editingSource = source },
                    onGroups = { groupsSource = source },
                    onDelete = { vm.deleteSource(source) },
                )
                HorizontalDivider()
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAddM3u = { name, url, headers, maxStreams ->
                vm.addM3uSource(name, url, headers, maxStreams)
                showAddDialog = false
            },
            onAddXtream = { name, url, user, pass, headers, maxStreams ->
                vm.addXtreamSource(name, url, user, pass, headers, maxStreams)
                showAddDialog = false
            }
        )
        DisposableEffect(Unit) { onDispose { addSourceFocus.requestFocus() } }
    }

    editingSource?.let { source ->
        EditSourceDialog(
            source = source,
            onDismiss = { editingSource = null },
            onUpdate = { updated ->
                vm.updateSource(updated)
                editingSource = null
            },
        )
    }

    groupsSource?.let { source ->
        val availableGroups by vm.getGroupsForSource(source.id).collectAsStateWithLifecycle(emptyList())
        GroupsDialog(
            source = source,
            availableGroups = availableGroups,
            onDismiss = { groupsSource = null },
            onConfirm = { selected ->
                vm.updateIncludedGroups(source.id, selected)
                groupsSource = null
            },
        )
    }
}

@Composable
private fun SourceItem(
    source: Source,
    isSyncing: Boolean,
    isEpgSyncing: Boolean,
    onSync: () -> Unit,
    onEpgSync: () -> Unit,
    onEdit: () -> Unit,
    onGroups: () -> Unit,
    onDelete: () -> Unit,
) {
    val groupCount = source.includedGroups?.split("|")?.filter { it.isNotBlank() }?.size
    val epgEligible = source.isEpgEligible()
    ListItem(
        headlineContent = { Text(source.name) },
        supportingContent = {
            Column {
                Text(when {
                    source.includedGroups == null -> "${source.type.name} · all groups"
                    source.includedGroups.isBlank() -> "${source.type.name} · no groups selected"
                    else -> "${source.type.name} · $groupCount group${if (groupCount == 1) "" else "s"}"
                })
                Text(
                    text = source.lastSyncedAt?.let { "Last synced ${formatRelativeTime(it)}" } ?: "Never synced",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (epgEligible) {
                    val epgLine = when {
                        !source.epgLastError.isNullOrBlank() -> "EPG error: ${source.epgLastError}"
                        source.lastEpgSyncedAt != null -> "EPG synced ${formatRelativeTime(source.lastEpgSyncedAt)}"
                        else -> "EPG never synced"
                    }
                    val epgColor = if (!source.epgLastError.isNullOrBlank())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = epgLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = epgColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync channels")
                    }
                }
                if (epgEligible) {
                    if (isEpgSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = onEpgSync, enabled = !isSyncing) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Sync EPG")
                        }
                    }
                }
                IconButton(onClick = onGroups, enabled = !isSyncing) {
                    Icon(Icons.Default.Tune, contentDescription = "Configure groups")
                }
                IconButton(onClick = onEdit, enabled = !isSyncing) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete, enabled = !isSyncing) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    )
}

@Composable
private fun GroupsDialog(
    source: Source,
    availableGroups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    // Key the selection state on the persisted value so an external sync
    // (e.g., the user toggled groups elsewhere, or a fresh Room emission)
    // doesn't leave the dialog showing stale checks. Earlier this was two
    // separate `remember`s — `initial` was keyed on `includedGroups` but
    // `selected` wasn't, so it kept the first value forever.
    var selected by remember(source.includedGroups) {
        val parsed = source.includedGroups
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.toMutableSet()
            ?: mutableSetOf()
        mutableStateOf(parsed)
    }
    var search by remember { mutableStateOf("") }

    val visible = remember(search, availableGroups) {
        if (search.isBlank()) availableGroups
        else availableGroups.filter { it.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Groups — ${source.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Filter groups") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { selected = (selected + visible).toMutableSet() }) {
                        Text(if (search.isBlank()) "All" else "Select matching")
                    }
                    TextButton(onClick = {
                        selected = if (search.isBlank()) mutableSetOf()
                                   else (selected - visible.toSet()).toMutableSet()
                    }) {
                        Text(if (search.isBlank()) "None" else "Deselect matching")
                    }
                }
                HorizontalDivider()
                if (availableGroups.isEmpty()) {
                    Text(
                        "No groups found. Sync the source first.",
                        modifier = Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(visible) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (group in selected)
                                            (selected - group).toMutableSet()
                                        else
                                            (selected + group).toMutableSet()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = group in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) (selected + group).toMutableSet()
                                                   else (selected - group).toMutableSet()
                                    },
                                )
                                Text(group, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAddM3u: (name: String, url: String, headers: String?, maxConcurrentStreams: Int) -> Unit,
    onAddXtream: (name: String, url: String, user: String, pass: String, headers: String?, maxConcurrentStreams: Int) -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    var sourceType by remember { mutableStateOf(SourceType.M3U) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var maxStreams by remember { mutableStateOf("0") }
    // Land focus on the first field so D-pad / keyboard can type immediately.
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    val urlValid = remember(url) {
        val t = url.trim()
        t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)
    }
    val canSubmit = name.isNotBlank() &&
        urlValid &&
        (sourceType != SourceType.XTREAM || (username.isNotBlank() && password.isNotBlank()))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceType.entries.filter { it != SourceType.MANUAL }.forEach { type ->
                        FilterChip(
                            selected = sourceType == type,
                            onClick = { sourceType = type },
                            label = { Text(type.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = url.isNotBlank() && !urlValid,
                    supportingText = {
                        if (url.isNotBlank() && !urlValid) Text("Must start with http:// or https://")
                    },
                )
                if (sourceType == SourceType.XTREAM) {
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text("HTTP Headers (optional)") },
                    placeholder = { Text("User-Agent: MyApp\nX-Token: secret") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxStreams,
                    onValueChange = { if (it.all(Char::isDigit)) maxStreams = it },
                    label = { Text("Max concurrent streams (0 = unlimited)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = headers.takeIf { it.isNotBlank() }
                    val max = maxStreams.toIntOrNull() ?: 0
                    if (sourceType == SourceType.M3U) onAddM3u(name.trim(), url.trim(), h, max)
                    else onAddXtream(name.trim(), url.trim(), username.trim(), password, h, max)
                },
                enabled = canSubmit,
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatRelativeTime(timestampMs: Long): String {
    val delta = System.currentTimeMillis() - timestampMs
    return when {
        delta < 60_000L -> "just now"
        delta < 3_600_000L -> "${delta / 60_000}m ago"
        delta < 86_400_000L -> "${delta / 3_600_000}h ago"
        else -> "${delta / 86_400_000}d ago"
    }
}

@Composable
private fun EditSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onUpdate: (Source) -> Unit,
) {
    var name by remember { mutableStateOf(source.name) }
    var url by remember { mutableStateOf(source.url) }
    var username by remember { mutableStateOf(source.username ?: "") }
    var password by remember { mutableStateOf(source.password ?: "") }
    var headers by remember { mutableStateOf(source.headers ?: "") }
    var maxStreams by remember { mutableStateOf(source.maxConcurrentStreams.toString()) }
    var epgUrl by remember { mutableStateOf(source.epgUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (source.type == SourceType.XTREAM) {
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (source.type == SourceType.M3U) {
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        label = { Text("EPG URL (optional)") },
                        placeholder = { Text("Auto-detected from playlist if blank") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text("HTTP Headers (optional)") },
                    placeholder = { Text("User-Agent: MyApp\nX-Token: secret") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxStreams,
                    onValueChange = { if (it.all(Char::isDigit)) maxStreams = it },
                    label = { Text("Max concurrent streams (0 = unlimited)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onUpdate(
                    source.copy(
                        name = name,
                        url = url,
                        username = username.takeIf { it.isNotBlank() },
                        password = password.takeIf { it.isNotBlank() },
                        headers = headers.takeIf { it.isNotBlank() },
                        maxConcurrentStreams = maxStreams.toIntOrNull() ?: 0,
                        epgUrl = epgUrl.takeIf { it.isNotBlank() && source.type == SourceType.M3U },
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
