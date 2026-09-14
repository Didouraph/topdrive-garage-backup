package com.topdrive.garagebackup

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.topdrive.garagebackup.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var awaitingManualResult = false

    private val manageAllFilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
        }

    private val legacyPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshStatus()
        }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.driveFolderUri = uri.toString()
                refreshStatus()
                Toast.makeText(this, "Dossier Drive enregistré", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.btnGrantStorage.setOnClickListener { requestStorageAccess() }
        binding.btnChooseFolder.setOnClickListener { openFolderPicker() }
        binding.btnBackupNow.setOnClickListener { runBackupNow() }
        binding.btnBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }

        binding.switchAuto.setOnCheckedChangeListener { _, checked ->
            prefs.autoBackupEnabled = checked
            if (checked) BackupScheduler.enable(this) else BackupScheduler.disable(this)
        }

        // Un seul observateur pour toute la durée de vie de l'activité : évite les
        // notifications en double si l'utilisateur appuie plusieurs fois sur "Sauvegarder".
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData("garage_manual_backup")
            .observe(this) { infos ->
                val info = infos?.firstOrNull() ?: return@observe
                if (info.state.isFinished && awaitingManualResult) {
                    awaitingManualResult = false
                    binding.btnBackupNow.isEnabled = true
                    refreshStatus()
                    val msg = if (info.state == WorkInfo.State.SUCCEEDED)
                        "Sauvegarde réussie" else "Échec : ${prefs.lastError ?: "voir l'état ci-dessous"}"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:$packageName".toUri()
            }
            manageAllFilesLauncher.launch(intent)
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Déjà exemptée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runBackupNow() {
        binding.btnBackupNow.isEnabled = false
        binding.statusText.text = "Sauvegarde en cours…"
        awaitingManualResult = true
        BackupScheduler.backupNow(this)
    }

    @SuppressLint("SetTextI18n")
    private fun refreshStatus() {
        val storageOk = StorageAccess.isGranted(this)
        binding.storageStatus.text =
            if (storageOk) getString(R.string.status_granted) else getString(R.string.status_not_granted)

        val folderUri: Uri? = prefs.driveFolderUri?.let { Uri.parse(it) }
        binding.folderStatus.text = if (folderUri != null) {
            "✅ ${folderDisplayName(folderUri)}"
        } else {
            getString(R.string.status_no_folder)
        }

        binding.switchAuto.setOnCheckedChangeListener(null)
        binding.switchAuto.isChecked = prefs.autoBackupEnabled
        binding.switchAuto.setOnCheckedChangeListener { _, checked ->
            prefs.autoBackupEnabled = checked
            if (checked) BackupScheduler.enable(this) else BackupScheduler.disable(this)
        }

        val sourceFile = java.io.File(
            Environment.getExternalStorageDirectory(),
            "Android/data/${BackupWorker.SOURCE_PACKAGE}/files/${BackupWorker.SOURCE_FILE_NAME}"
        )
        val sourceStatus = if (sourceFile.exists()) "Garage.dat trouvé" else "Garage.dat introuvable (lance Top Drive au moins une fois)"

        binding.statusText.text = "$sourceStatus\n" + if (prefs.lastBackupSuccess) {
            "Dernier état : OK"
        } else if (prefs.lastError != null) {
            "Dernier état : erreur — ${prefs.lastError}"
        } else {
            "Dernier état : pas encore de sauvegarde"
        }

        val lastTime = prefs.lastBackupTimeMillis
        val lastTimeText = if (lastTime > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(lastTime))
        } else {
            getString(R.string.last_backup_never)
        }
        binding.lastBackupText.text = getString(R.string.last_backup_label, lastTimeText)
    }

    private fun folderDisplayName(uri: Uri): String {
        return try {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)?.name ?: uri.lastPathSegment ?: "dossier choisi"
        } catch (e: Exception) {
            "dossier choisi"
        }
    }
}
