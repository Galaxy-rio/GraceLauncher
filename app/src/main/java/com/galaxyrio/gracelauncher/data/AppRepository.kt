package com.galaxyrio.gracelauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.graphics.drawable.Drawable
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.galaxyrio.gracelauncher.SettingsActivity
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.IconDominantColor
import com.galaxyrio.gracelauncher.data.icons.IconSnapshotCache
import com.galaxyrio.gracelauncher.data.icons.ProfileIconSource
import com.galaxyrio.gracelauncher.data.icons.renderIcon
import com.galaxyrio.gracelauncher.data.icons.PackIcon
import com.galaxyrio.gracelauncher.data.icons.iconBitmapBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

data class WorkProfile(val user: UserHandle, val serial: Long)

data class AppCatalog(
    val apps: List<LauncherApp>,
    val workProfiles: List<WorkProfile>,
    val complete: Boolean,
)

class AppRepository(private val context: Context, private val iconPacks: IconPackRepository = IconPackRepository(context)) {
    private val packageManager = context.packageManager
    private val launcherApps by lazy { context.getSystemService(LauncherApps::class.java) }
    private val profiles by lazy { LauncherProfiles(context) }
    private data class AppSourceSignature(val configuration: Configuration, val day: LocalDate,
        val label: String, val installedAt: Long, val updatedAt: Long, val version: Long,
        val apkPaths: List<String>, val flags: Int, val iconResource: Int, val revision: Long,
        val profileType: LauncherProfileType)
    private val personalSources = IconSnapshotCache<AppSourceSignature, LauncherApp>(
        maxBytes = 32 * 1024 * 1024, sizeOf = { iconBitmapBytes(it.icon, it.monochromeIcon) })
    private val profileSources = IconSnapshotCache<AppSourceSignature, LauncherApp>()
    private data class PackSignature(val source: Any?,
        val packs: List<Any>, val day: LocalDate)
    private val packedIcons = IconSnapshotCache<PackSignature, Pair<String, PackIcon>>(maxBytes = 8 * 1024 * 1024,
        sizeOf = { iconBitmapBytes(it.second.bitmap, it.second.monochrome) })
    private val packageRevisions = mutableMapOf<Pair<UserHandle, String>, Long>()

    /** Package changes invalidate only that installation's artwork, including the personal profile. */
    fun invalidateApps(user: UserHandle, packages: Collection<String>) = synchronized(packageRevisions) {
        packages.forEach { name ->
            val key = user to name
            packageRevisions[key] = (packageRevisions[key] ?: 0L) + 1
        }
    }

    suspend fun loadApps(iconPackPackage: String?): List<LauncherApp> = loadApps(listOfNotNull(iconPackPackage))

    /** Cheap startup validation; no labels, artwork, icon packs or foreign resources are loaded. */
    internal suspend fun launchableComponents(): Set<ComponentName> = withContext(Dispatchers.IO) {
        launcherApps.getActivityList(null, Process.myUserHandle()).map { it.componentName }.toSet()
    }

    suspend fun profileSnapshot(): LauncherProfileSnapshot = withContext(Dispatchers.IO) {
        val snapshot = profiles.snapshot()
        var complete = snapshot.complete
        val components = mutableMapOf<UserHandle, Set<ComponentName>>()
        for (profile in snapshot.profiles) {
            currentCoroutineContext().ensureActive()
            if (profile.user == Process.myUserHandle() || !profile.isPublic ||
                (profile.type == LauncherProfileType.Work && (profile.quietMode == true || profile.unlocked == false))) continue
            try {
                // Adding a second clone often reuses the same profile. Comparing only users
                // would miss that change on OEMs which omit the package-added callback.
                components[profile.user] = launcherApps.getActivityList(null, profile.user)
                    .map { it.componentName }.toSet()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AppRepository", "Unable to check launcher activities for ${profile.user}", error)
                complete = false
            }
        }
        snapshot.copy(complete = complete, launcherComponents = components)
    }

