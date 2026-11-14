package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.entity.JournalAuditAvecAuteur
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.UtilisateurEntity
import com.example.domain.model.BackupEntry
import com.example.domain.model.StoreSettings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.SectionCard
import com.example.ui.theme.AvailableBg
import com.example.ui.theme.AvailableText
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RuptureBg
import com.example.ui.theme.RuptureText
import com.example.util.Formatters
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    currentUser: UtilisateurEntity,
    settings: StoreSettings,
    users: List<UtilisateurEntity>,
    backups: List<BackupEntry>,
    auditLogs: List<JournalAuditAvecAuteur>,
    onCreateUser: (String, String, String, RoleUtilisateur, (Boolean, String?, String) -> Unit) -> Unit,
    onSetUserActive: (Long, Boolean) -> Unit,
    onResetUserPassword: (Long, String, (Boolean, String) -> Unit) -> Unit,
    onUpdateStoreSettings: (String, String, String, String) -> Unit,
    onPickLogoUri: (Uri) -> Unit,
    onRemoveLogo: () -> Unit,
    onCreateManualBackup: () -> Unit,
    onExportBackup: (String, Uri) -> Unit,
    onRestoreLocalBackup: (String) -> Unit,
    onRestoreExternalBackup: (Uri) -> Unit
) {
    if (currentUser.role != RoleUtilisateur.ADMINISTRATEUR) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            EmptyStatePanel(
                icon = Icons.Default.Lock,
                title = "Accès réservé à l'administrateur",
                subtitle = "La gestion des comptes, paramètres magasin et sauvegardes nécessite les droits Administrateur."
            )
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Comptes (${users.size})") },
                icon = { Icon(Icons.Default.People, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_users")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Magasin & Logo") },
                icon = { Icon(Icons.Default.Store, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_store")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Sauvegardes") },
                icon = { Icon(Icons.Default.Backup, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_backups")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Journal d'audit") },
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_audit")
            )
        }

        when (selectedTab) {
            0 -> UsersAdminTab(
                users = users,
                onCreateUser = onCreateUser,
                onSetUserActive = onSetUserActive,
                onResetUserPassword = onResetUserPassword
            )
            1 -> StoreSettingsTab(
                settings = settings,
                onUpdateStoreSettings = onUpdateStoreSettings,
                onPickLogoUri = onPickLogoUri,
                onRemoveLogo = onRemoveLogo
            )
            2 -> BackupsAdminTab(
                settings = settings,
                backups = backups,
                onCreateManualBackup = onCreateManualBackup,
                onExportBackup = onExportBackup,
                onRestoreLocalBackup = onRestoreLocalBackup,
                onRestoreExternalBackup = onRestoreExternalBackup
            )
            3 -> AuditLogTab(
                settings = settings,
                auditLogs = auditLogs
            )
        }
    }
}

