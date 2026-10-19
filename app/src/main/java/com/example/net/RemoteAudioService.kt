package com.example.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground service that only keeps the app process alive (and shows a notification) while the
 * Remote is listening. The actual audio player lives in MainViewModel, so there is exactly one player.
 */
class RemoteAudioService : Service() {
    companion object {
        const val CHANNEL_ID = "capture_remote_audio_channel"
        const val NOTIFICATION_ID = 45456

        const val ACTION_START = "com.example.net.action.START"
        const val ACTION_STOP = "com.example.net.action.STOP"

        @Volatile
        private var running = false

        fun isRunning(): Boolean = running

        fun startStreaming(context: Context, host: String, token: String) {
            if (running) {
                stopStreaming(context)
            }
            try {
                val intent = Intent(context, RemoteAudioService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
                // Background keep-alive is optional; never let it take the app down
            }
        }

        fun stopStreaming(context: Context) {
            // Only talk to the service if it is actually running. Sending a stop to a service that was
            // never started could race with a following start and crash the app.
            if (!running) return
            running = false
            try {
                context.stopService(Intent(context, RemoteAudioService::class.java))
            } catch (_: Throwable) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always satisfy startForegroundService() first, whatever the intent contains
        try {
            startForegroundWithNotification()
            running = true
        } catch (_: Throwable) {
            stopSelf()
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
            .setContentTitle("Capture Studio Remote")
            .setContentText("Listening to capture card audio")
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
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