    suspend fun loadApps(iconPackPackages: List<String> = emptyList()): List<LauncherApp> =
        loadCatalog(profileSnapshot(), iconPackPackages).apps

    /** Like Lawnchair, enumerate launcher activities separately for every visible user. */
    suspend fun loadCatalog(snapshot: LauncherProfileSnapshot, iconPackPackages: List<String>): AppCatalog = withContext(Dispatchers.IO) {
        val apps = mutableListOf<LauncherApp>()
        var complete = snapshot.complete
        for (profile in snapshot.profiles) {
            currentCoroutineContext().ensureActive()
            // Private Space retains its separate unlock/visibility and cache lifecycle.
            if (!profile.isPublic) continue
            if (profile.type == LauncherProfileType.Work &&
                (profile.quietMode == true || profile.unlocked == false)) continue
            try {
                apps += loadProfileApps(profile, emptyList())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Keep the current UI/default favorites intact when the main query fails.
                if (profile.user == Process.myUserHandle()) throw error
                // An unavailable OEM profile must not remove the other users' apps.
                Log.w("AppRepository", "Unable to read launcher apps for ${profile.user}", error)
                complete = false
            }
        }
        AppCatalog(applyIconPacks(apps.distinctBy(LauncherApp::key).sortedWith(LauncherAppOrder), iconPackPackages),
            snapshot.profiles.filter { it.type == LauncherProfileType.Work }.map { WorkProfile(it.user, it.serial) },
            complete)
    }

    suspend fun loadPrivateApps(user: UserHandle, serial: Long, iconPackPackages: List<String>): List<LauncherApp> =
        loadProfileApps(LauncherProfile(user, serial, LauncherProfileType.Private), iconPackPackages)

