package com.example.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import com.example.data.model.MotionDirective
import com.example.data.model.MusicTrack
import com.example.data.model.VideoFilter
import com.example.data.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object VideoSynthesisEngine {
    private const val TAG = "VideoSynthesisEngine"
    private const val MIME_VIDEO = "video/avc"
    private const val MIME_WEBM = "video/x-vnd.on2.vp8"
    private const val MIME_AUDIO = "audio/mp4a-latm"
    private const val FPS = 24
    private const val SAMPLE_RATE = 44100

    suspend fun generateMotionVideoMp4(
        sourceBitmap: Bitmap,
        targetKeyframeBitmap: Bitmap,
        directive: MotionDirective,
        durationSeconds: Int,
        quality: VideoQuality,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        try {
            val safeSource = ensureSoftwareBitmap(sourceBitmap)
            val safeTarget = ensureSoftwareBitmap(targetKeyframeBitmap)
            val hasDistinctTarget = targetKeyframeBitmap !== sourceBitmap
            require(safeSource.width > 0 && safeSource.height > 0) { "Invalid source image dimensions." }

            val (width, height) = resolveSafeDimensions(
                safeSource.width,
                safeSource.height,
                quality
            )
            val totalFrames = (durationSeconds * FPS).coerceAtLeast(24)
            if (outputFile.exists()) outputFile.delete()

            val accentColor = parseSafeColor(directive.moodColorHex)
            val particles = List(36) { idx ->
                ParticleSeed(
                    x = Random(idx * 31 + 7).nextFloat(),
                    y = Random(idx * 53 + 13).nextFloat(),
                    radius = 2.5f + Random(idx * 17).nextFloat() * 5.5f,
                    speedY = 0.08f + Random(idx * 71).nextFloat() * 0.22f,
                    phase = Random(idx * 97).nextFloat() * (2f * PI.toFloat())
                )
            }

            encodeVideoFrames(
                width = width,
                height = height,
                bitrate = quality.bitrate,
                totalFrames = totalFrames,
                outputFile = outputFile
            ) { frameIdx, canvas ->
                val cw = canvas.width.coerceAtLeast(1)
                val ch = canvas.height.coerceAtLeast(1)
                val t = frameIdx.toFloat() / (totalFrames - 1).coerceAtLeast(1)
                val easedT = 0.5f - 0.5f * cos(t * PI).toFloat()

                // Smooth Ken Burns zoom & pan
                val zoom = directive.startZoom + (directive.endZoom - directive.startZoom) * easedT
                val panX = directive.panX * easedT
                val panY = directive.panY * easedT

                canvas.drawColor(Color.BLACK)

                // Draw base source photo with zoom/pan
                drawFittedBitmap(canvas, safeSource, cw, ch, zoom, panX, panY, 255)

                // Cross-dissolve toward Gemini AI-edited keyframe smoothly when distinct
                if (hasDistinctTarget) {
                    val blendAlpha = when {
                        t < 0.15f -> 0
                        t > 0.85f -> 235
                        else -> (((t - 0.15f) / 0.70f) * 235f).toInt().coerceIn(0, 235)
                    }
                    if (blendAlpha > 0) {
                        drawFittedBitmap(
                            canvas,
                            safeTarget,
                            cw,
                            ch,
                            zoom * 1.02f,
                            panX * 1.1f,
                            panY * 1.1f,
                            blendAlpha
                        )
                    }
                }

                // Atmospheric light glow & particles directed by Gemini
                drawAtmosphereOverlay(
                    canvas = canvas,
                    width = cw,
                    height = ch,
                    progress = t,
                    accentColor = accentColor,
                    particleMode = directive.particleMode,
                    particles = particles
                )

                onProgress(0.15f + 0.80f * t)
            }

            require(outputFile.exists() && outputFile.length() > 512L) {
                "Video encoder finished without producing a valid MP4 file."
            }
            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to synthesize MP4 video", e)
            Result.failure(e)
        }
    }

    suspend fun applyVideoEdits(
        sourceVideoFile: File,
        outputFile: File,
        trimStartSec: Float,
        trimEndSec: Float,
        filter: VideoFilter,
        musicTrack: MusicTrack,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            require(sourceVideoFile.exists()) { "Source video file not found." }
            retriever.setDataSource(sourceVideoFile.absolutePath)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 5000L
            val srcWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1280
            val srcHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 720

            val startMs = (trimStartSec * 1000f).toLong().coerceIn(0L, (durationMs - 500L).coerceAtLeast(0L))
            val endMs = (trimEndSec * 1000f).toLong().coerceIn(startMs + 500L, durationMs)
            val trimmedDurationMs = (endMs - startMs).coerceAtLeast(500L)

            val (width, height) = align16(srcWidth, srcHeight)
            val totalFrames = ((trimmedDurationMs / 1000f) * FPS).toInt().coerceAtLeast(12)

            val tempVideoOnly = File(outputFile.parentFile, "edit_vid_${System.currentTimeMillis()}.mp4")
            if (tempVideoOnly.exists()) tempVideoOnly.delete()
            if (outputFile.exists()) outputFile.delete()

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                buildColorMatrix(filter)?.let { matrix ->
                    colorFilter = ColorMatrixColorFilter(matrix)
                }
            }

            encodeVideoFrames(
                width = width,
                height = height,
                bitrate = 5_000_000,
                totalFrames = totalFrames,
                outputFile = tempVideoOnly
            ) { frameIdx, canvas ->
                val cw = canvas.width.coerceAtLeast(1)
                val ch = canvas.height.coerceAtLeast(1)
                val ratio = frameIdx.toFloat() / (totalFrames - 1).coerceAtLeast(1)
                val targetTimeUs = (startMs + (trimmedDurationMs * ratio).toLong()) * 1000L
                val frameBitmap = retriever.getFrameAtTime(
                    targetTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                canvas.drawColor(Color.BLACK)
                if (frameBitmap != null) {
                    val dest = android.graphics.Rect(0, 0, cw, ch)
                    canvas.drawBitmap(frameBitmap, null, dest, paint)
                    frameBitmap.recycle()
                }
                val progressWeight = if (musicTrack == MusicTrack.NONE) 0.95f else 0.70f
                onProgress(0.05f + progressWeight * ratio)
            }

            if (musicTrack != MusicTrack.NONE) {
                val tempAudioOnly = File(outputFile.parentFile, "edit_aud_${System.currentTimeMillis()}.m4a")
                try {
                    val audioOk = encodeSynthesizedMusicAac(
                        track = musicTrack,
                        durationMs = trimmedDurationMs,
                        outputM4aFile = tempAudioOnly
                    )
                    onProgress(0.88f)
                    if (audioOk && tempAudioOnly.exists()) {
                        muxVideoAndAudio(tempVideoOnly, tempAudioOnly, outputFile)
                    } else {
                        tempVideoOnly.copyTo(outputFile, overwrite = true)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Audio mux fallback to video-only: ${e.message}")
                    tempVideoOnly.copyTo(outputFile, overwrite = true)
                } finally {
                    if (tempAudioOnly.exists()) tempAudioOnly.delete()
                }
            } else {
                tempVideoOnly.copyTo(outputFile, overwrite = true)
            }

            if (tempVideoOnly.exists()) tempVideoOnly.delete()
            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Video edit failed", e)
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun exportVideoToWebm(
        sourceVideoFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            require(sourceVideoFile.exists()) { "Source video file not found." }
            retriever.setDataSource(sourceVideoFile.absolutePath)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(500L) ?: 5000L
            val srcWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 960
            val srcHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 544

            val aspect = srcWidth.toFloat() / srcHeight.coerceAtLeast(1).toFloat()
            val targetLong = minOf(maxOf(srcWidth, srcHeight), 960)
            val rawW = if (aspect >= 1f) targetLong else (targetLong * aspect).toInt()
            val rawH = if (aspect >= 1f) (targetLong / aspect).toInt() else targetLong
            val (width, height) = align16(rawW, rawH)

            val webmFps = 20
            val totalFrames = ((durationMs / 1000f) * webmFps).toInt().coerceAtLeast(12)
            if (outputFile.exists()) outputFile.delete()

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val drawWebmFrame: (Int, Canvas) -> Unit = { frameIdx, canvas ->
                val cw = canvas.width.coerceAtLeast(1)
                val ch = canvas.height.coerceAtLeast(1)
                val ratio = frameIdx.toFloat() / (totalFrames - 1).coerceAtLeast(1)
                val targetTimeUs = (durationMs * ratio).toLong() * 1000L
                val frameBitmap = retriever.getFrameAtTime(
                    targetTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                canvas.drawColor(Color.BLACK)
                if (frameBitmap != null) {
                    val dest = android.graphics.Rect(0, 0, cw, ch)
                    canvas.drawBitmap(frameBitmap, null, dest, paint)
                    frameBitmap.recycle()
                }
                onProgress(0.05f + 0.92f * ratio)
            }
            try {
                encodeVideoFramesWithFormat(
                    mimeType = MIME_WEBM,
                    muxerOutputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM,
                    width = width,
                    height = height,
                    bitrate = 2_200_000,
                    fps = webmFps,
                    totalFrames = totalFrames,
                    outputFile = outputFile,
                    drawFrame = drawWebmFrame
                )
            } catch (vp8Err: Exception) {
                Log.w(TAG, "VP8 WebM encoding failed, trying VP9 fallback", vp8Err)
                if (outputFile.exists()) outputFile.delete()
                encodeVideoFramesWithFormat(
                    mimeType = "video/x-vnd.on2.vp9",
                    muxerOutputFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM,
                    width = width,
                    height = height,
                    bitrate = 2_200_000,
                    fps = webmFps,
                    totalFrames = totalFrames,
                    outputFile = outputFile,
                    drawFrame = drawWebmFrame
                )
            }

            require(outputFile.exists() && outputFile.length() > 128L) {
                "WebM export produced an empty file."
            }
            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "WebM export failed", e)
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun exportVideoToGif(
        sourceVideoFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            require(sourceVideoFile.exists()) { "Source video file not found." }
            retriever.setDataSource(sourceVideoFile.absolutePath)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(500L) ?: 5000L
            val srcWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 640
            val srcHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 360

            val maxGifSide = 360
            val scale = minOf(1f, maxGifSide.toFloat() / maxOf(srcWidth, srcHeight).coerceAtLeast(1))
            val gifW = (srcWidth * scale).toInt().coerceAtLeast(120)
            val gifH = (srcHeight * scale).toInt().coerceAtLeast(120)

            val gifFps = 10
            val delayCs = 100 / gifFps
            val totalFrames = ((durationMs / 1000f) * gifFps).toInt().coerceIn(8, 100)
            if (outputFile.exists()) outputFile.delete()

            BufferedOutputStream(FileOutputStream(outputFile), 65536).use { out ->
                val encoder = Gif89aEncoder(gifW, gifH, out)
                encoder.writeHeader()
                val argbBuffer = IntArray(gifW * gifH)
                val scaledPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                for (i in 0 until totalFrames) {
                    coroutineContext.ensureActive()
                    val ratio = i.toFloat() / (totalFrames - 1).coerceAtLeast(1)
                    val timeUs = (durationMs * ratio).toLong() * 1000L
                    val rawFrame = retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: continue

                    val frameBmp = Bitmap.createBitmap(gifW, gifH, Bitmap.Config.ARGB_8888)
                    val c = Canvas(frameBmp)
                    c.drawBitmap(rawFrame, null, android.graphics.Rect(0, 0, gifW, gifH), scaledPaint)
                    rawFrame.recycle()

                    frameBmp.getPixels(argbBuffer, 0, gifW, 0, 0, gifW, gifH)
                    frameBmp.recycle()

                    encoder.writeFrame(argbBuffer, delayCs)
                    onProgress(0.05f + 0.92f * ratio)
                }
                encoder.finish()
                out.flush()
            }

            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "GIF export failed", e)
            Result.failure(e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun encodeVideoFramesWithFormat(
        mimeType: String,
        muxerOutputFormat: Int,
        width: Int,
        height: Int,
        bitrate: Int,
        fps: Int,
        totalFrames: Int,
        outputFile: File,
        drawFrame: (frameIdx: Int, canvas: Canvas) -> Unit
    ) {
        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(mimeType)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, muxerOutputFormat)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameCounter = LongArray(1)

        try {
            val frameDurationUs = 1_000_000L / fps.coerceAtLeast(1)
            for (i in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val canvas = inputSurface.lockCanvas(null)
                try {
                    drawFrame(i, canvas)
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }
                drainEncoder(
                    codec = codec,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    trackIndexRef = { idx ->
                        trackIndex = idx
                        muxerStarted = true
                    },
                    currentTrackIndex = { trackIndex },
                    isMuxerStarted = { muxerStarted },
                    overridePtsUs = frameDurationUs,
                    frameCounter = frameCounter
                )
            }

            codec.signalEndOfInputStream()
            drainEncoder(
                codec = codec,
                muxer = muxer,
                bufferInfo = bufferInfo,
                endOfStream = true,
                trackIndexRef = { idx ->
                    trackIndex = idx
                    muxerStarted = true
                },
                currentTrackIndex = { trackIndex },
                isMuxerStarted = { muxerStarted },
                overridePtsUs = frameDurationUs,
                frameCounter = frameCounter
            )
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    private data class EncoderAttempt(
        val codecName: String?,
        val width: Int,
        val height: Int,
        val bitrate: Int
    )

    private fun listAvcEncoderNames(): List<String> {
        return try {
            val all = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info ->
                    info.isEncoder && info.supportedTypes.any { it.equals(MIME_VIDEO, ignoreCase = true) }
                }
                .map { it.name }
            // Put default hardware-first ordering, then explicit software codecs (c2.android / OMX.google)
            val sw = all.filter {
                it.contains("c2.android", ignoreCase = true) || it.contains("OMX.google", ignoreCase = true)
            }
            val hw = all.filterNot { it in sw }
            (hw + sw).distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun encodeVideoFrames(
        width: Int,
        height: Int,
        bitrate: Int,
        totalFrames: Int,
        outputFile: File,
        drawFrame: (frameIdx: Int, canvas: Canvas) -> Unit
    ) {
        val safe960 = align16(if (width >= height) 960 else 544, if (width >= height) 544 else 960)
        val safe640 = align16(if (width >= height) 640 else 368, if (width >= height) 368 else 640)

        val attempts = buildList {
            add(EncoderAttempt(null, width, height, clampEncoderBitrate(bitrate)))
            if (safe960.first != width || safe960.second != height) {
                add(EncoderAttempt(null, safe960.first, safe960.second, 2_200_000))
            }
            for (name in listAvcEncoderNames()) {
                add(EncoderAttempt(name, safe960.first, safe960.second, 2_000_000))
                add(EncoderAttempt(name, safe640.first, safe640.second, 1_500_000))
            }
            add(EncoderAttempt(null, safe640.first, safe640.second, 1_500_000))
        }.distinct()

        var lastError: Exception? = null
        for ((index, attempt) in attempts.withIndex()) {
            coroutineContext.ensureActive()
            if (outputFile.exists()) outputFile.delete()
            var codec: MediaCodec? = null
            try {
                val format = MediaFormat.createVideoFormat(MIME_VIDEO, attempt.width, attempt.height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, attempt.bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                codec = if (attempt.codecName != null) {
                    MediaCodec.createByCodecName(attempt.codecName)
                } else {
                    MediaCodec.createEncoderByType(MIME_VIDEO)
                }
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encodeWithConfiguredCodec(codec, totalFrames, outputFile, drawFrame)
                if (outputFile.exists() && outputFile.length() > 512L) {
                    return
                }
                throw IllegalStateException("Encoded MP4 file was empty (${outputFile.length()} bytes).")
            } catch (ce: kotlinx.coroutines.CancellationException) {
                runCatching { codec?.release() }
                throw ce
            } catch (e: Exception) {
                runCatching { codec?.release() }
                lastError = e
                Log.w(
                    TAG,
                    "AVC encode attempt #${index + 1} (${attempt.codecName ?: "default"} ${attempt.width}x${attempt.height}) failed: ${e.message}"
                )
            }
        }
        throw lastError ?: IllegalStateException("No compatible H.264 video encoder succeeded on this device.")
    }

    private suspend fun encodeWithConfiguredCodec(
        codec: MediaCodec,
        totalFrames: Int,
        outputFile: File,
        drawFrame: (frameIdx: Int, canvas: Canvas) -> Unit
    ) {
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameCounter = LongArray(1)

        try {
            val frameDurationUs = 1_000_000L / FPS
            for (i in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val canvas = inputSurface.lockCanvas(null)
                try {
                    drawFrame(i, canvas)
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }
                drainEncoder(
                    codec = codec,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    trackIndexRef = { idx ->
                        trackIndex = idx
                        muxerStarted = true
                    },
                    currentTrackIndex = { trackIndex },
                    isMuxerStarted = { muxerStarted },
                    overridePtsUs = frameDurationUs,
                    frameCounter = frameCounter
                )
            }

            codec.signalEndOfInputStream()
            drainEncoder(
                codec = codec,
                muxer = muxer,
                bufferInfo = bufferInfo,
                endOfStream = true,
                trackIndexRef = { idx ->
                    trackIndex = idx
                    muxerStarted = true
                },
                currentTrackIndex = { trackIndex },
                isMuxerStarted = { muxerStarted },
                overridePtsUs = frameDurationUs,
                frameCounter = frameCounter
            )
            if (!muxerStarted || frameCounter[0] == 0L) {
                throw IllegalStateException("Video muxer wrote 0 encoded frames.")
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    private suspend fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        trackIndexRef: (Int) -> Unit,
        currentTrackIndex: () -> Int,
        isMuxerStarted: () -> Boolean,
        overridePtsUs: Long,
        frameCounter: LongArray
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        var emptyTries = 0
        val maxEmptyTries = 250
        while (true) {
            coroutineContext.ensureActive()
            val status = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
                emptyTries++
                if (emptyTries >= maxEmptyTries) {
                    Log.w(TAG, "EOS drain timed out after $maxEmptyTries tries")
                    break
                }
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!isMuxerStarted()) {
                    val idx = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    trackIndexRef(idx)
                }
            } else if (status >= 0) {
                emptyTries = 0
                val encodedData = codec.getOutputBuffer(status)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size > 0 && isMuxerStarted() && encodedData != null) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    bufferInfo.presentationTimeUs = frameCounter[0] * overridePtsUs
                    frameCounter[0]++
                    muxer.writeSampleData(currentTrackIndex(), encodedData, bufferInfo)
                }
                codec.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun encodeSynthesizedMusicAac(
        track: MusicTrack,
        durationMs: Long,
        outputM4aFile: File
    ): Boolean {
        val pcmSamples = synthesizeTrackPcm(track, durationMs)
        val format = MediaFormat.createAudioFormat(MIME_AUDIO, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MIME_AUDIO)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputM4aFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIdx = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()

        try {
            var sampleCursor = 0
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx) ?: continue
                        inBuf.clear()
                        val maxShorts = inBuf.remaining() / 2
                        val count = minOf(maxShorts, pcmSamples.size - sampleCursor)
                        if (count > 0) {
                            for (i in 0 until count) {
                                val s = pcmSamples[sampleCursor + i]
                                inBuf.put((s.toInt() and 0xFF).toByte())
                                inBuf.put(((s.toInt() shr 8) and 0xFF).toByte())
                            }
                            val ptsUs = (sampleCursor.toLong() * 1_000_000L) / SAMPLE_RATE
                            sampleCursor += count
                            codec.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                        } else {
                            val ptsUs = (sampleCursor.toLong() * 1_000_000L) / SAMPLE_RATE
                            codec.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                ptsUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            audioTrackIdx = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0
                        }
                        if (info.size > 0 && muxerStarted && outBuf != null) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(audioTrackIdx, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            return muxerStarted
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val vSrcIdx = findTrack(videoExtractor, "video/")
            val aSrcIdx = findTrack(audioExtractor, "audio/")
            if (vSrcIdx < 0) {
                videoFile.copyTo(outputFile, overwrite = true)
                return
            }
            videoExtractor.selectTrack(vSrcIdx)
            val vDstIdx = muxer.addTrack(videoExtractor.getTrackFormat(vSrcIdx))

            var aDstIdx = -1
            if (aSrcIdx >= 0) {
                audioExtractor.selectTrack(aSrcIdx)
                aDstIdx = muxer.addTrack(audioExtractor.getTrackFormat(aSrcIdx))
            }

            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = if ((videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                muxer.writeSampleData(vDstIdx, buffer, info)
                videoExtractor.advance()
            }

            if (aDstIdx >= 0) {
                while (true) {
                    val sampleSize = audioExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = audioExtractor.sampleTime
                    info.flags = 0
                    muxer.writeSampleData(aDstIdx, buffer, info)
                    audioExtractor.advance()
                }
            }
        } finally {
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    fun synthesizeTrackPcm(track: MusicTrack, durationMs: Long): ShortArray {
        val totalSamples = ((durationMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(SAMPLE_RATE)
        val out = ShortArray(totalSamples)
        if (track == MusicTrack.NONE) return out

        val chords: List<DoubleArray> = when (track) {
            MusicTrack.MIDNIGHT_VELVET -> listOf(
                doubleArrayOf(146.83, 220.00, 261.63, 329.63), // Dm9
                doubleArrayOf(174.61, 220.00, 261.63, 349.23), // Fmaj7
                doubleArrayOf(130.81, 196.00, 246.94, 293.66), // Cmaj7
                doubleArrayOf(110.00, 164.81, 220.00, 261.63)  // Am7
            )
            MusicTrack.NEON_PULSE -> listOf(
                doubleArrayOf(164.81, 246.94, 329.63, 493.88),
                doubleArrayOf(196.00, 293.66, 392.00, 587.33),
                doubleArrayOf(146.83, 220.00, 293.66, 440.00),
                doubleArrayOf(130.81, 196.00, 261.63, 392.00)
            )
            MusicTrack.SOLARIS_DRIFT -> listOf(
                doubleArrayOf(220.00, 277.18, 329.63, 440.00),
                doubleArrayOf(196.00, 246.94, 293.66, 392.00),
                doubleArrayOf(174.61, 220.00, 261.63, 349.23),
                doubleArrayOf(164.81, 246.94, 329.63, 440.00)
            )
            MusicTrack.OBSIDIAN_ECHO -> listOf(
                doubleArrayOf(130.81, 196.00, 311.13, 392.00),
                doubleArrayOf(116.54, 174.61, 293.66, 349.23),
                doubleArrayOf(103.83, 155.56, 261.63, 311.13),
                doubleArrayOf(98.00, 146.83, 246.94, 293.66)
            )
            MusicTrack.NONE -> emptyList()
        }

        val stepDurationSec = 1.25
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val chordIdx = ((t / stepDurationSec).toInt()) % chords.size
            val chord = chords[chordIdx]
            val localT = (t % stepDurationSec) / stepDurationSec
            val env = sin(PI * localT).coerceIn(0.0, 1.0)
            val fadeIn = (t / 0.25).coerceIn(0.0, 1.0)
            val fadeOut = (((totalSamples - i).toDouble() / SAMPLE_RATE) / 0.35).coerceIn(0.0, 1.0)

            var sample = 0.0
            when (track) {
                MusicTrack.NEON_PULSE -> {
                    val arpIdx = ((t * 6.0).toInt()) % chord.size
                    val arpEnv = 1.0 - ((t * 6.0) % 1.0) * 0.7
                    sample += sin(2.0 * PI * chord[arpIdx] * t) * 0.45 * arpEnv
                    sample += sin(2.0 * PI * (chord[0] * 0.5) * t) * 0.30
                }
                MusicTrack.OBSIDIAN_ECHO -> {
                    val bellEnv = kotlin.math.exp(-localT * 3.5)
                    sample += sin(2.0 * PI * chord[2] * t) * 0.4 * bellEnv
                    sample += sin(2.0 * PI * chord[0] * t) * 0.35 * env
                }
                else -> {
                    for (freq in chord) {
                        sample += sin(2.0 * PI * freq * t) * 0.22
                    }
                    sample *= env
                }
            }
            val finalVal = (sample * fadeIn * fadeOut * 18000.0).toInt().coerceIn(-32767, 32767)
            out[i] = finalVal.toShort()
        }
        return out
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }
    }

    private fun resolveSafeDimensions(srcW: Int, srcH: Int, quality: VideoQuality): Pair<Int, Int> {
        val caps = getEncoderVideoCapabilities()
        val maxCapW = (caps?.supportedWidths?.upper ?: 1280).coerceAtMost(1920)
        val maxCapH = (caps?.supportedHeights?.upper ?: 1280).coerceAtMost(1920)
        val targetLong = minOf(quality.targetWidth, maxOf(maxCapW, maxCapH))
        val aspect = srcW.toFloat() / srcH.coerceAtLeast(1).toFloat()

        var scaleFactor = 1.0f
        repeat(5) {
            val scaledLong = (targetLong * scaleFactor).toInt().coerceAtLeast(320)
            val rawW: Int
            val rawH: Int
            if (aspect >= 1f) {
                rawW = minOf(scaledLong, maxCapW)
                rawH = (rawW / aspect).toInt()
            } else {
                rawH = minOf(scaledLong, maxCapH)
                rawW = (rawH * aspect).toInt()
            }
            val (aw, ah) = align16(rawW.coerceAtLeast(320), rawH.coerceAtLeast(320))
            if (caps == null || caps.areSizeAndRateSupported(aw, ah, FPS.toDouble()) || caps.isSizeSupported(aw, ah)) {
                return aw to ah
            }
            scaleFactor *= 0.75f
        }
        return if (aspect >= 1f) 640 to 368 else 368 to 640
    }

    private fun align16(w: Int, h: Int): Pair<Int, Int> {
        val aw = ((w + 8) / 16) * 16
        val ah = ((h + 8) / 16) * 16
        return aw.coerceAtLeast(320) to ah.coerceAtLeast(320)
    }

    private fun clampEncoderBitrate(requestedBitrate: Int): Int {
        val caps = getEncoderVideoCapabilities() ?: return requestedBitrate.coerceIn(500_000, 8_000_000)
        val range = caps.bitrateRange
        return requestedBitrate.coerceIn(range.lower, range.upper)
    }

    private fun getEncoderVideoCapabilities(): MediaCodecInfo.VideoCapabilities? {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (!info.isEncoder) continue
                if (info.supportedTypes.any { it.equals(MIME_VIDEO, ignoreCase = true) }) {
                    return info.getCapabilitiesForType(MIME_VIDEO).videoCapabilities
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun drawFittedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        canvasW: Int,
        canvasH: Int,
        zoom: Float,
        panXFraction: Float,
        panYFraction: Float,
        alpha: Int
    ) {
        val scale = maxOf(
            canvasW.toFloat() / bitmap.width.toFloat(),
            canvasH.toFloat() / bitmap.height.toFloat()
        ) * zoom
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val dx = (canvasW - drawW) / 2f + canvasW * panXFraction
        val dy = (canvasH - drawH) / 2f + canvasH * panYFraction

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha.coerceIn(0, 255)
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawAtmosphereOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        progress: Float,
        accentColor: Int,
        particleMode: String,
        particles: List<ParticleSeed>
    ) {
        // Subtle vignette + ambient light leak
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val cx = width * (0.2f + 0.6f * progress)
            val cy = height * 0.25f
            val glowColor = Color.argb(
                48,
                Color.red(accentColor),
                Color.green(accentColor),
                Color.blue(accentColor)
            )
            shader = RadialGradient(
                cx,
                cy,
                width * 0.65f,
                intArrayOf(glowColor, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

        if (particleMode.uppercase() != "NONE") {
            val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            for (p in particles) {
                val py = ((p.y - progress * p.speedY + 1f) % 1f) * height
                val px = (p.x + sin(progress * 6f + p.phase) * 0.02f) * width
                val pulse = 0.5f + 0.5f * sin(progress * 8f + p.phase)
                pPaint.color = Color.argb(
                    (55 + 90 * pulse).toInt().coerceIn(0, 160),
                    255,
                    255,
                    255
                )
                canvas.drawCircle(px, py, p.radius * (0.7f + 0.4f * pulse), pPaint)
            }
        }
    }

    fun buildColorMatrix(filter: VideoFilter): ColorMatrix? = when (filter) {
        VideoFilter.ORIGINAL -> null
        VideoFilter.NOIR -> ColorMatrix().apply {
            setSaturation(0f)
            val contrast = ColorMatrix(
                floatArrayOf(
                    1.25f, 0f, 0f, 0f, -20f,
                    0f, 1.25f, 0f, 0f, -20f,
                    0f, 0f, 1.25f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            postConcat(contrast)
        }
        VideoFilter.VIOLET_DREAM -> ColorMatrix(
            floatArrayOf(
                1.08f, 0f, 0.14f, 0f, 10f,
                0f, 0.92f, 0.05f, 0f, 0f,
                0.15f, 0.05f, 1.22f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        VideoFilter.GOLDEN_GLOW -> ColorMatrix(
            floatArrayOf(
                1.22f, 0.08f, 0f, 0f, 12f,
                0.04f, 1.08f, 0f, 0f, 6f,
                0f, 0f, 0.85f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        VideoFilter.CYBER_CRISP -> ColorMatrix(
            floatArrayOf(
                1.05f, 0f, 0.1f, 0f, -5f,
                0f, 1.15f, 0.08f, 0f, 0f,
                0.08f, 0.12f, 1.28f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun parseSafeColor(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.rgb(139, 92, 246)
    }

    private data class ParticleSeed(
        val x: Float,
        val y: Float,
        val radius: Float,
        val speedY: Float,
        val phase: Float
    )

    private class Gif89aEncoder(
        private val width: Int,
        private val height: Int,
        private val out: OutputStream
    ) {
        private val palette = ByteArray(256 * 3)

        init {
            var idx = 0
            for (r in 0..5) {
                for (g in 0..5) {
                    for (b in 0..5) {
                        palette[idx++] = (r * 51).toByte()
                        palette[idx++] = (g * 51).toByte()
                        palette[idx++] = (b * 51).toByte()
                    }
                }
            }
            for (i in 0 until 40) {
                val gray = (i * 255 / 39).toByte()
                palette[idx++] = gray
                palette[idx++] = gray
                palette[idx++] = gray
            }
        }

        fun writeHeader() {
            out.write("GIF89a".toByteArray(Charsets.US_ASCII))
            writeShort(width)
            writeShort(height)
            out.write(0xF7) // Global Color Table, 8-bit, 256 entries
            out.write(0)    // Background color index
            out.write(0)    // Pixel aspect ratio
            out.write(palette)

            // Netscape 2.0 infinite looping extension
            out.write(0x21)
            out.write(0xFF)
            out.write(11)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(3)
            out.write(1)
            writeShort(0) // Loop forever
            out.write(0)
        }

        fun writeFrame(argbPixels: IntArray, delayCentiseconds: Int) {
            // Graphic Control Extension
            out.write(0x21)
            out.write(0xF9)
            out.write(4)
            out.write(0x04) // Disposal: do not dispose
            writeShort(delayCentiseconds)
            out.write(0) // Transparent index unused
            out.write(0)

            // Image Descriptor
            out.write(0x2C)
            writeShort(0)
            writeShort(0)
            writeShort(width)
            writeShort(height)
            out.write(0) // No local color table

            val indices = ByteArray(argbPixels.size)
            for (i in argbPixels.indices) {
                val px = argbPixels[i]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val ri = (r * 5 + 127) / 255
                val gi = (g * 5 + 127) / 255
                val bi = (b * 5 + 127) / 255
                indices[i] = (ri * 36 + gi * 6 + bi).toByte()
            }

            writeLzwImageData(indices)
        }

        fun finish() {
            out.write(0x3B)
        }

        private fun writeShort(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
        }

        private fun writeLzwImageData(indices: ByteArray) {
            val minCodeSize = 8
            out.write(minCodeSize)

            val clearCode = 1 shl minCodeSize // 256
            val eoiCode = clearCode + 1       // 257

            val subBlock = ByteArray(255)
            var subBlockLen = 0
            var bitAccum = 0
            var bitCount = 0

            fun emitByte(b: Int) {
                subBlock[subBlockLen++] = b.toByte()
                if (subBlockLen == 255) {
                    out.write(255)
                    out.write(subBlock, 0, 255)
                    subBlockLen = 0
                }
            }

            fun writeBits(code: Int, bits: Int) {
                bitAccum = bitAccum or (code shl bitCount)
                bitCount += bits
                while (bitCount >= 8) {
                    emitByte(bitAccum and 0xFF)
                    bitAccum = bitAccum ushr 8
                    bitCount -= 8
                }
            }

            val tableSize = 5003
            val keys = IntArray(tableSize) { -1 }
            val values = IntArray(tableSize)

            var codeBits = minCodeSize + 1
            var nextCode = eoiCode + 1
            var maxCode = (1 shl codeBits) - 1

            fun resetTable() {
                keys.fill(-1)
                codeBits = minCodeSize + 1
                nextCode = eoiCode + 1
                maxCode = (1 shl codeBits) - 1
            }

            writeBits(clearCode, codeBits)
            var prefix = indices[0].toInt() and 0xFF

            for (i in 1 until indices.size) {
                val k = indices[i].toInt() and 0xFF
                val key = (prefix shl 8) or k
                var slot = ((key * 37) and 0x7FFFFFFF) % tableSize
                var found = -1
                while (keys[slot] != -1) {
                    if (keys[slot] == key) {
                        found = values[slot]
                        break
                    }
                    slot = (slot + 1) % tableSize
                }

                if (found != -1) {
                    prefix = found
                } else {
                    writeBits(prefix, codeBits)
                    if (nextCode < 4096) {
                        keys[slot] = key
                        values[slot] = nextCode++
                        if (nextCode > maxCode && codeBits < 12) {
                            codeBits++
                            maxCode = (1 shl codeBits) - 1
                        }
                    } else {
                        writeBits(clearCode, codeBits)
                        resetTable()
                    }
                    prefix = k
                }
            }

            writeBits(prefix, codeBits)
            writeBits(eoiCode, codeBits)
            if (bitCount > 0) {
                emitByte(bitAccum and 0xFF)
            }
            if (subBlockLen > 0) {
                out.write(subBlockLen)
                out.write(subBlock, 0, subBlockLen)
            }
            out.write(0) // Block terminator
        }
    }
}

class MusicPreviewPlayer {
    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null
    @Volatile private var isPlaying = false

    fun playPreview(track: MusicTrack, onFinished: () -> Unit) {
        stop()
        if (track == MusicTrack.NONE) {
            onFinished()
            return
        }
        val samples = VideoSynthesisEngine.synthesizeTrackPcm(track, 4000L)
        val minBuf = AudioTrack.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(44100)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = player
        isPlaying = true
        playThread = Thread {
            try {
                player.play()
                var offset = 0
                val chunk = 2048
                while (isPlaying && offset < samples.size) {
                    val toWrite = minOf(chunk, samples.size - offset)
                    player.write(samples, offset, toWrite)
                    offset += toWrite
                }
            } catch (_: Exception) {
            } finally {
                isPlaying = false
                runCatching { player.stop() }
                runCatching { player.release() }
                onFinished()
            }
        }.apply { start() }
    }

    fun stop() {
        isPlaying = false
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        playThread = null
    }
}
