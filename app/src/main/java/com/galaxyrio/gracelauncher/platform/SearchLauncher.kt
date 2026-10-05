package com.galaxyrio.gracelauncher.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.galaxyrio.gracelauncher.data.SearchContact

internal object SearchLauncher {
    fun contact(context: Context, contact: SearchContact): Boolean = open(context, Intent(Intent.ACTION_VIEW, contact.uri))

    fun internet(context: Context, query: String): Boolean {
        if (query.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search").buildUpon()
            .appendQueryParameter("q", query.trim()).build()).addCategory(Intent.CATEGORY_BROWSABLE)
        // Resolve a generic HTTPS link first so a search engine's app link cannot
        // replace the default browser. Resolution is local; this URL is never opened.
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
        val browser = context.packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        if (browser != null && browser.exported && browser.packageName != "android" && !browser.name.contains("ResolverActivity")) {
            intent.setPackage(browser.packageName)
        }
        return open(context, intent)
    }

    private fun open(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
