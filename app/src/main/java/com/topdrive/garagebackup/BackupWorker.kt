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

/**
 * Lit les sauvegardes des jeux activés (via Shizuku, seul moyen sans root de
 * lire le dossier d'une autre application depuis Android 11) et les copie —
 * en remplaçant l'ancienne copie — dans le dossier Google Drive choisi par
 * l'utilisateur, via le Storage Access Framework :
 *  - Top Drive : Android/data/com.hutchgames.cccg/files/Garage.dat
 *  - City Transport Manager : Android/data/com.pedepe.ctm/files/Saves/ (→ CTM_Saves.zip)
 */
class BackupWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FORCE = "force"
        private const val CHANNEL_ID = "garage_backup_status"
        private const val NOTIF_ID = 42
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        val force = inputData.getBoolean(KEY_FORCE, false)

        if (!prefs.topDriveEnabled && !prefs.ctmEnabled) {
            fail(prefs, "Aucun jeu sélectionné. Ouvre l'application pour en cocher au moins un.")
            return@withContext Result.failure()
        }

        // Dossier Drive choisi (commun aux deux jeux)
        val folderUriString = prefs.driveFolderUri
            ?: run {
                fail(prefs, "Aucun dossier Google Drive choisi. Ouvre l'application pour le configurer.")
                return@withContext Result.failure()
            }
        val folder = DocumentFile.fromTreeUri(applicationContext, Uri.parse(folderUriString))
        if (folder == null || !folder.exists() || !folder.canWrite()) {
            fail(prefs, "Le dossier Google Drive choisi n'est plus accessible. Choisis-le à nouveau.")
            return@withContext Result.failure()
        }

        // Chaque jeu est traité indépendamment : l'échec de l'un n'empêche
        // pas la sauvegarde de l'autre.
        val errors = mutableListOf<String>()
        if (prefs.topDriveEnabled) {
            runSource("Top Drive", errors) {
                TopDriveSource.backup(applicationContext, prefs, folder, force)
            }
        }
        if (prefs.ctmEnabled) {
            runSource("City Transport Manager", errors) {
                CtmSource.backup(applicationContext, prefs, folder, force)
            }
        }

        if (errors.isNotEmpty()) {
            fail(prefs, errors.joinToString("\n"))
            return@withContext Result.failure()
        }

        prefs.lastBackupTimeMillis = System.currentTimeMillis()
        prefs.lastBackupSuccess = true
        prefs.lastError = null
        Result.success()
    }

    private inline fun runSource(label: String, errors: MutableList<String>, block: () -> Boolean) {
        try {
            block()
        } catch (e: BackupException) {
            errors += "$label : ${e.message}"
        } catch (e: Exception) {
            errors += "$label : ${e.message ?: e.javaClass.simpleName}"
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
                "État des sauvegardes",
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
            .setContentTitle("Échec de la sauvegarde")
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
