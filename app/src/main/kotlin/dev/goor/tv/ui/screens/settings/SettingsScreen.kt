package dev.goor.tv.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import dev.goor.tv.R
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.model.Secret
import dev.goor.tv.data.model.XtreamOutput
import dev.goor.tv.data.model.isEpgEligible
import org.koin.androidx.compose.koinViewModel

private const val PRIVACY_POLICY_URL = "https://derkschooltink.github.io/GoorTV/privacy/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHiddenChannelsClick: () -> Unit = {},
    vm: SettingsViewModel = koinViewModel(),
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncingIds by vm.syncingIds.collectAsStateWithLifecycle()
    val epgSyncingIds by vm.epgSyncingIds.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()
    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<Source?>(null) }
    var groupsSource by remember { mutableStateOf<Source?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Focus restoration for the global Add Source button (only single-trigger
    // dialog on this screen; per-row edit/groups dialogs naturally restore via
    // Compose's focus tree since the row button stays composed).
    val addSourceFocus = remember { FocusRequester() }
    // Land D-pad focus on the first source row once the list loads, so a remote
    // has somewhere to go on entry. One-shot + runCatching-guarded: if the node
    // isn't placed yet, we just fall back to no initial focus rather than crash.
    val firstSourceFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    val snackbarText = snackbarMessage?.let { resolveSnackbarMessage(it) }
    LaunchedEffect(snackbarMessage) {
        if (snackbarText != null) {
            snackbarHostState.showSnackbar(snackbarText)
            vm.clearSnackbar()
        }
    }

    LaunchedEffect(sources) {
        if (!initialFocusDone && sources.isNotEmpty()) {
            initialFocusDone = true
            runCatching { firstSourceFocus.requestFocus() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_source))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // focusRestorer() remembers which per-row button (Edit / Groups / Sync /
        // Delete) was last focused and restores it whenever focus re-enters the
        // column — typically after a dialog dismisses, but also on back-stack
        // return. Complements the explicit FocusRequester on the global Add
        // Source IconButton (which has its own restore in #42).
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.focusRestorer(),
        ) {
            itemsIndexed(sources, key = { _, s -> s.id }) { index, source ->
                SourceItem(
                    source = source,
                    isSyncing = source.id in syncingIds,
                    isEpgSyncing = source.id in epgSyncingIds,
                    onSync = { vm.syncSource(source) },
                    onEpgSync = { vm.syncEpg(source) },
                    onEdit = { editingSource = source },
                    onGroups = { groupsSource = source },
                    onDelete = { vm.deleteSource(source) },
                    // ListItem itself isn't focusable; focusGroup redirects the
                    // focus request to its first focusable child (the Sync button).
                    modifier = if (index == 0) {
                        Modifier.focusRequester(firstSourceFocus).focusGroup()
                    } else {
                        Modifier
                    },
                )
                HorizontalDivider()
            }
            if (hiddenCount > 0) {
                item(key = "hidden_channels_entry") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onHiddenChannelsClick)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(R.string.settings_hidden_channels_entry, hiddenCount),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "privacy_policy_entry") {
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(PRIVACY_POLICY_URL),
                                ),
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.settings_privacy_policy),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            onAddXtream = { name, url, user, pass, headers, maxStreams, output ->
                vm.addXtreamSource(name, url, user, pass, headers, maxStreams, output)
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

@Suppress("SpreadOperator")
@Composable
private fun resolveSnackbarMessage(message: SnackbarMessage): String =
    stringResource(message.resId, *message.args.toTypedArray())

/** Single-line text field with a resource label — keeps the dialog call sites compact. */
@Composable
private fun SingleLineField(
    value: String,
    @androidx.annotation.StringRes labelRes: Int,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
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
    modifier: Modifier = Modifier,
) {
    val groupCount = source.includedGroups?.split("|")?.filter { it.isNotBlank() }?.size
    val epgEligible = source.isEpgEligible()
    ListItem(
        modifier = modifier,
        headlineContent = { Text(source.name) },
        supportingContent = {
            Column {
                Text(when {
                    source.includedGroups == null ->
                        stringResource(R.string.settings_source_all_groups, source.type.name)
                    source.includedGroups.isBlank() ->
                        stringResource(R.string.settings_source_no_groups_selected, source.type.name)
                    else -> pluralStringResource(
                        R.plurals.settings_source_group_count,
                        groupCount ?: 0,
                        source.type.name,
                        groupCount ?: 0,
                    )
                })
                Text(
                    text = source.lastSyncedAt
                        ?.let { stringResource(R.string.settings_last_synced, formatRelativeTime(it)) }
                        ?: stringResource(R.string.settings_never_synced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (epgEligible) {
                    val epgLine = when {
                        !source.epgLastError.isNullOrBlank() ->
                            stringResource(R.string.settings_epg_error, source.epgLastError)
                        source.lastEpgSyncedAt != null ->
                            stringResource(R.string.settings_epg_synced, formatRelativeTime(source.lastEpgSyncedAt))
                        else -> stringResource(R.string.settings_epg_never_synced)
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
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.settings_sync_channels),
                        )
                    }
                }
                if (epgEligible) {
                    if (isEpgSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = onEpgSync, enabled = !isSyncing) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = stringResource(R.string.settings_sync_epg),
                            )
                        }
                    }
                }
                IconButton(onClick = onGroups, enabled = !isSyncing) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.settings_configure_groups))
                }
                IconButton(onClick = onEdit, enabled = !isSyncing) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.settings_edit))
                }
                IconButton(onClick = onDelete, enabled = !isSyncing) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
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
        title = { Text(stringResource(R.string.settings_groups_title, source.name)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.settings_filter_groups)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.settings_clear))
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
                        if (search.isBlank()) Text(stringResource(R.string.settings_select_all))
                        else Text(stringResource(R.string.settings_select_matching))
                    }
                    TextButton(onClick = {
                        selected = if (search.isBlank()) mutableSetOf()
                                   else (selected - visible.toSet()).toMutableSet()
                    }) {
                        if (search.isBlank()) Text(stringResource(R.string.settings_select_none))
                        else Text(stringResource(R.string.settings_deselect_matching))
                    }
                }
                HorizontalDivider()
                if (availableGroups.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_no_groups_found),
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
            TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAddM3u: (name: String, url: String, headers: String?, maxConcurrentStreams: Int) -> Unit,
    onAddXtream: (
        name: String, url: String, user: String, pass: String,
        headers: String?, maxConcurrentStreams: Int, output: XtreamOutput,
    ) -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    var sourceType by remember { mutableStateOf(SourceType.M3U) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var maxStreams by remember { mutableStateOf("0") }
    var xtreamOutput by remember { mutableStateOf(XtreamOutput.TS) }
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
        title = { Text(stringResource(R.string.settings_add_source_title)) },
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
                    label = { Text(stringResource(R.string.common_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.settings_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = url.isNotBlank() && !urlValid,
                    supportingText = {
                        if (url.isNotBlank() && !urlValid) {
                            Text(stringResource(R.string.common_url_must_start_with_http))
                        }
                    },
                )
                if (sourceType == SourceType.XTREAM) {
                    SingleLineField(username, R.string.settings_username) { username = it }
                    SingleLineField(password, R.string.settings_password) { password = it }
                    XtreamOutputSelector(selected = xtreamOutput, onSelected = { xtreamOutput = it })
                }
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text(stringResource(R.string.settings_http_headers)) },
                    placeholder = { Text(stringResource(R.string.settings_http_headers_placeholder)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxStreams,
                    onValueChange = { if (it.all(Char::isDigit)) maxStreams = it },
                    label = { Text(stringResource(R.string.settings_max_concurrent_streams)) },
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
                    else onAddXtream(name.trim(), url.trim(), username.trim(), password, h, max, xtreamOutput)
                },
                enabled = canSubmit,
            ) { Text(stringResource(R.string.settings_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun XtreamOutputSelector(selected: XtreamOutput, onSelected: (XtreamOutput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.settings_stream_format),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XtreamOutput.entries.forEach { output ->
                FilterChip(
                    selected = selected == output,
                    onClick = { onSelected(output) },
                    label = { Text(output.ext.uppercase()) },
                )
            }
        }
    }
}

@Composable
private fun formatRelativeTime(timestampMs: Long): String {
    val delta = System.currentTimeMillis() - timestampMs
    return when {
        delta < 60_000L -> stringResource(R.string.settings_just_now)
        delta < 3_600_000L -> stringResource(R.string.settings_minutes_ago, delta / 60_000)
        delta < 86_400_000L -> stringResource(R.string.settings_hours_ago, delta / 3_600_000)
        else -> stringResource(R.string.settings_days_ago, delta / 86_400_000)
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
    var username by remember { mutableStateOf(source.username?.value ?: "") }
    var password by remember { mutableStateOf(source.password?.value ?: "") }
    var headers by remember { mutableStateOf(source.headers ?: "") }
    var maxStreams by remember { mutableStateOf(source.maxConcurrentStreams.toString()) }
    var epgUrl by remember { mutableStateOf(source.epgUrl ?: "") }
    var xtreamOutput by remember { mutableStateOf(source.xtreamOutput) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_edit_source_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleLineField(name, R.string.common_name) { name = it }
                SingleLineField(url, R.string.settings_url) { url = it }
                if (source.type == SourceType.XTREAM) {
                    SingleLineField(username, R.string.settings_username) { username = it }
                    SingleLineField(password, R.string.settings_password) { password = it }
                    XtreamOutputSelector(selected = xtreamOutput, onSelected = { xtreamOutput = it })
                }
                if (source.type == SourceType.M3U) {
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        label = { Text(stringResource(R.string.settings_epg_url)) },
                        placeholder = { Text(stringResource(R.string.settings_epg_url_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text(stringResource(R.string.settings_http_headers)) },
                    placeholder = { Text(stringResource(R.string.settings_http_headers_placeholder)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxStreams,
                    onValueChange = { if (it.all(Char::isDigit)) maxStreams = it },
                    label = { Text(stringResource(R.string.settings_max_concurrent_streams)) },
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
                        username = username.takeIf { it.isNotBlank() }?.let(::Secret),
                        password = password.takeIf { it.isNotBlank() }?.let(::Secret),
                        headers = headers.takeIf { it.isNotBlank() },
                        maxConcurrentStreams = maxStreams.toIntOrNull() ?: 0,
                        epgUrl = epgUrl.takeIf { it.isNotBlank() && source.type == SourceType.M3U },
                        xtreamOutput = xtreamOutput,
                    )
                )
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
