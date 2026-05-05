package dev.goor.tv.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goor.tv.data.model.Channel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onChannelClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    vm: HomeViewModel = koinViewModel(),
) {
    val channels by vm.channels.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GoorTV") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            items(channels, key = { it.id }) { channel ->
                ChannelItem(channel = channel, onClick = { onChannelClick(channel.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChannelItem(channel: Channel, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(channel.name) },
        supportingContent = channel.group?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
