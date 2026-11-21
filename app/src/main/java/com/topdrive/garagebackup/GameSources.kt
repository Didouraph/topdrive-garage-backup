package com.topdrive.garagebackup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Erreur "propre" à afficher telle quelle à l'utilisateur. */
class BackupException(message: String) : Exception(message)

/** Écriture d'un fichier dans le dossier Drive, en remplaçant l'ancienne copie. */
object DriveWriter {

    fun replaceFile(
        context: Context,
        folder: DocumentFile,
        name: String,
        mimeType: String,
        write: (OutputStream) -> Unit
    ) {
        folder.findFile(name)?.delete()
        val newFile = folder.createFile(mimeType, name)
            ?: throw BackupException("impossible de créer $name sur Drive.")
        context.contentResolver.openOutputStream(newFile.uri, "w")?.use { write(it) }
            ?: throw BackupException("impossible d'écrire $name sur Drive.")
    }
}

/** Top Drive : un seul fichier, Garage.dat. */
object TopDriveSource {
    const val PACKAGE = "com.hutchgames.cccg"
    const val FILE_NAME = "Garage.dat"
    const val PATH = "/storage/emulated/0/Android/data/$PACKAGE/files/$FILE_NAME"

    /** @return true si un envoi a eu lieu, false si rien n'avait changé. */
    fun backup(context: Context, prefs: Prefs, folder: DocumentFile, force: Boolean): Boolean {
        // Lecture en mémoire : le fichier ne fait que quelques Mo au maximum.
        val buffer = ByteArrayOutputStream()
        ShizukuHelper.catFileTo(PATH, buffer)?.let { throw BackupException(it) }
        val bytes = buffer.toByteArray()

        // Rien n'a changé (même taille) et la copie Drive existe toujours → on ne renvoie pas.
        if (!force && bytes.size.toLong() == prefs.lastSourceSize && folder.findFile(FILE_NAME) != null) {
            return false
        }

        DriveWriter.replaceFile(context, folder, FILE_NAME, "application/octet-stream") { it.write(bytes) }
        prefs.lastSourceSize = bytes.size.toLong()
        return true
    }
}

/**
 * City Transport Manager : tout le dossier Saves (plusieurs fichiers,
 * éventuellement des sous-dossiers). Il est compressé en un seul
 * CTM_Saves.zip sur Drive, remplacé à chaque envoi (comme Garage.dat).
 */
object CtmSource {
    const val PACKAGE = "com.pedepe.ctm"
    const val DIR = "/storage/emulated/0/Android/data/$PACKAGE/files/Saves"
    const val DRIVE_FILE_NAME = "CTM_Saves.zip"

    private data class Entry(val path: String, val size: Long, val mtimeSec: Long)

    /** Liste les fichiers (taille + date de modif) via `find … -exec stat`. */
    private fun listFiles(): List<Entry> {
        ShizukuHelper.readyError()?.let { throw BackupException(it) }
        val (result, stdout) = try {
            ShizukuHelper.execText(
                arrayOf("find", DIR, "-type", "f", "-exec", "stat", "-c", "%s|%Y|%n", "{}", "+")
            )
        } catch (e: Exception) {
            throw BackupException("erreur Shizuku : ${e.message ?: e.javaClass.simpleName}")
        }
        if (result.exitCode != 0) {
            if (result.stderr.contains("No such file")) {
                throw BackupException(
                    "dossier Saves introuvable (City Transport Manager est-il installé et a-t-il déjà sauvegardé une partie ?)"
                )
            }
            throw BackupException(
                "lecture du dossier Saves impossible (code ${result.exitCode}) : ${result.stderr.ifBlank { "inconnue" }}"
            )
        }
        val entries = stdout.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size < 3) null
                else Entry(parts[2], parts[0].toLongOrNull() ?: 0L, parts[1].toLongOrNull() ?: 0L)
            }
            .sortedBy { it.path }
            .toList()
        if (entries.isEmpty()) throw BackupException("le dossier Saves est vide.")
        return entries
    }

    /** Empreinte de l'état du dossier : change dès qu'un fichier est ajouté, supprimé ou modifié. */
    private fun signature(entries: List<Entry>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (e in entries) md.update("${e.path}|${e.size}|${e.mtimeSec}\n".toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** @return true si un envoi a eu lieu, false si rien n'avait changé. */
    fun backup(context: Context, prefs: Prefs, folder: DocumentFile, force: Boolean): Boolean {
        val entries = listFiles()
        val sig = signature(entries)
        if (!force && sig == prefs.ctmLastSignature && folder.findFile(DRIVE_FILE_NAME) != null) {
            return false
        }

        // Zip construit dans un fichier temporaire (le dossier peut peser
        // plusieurs dizaines de Mo : on évite de tout garder en mémoire).
        val tmp = File(context.cacheDir, "ctm_saves_tmp.zip")
        try {
            ZipOutputStream(FileOutputStream(tmp).buffered()).use { zip ->
                for (e in entries) {
                    val relative = e.path.removePrefix("$DIR/")
                    zip.putNextEntry(ZipEntry(relative).apply { time = e.mtimeSec * 1000 })
                    ShizukuHelper.catFileTo(e.path, zip, allowEmpty = true)
                        ?.let { throw BackupException("$relative : $it") }
                    zip.closeEntry()
                }
            }

            DriveWriter.replaceFile(context, folder, DRIVE_FILE_NAME, "application/zip") { out ->
                tmp.inputStream().use { it.copyTo(out) }
            }
        } finally {
            tmp.delete()
        }

        prefs.ctmLastSignature = sig
        return true
    }
}
