package com.topdrive.garagebackup

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.OutputStream
import java.lang.reflect.Method

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

    // Shizuku.newProcess(String[], String[], String) existe bien dans la
    // librairie mais n'est pas déclaré "public" côté Kotlin : c'est la
    // méthode que l'appli officielle de démonstration de Shizuku utilise
    // elle-même via réflexion pour lancer un process avec les droits shell.
    private val newProcessMethod: Method by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    private fun shizukuNewProcess(cmd: Array<String>): Process {
        @Suppress("UNCHECKED_CAST")
        return newProcessMethod.invoke(null, cmd, null, null) as Process
    }

    /**
     * Exécute `cat <path>` via Shizuku et copie la sortie vers [out].
     * Retourne `null` en cas de succès, sinon un message d'erreur lisible.
     */
    fun catFileTo(path: String, out: OutputStream): String? {
        if (!isBinderAlive()) return "Shizuku n'est pas connecté."
        if (!hasPermission()) return "Permission Shizuku non accordée."

        return try {
            val process = shizukuNewProcess(arrayOf("cat", path))
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
