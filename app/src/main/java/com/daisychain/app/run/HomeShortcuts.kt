package com.daisychain.app.run

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.daisychain.app.RunActivity
import com.daisychain.app.data.db.ShortcutEntity

/** Puts shortcuts on the launcher: pinned on the home screen, and favourites in the long-press menu of the app icon. */
object HomeShortcuts {

    fun runIntent(context: Context, id: Long): Intent =
        Intent(context, RunActivity::class.java)
            .setAction(RunActivity.ACTION_RUN)
            .putExtra(RunActivity.EXTRA_SHORTCUT_ID, id)

    private fun info(context: Context, s: ShortcutEntity): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, "shortcut-${s.id}")
            .setShortLabel(s.name)
            .setLongLabel(s.name)
            .setIcon(IconCompat.createWithAdaptiveBitmap(ShortcutArt.launcherIcon(s.iconKey, s.colorKey)))
            .setIntent(runIntent(context, s.id))
            .build()

    /** Asks the launcher to pin [s] to the home screen (the launcher shows its own confirmation). */
    fun requestPin(context: Context, s: ShortcutEntity): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return ShortcutManagerCompat.requestPinShortcut(context, info(context, s), null)
    }

    /** Shows [favorites] in the app icon's menu and refreshes pinned copies with their current name and icon. */
    fun sync(context: Context, favorites: List<ShortcutEntity>, all: List<ShortcutEntity>) {
        runCatching {
            val max = minOf(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context), 4)
            ShortcutManagerCompat.setDynamicShortcuts(context, favorites.take(max).map { info(context, it) })
            ShortcutManagerCompat.updateShortcuts(context, all.map { info(context, it) })
        }
    }
}
