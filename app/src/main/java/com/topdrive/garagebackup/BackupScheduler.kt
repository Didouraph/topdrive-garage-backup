package com.topdrive.garagebackup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object BackupScheduler {

    private const val UNIQUE_PERIODIC_NAME = "garage_auto_backup"
    private const val UNIQUE_ONE_TIME_NAME = "garage_manual_backup"

    /** Programme la sauvegarde automatique périodique (toutes les 6 heures environ). */
    fun enable(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_NAME)
    }

    /** Déclenche une sauvegarde immédiate (bouton "Sauvegarder maintenant"). */
    fun backupNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(workDataOf(BackupWorker.KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONE_TIME_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
