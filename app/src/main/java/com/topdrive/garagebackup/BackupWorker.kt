package com.topdrive.garagebackup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Lit Android/data/com.hutchgames.cccg/files/Garage.dat (via Shizuku, seul
 * moyen sans root de lire le dossier d'une autre application depuis
 * Android 11) et le copie — en remplaçant l'ancien — dans le dossier Google
 * Drive choisi par l'utilisateur, via le Storage Access Framework.
 */
class BackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        const val SOURCE_PACKAGE = "com.hutchgames.cccg"
        const val SOURCE_FILE_NAME = "Garage.dat"
        const val SOURCE_PATH = "/storage/emulated/0/Android/data/$SOURCE_PACKAGE/files/$SOURCE_FILE_NAME"
        const val KEY_FORCE = "force"
        private const val CHANNEL_ID = "garage_backup_status"
        private const val NOTIF_ID = 42
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        val force = inputData.getBoolean(KEY_FORCE, false)

        try {
            // 1. Lire Garage.dat via Shizuku (en mémoire : le fichier ne fait
            //    que quelques Mo au maximum).
            val buffer = ByteArrayOutputStream()
            val readError = ShizukuHelper.catFileTo(SOURCE_PATH, buffer)
            if (readError != null) {
                fail(prefs, readError)
                return@withContext Result.failure()
            }
            val fileBytes = buffer.toByteArray()

            // 2. Éviter de ré-uploader si rien n'a changé (comparaison par taille,
            //    Shizuku ne donne pas facilement la date de modification).
            if (!force && fileBytes.size.toLong() == prefs.lastSourceSize && prefs.lastBackupSuccess) {
                return@withContext Result.success()
            }

            // 3. Dossier Drive choisi
            val folderUriString = prefs.driveFolderUri
                ?: run {
                    fail(prefs, "Aucun dossier Google Drive choisi. Ouvre l'application pour le configurer.")
                    return@withContext Result.failure()
                }
            val folderUri = Uri.parse(folderUriString)
            val folder = DocumentFile.fromTreeUri(applicationContext, folderUri)
            if (folder == null || !folder.exists() || !folder.canWrite()) {
                fail(prefs, "Le dossier Google Drive choisi n'est plus accessible. Choisis-le à nouveau.")
                return@withContext Result.failure()
            }

            // 4. Supprimer l'ancienne copie puis en créer une nouvelle (remplacement complet)
            folder.findFile(SOURCE_FILE_NAME)?.delete()
            val newFile = folder.createFile("application/octet-stream", SOURCE_FILE_NAME)
                ?: run {
                    fail(prefs, "Impossible de créer le fichier sur Drive.")
                    return@withContext Result.failure()
                }

            applicationContext.contentResolver.openOutputStream(newFile.uri, "w")?.use { out ->
                out.write(fileBytes)
            } ?: run {
                fail(prefs, "Impossible d'écrire sur Drive.")
                return@withContext Result.failure()
            }

            // 5. Succès : mémoriser l'état
            prefs.lastBackupTimeMillis = System.currentTimeMillis()
            prefs.lastBackupSuccess = true
            prefs.lastError = null
            prefs.lastSourceSize = fileBytes.size.toLong()

            Result.success()
        } catch (e: Exception) {
            fail(prefs, e.message ?: "Erreur inconnue")
            Result.retry()
        }
    }

    private fun fail(prefs: Prefs, message: String) {
        prefs.lastBackupTimeMillis = System.currentTimeMillis()
        prefs.lastBackupSuccess = false
        prefs.lastError = message
        notifyError(message)
    }

    private fun notifyError(message: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sauvegarde Garage.dat",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Échec de la sauvegarde Garage.dat")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // Permission notifications refusée : on ignore silencieusement.
        }
    }
}
