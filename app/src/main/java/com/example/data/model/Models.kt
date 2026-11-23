package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CreationType {
    IMAGE,
    VIDEO;

    companion object {
        fun fromRaw(raw: String): CreationType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: IMAGE
    }
}

enum class BatchItemStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}

enum class VideoQuality(val label: String, val targetWidth: Int, val targetHeight: Int, val bitrate: Int) {
    HD("HD", 1280, 720, 4_500_000),
    UHD_4K("4K", 1920, 1080, 10_000_000)
}

enum class VideoFilter(val displayName: String) {
    ORIGINAL("Original"),
    NOIR("Obsidian Noir"),
    VIOLET_DREAM("Violet Dream"),
    GOLDEN_GLOW("Golden Glow"),
    CYBER_CRISP("Cyber Crisp")
}

enum class VideoExportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val subtitle: String,
    val badge: String
) {
    MP4(
        extension = "mp4",
        mimeType = "video/mp4",
        displayName = "MP4 (H.264)",
        subtitle = "Best quality & universal playback with audio",
        badge = "HD / 4K"
    ),
    GIF(
        extension = "gif",
        mimeType = "image/gif",
        displayName = "Animated GIF",
        subtitle = "Auto-looping clip for chats & social feeds",
        badge = "LOOP"
    ),
    WEBM(
        extension = "webm",
        mimeType = "video/webm",
        displayName = "WebM (VP8)",
        subtitle = "Compact open video format for the web",
        badge = "LIGHT"
    )
}

enum class SocialPlatform(
    val displayName: String,
    val subtitle: String,
    val badgeLabel: String,
    val packageNames: List<String>,
    val brandColorHex: Long,
    val isStories: Boolean = false
) {
    INSTAGRAM(
        displayName = "Instagram",
        subtitle = "Post directly to Feed or Reels",
        badgeLabel = "IG",
        packageNames = listOf("com.instagram.android"),
        brandColorHex = 0xFFE1306C
    ),
    INSTAGRAM_STORIES(
        displayName = "Instagram Stories",
        subtitle = "Open directly in Stories composer",
        badgeLabel = "STORY",
        packageNames = listOf("com.instagram.android"),
        brandColorHex = 0xFFC13584,
        isStories = true
    ),
    WHATSAPP(
        displayName = "WhatsApp",
        subtitle = "Send to chats, groups, or Status",
        badgeLabel = "WA",
        packageNames = listOf("com.whatsapp", "com.whatsapp.w4b"),
        brandColorHex = 0xFF25D366
    ),
    TIKTOK(
        displayName = "TikTok",
        subtitle = "Publish video or photo creation",
        badgeLabel = "TT",
        packageNames = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        brandColorHex = 0xFF00D4CC
    ),
    FACEBOOK(
        displayName = "Facebook",
        subtitle = "Share to your Feed or Story",
        badgeLabel = "FB",
        packageNames = listOf("com.facebook.katana"),
        brandColorHex = 0xFF1877F2
    ),
    X_TWITTER(
        displayName = "X (Twitter)",
        subtitle = "Post media with your caption",
        badgeLabel = "X",
        packageNames = listOf("com.twitter.android"),
        brandColorHex = 0xFF52525B
    ),
    TELEGRAM(
        displayName = "Telegram",
        subtitle = "Share with contacts or channels",
        badgeLabel = "TG",
        packageNames = listOf("org.telegram.messenger"),
        brandColorHex = 0xFF26A5E4
    ),
    SYSTEM_SHEET(
        displayName = "More Apps",
        subtitle = "Open Android Share Sheet",
        badgeLabel = "ALL",
        packageNames = emptyList(),
        brandColorHex = 0xFF8B5CF6
    )
}

enum class MusicTrack(val id: String, val displayName: String, val subtitle: String, val bpm: Int) {
    NONE("none", "No Music", "Silent track", 0),
    MIDNIGHT_VELVET("midnight_velvet", "Midnight Velvet", "Warm ambient synth pad", 72),
    NEON_PULSE("neon_pulse", "Neon Pulse", "Modern rhythmic arpeggio", 112),
    SOLARIS_DRIFT("solaris_drift", "Solaris Drift", "Uplifting shimmer chords", 88),
    OBSIDIAN_ECHO("obsidian_echo", "Obsidian Echo", "Minimalist studio chimes", 96)
}

enum class ImageAspectRatioOption(val label: String, val ratioLabel: String) {
    SQUARE("1:1", "Square"),
    LANDSCAPE("16:9", "Widescreen"),
    PORTRAIT("9:16", "Vertical"),
    CLASSIC("4:3", "Classic")
}

enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    ES("es", "Español"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    AR("ar", "العربية"),
    OM("om", "Afaan Oromoo"),
    AM("am", "አማርኛ");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val preferredLanguage: String = "en",
    val darkMode: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Creation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = CreationType.IMAGE.name,
    val title: String = "",
    val prompt: String = "",
    val motionStyle: String? = null,
    val durationSeconds: Long = 0L,
    val quality: String = "1K",
    val localFileName: String = "",
    val thumbnailBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val creationType: CreationType
        get() = CreationType.fromRaw(type)
}

data class MotionDirective(
    val motionStyle: String = "Cinematic Zoom",
    val startZoom: Float = 1.0f,
    val endZoom: Float = 1.18f,
    val panX: Float = 0.04f,
    val panY: Float = -0.02f,
    val particleMode: String = "SPARKLE",
    val moodColorHex: String = "#8B5CF6",
    val keyframeEditPrompt: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
