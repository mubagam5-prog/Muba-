package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AppLanguage
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

private val Context.mubaDataStore: DataStore<Preferences> by preferencesDataStore(name = "muba_ai_prefs")

class UserRepository(
    private val dbProvider: () -> FirebaseFirestore,
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(
        dbProvider = {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        },
        dataStore = context.applicationContext.mubaDataStore
    )

    private val db: FirebaseFirestore by lazy { dbProvider() }
    private val auth: FirebaseAuth by lazy { Firebase.auth }

    private object PrefKeys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE = stringPreferencesKey("preferred_language")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val localDarkMode: Flow<Boolean> = dataStore.data.map { it[PrefKeys.DARK_MODE] ?: true }
    val localLanguage: Flow<String> = dataStore.data.map { it[PrefKeys.LANGUAGE] ?: "en" }
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[PrefKeys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs -> prefs[PrefKeys.ONBOARDING_DONE] = completed }
    }

    suspend fun setLocalThemeAndLanguage(darkMode: Boolean, languageCode: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.DARK_MODE] = darkMode
            prefs[PrefKeys.LANGUAGE] = AppLanguage.fromCode(languageCode).code
        }
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing profile.")

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val docRef = db.collection("users").document(uid)
        return try {
            val signedInUid = requireUserId()
            check(signedInUid == uid) { "Signed-in user mismatch." }
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val displayName = (user.displayName?.takeIf { it.isNotBlank() } ?: "MUBA Creator")
                    .take(80)
                val email = (user.email ?: "").take(120)
                val photo = user.photoUrl?.toString()?.take(500)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to photo,
                    "preferredLanguage" to "en",
                    "darkMode" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        userId: String,
        preferredLanguage: String,
        darkMode: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val validLang = AppLanguage.fromCode(preferredLanguage).code
            setLocalThemeAndLanguage(darkMode, validLang)
            db.collection("users").document(uid).update(
                mapOf(
                    "preferredLanguage" to validLang,
                    "darkMode" to darkMode,
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

    suspend fun updateDisplayName(userId: String, newName: String): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val sanitized = newName.trim().take(80)
            require(sanitized.isNotEmpty()) { "Display name cannot be empty." }
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to sanitized,
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
}
