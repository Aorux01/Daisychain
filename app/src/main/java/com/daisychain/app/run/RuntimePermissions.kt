package com.daisychain.app.run

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.spec.ActionRegistry
import com.daisychain.engine.spec.Requirement

/** Works out which Android permissions a shortcut needs, so they can all be asked before it runs. */
object RuntimePermissions {
    private fun permissionsFor(requirement: Requirement): List<String> = when (requirement) {
        Requirement.Notifications -> if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        Requirement.Location -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        Requirement.Contacts -> listOf(Manifest.permission.READ_CONTACTS)
        Requirement.Calendar -> listOf(Manifest.permission.READ_CALENDAR)
        Requirement.Microphone -> listOf(Manifest.permission.RECORD_AUDIO)
        Requirement.SendSms -> listOf(Manifest.permission.SEND_SMS)
        Requirement.Photos ->
            if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        // The camera screen belongs to the camera app, so Daisychain needs no permission for it.
        Requirement.Camera, Requirement.WriteSettings, Requirement.Accessibility, Requirement.Network, Requirement.Shizuku -> emptyList()
    }

    /** Permissions the shortcut's blocks need and Daisychain does not have yet. */
    fun missing(context: Context, actions: List<ActionInstance>): Array<String> = actions
        .flatMap { ActionRegistry.find(it.identifier)?.requirements.orEmpty() }
        .distinct()
        .flatMap(::permissionsFor)
        .distinct()
        .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        .toTypedArray()
}
