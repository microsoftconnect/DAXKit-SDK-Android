package com.microsoft.dragoncopilot.ambient.sample.auth

import com.microsoft.dragoncopilot.ambient.data.AccessTokenProvider
import java.lang.Exception
import java.util.Date

/**
 * The Ambient Client authentication AccessTokenProvider
 *
 * This class serves as an interface between the specific authentication solution and the Dragon
 * Copilot Ambient SDK to provide authentication for use during ambient recording.
 */
class EntraTokenProvider(private val entra: EntraAuth): AccessTokenProvider {

    /**
     * The method to retrieve an access token
     *
     * A single method is required to be implemented to return the access token, or an error in
     * the case that the token retrieval fails.
     */
    override fun accessToken(
        onSuccess: (String, Date) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Forward the request on to the Entra provider
        entra.acquireToken(onSuccess, onError)
    }
}