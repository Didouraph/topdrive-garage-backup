package com.topdrive.garagebackup

import android.app.Application

class BackupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ré-active la sauvegarde périodique à chaque démarrage de l'app
        // si elle est activée dans les préférences (idempotent, sans risque).
        val prefs = Prefs(this)
        if (prefs.autoBackupEnabled) {
            BackupScheduler.enable(this)
        }
    }
}
