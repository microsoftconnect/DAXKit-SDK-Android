package com.microsoft.dragoncopilot.ambient.sample

import android.app.Application
import android.util.Log
import com.microsoft.dragoncopilot.ambient.AmbientClient
import com.microsoft.dragoncopilot.ambient.AmbientRecording
import com.microsoft.dragoncopilot.ambient.AmbientSession
import com.microsoft.dragoncopilot.ambient.data.Provider
import com.microsoft.dragoncopilot.ambient.sample.auth.EntraAuth
import com.microsoft.dragoncopilot.ambient.sample.auth.EntraAuthListener
import com.microsoft.dragoncopilot.ambient.sample.auth.EntraTokenProvider
import com.microsoft.dragoncopilot.ambient.sample.data.Recording
import com.microsoft.dragoncopilot.ambient.sample.data.Session
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

/**
 * Central application class that owns the Ambient SDK lifecycle.
 *
 * This class demonstrates the three key integration points with the Dragon Copilot Ambient SDK:
 *
 * 1. **[AmbientClient]** — Created after authentication, used to open sessions and manage uploads.
 *    The client is long-lived and should persist across activities.
 *
 * 2. **[AmbientClient.Listener]** — Receives client-level callbacks such as supported languages
 *    and upload status. Implement this to track upload progress in your own UI.
 *
 * 3. **[AmbientRecording.Listener]** — Receives real-time recording callbacks including audio
 *    levels, duration updates, and lifecycle events (started / stopped / interrupted).
 *
 * **SDK lifecycle:**
 * ```
 * authenticate → createClient(provider) → client.session(...) → session.startRecording(...)
 *                                                                    ↓
 *                                                              recording.stop()
 *                                                                    ↓
 *                                                         onRecordingStopped (auto-upload)
 * ```
 *
 * @see AmbientClient
 * @see AmbientRecording
 */
class AmbientApplication : EntraAuthListener, AmbientClient.Listener, AmbientRecording.Listener, Application() {
    lateinit var entra: EntraAuth
    lateinit var tokenProvider: EntraTokenProvider

    /** The long-lived SDK client. Non-null once the user is signed in and a [Provider] is configured. */
    var client: AmbientClient? = null

    /** Current SDK session — represents one patient encounter. */
    private var ambientSession: AmbientSession? = null

    /** Current SDK recording — represents one audio capture within a session. */
    private var ambientRecording: AmbientRecording? = null

    // ── Observable state exposed to ViewModels / UI ──────────────────────────

    /** `null` = auth state unknown (loading), `true` = signed in, `false` = signed out. */
    val _signedIn: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val signedIn = _signedIn.asStateFlow()

    /** One-shot events (e.g. errors, status messages) surfaced via Snackbar. */
    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    data class Event(val message: String)

    /** In-memory session list. Replace with a database in a production app. */
    private var _sessions: List<Session> = listOf()
    private val _sessionsStateFlow: MutableStateFlow<List<Session>> = MutableStateFlow(_sessions)
    val sessionsStateFlow = _sessionsStateFlow.asStateFlow()

    /**
     * Observable snapshot of the current recording's state, driven by
     * [AmbientRecording.Listener] callbacks.
     */
    data class RecordingState(
        val isRecording: Boolean = false,
        val currentSessionId: String? = null,
        val currentRecordingId: String? = null,
        val elapsedMs: Long = 0L,
        /** Normalized audio level (0.0 – 1.0) from the SDK's [onRecordedAudio] callback. */
        val audioLevel: Float = 0f,
    )

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState = _recordingState.asStateFlow()

