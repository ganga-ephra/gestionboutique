package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.MouvementAvecDetails
import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.UtilisateurEntity
import com.example.domain.model.StoreSettings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.SectionCard
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.AvailableText
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RuptureBg
import com.example.ui.theme.RuptureBorder
import com.example.ui.theme.RuptureText
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksScreen(
    currentUser: UtilisateurEntity,
    settings: StoreSettings,
    products: List<ProduitAvecStock>,
    movements: List<MouvementAvecDetails>,
    initialStatusFilter: StatutDisponibilite? = null,
    onConsumeInitialFilter: () -> Unit,
    onRestock: (Long, Int, String, (Boolean, String?, String) -> Unit) -> Unit,
    onCorrectInventory: (Long, Int, String, (Boolean, String?, String) -> Unit) -> Unit
) {
    val isAdmin = currentUser.role == RoleUtilisateur.ADMINISTRATEUR

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<StatutDisponibilite?>(initialStatusFilter) }

    LaunchedEffect(initialStatusFilter) {
        if (initialStatusFilter != null) {
            selectedTab = 0
            statusFilter = initialStatusFilter
            onConsumeInitialFilter()
        }
    }

    var restockTarget by remember { mutableStateOf<ProduitAvecStock?>(null) }
    var correctionTarget by remember { mutableStateOf<ProduitAvecStock?>(null) }

    val filteredProducts = remember(products, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase()
        products.filter { p ->
            val matchesQuery = q.isEmpty() ||
                p.nom.lowercase().contains(q) ||
                p.reference.lowercase().contains(q)
            val matchesStatus = when (statusFilter) {
                null -> p.actif
                else -> p.statut == statusFilter
            }
            matchesQuery && matchesStatus
        }.sortedWith(compareBy<ProduitAvecStock> { it.stockDisponible }.thenBy { it.nom.lowercase() })
    }

    val filteredMovements = remember(movements, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) movements else movements.filter { m ->
            m.produitNom.lowercase().contains(q) ||
                m.produitReference.lowercase().contains(q) ||
                m.motif.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Disponibilités (${products.count { it.actif }})") },
                icon = { Icon(Icons.Default.Inventory, contentDescription = null) },
                modifier = Modifier.testTag("stocks_tab_availability")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Mouvements (${movements.size})") },
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                modifier = Modifier.testTag("stocks_tab_movements")
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (selectedTab == 0) "Rechercher un produit par référence ou nom..."
                        else "Filtrer les mouvements par produit ou motif..."
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stocks_search_input")
            )

            if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("Actifs (${products.count { it.actif }})") }
                    )
                    FilterChip(
                        selected = statusFilter == StatutDisponibilite.EN_RUPTURE,
                        onClick = {
                            statusFilter = if (statusFilter == StatutDisponibilite.EN_RUPTURE) null
                            else StatutDisponibilite.EN_RUPTURE
                        },
                        label = {
                            Text("Ruptures (${products.count { it.actif && it.statut == StatutDisponibilite.EN_RUPTURE }})")
                        },
                        modifier = Modifier.testTag("filter_ruptures_chip")
                    )
                    FilterChip(
                        selected = statusFilter == StatutDisponibilite.STOCK_FAIBLE,
                        onClick = {
                            statusFilter = if (statusFilter == StatutDisponibilite.STOCK_FAIBLE) null
                            else StatutDisponibilite.STOCK_FAIBLE
                        },
                        label = {
                            Text("Stock faible (${products.count { it.actif && it.statut == StatutDisponibilite.STOCK_FAIBLE }})")
                        }
                    )
                    FilterChip(
                        selected = statusFilter == StatutDisponibilite.DISPONIBLE,
                        onClick = {
                            statusFilter = if (statusFilter == StatutDisponibilite.DISPONIBLE) null
                            else StatutDisponibilite.DISPONIBLE
                        },
                        label = { Text("Disponible") }
                    )
                    FilterChip(
                        selected = statusFilter == StatutDisponibilite.ARCHIVE,
                        onClick = {
                            statusFilter = if (statusFilter == StatutDisponibilite.ARCHIVE) null
                            else StatutDisponibilite.ARCHIVE
                        },
                        label = { Text("Archivés") }
                    )
                }

                if (filteredProducts.isEmpty()) {
                    EmptyStatePanel(
                        icon = Icons.Default.Inventory,
                        title = "Aucun produit dans ce filtre",
                        subtitle = "Modifiez le filtre de disponibilité ou la recherche pour afficher d'autres articles."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            val isRupture = product.statut == StatutDisponibilite.EN_RUPTURE
                            SectionCard(
                                containerColor = if (isRupture) RuptureBg else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isRupture) RuptureBorder else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${product.reference} • ${product.nom}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isRupture) RuptureText else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Seuil d'alerte : ${product.seuilAlerte} • Prix : ${Formatters.formatFcfa(product.prixVente)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isRupture) RuptureText.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${product.stockDisponible} u.",
                                                fontFamily = JetBrainsMono,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isRupture) RuptureText else MaterialTheme.colorScheme.onSurface
                                            )
                                            StockStatusBadge(product.statut)
                                        }
                                    }

                                    if (isAdmin) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = { restockTarget = product },
                                                enabled = product.actif,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("restock_btn_${product.reference}")
                                            ) {
                                                Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Réapprovisionner")
                                            }
                                            OutlinedButton(
                                                onClick = { correctionTarget = product },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("correct_stock_btn_${product.reference}")
                                            ) {
                                                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Inventaire")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (filteredMovements.isEmpty()) {
                    EmptyStatePanel(
                        icon = Icons.Default.History,
                        title = "Aucun mouvement enregistré",
                        subtitle = "Les entrées initiales, réapprovisionnements, ventes, annulations et corrections apparaîtront ici."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredMovements, key = { it.id }) { m ->
                            val isPositive = m.quantiteSignee >= 0
                            SectionCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = "${m.type.libelle} • ${m.produitReference} (${m.produitNom})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = m.motif,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val auditExtra = if (m.ancienneQuantite != null && m.nouvelleQuantite != null) {
                                            " • Stock ${m.ancienneQuantite} → ${m.nouvelleQuantite}"
                                        } else ""
                                        Text(
                                            text = "${Formatters.formatDateTimeUtc(m.dateHeureUtc, settings.fuseauHoraire)} • ${m.auteurNom}$auditExtra",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (isPositive) "+${m.quantiteSignee}" else "${m.quantiteSignee}",
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPositive) AvailableText else RuptureText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    restockTarget?.let { product ->
        RestockDialog(
            product = product,
            onDismiss = { restockTarget = null },
            onConfirm = { qty, motif, onErr ->
                onRestock(product.id, qty, motif) { ok, field, msg ->
                    if (ok) restockTarget = null else onErr(field, msg)
                }
            }
        )
    }

    correctionTarget?.let { product ->
        InventoryCorrectionDialog(
            product = product,
            onDismiss = { correctionTarget = null },
            onConfirm = { counted, motif, onErr ->
                onCorrectInventory(product.id, counted, motif) { ok, field, msg ->
                    if (ok) correctionTarget = null else onErr(field, msg)
                }
            }
        )
    }
}

