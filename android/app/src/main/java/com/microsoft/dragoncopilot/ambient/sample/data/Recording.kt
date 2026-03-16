package com.microsoft.dragoncopilot.ambient.sample.data

import java.util.UUID

/**
 * In-memory representation of a completed ambient recording.
 *
 * Populated from the SDK's [AmbientRecording.Listener.onRecordingStopped] callback,
 * which provides the server-assigned [recordingId] and final [durationMs].
 *
 * @property recordingId Unique identifier assigned by the SDK when recording starts.
 * @property sessionId   The parent session this recording belongs to.
 * @property durationMs  Total recording duration in milliseconds.
 * @property createdAt   Wall-clock timestamp (epoch millis) when the recording was saved.
 */
data class Recording(
    val recordingId: String = UUID.randomUUID().toString(),
    val sessionId: String = "",
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
)
