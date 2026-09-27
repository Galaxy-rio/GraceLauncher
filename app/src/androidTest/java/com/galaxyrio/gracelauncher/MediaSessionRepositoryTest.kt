package com.galaxyrio.gracelauncher

import android.Manifest
import android.app.ActivityOptions
import android.os.Build
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.media.MediaSessionRepository
import com.galaxyrio.gracelauncher.data.media.MediaSnapshot
import com.galaxyrio.gracelauncher.data.media.mediaPlayerLaunchOptions
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real system MediaSessions; shell identity is scoped to tests, never the application. */
@RunWith(AndroidJUnit4::class)
class MediaSessionRepositoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var repository: MediaSessionRepository
    private val sessions = mutableListOf<MediaSession>()
    private var allowed = true
    private val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS

    @Before fun setUp() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL)
        main { repository = MediaSessionRepository(context, hasAccess = { allowed }); repository.setEnabled(true) }
    }

    @After fun tearDown() {
        try { main { repository.close(); sessions.forEach { it.release() } } }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(predicate: (MediaSnapshot) -> Boolean): MediaSnapshot = runBlocking {
        withTimeout(10_000) { repository.state.first(predicate) }
    }
    private fun playback(state: Int, supported: Long = actions) = PlaybackState.Builder()
        .setState(state, 0, if (state == PlaybackState.STATE_PLAYING) 1f else 0f).setActions(supported).build()
    private fun metadata(title: String, artwork: Bitmap? = null) = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, "Fixture artist")
        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork).build()
    private fun session(title: String, state: Int = PlaybackState.STATE_PLAYING, supported: Long = actions): MediaSession {
        lateinit var result: MediaSession
        main {
            result = MediaSession(context, "grace-test:$title").also {
                sessions += it
                it.setMetadata(metadata(title))
                it.setPlaybackState(playback(state, supported))
                it.isActive = true
            }
            repository.refresh()
        }
        return result
    }

    @Test fun metadataArtworkAndTransportControlsFollowTheRealController() {
        val player = session("Grace media fixture")
        val next = AtomicInteger()
        val previous = AtomicInteger()
        main {
            player.setCallback(object : MediaSession.Callback() {
                override fun onPause() { player.setPlaybackState(playback(PlaybackState.STATE_PAUSED)) }
                override fun onPlay() { player.setPlaybackState(playback(PlaybackState.STATE_PLAYING)) }
                override fun onSkipToNext() { next.incrementAndGet(); player.setMetadata(metadata("Grace next fixture")) }
                override fun onSkipToPrevious() { previous.incrementAndGet(); player.setMetadata(metadata("Grace previous fixture")) }
            }, Handler(Looper.getMainLooper()))
            player.setMetadata(metadata("Grace media fixture", Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)))
        }
        val current = await { it.nowPlaying?.title == "Grace media fixture" && it.nowPlaying.artwork != null }.nowPlaying!!
        assertEquals("Fixture artist", current.artist)
        assertEquals(512, current.artwork!!.width)
        main { assertTrue(repository.command(current.sessionId, MediaCommand.TogglePlayback)) }
        await { it.nowPlaying?.sessionId == current.sessionId && !it.nowPlaying.playing }
        main { assertTrue(repository.command(current.sessionId, MediaCommand.TogglePlayback)) }
        await { it.nowPlaying?.sessionId == current.sessionId && it.nowPlaying.playing }
        main { assertTrue(repository.command(current.sessionId, MediaCommand.Next)) }
        await { it.nowPlaying?.title == "Grace next fixture" }
        main { assertTrue(repository.command(current.sessionId, MediaCommand.Previous)) }
        await { it.nowPlaying?.title == "Grace previous fixture" }
        assertEquals(1, next.get()); assertEquals(1, previous.get())
    }

    @Test fun disablingOrRevokingAccessClearsThePlayerAndRejectsStaleCommands() {
        session("Grace access fixture")
        val current = await { it.nowPlaying?.title == "Grace access fixture" }.nowPlaying!!
        main { repository.setEnabled(false) }
        assertNull(repository.state.value.nowPlaying)
        main { assertFalse(repository.command(current.sessionId, MediaCommand.Next)); repository.setEnabled(true) }
        await { it.nowPlaying?.title == "Grace access fixture" }
        main { allowed = false; repository.refresh() }
        assertFalse(repository.state.value.hasAccess)
        assertNull(repository.state.value.nowPlaying)
        main { assertFalse(repository.command(current.sessionId, MediaCommand.TogglePlayback)); allowed = true; repository.refresh() }
        val resumed = await { it.nowPlaying?.title == "Grace access fixture" }.nowPlaying!!
        assertNotEquals(current.sessionId, resumed.sessionId)
        main { assertFalse(repository.command(current.sessionId, MediaCommand.TogglePlayback)) }
    }

    @Test fun unsupportedActionsAndDestroyedSessionsDoNotRemainControllable() {
        val player = session("Grace limited fixture", supported = PlaybackState.ACTION_PAUSE)
        val current = await { it.nowPlaying?.title == "Grace limited fixture" }.nowPlaying!!
        assertTrue(current.canToggle); assertFalse(current.canPrevious); assertFalse(current.canNext)
        main {
            assertFalse(repository.command(current.sessionId, MediaCommand.Next))
            assertFalse(repository.command("obsolete-session", MediaCommand.TogglePlayback))
            player.release()
            sessions.remove(player)
        }
        await { it.nowPlaying?.sessionId != current.sessionId }
        main { assertFalse(repository.command(current.sessionId, MediaCommand.TogglePlayback)) }
    }

    @Test fun playingSessionWinsAndStoppedSessionsDisappear() {
        session("Grace paused fixture", PlaybackState.STATE_PAUSED)
        val playing = session("Grace active fixture")
        val current = await { it.nowPlaying?.title == "Grace active fixture" }.nowPlaying!!
        main { playing.setPlaybackState(playback(PlaybackState.STATE_STOPPED)) }
        await { it.nowPlaying?.sessionId != current.sessionId }
    }

    @Test fun missingOrUnreadableArtworkDoesNotBlockSongInformation() {
        val player = session("Grace missing art")
        main {
            player.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Grace inaccessible art")
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "content://unavailable.grace.test/cover").build())
        }
        val current = await { it.nowPlaying?.title == "Grace inaccessible art" }.nowPlaying!!
        assertNull(current.artwork)
        assertTrue(current.canToggle)
    }

    @Suppress("DEPRECATION")
    @Test fun playerPendingIntentUsesTheStrictestAvailableForegroundOptIn() {
        if (Build.VERSION.SDK_INT >= 34) {
            val expected = if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            assertEquals(expected, mediaPlayerLaunchOptions().pendingIntentBackgroundActivityStartMode)
        }
    }
}
