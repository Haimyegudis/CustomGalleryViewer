package com.example.customgalleryviewer.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

data class AudioTrackInfo(
    val index: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)

@Composable
fun AudioTrackDialog(
    player: ExoPlayer,
    onDismiss: () -> Unit
) {
    val tracks = remember(player.currentTracks) { getAudioTracks(player) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Tracks", fontWeight = FontWeight.SemiBold) },
        text = {
            if (tracks.isEmpty()) {
                Text("No audio tracks available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    itemsIndexed(tracks) { _, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectAudioTrack(player, track)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote, null,
                                modifier = Modifier.size(20.dp),
                                tint = if (track.isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.label,
                                    fontSize = 15.sp,
                                    fontWeight = if (track.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (track.isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (track.language != null) {
                                    Text(
                                        track.language,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (track.isSelected) {
                                Icon(
                                    Icons.Default.Check, "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun getAudioTracks(player: ExoPlayer): List<AudioTrackInfo> {
    val result = mutableListOf<AudioTrackInfo>()
    var counter = 0
    val currentTracks = player.currentTracks

    for (groupIndex in 0 until currentTracks.groups.size) {
        val group = currentTracks.groups[groupIndex]
        if (group.type != C.TRACK_TYPE_AUDIO) continue

        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            val isSelected = group.isTrackSelected(trackIndex)
            val label = format.label ?: "Track ${counter + 1}"
            val language = format.language

            result.add(
                AudioTrackInfo(
                    index = counter,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    label = label,
                    language = language,
                    isSelected = isSelected
                )
            )
            counter++
        }
    }
    return result
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun selectAudioTrack(player: ExoPlayer, track: AudioTrackInfo) {
    val currentTracks = player.currentTracks
    if (track.groupIndex < currentTracks.groups.size) {
        val group = currentTracks.groups[track.groupIndex]
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(track.trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }
}

fun getAudioTrackCount(player: ExoPlayer): Int {
    var count = 0
    for (groupIndex in 0 until player.currentTracks.groups.size) {
        val group = player.currentTracks.groups[groupIndex]
        if (group.type == C.TRACK_TYPE_AUDIO) {
            count += group.length
        }
    }
    return count
}
