package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.StoreSettings
import com.example.ui.components.SectionCard
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary

@Composable
fun AuthScreen(
    hasAnyAdmin: Boolean?,
    storeSettings: StoreSettings,
    onCreateFirstAdmin: (
        nom: String,
        identifiant: String,
        motDePasse: String,
        nomMagasin: String,
        onError: (String?, String) -> Unit
    ) -> Unit,
    onLogin: (
        identifiant: String,
        motDePasse: String,
        onError: (String?, String) -> Unit
    ) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (hasAnyAdmin == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SectionCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CobaltPrimary,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (hasAnyAdmin) Icons.Default.Storefront else Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = storeSettings.nomLogiciel,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasAnyAdmin) {
                                    "${storeSettings.nomMagasin} • Connexion"
                                } else {
                                    "Première installation • Compte Administrateur"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!hasAnyAdmin) {
                        FirstAdminSetupForm(
                            initialStoreName = storeSettings.nomMagasin,
                            onSubmit = onCreateFirstAdmin
                        )
                    } else {
                        LoginForm(onLogin = onLogin)
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstAdminSetupForm(
    initialStoreName: String,
    onSubmit: (
        nom: String,
        identifiant: String,
        motDePasse: String,
        nomMagasin: String,
        onError: (String?, String) -> Unit
    ) -> Unit
) {
    var storeName by remember { mutableStateOf(initialStoreName) }
    var nom by remember { mutableStateOf("") }
    var identifiant by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var nomError by remember { mutableStateOf<String?>(null) }
    var idError by remember { mutableStateOf<String?>(null) }
    var pwdError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    Text(
        text = "Aucun compte administrateur n'est encore configuré. Créez le premier compte responsable du magasin.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutlinedTextField(
        value = storeName,
        onValueChange = { storeName = it },
        label = { Text("Nom du magasin *") },
        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_store_name_input")
    )

    OutlinedTextField(
        value = nom,
        onValueChange = {
            nom = it
            nomError = null
        },
        label = { Text("Nom complet de l'administrateur *") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        isError = nomError != null,
        supportingText = nomError?.let { msg -> { Text(msg) } },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_admin_name_input")
    )

    OutlinedTextField(
        value = identifiant,
        onValueChange = {
            identifiant = it
            idError = null
        },
        label = { Text("Identifiant de connexion *") },
        isError = idError != null,
        supportingText = idError?.let { msg -> { Text(msg) } },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_admin_id_input")
    )

    OutlinedTextField(
        value = motDePasse,
        onValueChange = {
            motDePasse = it
            pwdError = null
        },
        label = { Text("Mot de passe (min. 4 caractères) *") },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Afficher ou masquer le mot de passe"
                )
            }
        },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = pwdError != null,
        supportingText = pwdError?.let { msg -> { Text(msg) } },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_admin_password_input")
    )

    OutlinedTextField(
        value = confirmation,
        onValueChange = {
            confirmation = it
            pwdError = null
        },
        label = { Text("Confirmer le mot de passe *") },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_admin_confirm_input")
    )

    if (generalError != null) {
        Text(
            text = generalError!!,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Button(
        onClick = {
            nomError = null
            idError = null
            pwdError = null
            generalError = null
            if (motDePasse != confirmation) {
                pwdError = "Les mots de passe ne correspondent pas."
                return@Button
            }
            onSubmit(nom, identifiant, motDePasse, storeName) { field, msg ->
                when (field) {
                    "nom" -> nomError = msg
                    "identifiant" -> idError = msg
                    "motDePasse" -> pwdError = msg
                    else -> generalError = msg
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("create_first_admin_button")
    ) {
        Text("Créer l'administrateur et démarrer")
    }
}

@Composable
private fun LoginForm(
    onLogin: (
        identifiant: String,
        motDePasse: String,
        onError: (String?, String) -> Unit
    ) -> Unit
) {
    var identifiant by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var idError by remember { mutableStateOf<String?>(null) }
    var pwdError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    val submitAction = {
        idError = null
        pwdError = null
        generalError = null
        onLogin(identifiant, motDePasse) { field, msg ->
            when (field) {
                "identifiant" -> idError = msg
                "motDePasse" -> pwdError = msg
                else -> generalError = msg
            }
        }
    }

    OutlinedTextField(
        value = identifiant,
        onValueChange = {
            identifiant = it
            idError = null
            generalError = null
        },
        label = { Text("Identifiant *") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        isError = idError != null,
        supportingText = idError?.let { msg -> { Text(msg) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login_identifiant_input")
    )

    OutlinedTextField(
        value = motDePasse,
        onValueChange = {
            motDePasse = it
            pwdError = null
            generalError = null
        },
        label = { Text("Mot de passe *") },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Afficher ou masquer le mot de passe"
                )
            }
        },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submitAction() }),
        isError = pwdError != null,
        supportingText = pwdError?.let { msg -> { Text(msg) } },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login_password_input")
    )

    if (generalError != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = generalError!!,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Button(
        onClick = submitAction,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("login_submit_button")
    ) {
        Text("Se connecter")
    }
}
