package com.example.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground Service for background audio streaming playback on Remote.
 */
class RemoteAudioService : Service() {
    companion object {
        const val CHANNEL_ID = "capture_remote_audio_channel"
        const val NOTIFICATION_ID = 45455

        const val ACTION_START = "com.example.net.action.START"
        const val ACTION_STOP = "com.example.net.action.STOP"

        const val EXTRA_HOST = "extra_host"
        const val EXTRA_TOKEN = "extra_token"

        fun startStreaming(context: Context, host: String, token: String) {
            val intent = Intent(context, RemoteAudioService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_TOKEN, token)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopStreaming(context: Context) {
            val intent = Intent(context, RemoteAudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getPlayer(): RemoteAudioPlayer? = audioPlayer
    }

    private val binder = LocalBinder()
    private var audioPlayer: RemoteAudioPlayer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
                val token = intent.getStringExtra(EXTRA_TOKEN) ?: ""
                startForegroundWithNotification()

                audioPlayer?.stop()
                val player = RemoteAudioPlayer(this) { stats ->
                    // stats callback
                }
                audioPlayer = player
                player.start(host, token)
            }
            ACTION_STOP -> {
                audioPlayer?.stop()
                audioPlayer = null
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Capture Studio Remote Audio")
            .setContentText("Live monitoring HDMI capture card audio")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors low-latency HDMI audio playback in background"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        audioPlayer?.stop()
        audioPlayer = null
        super.onDestroy()
    }
}
