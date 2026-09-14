package com.topdrive.garagebackup

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.topdrive.garagebackup.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var awaitingManualResult = false

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshStatus() } }

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

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
            // Shizuku pas encore disponible : sans conséquence, on réessaiera via refreshStatus().
        }

        binding.btnGrantStorage.setOnClickListener { requestShizuku() }
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

    override fun onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun requestShizuku() {
        when {
            !ShizukuHelper.isShizukuAppInstalled(this) -> {
                Toast.makeText(
                    this,
                    "Installe l'appli Shizuku (voir le README), puis reviens ici.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "market://details?id=${ShizukuHelper.SHIZUKU_PACKAGE_NAME}".toUri()
                        )
                    )
                } catch (_: Exception) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=${ShizukuHelper.SHIZUKU_PACKAGE_NAME}".toUri()
                        )
                    )
                }
            }

            !ShizukuHelper.isBinderAlive() -> {
                Toast.makeText(
                    this,
                    "Ouvre l'appli Shizuku et démarre-la (débogage sans fil), voir le README, puis reviens ici.",
                    Toast.LENGTH_LONG
                ).show()
                val launchIntent = packageManager.getLaunchIntentForPackage(ShizukuHelper.SHIZUKU_PACKAGE_NAME)
                if (launchIntent != null) startActivity(launchIntent)
            }

            !ShizukuHelper.hasPermission() -> ShizukuHelper.requestPermission()

            else -> Toast.makeText(this, "Shizuku est déjà connecté", Toast.LENGTH_SHORT).show()
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
        binding.storageStatus.text = when {
            !ShizukuHelper.isShizukuAppInstalled(this) -> getString(R.string.status_shizuku_not_installed)
            !ShizukuHelper.isBinderAlive() -> getString(R.string.status_shizuku_not_running)
            !ShizukuHelper.hasPermission() -> getString(R.string.status_not_granted)
            else -> getString(R.string.status_granted)
        }

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

        binding.statusText.text = if (prefs.lastBackupSuccess) {
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
