package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.Creation
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.CreationRepository
import com.example.data.repository.UserRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val userRepository: UserRepository,
    private val creationRepository: CreationRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        userRepository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { profile ->
                if (profile != null) {
                    viewModelScope.launch {
                        userRepository.setLocalThemeAndLanguage(
                            profile.darkMode,
                            profile.preferredLanguage
                        )
                    }
                }
                UiState.Success(profile)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing user profile", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val creationsState: StateFlow<UiState<List<Creation>>> =
        creationRepository.observeCreations(currentUserId)
            .map<List<Creation>, UiState<List<Creation>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing creations", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load creations"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    fun ensureProfile(user: FirebaseUser) {
        viewModelScope.launch {
            val result = userRepository.ensureUserProfile(user)
            result.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to initialize user profile."
            }
        }
    }

    fun updatePreferences(languageCode: String, darkMode: Boolean) {
        viewModelScope.launch {
            val result = userRepository.updatePreferences(currentUserId, languageCode, darkMode)
            result.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to save preferences."
            }
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            val result = userRepository.updateDisplayName(currentUserId, newName)
            result.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to update profile name."
            }
        }
    }

    fun renameCreation(creationId: String, newTitle: String) {
        viewModelScope.launch {
            val result = creationRepository.renameCreation(currentUserId, creationId, newTitle)
            result.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to rename creation."
            }
        }
    }

    fun deleteCreation(creation: Creation) {
        viewModelScope.launch {
            val result = creationRepository.deleteCreation(
                currentUserId,
                creation.id,
                creation.localFileName
            )
            result.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to delete creation."
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    companion object {
        private const val TAG = "MainVM"
        private const val STOP_TIMEOUT_MS = 5000L

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                MainViewModel(
                    userRepository = UserRepository(app),
                    creationRepository = CreationRepository(app),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