    private suspend fun loadProfileApps(profile: LauncherProfile, iconPackPackages: List<String>): List<LauncherApp> = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        val configuration = Configuration(context.resources.configuration)
        val day = LocalDate.now()
        val personal = profile.user == Process.myUserHandle()
        val sources = if (personal) personalSources else profileSources
        val englishLabels = EnglishLabelReader(context)
        val packages = mutableMapOf<String, PackageInfo?>()
        val apps = launcherApps.getActivityList(null, profile.user).asSequence()
            .mapNotNull { activity ->
                coroutine.ensureActive()
                val component = activity.componentName
                // Expose only our settings entry, never the launcher/HOME activity.
                if (component.packageName == context.packageName &&
                    component.className != SettingsActivity::class.java.name) return@mapNotNull null
                val label = runCatching { activity.label.toString().trim() }.getOrDefault("")
                    .ifBlank { component.shortClassName }
                val packageInfo = if (personal) packages.getOrPut(component.packageName) {
                    @Suppress("DEPRECATION")
                    runCatching { packageManager.getPackageInfo(component.packageName, 0) }.getOrNull()
                } else null
                val info = activity.applicationInfo
                val revision = synchronized(packageRevisions) { packageRevisions[profile.user to component.packageName] ?: 0L }
                val installedAt = runCatching { activity.firstInstallTime }.getOrDefault(packageInfo?.firstInstallTime ?: 0L)
                val activityInfo = if (Build.VERSION.SDK_INT >= 31) runCatching { activity.activityInfo }.getOrNull() else if (personal) {
                    @Suppress("DEPRECATION")
                    runCatching { packageManager.getActivityInfo(component, 0) }.getOrNull()
                } else null
                val signature = AppSourceSignature(configuration, day, label, installedAt,
                    packageInfo?.lastUpdateTime ?: 0, packageInfo?.longVersionCode ?: 0,
                    listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty(), info.flags,
                    activityInfo?.iconResource ?: info.icon, revision, profile.type)
                val key = if (personal) component.flattenToString() else "profile:${profile.serial}:${component.flattenToString()}"
                sources.get(key, signature) ?: run {
                    val drawable = runCatching { activity.getIcon(0) }.getOrNull()
                    val source = if (personal) null else runCatching { drawable?.let(ProfileIconSource::fromDrawable) }.getOrNull()
                    makeApp(component, label, drawable, info.flags).let { app -> app.copy(
                        // Preserve existing personal favorites, renames and icon overrides.
                        user = profile.user.takeUnless { personal }, userSerial = profile.serial.takeUnless { personal },
                        isPrivateSpace = profile.type == LauncherProfileType.Private,
                        isWorkProfile = profile.type == LauncherProfileType.Work,
                        isCloneProfile = profile.type == LauncherProfileType.Clone,
                        firstInstallTime = installedAt, isLauncherSettings = component.packageName == context.packageName,
                        englishLabel = activityInfo?.let(englishLabels::label), iconSourceRevision = signature,
                        profileIconSource = source, icon = source?.layers()?.original?.asImageBitmap() ?: app.icon)
                    }.also { app ->
                        coroutine.ensureActive()
                        if (app.icon != null) sources.put(key, signature, app)
                    }
                }
            }
            .distinctBy(LauncherApp::key)
            .sortedWith(LauncherAppOrder)
            .toList()
        applyIconPacks(apps, iconPackPackages)
    }

    suspend fun applyIconPacks(apps: List<LauncherApp>, iconPackPackages: List<String>): List<LauncherApp> = withContext(Dispatchers.IO) {
        val packs = normalizeIconPackOrder(iconPackPackages).mapNotNull { iconPacks.load(it) }
        if (packs.isEmpty()) return@withContext apps
        apps.map { app ->
            val signature = PackSignature(app.iconSourceRevision ?: app.profileIconSource?.cacheKey ?: app.icon,
                packs.map { it.cacheKey }, LocalDate.now())
            val (packageName, icon) = packedIcons.get(app.key, signature) ?: run {
                val match = firstMatchingPackIcon(packs) { it.iconFor(app.componentName, null) } ?: return@map app
                (match.first.packageName to match.second).also { packedIcons.put(app.key, signature, it) }
            }
            app.copy(icon = icon.bitmap, monochromeIcon = icon.monochrome, monochromeScale = icon.monochromeScale,
                iconPackPackage = packageName, themeIconPackPackage = packageName)
        }
    }

    private fun makeApp(component: ComponentName, label: String, drawable: Drawable?, flags: Int): LauncherApp {
        // Sample the provider's original drawable on IO, before considering packs.
        // Only the color survives; previews/drags never reload or resample icons.
        val originalColor = runCatching { drawable?.let {
            val sample = renderIcon(it, 48)
            try { IconDominantColor.of(sample) } finally { sample.recycle() }
        } }.getOrNull()
        val icon = runCatching { drawable?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
        val monochrome = if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                (drawable as? AdaptiveIconDrawable)?.monochrome?.toBitmap(144, 144)?.let { bitmap ->
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    bitmap.takeIf { pixels.count { pixel -> pixel ushr 24 > 32 } >= pixels.size / 100 }?.asImageBitmap()
                }
            }.getOrNull()
        } else null
        return LauncherApp(componentName = component, label = label, icon = icon, monochromeIcon = monochrome,
            isSystemApp = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            isAdaptiveIcon = drawable is AdaptiveIconDrawable, originalIconColor = originalColor)
    }

    fun launch(
        app: LauncherApp,
        sourceBounds: Rect? = null,
        options: Bundle? = null,
    ): Boolean = runCatching {
        val user = app.user ?: Process.myUserHandle()
        if (user == Process.myUserHandle()) {
            // Match Launcher3/Lawnchair: current-user entry activities may perform
            // OEM authentication or forwarding before opening the actual app.
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.componentName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .also { it.sourceBounds = sourceBounds }
            context.startActivity(intent, options)
        } else {
            // Never fall back to the personal installation of a cross-profile app.
            context.getSystemService(LauncherApps::class.java).startMainActivity(
                app.componentName, user, sourceBounds, options,
            )
        }
    }.onFailure { error ->
        Log.e("AppRepository", "Unable to launch ${app.componentName} for ${app.user ?: Process.myUserHandle()}", error)
    }.isSuccess
}
