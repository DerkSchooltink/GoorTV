package dev.goor.tv.ui.consent

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag
import dev.goor.tv.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * First-run UGC consent gate (Play UGC policy). User must acknowledge the
 * BYOC + responsible-use terms before any source can be added. Backed by
 * [UserPreferencesRepository.tosAccepted] so the dialog only appears once.
 */
@Composable
fun UgcConsentGate(prefs: UserPreferencesRepository = koinInject()) {
    val accepted by prefs.tosAccepted.collectAsStateWithLifecycle(initialValue = true)
    if (accepted) return
    val scope = rememberCoroutineScope()
    AlertDialog(
        modifier = androidx.compose.ui.Modifier.testTag("ugc_consent_dialog"),
        onDismissRequest = {},
        title = { Text("Before you start") },
        text = {
            Text(
                "GoorTV is a media player. It does not include or curate any " +
                    "content — you supply your own M3U playlists, EPG, and " +
                    "streams.\n\n" +
                    "By continuing you confirm that:\n" +
                    "• you have the right to access the sources you add\n" +
                    "• you will not use GoorTV to stream content you are not " +
                    "authorized to view\n" +
                    "• you can report or remove any source from Settings at " +
                    "any time."
            )
        },
        confirmButton = {
            TextButton(
                modifier = androidx.compose.ui.Modifier.testTag("ugc_consent_accept"),
                onClick = { scope.launch { prefs.setTosAccepted(true) } },
            ) { Text("I agree") }
        },
    )
}
