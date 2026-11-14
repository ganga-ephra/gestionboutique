package com.example.domain.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.ParametreKeys
import com.example.data.local.entity.JournalAuditEntity
import com.example.data.local.entity.ParametreEntity
import com.example.data.local.entity.RoleUtilisateur
import com.example.domain.model.BackupEntry
import com.example.domain.model.ServiceResult
import com.example.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupService(private val context: Context) {

    private val backupsDir: File
        get() = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }

    private fun dbFile(): File = context.getDatabasePath(AppDatabase.DB_NAME)

    suspend fun listBackups(): List<BackupEntry> = withContext(Dispatchers.IO) {
        val files = backupsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".db")
        } ?: return@withContext emptyList()
        files.sortedByDescending { it.lastModified() }.map { file ->
            BackupEntry(
                fileName = file.name,
                filePath = file.absolutePath,
                createdAtUtc = file.lastModified(),
                sizeBytes = file.length(),
                isAutomatic = file.name.startsWith("auto_")
            )
        }
    }

    suspend fun runDailyAutoBackupIfNeeded(zoneId: String) = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.getDatabase(context)
        val today = Formatters.businessDateFromUtc(System.currentTimeMillis(), zoneId)
        val lastAutoDate = db.parametreDao().getValue(ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_DATE)
        if (lastAutoDate == today) return@withContext

        // Only auto-backup once DB exists and has at least one admin
        val adminCount = db.utilisateurDao().countActiveByRole(RoleUtilisateur.ADMINISTRATEUR)
        if (adminCount == 0) return@withContext

        val retention = db.parametreDao().getValue(ParametreKeys.RETENTION_SAUVEGARDES)
            ?.toIntOrNull()?.coerceIn(1, 30) ?: 7

        val now = System.currentTimeMillis()
        try {
            val file = createConsistentBackupCopy(prefix = "auto_${today.replace("-", "")}")
            pruneAutoBackups(retention)
            db.parametreDao().upsertAll(
                listOf(
                    ParametreEntity(ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_DATE, today),
                    ParametreEntity(ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_HEURE_UTC, now.toString()),
                    ParametreEntity(ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_RESULTAT, "Succès (${file.name})")
                )
            )
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = null,
                    dateHeureUtc = now,
                    action = "SAUVEGARDE_AUTOMATIQUE",
                    entite = "Sauvegarde",
                    entiteId = null,
                    details = "Sauvegarde quotidienne réussie : ${file.name}"
                )
            )
        } catch (ex: Exception) {
            // Do not erase previous valid copies on error (Section 6)
            db.parametreDao().upsertAll(
                listOf(
                    ParametreEntity(ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_HEURE_UTC, now.toString()),
                    ParametreEntity(
                        ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_RESULTAT,
                        "Échec : ${ex.message ?: "erreur E/S"}"
                    )
                )
            )
        }
    }

    suspend fun createManualBackup(actorId: Long): ServiceResult<BackupEntry> =
        withContext(Dispatchers.IO) {
            val db = DatabaseProvider.getDatabase(context)
            val actor = db.utilisateurDao().findById(actorId)
            if (actor == null || !actor.actif || actor.role != RoleUtilisateur.ADMINISTRATEUR) {
                return@withContext ServiceResult.Error(
                    "Accès refusé : action réservée à l'administrateur."
                )
            }
            return@withContext try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = createConsistentBackupCopy(prefix = "manuel_$stamp")
                val now = System.currentTimeMillis()
                db.parametreDao().upsert(
                    ParametreEntity(ParametreKeys.DERNIERE_SAUVEGARDE_MANUELLE_UTC, now.toString())
                )
                db.journalAuditDao().insert(
                    JournalAuditEntity(
                        auteurId = actorId,
                        dateHeureUtc = now,
                        action = "SAUVEGARDE_MANUELLE",
                        entite = "Sauvegarde",
                        entiteId = null,
                        details = "Sauvegarde manuelle créée : ${file.name}"
                    )
                )
                ServiceResult.Success(
                    BackupEntry(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        createdAtUtc = file.lastModified(),
                        sizeBytes = file.length(),
                        isAutomatic = false
                    ),
                    "Sauvegarde manuelle créée : ${file.name}"
                )
            } catch (ex: Exception) {
                ServiceResult.Error("Échec de la sauvegarde : ${ex.message ?: "erreur inconnue"}")
            }
        }

    suspend fun exportBackupToUri(
        actorId: Long,
        sourcePath: String,
        targetUri: Uri
    ): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.getDatabase(context)
        val actor = db.utilisateurDao().findById(actorId)
        if (actor == null || !actor.actif || actor.role != RoleUtilisateur.ADMINISTRATEUR) {
            return@withContext ServiceResult.Error(
                "Accès refusé : action réservée à l'administrateur."
            )
        }
        val source = File(sourcePath)
        if (!source.exists()) {
            return@withContext ServiceResult.Error("Fichier de sauvegarde introuvable.")
        }
        return@withContext try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(source).use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext ServiceResult.Error("Impossible d'ouvrir la destination.")
            ServiceResult.Success(Unit, "Copie exportée sur le support externe.")
        } catch (ex: Exception) {
            ServiceResult.Error("Erreur d'export : ${ex.message ?: "erreur E/S"}")
        }
    }

    suspend fun restoreFromLocalFile(
        actorId: Long,
        backupFilePath: String
    ): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        val source = File(backupFilePath)
        if (!source.exists()) {
            return@withContext ServiceResult.Error("Sauvegarde introuvable.")
        }
        restoreInternal(actorId, source)
    }

    suspend fun restoreFromUri(
        actorId: Long,
        sourceUri: Uri
    ): ServiceResult<Unit> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "restore_candidate_${System.currentTimeMillis()}.db")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { out ->
                    input.copyTo(out)
                }
            } ?: return@withContext ServiceResult.Error("Impossible de lire le fichier sélectionné.")
            restoreInternal(actorId, tempFile)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun restoreInternal(actorId: Long, candidateFile: File): ServiceResult<Unit> {
        val db = DatabaseProvider.getDatabase(context)
        val actor = db.utilisateurDao().findById(actorId)
        if (actor == null || !actor.actif || actor.role != RoleUtilisateur.ADMINISTRATEUR) {
            return ServiceResult.Error("Accès refusé : restauration réservée à l'administrateur.")
        }

        // 1. Validate SQLite header, integrity, and schema compatibility before closing active DB
        val validationError = validateBackupFile(candidateFile)
        if (validationError != null) {
            return ServiceResult.Error(validationError)
        }

        // 2. Pre-restore safety backup of current database
        val safetyBackup: File
        try {
            safetyBackup = createConsistentBackupCopy(prefix = "pre_restore_${System.currentTimeMillis()}")
        } catch (ex: Exception) {
            return ServiceResult.Error(
                "Impossible de créer la sauvegarde préalable avant restauration : ${ex.message}"
            )
        }

        val liveFile = dbFile()
        val walFile = File("${liveFile.absolutePath}-wal")
        val shmFile = File("${liveFile.absolutePath}-shm")

        // 3. Close connections, swap files, reopen & verify; rollback on failure
        return try {
            DatabaseProvider.closeDatabase()
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            candidateFile.copyTo(liveFile, overwrite = true)

            val reopened = DatabaseProvider.reopenDatabase(context)
            // Touch database to verify it opens cleanly
            reopened.parametreDao().getLatestSchemaVersion()
            reopened.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = null,
                    dateHeureUtc = System.currentTimeMillis(),
                    action = "RESTAURATION_BASE",
                    entite = "Sauvegarde",
                    entiteId = null,
                    details = "Base restaurée depuis ${candidateFile.name} par ${actor.identifiant}"
                )
            )
            ServiceResult.Success(
                Unit,
                "Base restaurée avec succès. Veuillez vous reconnecter."
            )
        } catch (ex: Exception) {
            // Rollback to previous valid database
            runCatching {
                DatabaseProvider.closeDatabase()
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()
                safetyBackup.copyTo(liveFile, overwrite = true)
                DatabaseProvider.reopenDatabase(context)
            }
            ServiceResult.Error(
                "Restauration échouée (${ex.message ?: "fichier incompatible"}). Base précédente conservée."
            )
        }
    }

    private fun createConsistentBackupCopy(prefix: String): File {
        val db = DatabaseProvider.getDatabase(context)
        val writable = db.openHelper.writableDatabase
        // Flush WAL into main SQLite file inside checkpoint for consistent copy
        writable.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            cursor.moveToFirst()
        }
        val target = File(backupsDir, "${prefix}.db")
        val live = dbFile()
        if (!live.exists()) {
            throw IllegalStateException("Fichier de base introuvable")
        }
        // Use VACUUM INTO when supported (SQLite 3.27+, API 30+), else copy after FULL checkpoint
        val vacuumSucceeded = try {
            if (target.exists()) target.delete()
            val escaped = target.absolutePath.replace("'", "''")
            writable.execSQL("VACUUM INTO '$escaped'")
            target.exists() && target.length() > 0
        } catch (_: Exception) {
            false
        }
        if (!vacuumSucceeded) {
            live.copyTo(target, overwrite = true)
        }
        return target
    }

    private fun pruneAutoBackups(retention: Int) {
        val autoFiles = backupsDir.listFiles { file ->
            file.isFile && file.name.startsWith("auto_") && file.name.endsWith(".db")
        }?.sortedByDescending { it.lastModified() } ?: return
        if (autoFiles.size > retention) {
            autoFiles.drop(retention).forEach { it.delete() }
        }
    }

    fun validateBackupFile(file: File): String? {
        if (!file.exists() || file.length() < 100) {
            return "Copie invalide : fichier vide ou inexistant."
        }
        val header = ByteArray(16)
        try {
            FileInputStream(file).use { input ->
                val read = input.read(header)
                if (read < 16 || String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") {
                    return "Copie invalide : ce fichier n'est pas une base SQLite valide."
                }
            }
        } catch (_: Exception) {
            return "Copie invalide : lecture impossible."
        }

        var sqliteDb: SQLiteDatabase? = null
        return try {
            sqliteDb = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            sqliteDb.rawQuery("PRAGMA integrity_check", null).use { c ->
                if (!c.moveToFirst() || c.getString(0)?.lowercase() != "ok") {
                    return "Copie corrompue : échec du contrôle d'intégrité SQLite."
                }
            }
            val requiredTables = setOf(
                "utilisateur",
                "categorie",
                "produit",
                "vente",
                "ligne_vente",
                "annulation_vente",
                "mouvement_stock",
                "journal_audit",
                "parametre",
                "version_schema"
            )
            val presentTables = mutableSetOf<String>()
            sqliteDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    presentTables.add(c.getString(0))
                }
            }
            if (!presentTables.containsAll(requiredTables)) {
                return "Copie incompatible : schéma de base Gestion Magasin manquant."
            }
            sqliteDb.rawQuery("SELECT MAX(numero) FROM version_schema", null).use { c ->
                if (!c.moveToFirst() || c.isNull(0)) {
                    return "Copie incompatible : version de schéma absente."
                }
                val schemaNum = c.getInt(0)
                if (schemaNum > AppDatabase.SCHEMA_VERSION) {
                    return "Copie incompatible : version de schéma ($schemaNum) plus récente que l'application."
                }
            }
            null
        } catch (ex: Exception) {
            "Copie invalide : ${ex.message ?: "fichier SQLite illisible"}"
        } finally {
            sqliteDb?.close()
        }
    }

    suspend fun savePickedLogo(uri: Uri): ServiceResult<String> = withContext(Dispatchers.IO) {
        try {
            val dest = File(context.filesDir, "store_logo.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out ->
                    input.copyTo(out)
                }
            } ?: return@withContext ServiceResult.Error("Impossible de lire l'image sélectionnée.")
            ServiceResult.Success(dest.absolutePath, "Logo du magasin mis à jour.")
        } catch (ex: Exception) {
            ServiceResult.Error("Erreur lors de l'enregistrement du logo : ${ex.message}")
        }
    }
}
