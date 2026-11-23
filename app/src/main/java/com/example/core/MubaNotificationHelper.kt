package com.example.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class InAppVideoCompletionAlert(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val body: String,
    val isBatch: Boolean,
    val isError: Boolean
)

/**
 * Tracks foreground/background visibility and active navigation tab so notifications
 * know whether the user navigated away from the app or switched to another studio tab.
 */
object AppVisibilityTracker {
    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _activeRoute = MutableStateFlow("home")
    val activeRoute: StateFlow<String> = _activeRoute.asStateFlow()

    fun setAppForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun setActiveRoute(route: String) {
        _activeRoute.value = route
    }

    fun isViewingVideoStudio(): Boolean {
        return _isAppInForeground.value && _activeRoute.value == "video_gen"
    }
}

object MubaNotificationHelper {
    private const val TAG = "MubaNotifications"

    const val CHANNEL_PROGRESS_ID = "muba_video_progress"
    const val CHANNEL_COMPLETE_ID = "muba_video_complete"

    const val PROGRESS_NOTIFICATION_ID = 4101
    private const val COMPLETE_NOTIFICATION_BASE_ID = 4200

    const val EXTRA_NAV_ROUTE = "extra_nav_route"
    const val EXTRA_IS_BATCH_MODE = "extra_is_batch_mode"

    private val _inAppAlerts = MutableSharedFlow<InAppVideoCompletionAlert>(extraBufferCapacity = 4)
    val inAppAlerts: SharedFlow<InAppVideoCompletionAlert> = _inAppAlerts.asSharedFlow()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS_ID,
            "Video Rendering Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent ongoing progress while MUBA AI generates your video."
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        val completeChannel = NotificationChannel(
            CHANNEL_COMPLETE_ID,
            "AI Video Completion Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts you as soon as your AI video or batch variations finish rendering."
            setShowBadge(true)
            enableLights(true)
            enableVibration(true)
        }

        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(completeChannel)
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return hasPostNotificationsPermission(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open notification settings", e)
        }
    }

    fun createVideoStudioPendingIntent(
        context: Context,
        isBatch: Boolean,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.ACTION_OPEN_VIDEO_RESULT_${if (isBatch) "BATCH" else "SINGLE"}"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_ROUTE, "video_gen")
            putExtra(EXTRA_IS_BATCH_MODE, isBatch)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    fun buildProgressNotification(
        context: Context,
        title: String,
        body: String,
        progressPercent: Int,
        isBatch: Boolean
    ): Notification {
        ensureChannels(context)
        val clampedProgress = progressPercent.coerceIn(0, 100)
        val contentIntent = createVideoStudioPendingIntent(
            context = context,
            isBatch = isBatch,
            requestCode = 1001
        )
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(R.drawable.ic_notification_muba)
            .setColor(0xFF7C3AED.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, clampedProgress, clampedProgress <= 0)
            .setContentIntent(contentIntent)
            .build()
    }

    fun updateProgressNotification(
        context: Context,
        title: String,
        body: String,
        progressPercent: Int,
        isBatch: Boolean
    ) {
        if (!areNotificationsEnabled(context)) return
        val notification = buildProgressNotification(context, title, body, progressPercent, isBatch)
        try {
            NotificationManagerCompat.from(context).notify(PROGRESS_NOTIFICATION_ID, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException posting progress notification", se)
        }
    }

    fun cancelProgressNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel progress notification", e)
        }
    }

    /**
     * Alerts the user when AI video generation completes or fails.
     * - Cancels the ongoing progress notification.
     * - If the user is inside the app on another tab, emits an interactive in-app top banner alert.
     * - Whenever the user navigated away from the app or away from the Video Studio tab, posts a
     *   high-priority Android system notification (with deep-link back to the finished video).
     */
    fun notifyGenerationComplete(
        context: Context,
        title: String,
        body: String,
        isBatch: Boolean,
        isError: Boolean = false
    ) {
        ensureChannels(context)
        cancelProgressNotification(context)

        val inAppForeground = AppVisibilityTracker.isAppInForeground.value
        val viewingStudio = AppVisibilityTracker.isViewingVideoStudio()

        if (inAppForeground && !viewingStudio) {
            _inAppAlerts.tryEmit(
                InAppVideoCompletionAlert(
                    title = title,
                    body = body,
                    isBatch = isBatch,
                    isError = isError
                )
            )
        }

        // Post system tray notification whenever user left the app or navigated away from Video Studio
        if (viewingStudio) return
        if (!areNotificationsEnabled(context)) return

        val notifId = COMPLETE_NOTIFICATION_BASE_ID + (if (isBatch) 2 else 1) + (if (isError) 10 else 0)
        val contentIntent = createVideoStudioPendingIntent(
            context = context,
            isBatch = isBatch,
            requestCode = notifId
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setSmallIcon(R.drawable.ic_notification_muba)
            .setColor(if (isError) 0xFFEF4444.toInt() else 0xFF7C3AED.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (isError) NotificationCompat.CATEGORY_ERROR else NotificationCompat.CATEGORY_STATUS
            )
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException posting completion notification", se)
        }
    }
}
