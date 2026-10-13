package com.daisychain.app.run

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.daisychain.engine.run.ActionFailed
import com.daisychain.engine.spec.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Android does not let an app turn Wi-Fi, Bluetooth, mobile data or airplane mode on or off by itself.
 * The "shell" user can, and the Shizuku app lends that power to apps the user allows: Daisychain asks it to run
 * the same commands as `adb shell`.
 */
object PrivilegedShell {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 4242

    enum class State { NotInstalled, NotRunning, NeedsPermission, Ready }

    fun state(context: Context): State = when {
        runCatching { Shizuku.pingBinder() }.getOrDefault(false) ->
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) State.Ready else State.NeedsPermission
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess -> State.NotRunning
        else -> State.NotInstalled
    }

    fun requestPermission() {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun openShizuku(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Runs [command] with shell rights and returns what it printed. Throws a readable [ActionFailed] when Shizuku is not ready. */
    suspend fun run(context: Context, vararg command: String): String = withContext(Dispatchers.IO) {
        when (state(context)) {
            State.Ready -> Unit
            State.NeedsPermission -> {
                requestPermission()
                throw ActionFailed(t("Allow Daisychain in the Shizuku app, then run again", "Autorisez Daisychain dans l'application Shizuku, puis relancez"))
            }
            State.NotRunning -> throw ActionFailed(t("Start Shizuku first (see Settings → Advanced control)", "Démarrez d'abord Shizuku (voir Réglages → Contrôle avancé)"))
            State.NotInstalled -> {
                runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                throw ActionFailed(t("This block needs the Shizuku app (see Settings → Advanced control)", "Ce bloc a besoin de l'application Shizuku (voir Réglages → Contrôle avancé)"))
            }
        }
        // Shizuku keeps newProcess private since 13.1: it is still the simplest way to run a command as the shell user.
        val process = try {
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            method.invoke(null, command.toList().toTypedArray(), null, null) as Process
        } catch (e: Exception) {
            throw ActionFailed(t("Shizuku refused the command: ${e.cause?.message ?: e.message}", "Shizuku a refusé la commande : ${e.cause?.message ?: e.message}"))
        }
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) throw ActionFailed(t("Command failed: ${error.ifBlank { output }.trim().take(200)}", "La commande a échoué : ${error.ifBlank { output }.trim().take(200)}"))
        output
    }

    /** A command line such as `cmd wifi status`, cut at spaces (no quoting support: pass arguments with [run] when needed). */
    suspend fun runLine(context: Context, line: String): String = run(context, "sh", "-c", line)
}
