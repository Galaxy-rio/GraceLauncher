package com.galaxyrio.gracelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val packageManager = context.packageManager

    suspend fun loadApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        resolved.asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                val label = resolveInfo.loadLabel(packageManager)
                    .toString()
                    .trim()
                    .ifBlank { activityInfo.name.substringAfterLast('.') }
                val icon = runCatching {
                    resolveInfo.loadIcon(packageManager)
                        .toBitmap(width = 144, height = 144)
                        .asImageBitmap()
                }.getOrNull()
                LauncherApp(componentName = component, label = label, icon = icon)
            }
            .distinctBy(LauncherApp::key)
            .sortedWith { left, right -> collator.compare(left.label, right.label) }
            .toList()
    }

    fun launch(app: LauncherApp): Boolean = runCatching {
        val intent = Intent.makeMainActivity(app.componentName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        context.startActivity(intent)
    }.isSuccess
}