@Composable
private fun UsersAdminTab(
    users: List<UtilisateurEntity>,
    onCreateUser: (String, String, String, RoleUtilisateur, (Boolean, String?, String) -> Unit) -> Unit,
    onSetUserActive: (Long, Boolean) -> Unit,
    onResetUserPassword: (Long, String, (Boolean, String) -> Unit) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<UtilisateurEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Comptes utilisateurs", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("admin_create_user_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nouveau compte")
                }
            }
        }

        items(users, key = { it.id }) { user ->
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(user.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "@${user.identifiant} • ${user.role.libelle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = if (user.actif) AvailableBg else RuptureBg,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = if (user.actif) "Actif" else "Désactivé",
                                color = if (user.actif) AvailableText else RuptureText,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { resetTarget = user }) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mot de passe")
                        }
                        Switch(
                            checked = user.actif,
                            onCheckedChange = { onSetUserActive(user.id, it) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateUserDialog(
            onDismiss = { showCreateDialog = false },
            onSubmit = { nom, id, pwd, role, onErr ->
                onCreateUser(nom, id, pwd, role) { ok, field, msg ->
                    if (ok) showCreateDialog = false else onErr(field, msg)
                }
            }
        )
    }

    resetTarget?.let { target ->
        var pwd by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            title = { Text("Mot de passe — ${target.nom}") },
            text = {
                OutlinedTextField(
                    value = pwd,
                    onValueChange = {
                        pwd = it
                        err = null
                    },
                    label = { Text("Nouveau mot de passe *") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = err != null,
                    supportingText = err?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onResetUserPassword(target.id, pwd) { ok, msg ->
                        if (ok) resetTarget = null else err = msg
                    }
                }) {
                    Text("Mettre à jour")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetTarget = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, RoleUtilisateur, (String?, String) -> Unit) -> Unit
) {
    var nom by remember { mutableStateOf("") }
    var identifiant by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(RoleUtilisateur.VENDEUR) }
    var nomErr by remember { mutableStateOf<String?>(null) }
    var idErr by remember { mutableStateOf<String?>(null) }
    var pwdErr by remember { mutableStateOf<String?>(null) }
    var generalErr by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Nouveau compte utilisateur", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it; nomErr = null },
                    label = { Text("Nom complet *") },
                    isError = nomErr != null,
                    supportingText = nomErr?.let { m -> { Text(m) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_user_name_input")
                )

                OutlinedTextField(
                    value = identifiant,
                    onValueChange = { identifiant = it; idErr = null },
                    label = { Text("Identifiant *") },
                    isError = idErr != null,
                    supportingText = idErr?.let { m -> { Text(m) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_user_id_input")
                )

                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it; pwdErr = null },
                    label = { Text("Mot de passe (min. 4 caractères) *") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = pwdErr != null,
                    supportingText = pwdErr?.let { m -> { Text(m) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_user_pwd_input")
                )

                Text("Rôle *", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoleUtilisateur.entries.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.libelle) }
                        )
                    }
                }

                if (generalErr != null) {
                    Text(generalErr!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = {
                            onSubmit(nom, identifiant, pwd, role) { field, msg ->
                                when (field) {
                                    "nom" -> nomErr = msg
                                    "identifiant" -> idErr = msg
                                    "motDePasse" -> pwdErr = msg
                                    else -> generalErr = msg
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_create_user_button")
                    ) {
                        Text("Créer")
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreSettingsTab(
    settings: StoreSettings,
    onUpdateStoreSettings: (String, String, String, String) -> Unit,
    onPickLogoUri: (Uri) -> Unit,
    onRemoveLogo: () -> Unit
) {
    var nomLogiciel by remember(settings.nomLogiciel) { mutableStateOf(settings.nomLogiciel) }
    var nomMagasin by remember(settings.nomMagasin) { mutableStateOf(settings.nomMagasin) }
    var coordonnees by remember(settings.coordonneesMagasin) { mutableStateOf(settings.coordonneesMagasin) }
    var fuseau by remember(settings.fuseauHoraire) { mutableStateOf(settings.fuseauHoraire) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickLogoUri(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Identité du magasin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = nomLogiciel,
                    onValueChange = { nomLogiciel = it },
                    label = { Text("Nom commercial du logiciel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nomMagasin,
                    onValueChange = { nomMagasin = it },
                    label = { Text("Nom du magasin *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coordonnees,
                    onValueChange = { coordonnees = it },
                    label = { Text("Coordonnées (adresse, téléphone)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fuseau,
                    onValueChange = { fuseau = it },
                    label = { Text("Fuseau horaire (ex: Africa/Brazzaville)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Devise active : ${settings.devise} (montants entiers)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { onUpdateStoreSettings(nomLogiciel, nomMagasin, coordonnees, fuseau) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enregistrer les paramètres")
                }
            }
        }

        SectionCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Logo du magasin (optionnel)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (settings.logoUri.isNotBlank() && File(settings.logoUri).exists()) {
                    AsyncImage(
                        model = File(settings.logoUri),
                        contentDescription = "Logo du magasin",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        logoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choisir un logo")
                    }
                    if (settings.logoUri.isNotBlank()) {
                        TextButton(onClick = onRemoveLogo) {
                            Text("Retirer le logo", color = RuptureText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupsAdminTab(
    settings: StoreSettings,
    backups: List<BackupEntry>,
    onCreateManualBackup: () -> Unit,
    onExportBackup: (String, Uri) -> Unit,
    onRestoreLocalBackup: (String) -> Unit,
    onRestoreExternalBackup: (Uri) -> Unit
) {
    var pendingExportPath by remember { mutableStateOf<String?>(null) }
    var confirmLocalRestore by remember { mutableStateOf<BackupEntry?>(null) }
    var confirmExternalUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val path = pendingExportPath
        pendingExportPath = null
        if (uri != null && path != null) {
            onExportBackup(path, uri)
        }
    }

    val restoreFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            confirmExternalUri = uri
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sauvegarde et restauration SQLite", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AvailableText, modifier = Modifier.size(18.dp))
                        val autoText = if (settings.derniereSauvegardeAutoHeureUtc != null) {
                            "Dernière sauvegarde auto : ${Formatters.formatDateTimeUtc(settings.derniereSauvegardeAutoHeureUtc, settings.fuseauHoraire)} — ${settings.derniereSauvegardeAutoResultat}"
                        } else {
                            "Sauvegarde automatique quotidienne active (${settings.retentionSauvegardes} dernières conservées)."
                        }
                        Text(autoText, style = MaterialTheme.typography.bodySmall)
                    }
                    if (settings.derniereSauvegardeManuelleUtc != null) {
                        Text(
                            "Dernière sauvegarde manuelle : ${Formatters.formatDateTimeUtc(settings.derniereSauvegardeManuelleUtc, settings.fuseauHoraire)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onCreateManualBackup,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_manual_backup_button")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sauvegarder maintenant")
                        }
                        OutlinedButton(
                            onClick = { restoreFilePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_external_backup_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restaurer un fichier")
                        }
                    }
                }
            }
        }

        item {
            Text("Copies locales (${backups.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (backups.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Default.Backup,
                    title = "Aucune sauvegarde locale",
                    subtitle = "Créez une sauvegarde manuelle pour sécuriser vos données et l'exporter sur support externe."
                )
            }
        } else {
            items(backups, key = { it.filePath }) { entry ->
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.fileName, fontFamily = JetBrainsMono, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${Formatters.formatDateTimeUtc(entry.createdAtUtc, settings.fuseauHoraire)} • ${entry.sizeBytes / 1024} Ko • ${if (entry.isAutomatic) "Automatique" else "Manuelle"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    pendingExportPath = entry.filePath
                                    exportLauncher.launch(entry.fileName)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Exporter")
                            }
                            OutlinedButton(
                                onClick = { confirmLocalRestore = entry },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RuptureText),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restaurer")
                            }
                        }
                    }
                }
            }
        }
    }

    confirmLocalRestore?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmLocalRestore = null },
            title = { Text("Confirmer la restauration") },
            text = {
                Text(
                    "Remplacer la base actuelle par '${entry.fileName}' (${Formatters.formatDateTimeUtc(entry.createdAtUtc, settings.fuseauHoraire)}) ?\n\nUne sauvegarde préalable automatique sera créée avant le remplacement, et vous serez ramené à l'écran de connexion."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmLocalRestore = null
                        onRestoreLocalBackup(entry.filePath)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RuptureText)
                ) {
                    Text("Restaurer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLocalRestore = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    confirmExternalUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmExternalUri = null },
            title = { Text("Restaurer un fichier externe") },
            text = {
                Text(
                    "L'intégrité SQLite et la compatibilité du schéma seront vérifiées avant activation. En cas de fichier invalide, la base active restera intacte. Continuer ?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmExternalUri = null
                        onRestoreExternalBackup(uri)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RuptureText)
                ) {
                    Text("Vérifier et restaurer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExternalUri = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun AuditLogTab(
    settings: StoreSettings,
    auditLogs: List<JournalAuditAvecAuteur>
) {
    if (auditLogs.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStatePanel(
                icon = Icons.Default.History,
                title = "Journal d'audit vide",
                subtitle = "Les connexions, créations, modifications de stock, ventes, annulations et restaurations sont tracées ici."
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(auditLogs, key = { it.id }) { log ->
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.action, fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            Formatters.formatDateTimeUtc(log.dateHeureUtc, settings.fuseauHoraire),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(log.details, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "Auteur : ${log.auteurNom ?: "Système"} • Entité : ${log.entite}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
