package com.noahjutz.kinetiquery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKitOverrides
import io.livekit.android.compose.flow.rememberDataMessageHandler
import io.livekit.android.compose.local.RoomScope

@Composable
fun Main(
    vm: MainVM = viewModel(),
    onError: (e: Exception) -> Unit,
) {
    var isConnected by remember { mutableStateOf(false) }
    val isMuted by vm.isMuted.collectAsState(false)
    val url by vm.url.collectAsState(null)
    val token by vm.token.collectAsState(null)
    val error by vm.error.collectAsState(null)
    val currentTranscript by vm.currentTranscript.collectAsState(MainVM.emptyBotTranscript)
    val transcriptHistory by vm.transcriptHistory.collectAsState(emptyList())

    RoomScope(
        url = url,
        token = token,
        audio = true,
        onConnected = { isConnected = true },
        onDisconnected = { isConnected = false },
        onError = { _, e -> onError(e ?: NullPointerException()) },
        liveKitOverrides = LiveKitOverrides(
            audioOptions = AudioOptions(
                audioOutputType = AudioType.MediaAudioType()
            )
        ),
        connectOptions = ConnectOptions(
            audio = true
        )
    ) { room ->
        if (isMuted) {
            room.disconnect()
        }

        val messages = rememberDataMessageHandler(room)
        LaunchedEffect(Unit) {
            messages.messageFlow.collect { message ->
                val msg = message.payload.decodeToString()
                vm.onMessage(msg)
            }
        }

    }

    LaunchedEffect(Unit) {
        vm.mute()
        vm.unmute()
    }

    LaunchedEffect(error) {
        error?.let {
            onError(it)
            vm.disconnect()
        }
    }

    Scaffold(
        bottomBar = { MainBottomBar(isMuted, vm::toggleMute) }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            AnimatedVisibility(!isMuted, enter = fadeIn(), exit = fadeOut()) {
                Transcript(currentTranscript, transcriptHistory)
            }
        }
    }
}

@Composable
fun Transcript(
    current: Transcript,
    history: List<Transcript>
) {
    val items = listOf(current) + history.reversed()
    Box(Modifier.fillMaxSize()) {

        val state = rememberLazyListState()
        LaunchedEffect(items) {
            state.animateScrollToItem(0)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            reverseLayout = true
        ) {
            itemsIndexed(items, key = { index, item -> item.id }) { index, item ->
                Box(Modifier.animateItem()) {
                    Box(
                        Modifier
                            .padding(vertical = 8.dp)
                    ) {
                        if (index == 0) {
                            CurrentTranscriptItem(item)
                        } else {
                            HistoryTranscriptItem(item)
                        }
                    }
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background.copy(alpha = 0f)
                    )
                )
            )
    )
}

@Composable
fun HistoryTranscriptItem(transcript: Transcript) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth(.5f)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SappCircle(
                fill = when (transcript.role) {
                    Role.Bot -> SolidColor(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                    Role.User -> SolidColor(Color.White.copy(alpha = 0.2f))
                }
            )
            Text(transcript.text, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun CurrentTranscriptItem(transcript: Transcript) {
    Surface(Modifier.fillMaxWidth(), tonalElevation = 16.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .defaultMinSize(minHeight = 200.dp)
                .animateContentSize()
        ) {
            Row(
                Modifier
                    .fillMaxWidth(.5f)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SappCircle(
                    angle = infiniteRotation().value, fill = when (transcript.role) {
                        Role.Bot -> Brush.linearGradient(sappGradient)
                        Role.User -> SolidColor(pulseWhite.value)
                    }
                )
                Text(transcript.text)
            }
        }
    }
}

@Composable
fun MainBottomBar(
    isMuted: Boolean,
    onToggleMute: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(48.dp)
            .alpha(.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier)
        IconButton(
            onClick = onToggleMute,
            Modifier.size(80.dp)
        ) {
            Icon(
                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                null,
                modifier = Modifier.size(80.dp)
            )
        }
    }
}