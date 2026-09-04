package com.example.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R

class RitmoMediaSessionService : MediaSessionService() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ritmo_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setNotificationId(NOTIFICATION_ID)
            .build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        setMediaNotificationProvider(notificationProvider)

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Reproducción de Ritmo"
            val channelDesc = "Controles de reproducción y mini-reproductor en segundo plano"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance).apply {
                description = channelDesc
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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
