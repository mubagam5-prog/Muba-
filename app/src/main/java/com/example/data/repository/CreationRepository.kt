package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.MediaStorageHelper
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Creation
import com.example.data.model.CreationType
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CreationRepository(
    private val appContext: Context,
    private val dbProvider: (() -> FirebaseFirestore)? = null
) {
    constructor(db: FirebaseFirestore, appContext: Context) : this(appContext, { db })

    private val db: FirebaseFirestore by lazy {
        dbProvider?.invoke() ?: FirebaseFirestore.getInstance(
            appContext.getString(R.string.firestore_database_id)
        )
    }

    private val auth by lazy { Firebase.auth }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing creations.")

    fun observeCreations(userId: String): Flow<List<Creation>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val colRef = db.collection("users").document(uid).collection("creations")
        val path = colRef.path
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Creation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveCreation(
        userId: String,
        type: CreationType,
        title: String,
        prompt: String,
        motionStyle: String? = null,
        durationSeconds: Long = 0L,
        quality: String = "1K",
        localFileName: String,
        thumbnailBase64: String? = null
    ): Result<String> {
        var docPath = "users/$userId/creations"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }

            val sanitizedTitle = title.trim().ifBlank { "MUBA AI Creation" }.take(120)
            val sanitizedPrompt = prompt.trim().ifBlank { "AI creation" }.take(1000)
            val sanitizedMotion = motionStyle?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
            val validDuration = when {
                type == CreationType.IMAGE -> 0L
                durationSeconds == 10L -> 10L
                else -> 5L
            }
            val validQuality = when (quality) {
                "HD", "4K", "1K" -> quality
                else -> if (type == CreationType.VIDEO) "HD" else "1K"
            }
            val sanitizedFileName = localFileName.trim().take(200)
            require(sanitizedFileName.isNotEmpty()) { "Local file name is required." }
            val validThumb = thumbnailBase64?.takeIf { it.length <= 100_000 }

            val docRef = db.collection("users").document(uid).collection("creations").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "type" to type.name,
                "title" to sanitizedTitle,
                "prompt" to sanitizedPrompt,
                "motionStyle" to sanitizedMotion,
                "durationSeconds" to validDuration,
                "quality" to validQuality,
                "localFileName" to sanitizedFileName,
                "thumbnailBase64" to validThumb,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun renameCreation(userId: String, creationId: String, newTitle: String): Result<Unit> {
        val docPath = "users/$userId/creations/$creationId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(creationId.isNotBlank()) { "Creation ID cannot be blank." }
            val sanitized = newTitle.trim().take(120)
            require(sanitized.isNotEmpty()) { "Title cannot be empty." }
            db.collection("users").document(uid)
                .collection("creations").document(creationId)
                .update(
                    mapOf(
                        "title" to sanitized,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCreation(
        userId: String,
        creationId: String,
        localFileName: String
    ): Result<Unit> {
        val docPath = "users/$userId/creations/$creationId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(creationId.isNotBlank()) { "Creation ID cannot be blank." }
            db.collection("users").document(uid)
                .collection("creations").document(creationId)
                .delete()
                .await()
            runCatching {
                val file = MediaStorageHelper.getCreationFile(appContext, localFileName)
                if (file.exists()) file.delete()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
