package com.microsoft.dragoncopilot.ambient.sample.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.microsoft.dragoncopilot.ambient.sample.AmbientApplication
import com.microsoft.dragoncopilot.ambient.sample.config.Configuration
import com.microsoft.dragoncopilot.ambient.sample.data.Recording
import com.microsoft.dragoncopilot.ambient.sample.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel that bridges the [AmbientApplication] state to the Compose UI layer.
 *
 * Exposes authentication state, session data, and recording state as observable flows.
 * Delegates all SDK operations to [AmbientApplication] which owns the [AmbientClient].
 */
class AmbientViewModel(private val application: AmbientApplication) : ViewModel() {
    val signedIn = application.signedIn
    val sessions = application.sessionsStateFlow
    val recordingState = application.recordingState

    companion object {
        /** ViewModelProvider factory that injects the [AmbientApplication] instance. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY] as AmbientApplication
                AmbientViewModel(app)
            }
        }
    }

    /** Launch the Entra interactive sign-in flow. Requires an [Activity] for the auth UI. */
    fun signIn(activity: Activity) {
        viewModelScope.launch(Dispatchers.Default) {
            application.entra.signIn(activity)
        }
    }

    /**
     * Sign out the current user: closes the [AmbientClient], clears session state,
     * and signs out from Entra. Invokes [onComplete] on the main thread once finished
     * so callers can safely trigger navigation.
     */
    fun signOut(onComplete: () -> Unit) {
        application.closeClient()
        viewModelScope.launch(Dispatchers.Default) {
            application.entra.signOut()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun createSession(): Session {
        return application.createSession()
    }

    fun getSession(sessionId: String): Session? {
        return application.getSession(sessionId)
    }

    fun getRecordingsForSession(sessionId: String): List<Recording> {
        return application.getRecordingsForSession(sessionId)
    }

    /** Start an ambient recording in the given session. See [AmbientApplication.startRecording]. */
    fun startRecording(sessionId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            application.startRecording(sessionId, Configuration.ehrData)
        }
    }

    /** Stop the current recording. The SDK will automatically begin uploading. */
    fun stopRecording() {
        viewModelScope.launch(Dispatchers.Default) {
            application.stopRecording()
        }
    }
}