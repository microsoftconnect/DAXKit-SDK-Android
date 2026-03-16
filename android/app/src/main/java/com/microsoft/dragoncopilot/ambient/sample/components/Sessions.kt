package com.microsoft.dragoncopilot.ambient.sample.components

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.microsoft.dragoncopilot.ambient.sample.viewmodel.AmbientViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sessions list screen.
 *
 * Displays all in-memory sessions with their IDs and timestamps. The top bar contains a
 * logout button that closes the [AmbientClient] and navigates back to the login screen.
 * The floating action button creates a new session and navigates to its recordings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: AmbientViewModel = viewModel(factory = AmbientViewModel.Factory),
    modifier: Modifier,
    onOpenSession: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val sessions = viewModel.sessions.collectAsState()
    val activity = LocalActivity.current!!
    val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                actions = {
                    IconButton(onClick = {
                        viewModel.signOut(onComplete = onLogout)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val session = viewModel.createSession()
                onOpenSession(session.sessionId)
            }) {
                Icon(Icons.Default.Add, contentDescription = "New Session")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            items(sessions.value) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onOpenSession(session.sessionId) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = session.sessionId,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = dateFormat.format(Date(session.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${session.recordings.size} recording(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
