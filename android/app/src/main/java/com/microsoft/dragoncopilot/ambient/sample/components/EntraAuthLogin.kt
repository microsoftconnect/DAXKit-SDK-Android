package com.microsoft.dragoncopilot.ambient.sample.components

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.microsoft.dragoncopilot.ambient.sample.viewmodel.AmbientViewModel

/**
 * Simple sign-in / sign-out button used on the Splash screen.
 *
 * The button is disabled while the auth state is loading (`signedIn == null`).
 * Integrators will typically replace this with their own authentication UI.
 */
@Composable
fun Login(viewModel: AmbientViewModel = viewModel(factory = AmbientViewModel.Factory), modifier: Modifier) {
    val activity = LocalActivity.current!!
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()

    Button(
        enabled = signedIn != null,
        onClick = {
            if (signedIn == true) {
                viewModel.signOut {}
            } else {
                viewModel.signIn(activity)
            }
        },
        modifier = modifier,
    ) {
        Text(if (signedIn == true) "Sign Out" else "Sign In")
    }
}
