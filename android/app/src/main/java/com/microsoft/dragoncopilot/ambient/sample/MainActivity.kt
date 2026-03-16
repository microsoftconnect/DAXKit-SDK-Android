package com.microsoft.dragoncopilot.ambient.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.microsoft.dragoncopilot.ambient.sample.ui.theme.AmbientSampleTheme
import com.microsoft.dragoncopilot.ambient.sample.components.Login
import com.microsoft.dragoncopilot.ambient.sample.components.RecordingsScreen
import com.microsoft.dragoncopilot.ambient.sample.components.SessionsScreen
import com.microsoft.dragoncopilot.ambient.sample.config.Configuration
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Entry point for the sample application.
 *
 * Requests the runtime permissions required by the Ambient SDK (microphone, phone state,
 * notifications) and launches the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled by the OS; SDK checks at recording time */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAppPermissions()

        enableEdgeToEdge()
        setContent {
            AmbientSampleTheme {
                AmbientSampleApp()
            }
        }
    }

    /**
     * Request the runtime permissions needed by the Ambient SDK.
     * - `RECORD_AUDIO` — required to capture microphone audio.
     * - `READ_PHONE_STATE` — used to pause recording during phone calls.
     * - `POST_NOTIFICATIONS` (API 33+) — allows the foreground service notification.
     */
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
}

/**
 * Top-level ViewModel for the app shell. Observes auth state and creates the [AmbientClient]
 * once the user signs in.
 */
class MainViewModel(private val application: AmbientApplication) : ViewModel() {
    val signedIn = application.signedIn
    val events = application.events

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY] as AmbientApplication
                MainViewModel(app)
            }
        }
    }

    /**
     * Create the [AmbientClient] using the configured [Provider].
     * Called once after successful sign-in, before navigating to the Sessions screen.
     */
    fun createClient() {
        application.createClient(provider = Configuration.getProvider())
    }
}

// ── Navigation routes ────────────────────────────────────────────────────────

/** Splash / login screen — the start destination. */
@Serializable
object MainRoute

/** Session list screen — shown after sign-in. */
@Serializable
object SessionsRoute

/** Recordings screen for a specific session. */
@Serializable
data class RecordingsRoute(val sessionId: String)

/**
 * Root composable that defines the app's navigation graph.
 *
 * **Screen flow:** Splash → Sessions → Recordings
 *
 * The [AmbientClient] is created in [MainViewModel.createClient] when the user first signs in.
 * It is closed via [AmbientViewModel.signOut] when the user taps the logout button.
 */
@PreviewScreenSizes
@Composable
fun AmbientSampleApp(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()

    // Collect SDK events (errors, status) and show them as Snackbar messages
    LaunchedEffect(key1 = Unit) {
        scope.launch {
            viewModel.events.collect { event ->
                launch {
                    snackbarHostState.showSnackbar(message = event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(navController, startDestination = MainRoute) {
            composable<MainRoute> {
                Splash(
                    modifier = modifier.padding(innerPadding),
                    onSignedIn = {
                        // Create the AmbientClient immediately after sign-in
                        viewModel.createClient()
                        navController.navigate(route = SessionsRoute) {
                            popUpTo(MainRoute) { inclusive = true }
                        }
                    }
                )
            }
            composable<SessionsRoute> {
                SessionsScreen(
                    modifier = modifier.padding(innerPadding),
                    onOpenSession = { sessionId ->
                        navController.navigate(route = RecordingsRoute(sessionId))
                    },
                    onLogout = {
                        // Return to the login screen after sign-out
                        navController.navigate(route = MainRoute) {
                            popUpTo(SessionsRoute) { inclusive = true }
                        }
                    }
                )
            }
            composable<RecordingsRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<RecordingsRoute>()
                RecordingsScreen(
                    sessionId = route.sessionId,
                    modifier = modifier.padding(innerPadding),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Splash / login screen displayed on app launch.
 *
 * If the user has a cached Entra session, they are automatically navigated to
 * the Sessions screen via the [onSignedIn] callback. Otherwise a Sign In button
 * is shown (see [Login]).
 */
@Composable
fun Splash(
    modifier: Modifier,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
    onSignedIn: () -> Unit,
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()

    // Auto-navigate when signed in
    LaunchedEffect(signedIn) {
        if (signedIn == true) {
            onSignedIn()
        }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
    ) {
        Text(
            text = "Microsoft",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Dragon Copilot",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Ambient Sample",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.size(50.dp))
        Image(
            painter = painterResource(R.drawable.dragon_copilot),
            contentDescription = "LOGO",
            modifier = Modifier.size(200.dp)
        )
        if (signedIn == false) {
            Spacer(Modifier.size(30.dp))
            Login(modifier = Modifier)
        }
    }
}
