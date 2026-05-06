package dev.goor.tv.ui.screens.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
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
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<Source?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                    IconButton(onClick = { showAddDialog = true }, enabled = !syncing) {
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
                    onSync = { vm.syncSource(source) },
                    onEdit = { editingSource = source },
                    onDelete = { vm.deleteSource(source) },
                )
                HorizontalDivider()
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAddM3u = { name, url ->
                vm.addM3uSource(name, url)
                showAddDialog = false
            },
            onAddXtream = { name, url, user, pass ->
                vm.addXtreamSource(name, url, user, pass)
                showAddDialog = false
            }
        )
    }

    editingSource?.let { source ->
        EditSourceDialog(
            source = source,
            onDismiss = { editingSource = null },
            onUpdate = { updated ->
                vm.updateSource(updated)
                editingSource = null
            }
        )
    }
}

@Composable
private fun SourceItem(
    source: Source,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(source.name) },
        supportingContent = { Text(source.type.name) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
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
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAddM3u: (name: String, url: String) -> Unit,
    onAddXtream: (name: String, url: String, user: String, pass: String) -> Unit,
) {
    var sourceType by remember { mutableStateOf(SourceType.M3U) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceType.entries.forEach { type ->
                        FilterChip(
                            selected = sourceType == type,
                            onClick = { sourceType = type },
                            label = { Text(type.name) }
                        )
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (sourceType == SourceType.XTREAM) {
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (sourceType == SourceType.M3U) onAddM3u(name, url)
                else onAddXtream(name, url, username, password)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
