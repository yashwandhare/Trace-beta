package com.trace.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trace.app.ui.modelmanager.ModelManagerViewModel

@Composable
fun SearchScopeDialog(
    onDismissed: () -> Unit,
    viewModel: ModelManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var treeGranted by androidx.compose.runtime.remember {
      androidx.compose.runtime.mutableStateOf(viewModel.getDocumentTreeUri().isNotBlank())
    }
    val treeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
      if (uri != null) {
        try {
          context.contentResolver.takePersistableUriPermission(
            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
        } catch (_: Exception) { /* some providers don't support persistable grants */ }
        viewModel.setDocumentTreeUri(uri.toString())
        treeGranted = true
      }
    }

    AlertDialog(
        onDismissRequest = onDismissed,
        confirmButton = {
            TextButton(onClick = onDismissed) {
                Text(text = "OK")
            }
        },
        title = {
            Text(
                text = "Search Scope",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Document folder for PDFs/docs — media permissions can't see these,
                // so the user grants a folder once (persisted) to make them searchable.
                Text(
                    text = "Document Folder (PDFs & docs)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (treeGranted)
                        "Granted — PDFs and documents in the chosen folder are searchable."
                    else
                        "Not set. Fetch can't find PDFs until you grant a folder (e.g. Downloads).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Button(
                        onClick = { treeLauncher.launch(null) },
                    ) {
                        Text(if (treeGranted) "Change folder" else "Grant folder")
                    }
                    if (treeGranted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            viewModel.setDocumentTreeUri("")
                            treeGranted = false
                        }) { Text("Clear") }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Folders to Include",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.searchScopeDownloadsEnabled,
                        onCheckedChange = { viewModel.setSearchScopeDownloadsEnabled(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Screenshots",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.searchScopeScreenshotsEnabled,
                        onCheckedChange = { viewModel.setSearchScopeScreenshotsEnabled(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Documents",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.searchScopeDocumentsEnabled,
                        onCheckedChange = { viewModel.setSearchScopeDocumentsEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Recent Images",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                val count = uiState.searchScopeRecentImagesCount
                val estimateSeconds = (count * 0.5f).toInt()
                Text(
                    text = "Scan the last $count photos",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Estimated search time: ~$estimateSeconds seconds worst case",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = count.toFloat(),
                    onValueChange = { viewModel.setSearchScopeRecentImagesCount(it.toInt()) },
                    valueRange = 10f..50f,
                    steps = 39,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Larger scope means longer search time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}
