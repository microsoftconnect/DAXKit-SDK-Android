# Dragon Copilot Ambient SDK Android Sample

This sample application demonstrates how to integrate the Dragon Copilot Ambient SDK into an Android app. Use it as a reference for adding ambient recording, session management, and audio upload capabilities to your own project.

## Prerequisites

- **Android Studio** (latest stable release recommended)
- **Android SDK** with API 33+ (the Ambient SDK supports Android 13.0 and above)
- **Entra ID (Azure AD) app registration** — required to authenticate with the Dragon Copilot service (see [Authentication](#authentication) below)
- **Dragon Copilot partner credentials** — your Partner ID, Product ID, Org ID, and User ID (see [Configuration](#configuration) below)

## Getting Started

1. **Clone the repository** and open the `android/` directory in Android Studio.

2. **Generate the Gradle wrapper** (required for command-line builds):
   ```sh
   cd android
   gradle wrapper --gradle-version 8.13
   ```
   Android Studio handles this automatically when you open the project.

3. **Configure authentication** — copy the template and fill in your Entra ID client ID:
   ```sh
   cp app/src/main/res/raw/auth_config_single_account_template.json \
      app/src/main/res/raw/auth_config_single_account.json
   ```
   Then edit `auth_config_single_account.json` with your credentials (see [Authentication](#authentication)). This file is git-ignored.

4. **Set your provider IDs** — open [`app/src/main/java/.../config/Configuration.kt`](app/src/main/java/com/microsoft/dragoncopilot/ambient/sample/config/Configuration.kt) and replace the placeholder IDs with values from your account management system (see [Configuration](#configuration)).

5. **Build and run** the app on a device or emulator running API 33+.

## Project Structure

```
android/
├── app/src/main/java/com/microsoft/dragoncopilot/ambient/sample/
│   ├── AmbientApplication.kt   # SDK lifecycle: client, session, recording management
│   ├── MainActivity.kt          # Entry point, permissions, Compose navigation
│   ├── auth/
│   │   ├── EntraAuth.kt         # Entra ID (MSAL) sign-in/sign-out
│   │   └── EntraTokenProvider.kt# AccessTokenProvider bridge for the SDK
│   ├── config/
│   │   └── Configuration.kt     # Partner/product/org IDs and sample EHR data
│   ├── components/              # Compose UI screens (Login, Sessions, Recordings)
│   ├── data/                    # Session and Recording data classes
│   └── viewmodel/               # ViewModels connecting SDK state to the UI
├── gradle/libs.versions.toml    # Dependency versions (SDK, MSAL, Compose, etc.)
└── build.gradle.kts             # Root build configuration
```

## How the Sample Integrates the SDK

### SDK Dependency

The Ambient SDK is declared in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and added to the app module's [`build.gradle.kts`](app/build.gradle.kts):

`libs.versions.toml`

```toml
ambientVersion = "2.0.0"
microsoft-dragoncopilot-ambient = { group = "com.microsoft.dragoncopilot.ambient", name = "dragoncopilot-ambient", version.ref = "ambientVersion" }
```

`app/build.gradle.kts`

```kotlin
implementation(libs.microsoft.dragoncopilot.ambient)
```

### Permissions

The SDK requires the following permissions, declared in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

Runtime permissions are requested at launch in [`MainActivity.kt`](app/src/main/java/com/microsoft/dragoncopilot/ambient/sample/MainActivity.kt):

```kotlin
private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { /* results handled by the OS; SDK checks at recording time */ }

private fun requestAppPermissions() {
    val permissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val needed = permissions.filter {
        checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
    }
    if (needed.isNotEmpty()) {
        permissionLauncher.launch(needed.toTypedArray())
    }
}
```

### Authentication

The SDK requires an `AccessTokenProvider` to authorize API requests. The only requirement is that your implementation supplies a valid token (and its expiry) via the `onSuccess` callback, or reports failures via `onError`. Any authentication scheme that can satisfy this interface will work — Entra ID, a custom OAuth server, machine-to-machine credentials, etc.

For full details on what the SDK expects from access tokens, see the [Access Token Requirements](https://learn.microsoft.com/en-us/industry/healthcare/dragon-copilot/sdk/get-started/access-token-requirements) documentation.

This sample uses Entra ID (MSAL) as a concrete example. To use a different identity provider, replace the `auth/` classes with your own implementation of `AccessTokenProvider` and update the login UI in `components/EntraAuthLogin.kt` to match your sign-in flow.

#### Entra ID setup (used by this sample)

A template is provided at `app/src/main/res/raw/auth_config_single_account_template.json`. Copy it to `auth_config_single_account.json` (in the same directory) and replace `<YOUR_CLIENT_ID>` with your Entra ID application client ID. The real config file is git-ignored so credentials are never committed.

The full Entra sign-in/sign-out flow (interactive login, silent token refresh, account caching) is in [`EntraAuth.kt`](app/src/main/java/com/microsoft/dragoncopilot/ambient/sample/auth/EntraAuth.kt).

#### Implementing `AccessTokenProvider`

The SDK needs an `AccessTokenProvider` implementation. See [`EntraTokenProvider.kt`](app/src/main/java/com/microsoft/dragoncopilot/ambient/sample/auth/EntraTokenProvider.kt) for how this sample bridges Entra to the SDK:

```kotlin
class EntraTokenProvider(private val entra: EntraAuth): AccessTokenProvider {
    override fun accessToken(
        onSuccess: (String, Date) -> Unit,
        onError: (Exception) -> Unit
    ) {
        entra.acquireToken(onSuccess, onError)
    }
}
```

To use your own auth provider, create a class that implements `AccessTokenProvider` and pass it to `AmbientClient` in place of `EntraTokenProvider`.

### Configuration

Before creating an `AmbientClient`, you must configure a `Provider` with your partner, product, organization, and user identifiers. In a production app these values would come from your account management system. This sample defines them as constants in [`Configuration.kt`](app/src/main/java/com/microsoft/dragoncopilot/ambient/sample/config/Configuration.kt):

```kotlin
class Configuration {
    companion object {
        const val PARTNER_ID = /* your partner ID */
        const val PRODUCT_ID = /* your product ID */
        const val ORG_ID = /* your org ID */
        const val USER_ID = /* your user ID */
        const val PROVIDER_NAME = "Dragon Copilot Ambient Android Sample Provider"

        const val GEOGRAPHY = "US"

        fun getProvider(): Provider {
            return Provider(
                userId = USER_ID,
                name = PROVIDER_NAME,
                productId = PRODUCT_ID,
                partnerId = PARTNER_ID,
                orgId = ORG_ID,
                geography = GEOGRAPHY,
            )
        }
    }
}
```

### Creating the Ambient Client

The `AmbientClient` is the central entry point to the SDK. This sample creates it at the `Application` level so it survives configuration changes and can manage background uploads. See [`AmbientApplication.kt`](app/src/main/java/com/microsoft/dragoncopilot/ambient/sample/AmbientApplication.kt):

```kotlin
fun createClient(provider: Provider) {
    client = AmbientClient(
        applicationContext,
        provider,       // Partner/product/org/user configuration
        tokenProvider,  // Your AccessTokenProvider implementation
        this,           // AmbientClient.Listener for upload and language callbacks
    )
}
```

`AmbientApplication` implements `AmbientClient.Listener` to receive client-level callbacks:

- `onSupportedLanguages(recordingLocales, reportLocales)` — Available languages after client creation
- `onUploadStarted(recordingId, sessionId)` — Audio upload has begun
- `onUploadComplete(recordingId, sessionId)` — Audio upload finished successfully
- `onUploadFailed(recordingId, sessionId, error, willRetryUpload)` — Upload failed (SDK may auto-retry)

## App Flow

The sample follows this screen flow:

1. **Splash / Login** — Displays the Dragon Copilot logo. If the user is not signed in, a Sign In button appears. Once signed in, the `AmbientClient` is created and the app navigates to the Sessions screen.

2. **Sessions** — Displays a scrollable list of sessions with their IDs and creation timestamps. A logout button is available in the top bar. Tapping the **+** floating action button creates a new session and navigates to its Recordings screen.

3. **Recordings** — Shows all completed recordings for a session with their IDs and durations. Tapping the **microphone** floating action button starts a recording via the Ambient SDK. While recording, a live waveform visualization (driven by `onRecordedAudio` callbacks) and elapsed duration are displayed. Tapping the **stop** button ends the recording.

### Recording with the Ambient SDK

Recording is managed through the SDK's session and recording lifecycle. See the `startRecording` and `stopRecording` methods in [`AmbientApplication.kt`](app/src/main/java/com/microsoft/dragoncopilot/ambient/sample/AmbientApplication.kt):

```kotlin
// 1. Open a session — binds patient/encounter context to a session ID
val session = client.session(sessionId, ehrData, locale, locale)

// 2. Start recording — returns an AmbientRecording, callbacks go to the Listener
val recording = session.startRecording(arrayOf(locale), noteGeneration, listener)

// 3. Stop recording — the SDK automatically begins uploading the audio
recording.stop()
```

The `AmbientRecording.Listener` provides real-time feedback during recording:

- `onRecordingStarted(recordingId, sessionId)` — Recording has begun
- `onRecordedAudio(recordingId, sessionId, duration, level)` — Live audio level and elapsed time updates
- `onRecordingStopped(recordingId, sessionId, duration)` — Recording complete with final duration
- `onRecordingInterrupted(recordingId, sessionId, reason, details)` — Recording was interrupted

### Data Model

All data is held in memory for the duration of the app run (no database persistence):

- **Session** — Contains a `sessionId` (UUID), `createdAt` timestamp, and a list of its `Recording` objects.
- **Recording** — Contains a `recordingId` (from the SDK), `sessionId`, `durationMs`, and `createdAt` timestamp.

The `AmbientApplication` owns all state and exposes it via `StateFlow`. ViewModels reference the application state and expose it to the Compose UI.
