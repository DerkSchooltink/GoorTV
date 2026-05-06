package dev.goor.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
    val groups by vm.groups.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedGroup by vm.selectedGroup.collectAsStateWithLifecycle()
    val showFavoritesOnly by vm.showFavoritesOnly.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val recentlyWatched by vm.recentlyWatched.collectAsStateWithLifecycle()

    var searchActive by remember { mutableStateOf(false) }
    val isDefaultView = searchQuery.isBlank() && selectedGroup == null && !showFavoritesOnly

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GoorTV") },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) vm.setSearchQuery("")
                    }) {
                        Icon(
                            if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchActive) "Close search" else "Search",
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = vm::setSearchQuery,
                    placeholder = { Text("Search channels…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                )
            }

            if (sources.isNotEmpty()) {
                FilterRow(
                    groups = groups,
                    selectedGroup = selectedGroup,
                    showFavoritesOnly = showFavoritesOnly,
                    onGroupSelected = vm::selectGroup,
                    onFavoritesToggle = vm::toggleFavoritesOnly,
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    sources.isEmpty() -> EmptySourcesState(
                        onSettingsClick = onSettingsClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                    isSyncing && channels.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    channels.isEmpty() -> EmptyChannelsState(modifier = Modifier.fillMaxSize())
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (recentlyWatched.isNotEmpty() && isDefaultView) {
                            item(key = "recent_header") {
                                Text(
                                    "Recently Watched",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item(key = "recent_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                ) {
                                    items(recentlyWatched, key = { "recent_${it.id}" }) { channel ->
                                        RecentChannelCard(
                                            channel = channel,
                                            onClick = { onChannelClick(channel.id) },
                                        )
                                    }
                                }
                            }
                            item(key = "recent_divider") { HorizontalDivider() }
                        }
                        items(channels, key = { it.id }) { channel ->
                            ChannelItem(
                                channel = channel,
                                onClick = { onChannelClick(channel.id) },
                                onFavoriteToggle = { vm.toggleFavorite(channel.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    groups: List<String>,
    selectedGroup: String?,
    showFavoritesOnly: Boolean,
    onGroupSelected: (String?) -> Unit,
    onFavoritesToggle: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = showFavoritesOnly,
                onClick = onFavoritesToggle,
                label = { Text("Favorites") },
                leadingIcon = {
                    Icon(
                        if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
        if (groups.isNotEmpty()) {
            item {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { onGroupSelected(null) },
                    label = { Text("All") },
                )
            }
            items(groups) { group ->
                FilterChip(
                    selected = selectedGroup == group,
                    onClick = { onGroupSelected(if (selectedGroup == group) null else group) },
                    label = { Text(group) },
                )
            }
        }
    }
}

@Composable
private fun ChannelItem(channel: Channel, onClick: () -> Unit, onFavoriteToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(channel.name) },
        supportingContent = channel.group?.let { group -> { Text(group) } },
        leadingContent = { ChannelLogo(logoUrl = channel.logoUrl, size = 40) },
        trailingContent = {
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (channel.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (channel.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun RecentChannelCard(channel: Channel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelLogo(
            logoUrl = channel.logoUrl,
            size = 64,
            shape = RoundedCornerShape(8.dp),
        )
        Text(
            channel.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ChannelLogo(
    logoUrl: String?,
    size: Int,
    shape: Shape = CircleShape,
) {
    if (logoUrl != null) {
        AsyncImage(
            model = logoUrl,
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Tv,
                contentDescription = null,
                modifier = Modifier.size((size * 0.6f).dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptySourcesState(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Tv,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("No sources added", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSettingsClick) { Text("Add a source") }
    }
}

@Composable
private fun EmptyChannelsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("No channels match", style = MaterialTheme.typography.titleMedium)
    }
}
