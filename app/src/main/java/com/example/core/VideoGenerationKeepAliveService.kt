package com.example.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Lightweight dataSync foreground service that keeps long-running AI video generation
 * alive when the user navigates away from MUBA AI and displays silent live progress.
 */
class VideoGenerationKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCleanly()
                return START_NOT_STICKY
            }
            ACTION_START_OR_UPDATE, null -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "MUBA AI Video Studio"
                val body = intent?.getStringExtra(EXTRA_BODY) ?: "Generating your AI video..."
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                val isBatch = intent?.getBooleanExtra(EXTRA_IS_BATCH, false) ?: false

                MubaNotificationHelper.ensureChannels(this)
                val notification = MubaNotificationHelper.buildProgressNotification(
                    context = this,
                    title = title,
                    body = body,
                    progressPercent = progress,
                    isBatch = isBatch
                )
                try {
                    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    }
                    ServiceCompat.startForeground(
                        this,
                        MubaNotificationHelper.PROGRESS_NOTIFICATION_ID,
                        notification,
                        fgsType
                    )
                    isServiceRunning = true
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to enter foreground mode cleanly", e)
                    isServiceRunning = false
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }

    private fun stopForegroundCleanly() {
        isServiceRunning = false
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground warning", e)
        }
        MubaNotificationHelper.cancelProgressNotification(this)
        stopSelf()
    }

    companion object {
        private const val TAG = "VideoKeepAliveSvc"
        private const val ACTION_START_OR_UPDATE = "com.example.action.VIDEO_FGS_UPDATE"
        private const val ACTION_STOP = "com.example.action.VIDEO_FGS_STOP"

        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_BODY = "extra_body"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val EXTRA_IS_BATCH = "extra_is_batch"

        @Volatile
        private var isServiceRunning = false

        fun startOrUpdate(
            context: Context,
            title: String,
            body: String,
            progressPercent: Int,
            isBatch: Boolean
        ) {
            MubaNotificationHelper.ensureChannels(context)
            val appVisible = AppVisibilityTracker.isAppInForeground.value

            // On Android 12+, starting a foreground service from the background may throw
            // ForegroundServiceStartNotAllowedException unless it's already running.
            if (!appVisible && !isServiceRunning) {
                MubaNotificationHelper.updateProgressNotification(
                    context, title, body, progressPercent, isBatch
                )
                return
            }

            val intent = Intent(context, VideoGenerationKeepAliveService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_PROGRESS, progressPercent)
                putExtra(EXTRA_IS_BATCH, isBatch)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Foreground service start fallback to plain notification", e)
                MubaNotificationHelper.updateProgressNotification(
                    context, title, body, progressPercent, isBatch
                )
            }
        }

        fun stop(context: Context) {
            if (isServiceRunning) {
                val intent = Intent(context, VideoGenerationKeepAliveService::class.java).apply {
                    action = ACTION_STOP
                }
                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not deliver ACTION_STOP; calling stopService", e)
                    try {
                        context.stopService(Intent(context, VideoGenerationKeepAliveService::class.java))
                    } catch (_: Exception) {
                    }
                    isServiceRunning = false
                }
            }
            MubaNotificationHelper.cancelProgressNotification(context)
        }
    }
}
