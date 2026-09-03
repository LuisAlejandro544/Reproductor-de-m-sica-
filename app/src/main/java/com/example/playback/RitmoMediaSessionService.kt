package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity

class RitmoMediaSessionService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val playerManager = AudioPlayerManager.getInstance(applicationContext)

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ForwardingPlayer conecta los controles de la notificación nativa, pantalla de bloqueo
        // y botones Bluetooth (Play, Pause, Next, Previous, Seek) con AudioPlayerManager,
        // garantizando compatibilidad idéntica tanto en motor ExoPlayer como en Oboe C++.
        val forwardingPlayer = object : ForwardingPlayer(playerManager.exoPlayer) {
            override fun play() {
                playerManager.playPause()
            }

            override fun pause() {
                playerManager.playPause()
            }

            override fun seekToNext() {
                playerManager.next()
            }

            override fun seekToPrevious() {
                playerManager.previous()
            }

            override fun seekTo(positionMs: Long) {
                playerManager.seekTo(positionMs)
            }

            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                playerManager.seekTo(positionMs)
            }

            override fun isPlaying(): Boolean {
                return playerManager.isPlaying.value
            }

            override fun getPlayWhenReady(): Boolean {
                return playerManager.isPlaying.value
            }

            override fun getPlaybackState(): Int {
                return if (playerManager.currentTrack.value != null) Player.STATE_READY else Player.STATE_IDLE
            }

            override fun getCurrentPosition(): Long {
                return playerManager.currentPosition.value
            }

            override fun getDuration(): Long {
                val dur = playerManager.duration.value
                return if (dur > 0) dur else super.getDuration()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
