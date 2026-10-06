@file:OptIn(UnstableApi::class)

package com.edrive.app.ui.viewer

import android.content.Context
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.edrive.app.media.EncryptedDataSource
import com.edrive.app.ui.theme.EColors
import com.edrive.crypto.AuthenticationFailedException
import com.edrive.crypto.RandomAccessDecryptor
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Tətbiqdaxili video pleyer: şifrəli faylı yaddaşda deşifrə edib oynadır, diskə açıq mətn yazılmır.
 * Pleyer composable ekrandan çıxanda buraxılır; mövqe və "oynayır" vəziyyəti [onState] ilə bildirilir.
 */
@Composable
fun EncryptedVideoPlayer(
    source: RandomAccessDecryptor,
    startPositionMs: Long,
    startPlayWhenReady: Boolean,
    onState: (positionMs: Long, playWhenReady: Boolean) -> Unit,
    onOpenExternal: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    val player = remember(source) { createPlayer(context, source, startPositionMs, startPlayWhenReady) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) { isPlaying = isPlayingNow }
            override fun onPlaybackStateChanged(state: Int) { playbackState = state }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val swapped = videoSize.unappliedRotationDegrees % 180 != 0
                val w = if (swapped) videoSize.height else videoSize.width
                val h = if (swapped) videoSize.width else videoSize.height
                if (w > 0 && h > 0) aspect = w * videoSize.pixelWidthHeightRatio / h
            }
            override fun onPlayerError(e: PlaybackException) { error = describe(e) }
        }
        player.addListener(listener)
        onDispose {
            onState(player.currentPosition, player.playWhenReady)
            player.removeListener(listener)
            player.release()
        }
    }

    // Tətbiq fona keçəndə dayandır (səs davam etməsin)
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            val d = player.duration
            durationMs = if (d == C.TIME_UNSET || d < 0) 0L else d
            delay(250)
        }
    }

    // Oxunma zamanı idarə düymələri 3 saniyədən sonra gizlənir
    LaunchedEffect(controlsVisible, isPlaying, interaction, scrubbing) {
        if (controlsVisible && isPlaying && !scrubbing) {
            delay(3000)
            controlsVisible = false
        }
    }
    LaunchedEffect(playbackState) { if (playbackState == Player.STATE_ENDED) controlsVisible = true }

    fun seekBy(deltaMs: Long) {
        val dur = player.duration
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(if (dur != C.TIME_UNSET && dur > 0) target.coerceAtMost(dur) else target)
        interaction++
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        key(player) {
            AndroidView(
                factory = { ctx -> SurfaceView(ctx).apply { keepScreenOn = true; player.setVideoSurfaceView(this) } },
                modifier = Modifier.align(Alignment.Center).aspectRatio(aspect),
            )
        }

        // Toxunuş: idarə düymələrini göstər/gizlət; qoşa toxunuş: ±10 san
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible; interaction++ },
                    onDoubleTap = { o -> if (o.x < size.width / 2) seekBy(-10_000) else seekBy(10_000) },
                )
            },
        )

        if (playbackState == Player.STATE_BUFFERING && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = EColors.Accent, strokeWidth = 3.dp)
        }

        AnimatedVisibility(visible = controlsVisible && error == null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { seekBy(-10_000) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Outlined.Replay10, "10 saniyə geri", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = {
                            if (playbackState == Player.STATE_ENDED) { player.seekTo(0); player.play() }
                            else if (isPlaying) player.pause() else player.play()
                            interaction++
                        },
                        modifier = Modifier.size(68.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (isPlaying) "Dayandır" else "Oynat",
                            tint = Color.White, modifier = Modifier.size(48.dp),
                        )
                    }
                    IconButton(onClick = { seekBy(10_000) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Outlined.Forward10, "10 saniyə irəli", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = if (scrubbing) scrubValue else fraction,
                        onValueChange = { scrubbing = true; scrubValue = it },
                        onValueChangeFinished = {
                            player.seekTo((scrubValue * durationMs).toLong())
                            scrubbing = false
                        },
                        enabled = durationMs > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = EColors.Accent, activeTrackColor = EColors.Accent, inactiveTrackColor = Color(0x33FFFFFF),
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val shown = if (scrubbing) (scrubValue * durationMs).toLong() else positionMs
                        Text(formatTime(shown), color = Color.White, fontSize = 12.sp)
                        Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }

        error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(msg, color = EColors.Danger, textAlign = TextAlign.Center)
                TextButton(onClick = onOpenExternal) { Text("Kənar tətbiqdə aç", color = EColors.Accent) }
            }
        }
    }
}

private fun createPlayer(context: Context, source: RandomAccessDecryptor, startMs: Long, playWhenReady: Boolean): ExoPlayer {
    val factory = DataSource.Factory { EncryptedDataSource(source) }
    // Standart yük nəzarəti video üçün yüzlərlə MB yaddaş ayıra bilər — açıq mətn buferini 24 MB ilə məhdudlaşdırırıq
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
        .setTargetBufferBytes(24 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()
    val player = ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(loadControl)
        .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .build()
    val mediaSource = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(EncryptedDataSource.URI))
    player.setMediaSource(mediaSource, startMs.coerceAtLeast(0L))
    player.playWhenReady = playWhenReady
    player.prepare()
    return player
}

private fun describe(e: PlaybackException): String {
    val corrupted = generateSequence<Throwable>(e) { it.cause }.any { it is AuthenticationFailedException }
    if (corrupted) return "Fayl zədələnib və ya dəyişdirilib (autentifikasiya alınmadı)."
    return when (e.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        -> "Bu video formatı cihazda dəstəklənmir. Kənar tətbiqdə açmağı sınayın."
        else -> "Video oxunmadı (${e.errorCodeName})."
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s) else "%d:%02d".format(Locale.US, m, s)
}
