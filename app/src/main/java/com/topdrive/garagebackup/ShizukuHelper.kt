package com.topdrive.garagebackup

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.OutputStream

/**
 * Depuis Android 11, aucune application tierce ne peut lire le dossier
 * Android/data d'une AUTRE application (ici com.hutchgames.cccg, Top Drive),
 * même avec la permission "Autoriser l'accès à tous les fichiers" : c'est une
 * protection du système, pas un simple problème de permission.
 *
 * Shizuku contourne cette limite sans root : une fois activé (via le
 * débogage sans fil d'Android), il permet à notre appli d'exécuter une
 * commande shell avec les mêmes droits qu'adb, qui lui garde un accès en
 * lecture à ces dossiers.
 */
object ShizukuHelper {

    const val PERMISSION_REQUEST_CODE = 5901
    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean {
        if (!isBinderAlive()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (isBinderAlive() && !hasPermission()) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (_: Throwable) {
            // Shizuku non disponible : on ignore, l'UI affichera le bon état.
        }
    }

    fun isShizukuAppInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Exécute `cat <path>` via Shizuku et copie la sortie vers [out].
     * Retourne `null` en cas de succès, sinon un message d'erreur lisible.
     */
    fun catFileTo(path: String, out: OutputStream): String? {
        if (!isBinderAlive()) return "Shizuku n'est pas connecté."
        if (!hasPermission()) return "Permission Shizuku non accordée."

        return try {
            val process = Shizuku.newProcess(arrayOf("cat", path), null, null)
            val copied = process.inputStream.use { it.copyTo(out) }
            val stderrText = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            when {
                exitCode != 0 && stderrText.contains("No such file") ->
                    "Garage.dat introuvable sur le téléphone."
                exitCode != 0 ->
                    "Erreur Shizuku (code $exitCode) : ${stderrText.ifBlank { "inconnue" }}"
                copied == 0L ->
                    "Garage.dat introuvable ou vide."
                else -> null
            }
        } catch (e: Exception) {
            "Erreur Shizuku : ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
