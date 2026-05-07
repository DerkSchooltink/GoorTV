package dev.goor.tv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.goor.tv.data.model.Channel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onChannelClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    vm: HomeViewModel = koinViewModel(),
) {
    val pagingItems = vm.pagingData.collectAsLazyPagingItems()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val showFavoritesOnly by vm.showFavoritesOnly.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val recentlyWatched by vm.recentlyWatched.collectAsStateWithLifecycle()
    val syncErrors by vm.syncErrors.collectAsStateWithLifecycle()
    val searchHistory by vm.searchHistory.collectAsStateWithLifecycle()

    var searchActive by remember { mutableStateOf(false) }
    val isDefaultView = searchQuery.isBlank() && !showFavoritesOnly
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    LaunchedEffect(syncErrors) {
        if (syncErrors.isNotEmpty()) {
            snackbarHostState.showSnackbar(syncErrors.joinToString("\n"))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(visible = showScrollToTop) {
                SmallFloatingActionButton(
                    onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        },
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
                    IconButton(onClick = vm::toggleFavoritesOnly) {
                        Icon(
                            if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (showFavoritesOnly) "Show all" else "Favourites",
                            tint = if (showFavoritesOnly) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = {
                        if (searchActive && searchQuery.isNotBlank()) vm.addToSearchHistory(searchQuery)
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
                    keyboardActions = KeyboardActions(
                        onSearch = { if (searchQuery.isNotBlank()) vm.addToSearchHistory(searchQuery) },
                        onDone = { if (searchQuery.isNotBlank()) vm.addToSearchHistory(searchQuery) },
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                )
                if (searchHistory.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(searchHistory) { query ->
                            SuggestionChip(
                                onClick = { vm.setSearchQuery(query) },
                                label = { Text(query) },
                                icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    sources.isEmpty() -> EmptySourcesState(
                        onSettingsClick = onSettingsClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                    isSyncing && pagingItems.itemCount == 0 -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    pagingItems.itemCount == 0 && !isSyncing -> EmptyChannelsState(modifier = Modifier.fillMaxSize())
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("channel_list")) {
                        if (recentlyWatched.isNotEmpty() && isDefaultView) {
                            item(key = "recent_header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Recently Watched",
                                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    IconButton(onClick = vm::clearRecentlyWatched) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear recently watched",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
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

                        items(
                            count = pagingItems.itemCount,
                            key = pagingItems.itemKey { item ->
                                when (item) {
                                    is ChannelListItem.Header -> "header_${item.title}"
                                    is ChannelListItem.Item -> item.channel.id
                                }
                            },
                            contentType = pagingItems.itemContentType { item ->
                                when (item) {
                                    is ChannelListItem.Header -> "header"
                                    is ChannelListItem.Item -> "channel"
                                }
                            },
                        ) { index ->
                            when (val item = pagingItems[index]) {
                                is ChannelListItem.Header -> stickyGroupHeader(item.title)
                                is ChannelListItem.Item -> ChannelItem(
                                    channel = item.channel,
                                    onClick = { onChannelClick(item.channel.id) },
                                    onFavoriteToggle = { vm.toggleFavorite(item.channel.id) },
                                )
                                null -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun stickyGroupHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChannelItem(channel: Channel, onClick: () -> Unit, onFavoriteToggle: () -> Unit) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 0.5.dp.toPx(),
                )
            }
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(logoUrl = channel.logoUrl, size = 40)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (channel.group != null) {
                Text(
                    channel.group,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onFavoriteToggle) {
            Icon(
                if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = if (channel.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
        val sizePx = size * 2
        val context = LocalContext.current
        val request = remember(logoUrl, sizePx) {
            ImageRequest.Builder(context)
                .data(logoUrl)
                .size(sizePx)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
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
