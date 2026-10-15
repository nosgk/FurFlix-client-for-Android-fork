package com.furflix.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furflix.app.R
import com.furflix.app.player.CustomPlaylistStore
import com.furflix.app.player.PlaybackController
import com.furflix.app.player.Playlist
import com.furflix.app.player.PlaylistSource
import com.furflix.app.player.StoredPlaylist
import com.furflix.app.player.toPlaylistItem
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.theme.SubtleGray
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * 播放列表管理：内置"收藏"列表 + 用户自建列表。
 *
 * 列表条目在详情页音频面板里"加入列表"添加；这里负责播放、改名、删除。
 */
@Composable
fun PlaylistsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CustomPlaylistStore.getInstance(context) }
    val scope = rememberCoroutineScope()
    val playlists by store.playlists.collectAsState(initial = emptyList())
    val playback by PlaybackController.state.collectAsState()
    // 内置列表名按 id 本地化（不落库），故先取好再进循环——避免在非组合 lambda 里调 stringResource
    val builtInLabel = stringResource(R.string.playlist_builtin_favorites)

    var draftName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<StoredPlaylist?>(null) }

    val hazeState = remember { HazeState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HazeTopAppBar(
                title = { Text(stringResource(R.string.playlist_title)) },
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
        ) {
            TextButton(
                onClick = { draftName = ""; creating = true },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.playlist_create))
            }

            playlists.forEachIndexed { index, list ->
                val name = if (list.isBuiltIn) builtInLabel else list.name
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
                PlaylistRow(
                    name = name,
                    count = list.items.size,
                    playing = playback.playlistName == name && playback.track != null,
                    canDelete = !list.isBuiltIn,
                    onPlay = {
                        val items = list.items.map { it.toPlaylistItem() }
                        if (items.isNotEmpty()) {
                            PlaybackController.playPlaylist(
                                context,
                                Playlist(name, PlaylistSource.Custom(list.id), items),
                                0
                            )
                        }
                    },
                    onRename = { renaming = list },
                    onDelete = { scope.launch { store.delete(list.id) } }
                )
            }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.playlist_create),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { value ->
                scope.launch { store.create(value) }
                creating = false
            }
        )
    }

    renaming?.let { target ->
        NameDialog(
            title = stringResource(R.string.playlist_rename),
            initial = target.name,
            onDismiss = { renaming = null },
            onConfirm = { value ->
                scope.launch { store.rename(target.id, value) }
                renaming = null
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    name: String,
    count: Int,
    playing: Boolean,
    canDelete: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.playlist_track_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = SubtleGray
            )
        }
        IconButton(onClick = onPlay, enabled = count > 0) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.playlist_play),
                tint = if (count > 0) MaterialTheme.colorScheme.primary else SubtleGray.copy(alpha = 0.4f)
            )
        }
        IconButton(onClick = onRename) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.playlist_rename),
                tint = SubtleGray
            )
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.playlist_delete),
                tint = if (canDelete) SubtleGray else SubtleGray.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text(stringResource(R.string.playlist_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_action_cancel)) }
        }
    )
}