@Composable
private fun RestockDialog(
    product: ProduitAvecStock,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, (String?, String) -> Unit) -> Unit
) {
    var qtyText by remember { mutableStateOf("") }
    var motif by remember { mutableStateOf("") }
    var qtyError by remember { mutableStateOf<String?>(null) }
    var motifError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

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
                Text("Réapprovisionnement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${product.reference} — ${product.nom} (stock actuel : ${product.stockDisponible})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = {
                        qtyText = it.filter { ch -> ch.isDigit() }
                        qtyError = null
                    },
                    label = { Text("Quantité ajoutée *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = qtyError != null,
                    supportingText = qtyError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restock_qty_input")
                )

                OutlinedTextField(
                    value = motif,
                    onValueChange = {
                        motif = it
                        motifError = null
                    },
                    label = { Text("Motif ou référence bon d'entrée *") },
                    isError = motifError != null,
                    supportingText = motifError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restock_motif_input")
                )

                if (generalError != null) {
                    Text(generalError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = {
                            val qty = qtyText.toIntOrNull()
                            if (qty == null || qty <= 0) {
                                qtyError = "Saisissez une quantité entière positive."
                                return@Button
                            }
                            onConfirm(qty, motif) { field, msg ->
                                when (field) {
                                    "quantite" -> qtyError = msg
                                    "motif" -> motifError = msg
                                    else -> generalError = msg
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_restock_button")
                    ) {
                        Text("Valider l'entrée")
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryCorrectionDialog(
    product: ProduitAvecStock,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, (String?, String) -> Unit) -> Unit
) {
    var countedText by remember { mutableStateOf(product.stockDisponible.toString()) }
    var motif by remember { mutableStateOf("") }
    var countedError by remember { mutableStateOf<String?>(null) }
    var motifError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    val countedVal = countedText.toIntOrNull()
    val diff = countedVal?.let { it - product.stockDisponible }

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
                Text("Correction d'inventaire", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "${product.reference} — ${product.nom}\nStock théorique actuel : ${product.stockDisponible}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = countedText,
                    onValueChange = {
                        countedText = it.filter { ch -> ch.isDigit() }
                        countedError = null
                    },
                    label = { Text("Quantité comptée en rayon *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = countedError != null,
                    supportingText = countedError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("correction_counted_input")
                )

                if (diff != null) {
                    val diffStr = if (diff > 0) "+$diff" else "$diff"
                    Text(
                        text = "Écart calculé : $diffStr unité(s)",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (diff == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = motif,
                    onValueChange = {
                        motif = it
                        motifError = null
                    },
                    label = { Text("Justification / motif obligatoire *") },
                    isError = motifError != null,
                    supportingText = motifError?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("correction_motif_input")
                )

                if (generalError != null) {
                    Text(generalError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = {
                            val counted = countedText.toIntOrNull()
                            if (counted == null || counted < 0) {
                                countedError = "Saisissez une quantité positive ou nulle."
                                return@Button
                            }
                            onConfirm(counted, motif) { field, msg ->
                                when (field) {
                                    "quantiteComptee" -> countedError = msg
                                    "motif" -> motifError = msg
                                    else -> generalError = msg
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_correction_button")
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}
