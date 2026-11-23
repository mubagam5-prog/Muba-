package com.example.core

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.model.SocialPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaStorageHelper {
    private const val CREATIONS_DIR = "creations"

    fun creationsDir(context: Context): File {
        val dir = File(context.filesDir, CREATIONS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCreationFile(context: Context, fileName: String): File =
        File(creationsDir(context), fileName)

    suspend fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val w = info.size.width
                        val h = info.size.height
                        val maxSide = maxOf(w, h)
                        if (maxSide > maxDimension) {
                            val scale = maxDimension.toFloat() / maxSide
                            decoder.setTargetSize(
                                (w * scale).toInt().coerceAtLeast(1),
                                (h * scale).toInt().coerceAtLeast(1)
                            )
                        }
                    }
                } catch (_: Exception) {
                    decodeWithBitmapFactory(context, uri, maxDimension)
                }
            } else {
                decodeWithBitmapFactory(context, uri, maxDimension)
            } ?: return@withContext null

            val maxSide = maxOf(rawBitmap.width, rawBitmap.height)
            if (maxSide > maxDimension) {
                val scale = maxDimension.toFloat() / maxSide
                Bitmap.createScaledBitmap(
                    rawBitmap,
                    (rawBitmap.width * scale).toInt().coerceAtLeast(1),
                    (rawBitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else if (rawBitmap.config != Bitmap.Config.ARGB_8888) {
                rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                rawBitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        var sampleSize = 1
        val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        while (maxSide / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        return applyExifOrientation(context, uri, bitmap)
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun saveBitmapToCreations(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): File = withContext(Dispatchers.IO) {
        val swBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }
        val file = getCreationFile(context, fileName)
        if (file.exists()) file.delete()
        val compressedOk = FileOutputStream(file).use { out ->
            val ok = swBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.flush()
            ok
        }
        require(compressedOk && file.exists() && file.length() > 0L) {
            "Failed to write generated image to device storage."
        }
        file
    }

    fun encodeThumbnailBase64(bitmap: Bitmap): String? {
        return try {
            val swBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            }
            val maxDim = 256
            val scale = minOf(1f, maxDim.toFloat() / maxOf(swBitmap.width, swBitmap.height))
            val thumb = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    swBitmap,
                    (swBitmap.width * scale).toInt().coerceAtLeast(1),
                    (swBitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                swBitmap
            }
            val out = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 70, out)
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= 90_000) encoded else null
        } catch (_: Exception) {
            null
        }
    }

    fun decodeThumbnailBase64(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractVideoThumbnailBase64(videoFile: File): String? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            frame?.let { encodeThumbnailBase64(it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun saveImageToGallery(
        context: Context,
        sourceFile: File,
        displayName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            require(sourceFile.exists()) { "Image file not found on device." }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MUBA AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Unable to create MediaStore image entry."))

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to write image to gallery."))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveVideoToGallery(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String = "video/mp4"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            require(sourceFile.exists()) { "Video file not found on device." }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/MUBA AI")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Unable to create MediaStore video entry."))

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to write video to gallery."))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveGifToGallery(
        context: Context,
        sourceFile: File,
        displayName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            require(sourceFile.exists()) { "GIF file not found on device." }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MUBA AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Unable to create MediaStore GIF entry."))

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to write GIF to gallery."))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isPlatformInstalled(
        context: Context,
        platform: SocialPlatform,
        mimeType: String = "*/*"
    ): Boolean {
        if (platform.packageNames.isEmpty()) return true
        val action = if (platform.isStories) {
            "com.instagram.share.ADD_TO_STORY"
        } else {
            Intent.ACTION_SEND
        }
        return platform.packageNames.any { pkg ->
            val probe = Intent(action).apply {
                type = mimeType
                setPackage(pkg)
            }
            resolveShareActivity(context.packageManager, probe)
        }
    }

    fun shareToSocialPlatform(
        context: Context,
        file: File,
        mimeType: String,
        title: String,
        caption: String,
        platform: SocialPlatform
    ): Result<String> {
        return try {
            require(file.exists()) { "Media file does not exist locally." }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val cleanCaption = caption.trim()

            if (platform == SocialPlatform.SYSTEM_SHEET) {
                launchSystemChooser(context, uri, mimeType, title, cleanCaption)
                return Result.success("Opened Android Share Sheet.")
            }

            if (platform.isStories) {
                val igPackage = platform.packageNames.first()
                val storiesIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                    setDataAndType(uri, mimeType)
                    setPackage(igPackage)
                    putExtra("source_application", context.packageName)
                    clipData = ClipData.newRawUri(title, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (resolveShareActivity(context.packageManager, storiesIntent)) {
                    context.grantUriPermission(
                        igPackage,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    context.startActivity(storiesIntent)
                    return Result.success("Opened Instagram Stories composer.")
                }

                // Fallback to regular Instagram share if Stories intent handler isn't exposed
                val igSendIntent = buildDirectSendIntent(uri, mimeType, title, cleanCaption, igPackage)
                if (resolveShareActivity(context.packageManager, igSendIntent)) {
                    context.startActivity(igSendIntent)
                    return Result.success("Opened Instagram share.")
                }

                launchSystemChooser(context, uri, mimeType, title, cleanCaption)
                return Result.success(
                    "Instagram is not installed on this device — opened Android Share Sheet instead."
                )
            }

            val targetPackage = platform.packageNames.firstOrNull { pkg ->
                val probe = buildDirectSendIntent(uri, mimeType, title, cleanCaption, pkg)
                resolveShareActivity(context.packageManager, probe)
            }

            if (targetPackage != null) {
                val directIntent = buildDirectSendIntent(uri, mimeType, title, cleanCaption, targetPackage)
                context.startActivity(directIntent)
                Result.success("Opened ${platform.displayName}.")
            } else {
                launchSystemChooser(context, uri, mimeType, title, cleanCaption)
                Result.success(
                    "${platform.displayName} is not installed on this device — opened Android Share Sheet instead."
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildDirectSendIntent(
        uri: Uri,
        mimeType: String,
        title: String,
        caption: String,
        packageName: String?
    ): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        if (caption.isNotBlank()) {
            putExtra(Intent.EXTRA_TEXT, caption)
        }
        clipData = ClipData.newRawUri(title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (packageName != null) {
            setPackage(packageName)
        }
    }

    private fun launchSystemChooser(
        context: Context,
        uri: Uri,
        mimeType: String,
        title: String,
        caption: String
    ) {
        val shareIntent = buildDirectSendIntent(uri, mimeType, title, caption, packageName = null)
        val chooser = Intent.createChooser(shareIntent, "Share via MUBA AI").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun resolveShareActivity(pm: PackageManager, intent: Intent): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            ) != null
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }

    fun canLaunchCamera(context: Context): Boolean {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return resolveShareActivity(context.packageManager, intent)
    }

    fun createCameraCaptureTarget(context: Context): Pair<File, Uri> {
        val sharedDir = File(context.cacheDir, "shared").apply {
            if (!exists()) mkdirs()
        }
        val file = File(sharedDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    fun getCameraCaptureUri(context: Context, fileName: String): Uri {
        val sharedDir = File(context.cacheDir, "shared").apply {
            if (!exists()) mkdirs()
        }
        val file = File(sharedDir, fileName)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun deleteCameraCaptureFile(context: Context, fileName: String) {
        val file = File(File(context.cacheDir, "shared"), fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String): Result<Unit> {
        return shareToSocialPlatform(
            context = context,
            file = file,
            mimeType = mimeType,
            title = title,
            caption = title,
            platform = SocialPlatform.SYSTEM_SHEET
        ).map { Unit }
    }
}
