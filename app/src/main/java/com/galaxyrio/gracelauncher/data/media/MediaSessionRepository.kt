package com.galaxyrio.gracelauncher.data.media

import android.content.Context
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.KeyEvent
import androidx.annotation.MainThread
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** MediaSession controllers, not a local music player or a notification-content scraper. */
@MainThread
class MediaSessionRepository(
    private val context: Context,
    private val hasAccess: () -> Boolean = { MediaAccess.isGranted(context) },
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(MediaSnapshot())
    val state = _state.asStateFlow()
    private val entries = linkedMapOf<MediaSession.Token, Entry>()
    private var order = emptyList<MediaSession.Token>()
    private var enabled = false
    private var listening = false
    private var closed = false
    private var artJob: Job? = null
    private var artRequest: ArtworkRequest? = null
    private var selected: Entry? = null
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (enabled && hasAccess()) sync(controllers.orEmpty()) else refresh()
    }

    init { scope.launch { MediaAccess.connectionChanges.collect { refresh() } } }

    fun setEnabled(value: Boolean) { enabled = value; refresh() }

    fun refresh() {
        if (closed) return
        val granted = hasAccess()
        if (!enabled || !granted) {
            detach()
            _state.value = MediaSnapshot(hasAccess = granted)
            return
        }
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(listener, MediaAccess.component(context), handler)
                listening = true
            }
            _state.update { it.copy(hasAccess = true, failed = false) }
            sync(manager.getActiveSessions(MediaAccess.component(context)))
        } catch (_: SecurityException) {
            detach()
            _state.value = MediaSnapshot(hasAccess = hasAccess())
        } catch (_: RuntimeException) {
            detach()
            _state.value = MediaSnapshot(hasAccess = granted, failed = true)
        }
    }

    private inner class Entry(val controller: MediaController) {
        val id = UUID.randomUUID().toString()
        val playerName = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(controller.packageName, 0)).toString()
        }.getOrDefault(controller.packageName)
        var playback: PlaybackState? = controller.playbackState
        var metadata: MediaMetadata? = controller.metadata
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { playback = state; publish() }
            override fun onMetadataChanged(value: MediaMetadata?) { metadata = value; publish() }
            override fun onSessionDestroyed() {
                entries.remove(controller.sessionToken)
                runCatching { controller.unregisterCallback(this) }
                publish()
            }
        }
    }

    private fun sync(controllers: List<MediaController>) {
        if (closed) return
        val tokens = controllers.map { it.sessionToken }.toSet()
        entries.keys.filter { it !in tokens }.forEach { token ->
            entries.remove(token)?.let { runCatching { it.controller.unregisterCallback(it.callback) } }
        }
        controllers.forEach { controller ->
            if (controller.sessionToken !in entries) runCatching {
                Entry(controller).also { entry ->
                    controller.registerCallback(entry.callback, handler)
                    entries[controller.sessionToken] = entry
                }
            }
        }
        order = controllers.map { it.sessionToken }
        publish()
    }

    private fun publish() {
        if (closed || !enabled) return
        // The system supplies priority order; among these, prefer an actually playing session.
        val entry = order.mapNotNull(entries::get).filter { playbackPriority(it.playback?.state ?: 0) > 0 }
            .maxByOrNull { playbackPriority(it.playback?.state ?: 0) }
        selected = entry
        if (entry == null) {
            artJob?.cancel(); artRequest = null
            _state.update { it.copy(nowPlaying = null) }
            return
        }
        val metadata = entry.metadata
        val title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: metadata.text(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.text(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val playing = playbackPriority(entry.playback?.state ?: 0) == 2
        val actions = entry.playback?.actions ?: 0L
        fun supports(action: Long) = actions and action != 0L
        val request = ArtworkRequest(entry.id, metadata.text(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: title,
            metadata.bitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata.bitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.bitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            metadata.text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: metadata.text(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))
        val previousArt = _state.value.nowPlaying?.artwork.takeIf { request == artRequest }
        _state.update { it.copy(nowPlaying = NowPlaying(entry.id, entry.playerName, title, artist, previousArt, playing,
            canToggle = supports(PlaybackState.ACTION_PLAY_PAUSE) || supports(if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY),
            canPrevious = supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS), canNext = supports(PlaybackState.ACTION_SKIP_TO_NEXT))) }
        if (request != artRequest) {
            artRequest = request
            artJob?.cancel()
            artJob = scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadArtwork(request) }?.asImageBitmap()
                if (request == artRequest) _state.update { it.copy(nowPlaying = it.nowPlaying?.copy(artwork = bitmap)) }
            }
        }
    }

    /** The rendered session id guards against a stale tap controlling a different player. */
    fun command(sessionId: String, command: MediaCommand): Boolean {
        val entry = selected?.takeIf { it.id == sessionId } ?: return false
        if (!enabled || !hasAccess()) { refresh(); return false }
        val current = _state.value.nowPlaying ?: return false
        return runCatching {
            val controls = entry.controller.transportControls
            when (command) {
                MediaCommand.Previous -> { if (!current.canPrevious) return false; controls.skipToPrevious() }
                MediaCommand.Next -> { if (!current.canNext) return false; controls.skipToNext() }
                MediaCommand.TogglePlayback -> {
                    if (!current.canToggle) return false
                    val direct = if (current.playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
                    if ((entry.playback?.actions ?: 0L) and direct != 0L) {
                        if (current.playing) controls.pause() else controls.play()
                    } else {
                        entry.controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                        entry.controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    }
                }
                MediaCommand.OpenPlayer -> {
                    val opened = entry.controller.sessionActivity?.let {
                        runCatching { it.send(context, 0, null, null, null, null, mediaPlayerLaunchOptions().toBundle()) }.isSuccess
                    } ?: false
                    if (!opened) {
                        val intent = context.packageManager.getLaunchIntentForPackage(entry.controller.packageName) ?: return false
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }.isSuccess
    }

    private fun detach() {
        if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
        listening = false
        entries.values.forEach { runCatching { it.controller.unregisterCallback(it.callback) } }
        entries.clear(); order = emptyList(); selected = null
        artJob?.cancel(); artRequest = null
    }

    fun close() { closed = true; detach(); scope.cancel() }

    private data class ArtworkRequest(val sessionId: String, val track: String?, val bitmap: Bitmap?, val uri: String?)

    private fun loadArtwork(request: ArtworkRequest): Bitmap? {
        return try {
            val bitmap = request.bitmap ?: request.uri?.let { path ->
                val uri = path.toUri()
                // No network fetches, storage permission, or arbitrary filesystem paths.
                if (uri.scheme !in setOf("content", "android.resource")) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
                }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            } ?: return null
            val size = maxOf(bitmap.width, bitmap.height)
            if (size > 512) bitmap.scale(
                (bitmap.width * 512f / size).toInt().coerceAtLeast(1),
                (bitmap.height * 512f / size).toInt().coerceAtLeast(1),
            ) else bitmap
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}

private fun MediaMetadata?.text(key: String): String? = runCatching { this?.getText(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
private fun MediaMetadata?.bitmap(key: String): Bitmap? = runCatching { this?.getBitmap(key) }.getOrNull()

/** Called only from a visible, user-clicked cover/title; never from a session callback. */
@Suppress("DEPRECATION")
internal fun mediaPlayerLaunchOptions(): ActivityOptions = ActivityOptions.makeBasic().apply {
    if (Build.VERSION.SDK_INT >= 36) {
        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
    } else if (Build.VERSION.SDK_INT >= 34) {
        // Android 14/15 require sender opt-in but do not have the visible-only mode.
        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
    }
}
