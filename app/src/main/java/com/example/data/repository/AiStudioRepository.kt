package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ImageAspectRatioOption
import com.example.data.model.MotionDirective
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class AiStudioRepository {
    companion object {
        private const val TAG = "AiStudioRepo"
        private const val AI_TIMEOUT_MS = 60_000L
    }

    private val motionSchema = Schema.obj(
        properties = mapOf(
            "motionStyle" to Schema.string(description = "Short 2-3 word label like 'Golden Kinetic Drift'"),
            "startZoom" to Schema.double(description = "Initial scale between 1.0 and 1.15"),
            "endZoom" to Schema.double(description = "Ending scale between 1.05 and 1.32"),
            "panX" to Schema.double(description = "Horizontal pan fraction between -0.08 and 0.08"),
            "panY" to Schema.double(description = "Vertical pan fraction between -0.08 and 0.08"),
            "particleMode" to Schema.enumeration(
                values = listOf("SPARKLE", "NEBULA", "NONE"),
                description = "Atmospheric overlay mode"
            ),
            "moodColorHex" to Schema.string(description = "Hex color string like '#8B5CF6'"),
            "keyframeEditPrompt" to Schema.string(
                description = "Detailed image editing prompt transforming the source photo toward the final cinematic video frame"
            )
        )
    )

    private val directorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = motionSchema
            }
        )
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private fun prepareAiBitmap(bitmap: Bitmap, maxSide: Int): Bitmap {
        require(!bitmap.isRecycled) { "Source photo was recycled before AI processing." }
        val swBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("Unable to convert photo to ARGB_8888 for AI processing.")
        }
        val longest = maxOf(swBitmap.width, swBitmap.height)
        if (longest <= maxSide) return swBitmap
        val scale = maxSide.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            swBitmap,
            (swBitmap.width * scale).toInt().coerceAtLeast(1),
            (swBitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun formatAiException(e: Exception, fallback: String): IllegalStateException {
        val causes = generateSequence(e as Throwable?) { it.cause }.toList()
        val quotaError = causes.filterIsInstance<QuotaExceededException>().firstOrNull()
        if (quotaError != null) {
            return IllegalStateException(
                "AI quota or rate limit exceeded. Ensure your Firebase project is on the Blaze plan or try again shortly. (${quotaError.localizedMessage ?: "QuotaExceeded"})",
                e
            )
        }
        val rawMsg = causes.firstNotNullOfOrNull { t ->
            t.localizedMessage?.takeIf { it.isNotBlank() } ?: t.message?.takeIf { it.isNotBlank() }
        }
        val lower = rawMsg?.lowercase().orEmpty()
        val msg = when {
            "403" in lower || "app check" in lower || "appcheck" in lower || "attestation" in lower ->
                "Firebase AI request was rejected (HTTP 403 / App Check). Please reinstall or rebuild the app with valid Firebase App Check registration. Details: $rawMsg"
            "billing" in lower || "blaze" in lower ->
                "This Gemini model requires Firebase Blaze billing enabled on the project. Details: $rawMsg"
            "network" in lower || "unable to resolve host" in lower || "connect" in lower ->
                "Network error contacting MUBA AI servers. Check your internet connection and try again."
            !rawMsg.isNullOrBlank() -> rawMsg
            else -> fallback
        }
        return IllegalStateException(msg, e)
    }

    private fun safeJsonFloat(json: JSONObject, key: String, fallback: Double, min: Float, max: Float): Float {
        val raw = json.optDouble(key, fallback)
        val finite = if (raw.isFinite()) raw else fallback
        return finite.toFloat().coerceIn(min, max)
    }

    suspend fun planVideoMotion(
        sourceBitmap: Bitmap,
        userPrompt: String,
        variationHint: String? = null
    ): MotionDirective {
        return try {
            val aiBitmap = prepareAiBitmap(sourceBitmap, 768)
            val variationInstruction = variationHint?.takeIf { it.isNotBlank() }?.let {
                " Batch creative variation direction: $it."
            }.orEmpty()
            val response = withTimeout(AI_TIMEOUT_MS) {
                directorModel.generateContent(
                    content {
                        image(aiBitmap)
                        text(
                            "You are a cinematic AI video motion director. Given this photo and the user's video prompt: '$userPrompt', " +
                                "design smooth camera motion and a rich visual transformation instruction (keyframeEditPrompt) that keeps the core subject recognizable while bringing the prompt to life." +
                                variationInstruction
                        )
                    }
                )
            }
            val rawText = response.text?.trim().orEmpty()
            if (rawText.isEmpty()) {
                throw IllegalStateException("Director model returned an empty plan.")
            }
            val cleanedJson = rawText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val json = JSONObject(cleanedJson)
            MotionDirective(
                motionStyle = json.optString("motionStyle", "Cinematic Drift").ifBlank { "Cinematic Drift" },
                startZoom = safeJsonFloat(json, "startZoom", 1.0, 1.0f, 1.15f),
                endZoom = safeJsonFloat(json, "endZoom", 1.22, 1.05f, 1.35f),
                panX = safeJsonFloat(json, "panX", 0.04, -0.08f, 0.08f),
                panY = safeJsonFloat(json, "panY", -0.02, -0.08f, 0.08f),
                particleMode = json.optString("particleMode", "SPARKLE").ifBlank { "SPARKLE" },
                moodColorHex = json.optString("moodColorHex", "#8B5CF6").ifBlank { "#8B5CF6" },
                keyframeEditPrompt = json.optString("keyframeEditPrompt", userPrompt).ifBlank { userPrompt }
            )
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("AI video director timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Motion planning failed", e)
            throw formatAiException(e, "AI video motion planning failed.")
        }
    }

    suspend fun generateAiKeyframe(
        sourceBitmap: Bitmap,
        keyframePrompt: String
    ): Bitmap {
        return try {
            val aiBitmap = prepareAiBitmap(sourceBitmap, 1024)
            val response = withTimeout(AI_TIMEOUT_MS) {
                imageModel.generateContent(
                    content {
                        image(aiBitmap)
                        text(
                            "Transform this image into the climax frame of a cinematic video while preserving the subject composition: $keyframePrompt"
                        )
                    }
                )
            }
            val candidate = response.candidates.firstOrNull()
            val imagePart = candidate?.content?.parts?.filterIsInstance<ImagePart>()?.firstOrNull()
            val bmp = imagePart?.image ?: run {
                val textReason = candidate?.content?.parts?.filterIsInstance<TextPart>()?.firstOrNull()?.text
                val finishReason = candidate?.finishReason?.name
                throw IllegalStateException(
                    textReason ?: "AI keyframe model returned no image${finishReason?.let { " ($it)" } ?: ""}."
                )
            }
            require(bmp.width > 0 && bmp.height > 0) { "AI keyframe model returned an empty bitmap." }
            bmp
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("AI keyframe synthesis timed out after 60 seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Keyframe synthesis failed", e)
            throw formatAiException(e, "AI keyframe synthesis failed.")
        }
    }

    suspend fun generateImage(
        prompt: String,
        aspectRatio: ImageAspectRatioOption,
        referenceBitmap: Bitmap? = null
    ): Bitmap {
        return try {
            val enrichedPrompt = if (referenceBitmap != null) {
                "Transform and reimagine this reference photo according to: $prompt (framed in ${aspectRatio.label} ${aspectRatio.ratioLabel} composition, crisp studio lighting, high detail)"
            } else {
                "$prompt (framed in ${aspectRatio.label} ${aspectRatio.ratioLabel} composition, crisp studio lighting, high detail)"
            }
            val response = withTimeout(AI_TIMEOUT_MS) {
                if (referenceBitmap != null) {
                    val prepared = prepareAiBitmap(referenceBitmap, 1024)
                    imageModel.generateContent(
                        content {
                            image(prepared)
                            text(enrichedPrompt)
                        }
                    )
                } else {
                    imageModel.generateContent(enrichedPrompt)
                }
            }
            val candidate = response.candidates.firstOrNull()
            val imagePart = candidate?.content?.parts?.filterIsInstance<ImagePart>()?.firstOrNull()
            val bmp = imagePart?.image ?: run {
                val textReason = candidate?.content?.parts?.filterIsInstance<TextPart>()?.firstOrNull()?.text
                val finishReason = candidate?.finishReason?.name
                throw IllegalStateException(
                    textReason ?: "No image was returned by Gemini Nano Banana${finishReason?.let { " ($it)" } ?: ""}."
                )
            }
            require(bmp.width > 0 && bmp.height > 0) { "AI image model returned an empty bitmap." }
            bmp
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Image generation timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            throw formatAiException(e, "AI image generation failed.")
        }
    }
}