    // ── Application lifecycle ────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        // Initialize authentication. Replace EntraAuth with your own identity provider.
        entra = EntraAuth(this, this)
        // Wrap the auth provider in an AccessTokenProvider for the SDK.
        tokenProvider = EntraTokenProvider(entra)
    }

    // ── EntraAuthListener ────────────────────────────────────────────────────

    /** Called by [EntraAuth] whenever the signed-in account changes. */
    override fun onAccountUpdate() {
        _signedIn.value = entra.account != null
    }

    // ── Ambient Client management ────────────────────────────────────────────

    /**
     * Close the current [AmbientClient] and reset all session/recording state.
     *
     * Call this before signing out so that in-flight uploads are cancelled and resources are
     * released. A new client must be created via [createClient] after the next sign-in.
     */
    fun closeClient() {
        client?.close()
        client = null
        ambientSession = null
        ambientRecording = null
        _sessions = listOf()
        _sessionsStateFlow.value = _sessions
        _recordingState.value = RecordingState()
    }

    /**
     * Create a new [AmbientClient] with the given [Provider] configuration.
     *
     * The client is stored at the Application level so it survives configuration changes and can
     * continue background uploads while the user navigates between screens.
     *
     * @param provider Identifies the partner, product, organization, and user. See [Configuration].
     */
    fun createClient(provider: Provider) {
        client?.close()
        client = AmbientClient(
            applicationContext,
            provider,
            tokenProvider,
            this, // AmbientClient.Listener — receives upload and language callbacks
        )
        _events.trySend(Event("Ambient Client Created"))
    }

    // ── Session management (sample-specific, in-memory) ──────────────────────

    fun createSession(): Session {
        val session = Session()
        _sessions = _sessions + session
        _sessionsStateFlow.value = _sessions
        return session
    }

    fun getSession(sessionId: String): Session? {
        return _sessions.find { it.sessionId == sessionId }
    }

    fun getRecordingsForSession(sessionId: String): List<Recording> {
        return _sessions.find { it.sessionId == sessionId }?.recordings ?: emptyList()
    }

    private fun addRecordingToSession(sessionId: String, recording: Recording) {
        _sessions = _sessions.map { session ->
            if (session.sessionId == sessionId) {
                session.copy(recordings = session.recordings + recording)
            } else session
        }
        _sessionsStateFlow.value = _sessions
    }

    // ── SDK recording lifecycle ──────────────────────────────────────────────

    /**
     * Start an ambient recording within a session.
     *
     * This demonstrates the two-step SDK pattern:
     * 1. **Open a session** — `client.session(sessionId, ehrData, ...)` binds the encounter's
     *    EHR context (patient, appointment, etc.) to a session ID.
     * 2. **Start recording** — `session.startRecording(locales, noteGeneration, listener)` begins
     *    audio capture and returns an [AmbientRecording] handle.
     *
     * @param sessionId Unique identifier for the encounter session.
     * @param ehrData   Patient and encounter context sent alongside the audio.
     */
    suspend fun startRecording(sessionId: String, ehrData: com.microsoft.dragoncopilot.ambient.data.EHRData) {
        val c = client ?: run {
            _events.trySend(Event("Client not initialized"))
            return
        }
        try {
            // Step 1: Open (or resume) an SDK session for this encounter
            val session = c.session(
                sessionId,
                ehrData,
                Locale.getDefault(), // encounter locale
                Locale.getDefault(), // report locale
            )
            ambientSession = session

            // Step 2: Begin audio capture
            val recording = session.startRecording(
                arrayOf(Locale.getDefault()), // spoken language(s) expected in the recording
                true,  // noteGeneration — set to true to generate clinical notes
                this,  // AmbientRecording.Listener — receives real-time audio callbacks
            )
            ambientRecording = recording
        } catch (e: Exception) {
            _events.trySend(Event("Failed to start recording: ${e.message}"))
            _recordingState.value = RecordingState()
        }
    }

    /**
     * Stop the current recording.
     *
     * After stopping, the SDK automatically begins uploading the audio.
     * Upload progress is reported through the [AmbientClient.Listener] callbacks
     * ([onUploadStarted], [onUploadComplete], [onUploadFailed]).
     */
    suspend fun stopRecording() {
        try {
            ambientRecording?.stop()
        } catch (e: Exception) {
            _events.trySend(Event("Failed to stop recording: ${e.message}"))
        }
        ambientRecording = null
    }

    // ── AmbientRecording.Listener callbacks ──────────────────────────────────
    // These callbacks fire on a background thread during an active recording.

    /** Called once when audio capture begins. Use this to update UI to a "recording" state. */
    override fun onRecordingStarted(recordingId: String, sessionId: String) {
        Log.i("AmbientApp", "Recording started: $recordingId in session $sessionId")
        _recordingState.value = RecordingState(
            isRecording = true,
            currentSessionId = sessionId,
            currentRecordingId = recordingId,
            elapsedMs = 0L,
            audioLevel = 0f,
        )
        _events.trySend(Event("Recording started"))
    }

    /** Called when the recording finishes normally. The SDK begins uploading automatically. */
    override fun onRecordingStopped(recordingId: String, sessionId: String, duration: kotlin.time.Duration) {
        val durationMs = duration.inWholeMilliseconds
        Log.i("AmbientApp", "Recording stopped: $recordingId duration=${durationMs}ms")
        val recording = Recording(recordingId = recordingId, sessionId = sessionId, durationMs = durationMs)
        addRecordingToSession(sessionId, recording)
        _recordingState.value = RecordingState()
        _events.trySend(Event("Recording stopped"))
    }

    /**
     * Called if the recording is interrupted unexpectedly (e.g. phone call, audio focus loss).
     * Handle this to inform the user and potentially restart the recording.
     */
    override fun onRecordingInterrupted(
        recordingId: String,
        sessionId: String,
        reason: AmbientRecording.InterruptionReason,
        details: String?,
    ) {
        Log.w("AmbientApp", "Recording interrupted: $details")
        _recordingState.value = RecordingState()
        _events.trySend(Event("Recording interrupted: $details"))
    }

    /**
     * Called when the recording approaches its maximum allowed duration.
     * Use this to warn the user so they can stop and start a new recording.
     */
    override fun onWarnDurationReached(recordingId: String, sessionId: String, remaining: kotlin.time.Duration) {
        _events.trySend(Event("Recording time warning: ${remaining.inWholeSeconds}s remaining"))
    }

    /**
     * Called frequently during recording with the current elapsed time and audio level.
     * Use [level] (0.0–1.0) to drive a waveform or VU meter in your UI.
     */
    override fun onRecordedAudio(recordingId: String, sessionId: String, duration: kotlin.time.Duration, level: Float) {
        _recordingState.value = _recordingState.value.copy(
            elapsedMs = duration.inWholeMilliseconds,
            audioLevel = level,
        )
    }

    // ── AmbientClient.Listener callbacks ─────────────────────────────────────
    // These callbacks report client-level events such as upload progress.

    /** Called once after client creation with the languages the SDK supports. */
    override fun onSupportedLanguages(
        recordingLocales: Array<Locale>,
        reportLocales: Array<Locale>,
    ) {
        _events.trySend(Event(
            "Languages: ${recordingLocales.joinToString(separator = " ") { it.displayLanguage }}"))
    }

    /** Called when the SDK finishes uploading a recording's audio to the server. */
    override fun onUploadComplete(recordingId: String, sessionId: String) {
        _events.trySend(Event("Upload Complete: $recordingId"))
    }

    /**
     * Called when an upload attempt fails. Check [willRetryUpload] — if `true`, the SDK will
     * retry automatically; if `false`, the upload has been abandoned.
     */
    override fun onUploadFailed(
        recordingId: String,
        sessionId: String,
        error: Error,
        willRetryUpload: Boolean,
    ) {
        _events.trySend(Event("Upload Failed: $recordingId"))
    }

    /** Called when the SDK begins uploading a recording's audio. */
    override fun onUploadStarted(recordingId: String, sessionId: String) {
        _events.trySend(Event("Upload Start: $recordingId"))
    }
}