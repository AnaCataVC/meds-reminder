package com.medsreminder.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Returns a launcher for the system ringtone picker. Call it with the current URI;
 * [onPicked] receives the chosen URI. Silence is not offered: a null URI means
 * "inherit" (group -> person -> system default), not "no sound".
 */
@Composable
fun rememberRingtonePicker(onPicked: (String) -> Unit): (currentUriString: String?) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        uri?.let { onPicked(it.toString()) }
    }
    return { currentUriString ->
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUriString?.let { Uri.parse(it) })
        }
        launcher.launch(intent)
    }
}

/** Human-readable name of a ringtone URI, or [nullLabel] when none is set. */
fun ringtoneTitle(context: Context, uriString: String?, nullLabel: String): String {
    if (uriString.isNullOrBlank()) return nullLabel
    return runCatching {
        RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context)
    }.getOrNull() ?: "Tono personalizado"
}
