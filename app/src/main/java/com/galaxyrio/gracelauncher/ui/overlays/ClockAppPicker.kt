package com.galaxyrio.gracelauncher.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.components.AppIcon

/** A launcher-app picker, not another resolver for the unsupported clock intent. */
@Composable
internal fun ClockAppPicker(
    apps: List<LauncherApp>,
    isLoading: Boolean,
    loadFailed: Boolean,
    onSelect: (LauncherApp) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val ownPackage = LocalContext.current.packageName
    val results = remember(apps, query, ownPackage) {
        val term = query.trim()
        // Don't guess clock apps from English labels or a list of OEM packages.
        // A hidden clock is still a valid explicit choice here.
        apps.filter { app ->
            app.packageName != ownPackage &&
                (app.label.contains(term, ignoreCase = true) ||
                    app.originalLabel.contains(term, ignoreCase = true))
        }
    }
    AlertDialog(
        modifier = Modifier.testTag("clock_app_picker"),
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(40.dp),
        title = { Text(stringResource(R.string.clock_choose_app)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.clock_choose_app_description))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().testTag("clock_app_query"),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 320.dp).testTag("clock_app_options")) {
                    items(results, key = LauncherApp::key) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .testTag("clock_app:${app.key}")
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) { onSelect(app) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AppIcon(app, size = 36.dp)
                            Text(app.label, style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (results.isEmpty() && !isLoading && !loadFailed) item {
                        Text(stringResource(R.string.search_no_results), Modifier.padding(vertical = 12.dp))
                    }
                }
                if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (loadFailed) {
                    Text(stringResource(R.string.clock_apps_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
