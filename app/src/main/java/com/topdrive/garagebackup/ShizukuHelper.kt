package com.topdrive.garagebackup

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.lang.reflect.Method
import kotlin.concurrent.thread

/**
 * Depuis Android 11, aucune application tierce ne peut lire le dossier
 * Android/data d'une AUTRE application (Top Drive, City Transport Manager…),
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

    /** `null` si Shizuku est prêt, sinon un message d'erreur lisible. */
    fun readyError(): String? = when {
        !isBinderAlive() -> "Shizuku n'est pas connecté."
        !hasPermission() -> "Permission Shizuku non accordée."
        else -> null
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

    class ShellResult(val exitCode: Int, val bytesCopied: Long, val stderr: String)

    /**
     * Lance [cmd] (tableau d'arguments, sans passer par un shell : pas de
     * problème d'espaces ou de caractères spéciaux dans les chemins) avec
     * les droits shell, et copie sa sortie standard vers [out].
     * stderr est lu dans un thread séparé pour éviter tout blocage.
     */
    fun exec(cmd: Array<String>, out: OutputStream): ShellResult {
        val process = shizukuNewProcess(cmd)
        val stderrBuffer = ByteArrayOutputStream()
        val stderrThread = thread(name = "shizuku-stderr") {
            try {
                process.errorStream.use { it.copyTo(stderrBuffer) }
            } catch (_: Exception) {
            }
        }
        val copied = process.inputStream.use { it.copyTo(out) }
        val exitCode = process.waitFor()
        stderrThread.join(5_000)
        return ShellResult(exitCode, copied, stderrBuffer.toString(Charsets.UTF_8.name()))
    }

    /** Variante qui renvoie stdout sous forme de texte. */
    fun execText(cmd: Array<String>): Pair<ShellResult, String> {
        val buffer = ByteArrayOutputStream()
        val result = exec(cmd, buffer)
        return result to buffer.toString(Charsets.UTF_8.name())
    }

    /**
     * Exécute `cat <path>` via Shizuku et copie la sortie vers [out].
     * Retourne `null` en cas de succès, sinon un message d'erreur lisible.
     * [allowEmpty] : accepter un fichier vide (utile pour les dossiers de
     * sauvegarde qui peuvent contenir des fichiers vides légitimes).
     */
    fun catFileTo(path: String, out: OutputStream, allowEmpty: Boolean = false): String? {
        readyError()?.let { return it }
        val fileName = path.substringAfterLast('/')

        return try {
            val r = exec(arrayOf("cat", path), out)
            when {
                r.exitCode != 0 && r.stderr.contains("No such file") ->
                    "$fileName introuvable sur le téléphone."
                r.exitCode != 0 ->
                    "Erreur Shizuku (code ${r.exitCode}) : ${r.stderr.ifBlank { "inconnue" }}"
                r.bytesCopied == 0L && !allowEmpty ->
                    "$fileName introuvable ou vide."
                else -> null
            }
        } catch (e: Exception) {
            "Erreur Shizuku : ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
