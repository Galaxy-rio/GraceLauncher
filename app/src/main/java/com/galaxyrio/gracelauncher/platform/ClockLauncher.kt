package com.galaxyrio.gracelauncher.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log

internal enum class ClockLaunchResult { Opened, NoHandler, Failed }

internal object ClockLauncher {
    fun open(context: Context): ClockLaunchResult = try {
        // Let Android honor its default app / resolver. A visibility-filtered
        // resolveActivity query must not prevent an otherwise valid launch.
        context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        ClockLaunchResult.Opened
    } catch (error: ActivityNotFoundException) {
        Log.i("ClockLauncher", "No activity handles ACTION_SHOW_ALARMS", error)
        ClockLaunchResult.NoHandler
    } catch (error: Exception) {
        // Permission/OEM failures are not evidence that no clock is installed.
        Log.e("ClockLauncher", "Unable to open ACTION_SHOW_ALARMS", error)
        ClockLaunchResult.Failed
    }
}
