package com.topdrive.garagebackup

import android.content.Context

/**
 * Petit wrapper autour de SharedPreferences pour stocker :
 *  - l'URI du dossier Google Drive choisi par l'utilisateur (arbre SAF)
 *  - si la sauvegarde automatique est activée
 *  - la date/état de la dernière sauvegarde
 *  - taille/date de modification du dernier fichier sauvegardé (pour éviter
 *    de ré-uploader si Garage.dat n'a pas changé)
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("garage_backup_prefs", Context.MODE_PRIVATE)

    var driveFolderUri: String?
        get() = sp.getString(KEY_FOLDER_URI, null)
        set(value) = sp.edit().putString(KEY_FOLDER_URI, value).apply()

    var autoBackupEnabled: Boolean
        get() = sp.getBoolean(KEY_AUTO_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_ENABLED, value).apply()

    var lastBackupTimeMillis: Long
        get() = sp.getLong(KEY_LAST_TIME, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_TIME, value).apply()

    var lastBackupSuccess: Boolean
        get() = sp.getBoolean(KEY_LAST_SUCCESS, false)
        set(value) = sp.edit().putBoolean(KEY_LAST_SUCCESS, value).apply()

    var lastError: String?
        get() = sp.getString(KEY_LAST_ERROR, null)
        set(value) = sp.edit().putString(KEY_LAST_ERROR, value).apply()

    var lastSourceMtime: Long
        get() = sp.getLong(KEY_SRC_MTIME, -1L)
        set(value) = sp.edit().putLong(KEY_SRC_MTIME, value).apply()

    var lastSourceSize: Long
        get() = sp.getLong(KEY_SRC_SIZE, -1L)
        set(value) = sp.edit().putLong(KEY_SRC_SIZE, value).apply()

    companion object {
        private const val KEY_FOLDER_URI = "drive_folder_uri"
        private const val KEY_AUTO_ENABLED = "auto_backup_enabled"
        private const val KEY_LAST_TIME = "last_backup_time"
        private const val KEY_LAST_SUCCESS = "last_backup_success"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_SRC_MTIME = "last_source_mtime"
        private const val KEY_SRC_SIZE = "last_source_size"
    }
}
