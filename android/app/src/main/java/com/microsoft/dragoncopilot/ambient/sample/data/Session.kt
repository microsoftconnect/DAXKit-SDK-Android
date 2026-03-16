package com.microsoft.dragoncopilot.ambient.sample.data

import java.util.UUID

/**
 * In-memory representation of a patient encounter session.
 *
 * Each session groups one or more [Recording]s that belong to the same encounter.
 * In a production app this would typically be persisted to a local database.
 *
 * @property sessionId  Unique identifier passed to the SDK's `client.session(...)`.
 * @property createdAt  Wall-clock timestamp (epoch millis) when the session was created.
 * @property recordings Completed recordings associated with this session.
 */
data class Session(
    val sessionId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val recordings: List<Recording> = emptyList(),
)
