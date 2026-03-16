package com.microsoft.dragoncopilot.ambient.sample.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.microsoft.dragoncopilot.ambient.sample.R
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication.ISingleAccountApplicationCreatedListener
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import java.util.Date

/**
 * Microsoft Entra ID (formerly Azure AD) authentication manager using MSAL.
 *
 * This is the sample app's authentication provider. Integrators should replace this class
 * with their own identity provider implementation. The key contract the Ambient SDK needs
 * is an [AccessTokenProvider] (see [EntraTokenProvider]) — the rest of this class is
 * standard MSAL boilerplate.
 *
 * **Setup:** Place your `auth_config_single_account.json` in `res/raw/`. See the README
 * for the required JSON schema.
 *
 * @param context  Application context used to initialize the MSAL library.
 * @param listener Notified whenever the signed-in account changes so the UI can react.
 */
class EntraAuth(context: Context, val listener: EntraAuthListener) {
    private var application: ISingleAccountPublicClientApplication? = null
    var account: IAccount? = null
    /** The OAuth scope required by Dragon Copilot to authorize configuration and streaming. */
    private val SCOPE = "api://streaming.daxcopilot.com/Data.Write"
    private var accessToken: String? = null
    private var date: Date? = null

    init {
        // Initialize MSAL with the config from res/raw and check for a cached account
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.auth_config_single_account,
            object: ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication?) {
                    this@EntraAuth.application = application
                    // Check for an existing signed-in account (e.g. returning user)
                    application?.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                        override fun onAccountLoaded(activeAccount: IAccount?) {
                            account = activeAccount
                            listener.onAccountUpdate()
                        }

                        override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                            account = currentAccount
                            listener.onAccountUpdate()
                        }

                        override fun onError(exception: MsalException) {
                            account = null
                            listener.onAccountUpdate()
                        }
                    })
                }

                override fun onError(exception: MsalException?) {
                    listener.onAccountUpdate()
                }
            }
        )
    }

    /**
     * Launch the interactive Entra sign-in flow.
     *
     * MSAL displays a web-based login UI within the provided [activity]. On success the
     * [account] and [accessToken] are cached for subsequent silent token refreshes.
     */
    fun signIn(activity: Activity) {
            application!!.signIn(
                SignInParameters.builder().withActivity(activity)
                    .withScope(SCOPE)
                    .withCallback(
                        object : AuthenticationCallback {
                            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                Log.d(null, "Successfully authenticated")
                                Log.d(
                                    null,
                                    "ID Token: " + authenticationResult.account.claims!!["id_token"]
                                )

                                account = authenticationResult.account
                                accessToken = authenticationResult.accessToken
                                date = authenticationResult.expiresOn
                                listener.onAccountUpdate()
                            }

                            override fun onError(exception: MsalException) {
                                Log.d(
                                    null,
                                    "Authentication failed: $exception"
                                )
                                account = null
                                listener.onAccountUpdate()
                            }

                            override fun onCancel() {
                                Log.d(null, "User cancelled login.")
                                account = null
                                listener.onAccountUpdate()
                            }
                        })
                    .build()
            )
    }

    /** Sign out the current account and clear cached tokens. */
    fun signOut() {
        application!!.signOut()
        account = null
        listener.onAccountUpdate()
    }

    /**
     * Retrieve a valid access token for the Dragon Copilot API.
     *
     * This method is called by [EntraTokenProvider] (the SDK's [AccessTokenProvider]) whenever
     * the SDK needs to authenticate an API request. It first checks the in-memory cache, then
     * falls back to a silent MSAL token refresh.
     *
     * @param onSuccess Called with the token string and its expiry time.
     * @param onError   Called if no valid token can be obtained (e.g. user signed out).
     */
    fun acquireToken(
        onSuccess: (token: String, expiryTime: Date) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Return the cached token if it hasn't expired
        if (date?.after(Date()) == true) {
            onSuccess(accessToken!!, date!!)
        } else {
            account?.let { account ->
                // Attempt a silent token refresh (no UI)
                application?.acquireTokenSilentAsync(
                    AcquireTokenSilentParameters.Builder()
                        .withScopes(listOf(SCOPE))
                        .forAccount(account)
                        .fromAuthority(account.authority)
                        .withCallback(object : SilentAuthenticationCallback {
                            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                // Cache the token and expiration and forward to the callback from
                                // the AccessTokenProvider
                                accessToken = authenticationResult.accessToken
                                date = authenticationResult.expiresOn
                                onSuccess(accessToken!!, date!!)
                            }

                            override fun onError(exception: MsalException?) {
                                // Erase the cached token and forward an error to the callback from
                                // the AccessTokenProvider
                                accessToken = null
                                date = null
                                onError(Exception())
                            }
                        })
                        .build()
                )
            } ?: run {
                // No signed-in account — cannot acquire a token
                onError(Exception())
            }
        }
    }
}

/**
 * Callback interface for authentication state changes.
 * Implement this to react when the user signs in or out.
 */
interface EntraAuthListener {
    fun onAccountUpdate()
}

