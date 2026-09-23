package com.galaxyrio.gracelauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The install/app-list entry is separate from the HOME activity. Opening Grace from
 * another launcher must not first create MainActivity as an ordinary application task.
 */
class LauncherEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep this implicit (package scoped, not component scoped), with HOME as its
        // only category. Those are the platform's conditions for a genuine HOME task.
        // This opens Grace for preview; it does not change the user's default launcher.
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
        finish()
    }
}
