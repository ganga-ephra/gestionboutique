package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutVente
import com.example.data.local.entity.UtilisateurEntity
import com.example.data.local.entity.VenteDetail
import com.example.data.local.entity.VenteResume
import com.example.domain.model.AppWidthClass
import com.example.domain.model.StoreSettings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.components.SaleStatusBadge
import com.example.ui.components.SectionCard
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RuptureBg
import com.example.ui.theme.RuptureBorder
import com.example.ui.theme.RuptureText
import com.example.util.Formatters

private enum class HistoryPeriodFilter(val label: String) {
    TODAY("Aujourd'hui"),
    SEVEN_DAYS("7 jours"),
    THIRTY_DAYS("30 jours"),
    ALL("Tout")
}

@Composable
fun HistoryScreen(
    widthClass: AppWidthClass,
    currentUser: UtilisateurEntity,
    settings: StoreSettings,
    sales: List<VenteResume>,
    users: List<UtilisateurEntity>,
    initialTodayOnly: Boolean,
    onConsumeInitialToday: () -> Unit,
    onLoadSaleDetail: suspend (Long) -> VenteDetail?,
    onCancelSale: (Long, String, (Boolean, VenteDetail?, String) -> Unit) -> Unit,
    onPrintReceipt: (VenteDetail) -> Unit
) {
    val isAdmin = currentUser.role == RoleUtilisateur.ADMINISTRATEUR

    var searchQuery by remember { mutableStateOf("") }
    var periodFilter by remember {
        mutableStateOf(if (initialTodayOnly) HistoryPeriodFilter.TODAY else HistoryPeriodFilter.ALL)
    }
    var statusFilter by remember { mutableStateOf<StatutVente?>(null) }
    var sellerFilterId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(initialTodayOnly) {
        if (initialTodayOnly) {
            periodFilter = HistoryPeriodFilter.TODAY
            onConsumeInitialToday()
        }
    }

    var selectedSaleId by remember { mutableStateOf<Long?>(null) }
    var selectedDetail by remember { mutableStateOf<VenteDetail?>(null) }
    var receiptPreviewDetail by remember { mutableStateOf<VenteDetail?>(null) }
    var cancellingSale by remember { mutableStateOf<VenteDetail?>(null) }

    LaunchedEffect(selectedSaleId, sales) {
        selectedDetail = selectedSaleId?.let { onLoadSaleDetail(it) }
    }

    val todayStr = Formatters.businessDateFromUtc(System.currentTimeMillis(), settings.fuseauHoraire)
    val min7Days = Formatters.businessDateFromUtc(
        System.currentTimeMillis() - 6L * 86_400_000L,
        settings.fuseauHoraire
    )
    val min30Days = Formatters.businessDateFromUtc(
        System.currentTimeMillis() - 29L * 86_400_000L,
        settings.fuseauHoraire
    )

    val filteredSales = remember(sales, searchQuery, periodFilter, statusFilter, sellerFilterId, todayStr) {
        val q = searchQuery.trim().lowercase()
        sales.filter { s ->
            val matchesQ = q.isEmpty() ||
                s.numero.lowercase().contains(q) ||
                s.vendeurNom.lowercase().contains(q)
            val matchesPeriod = when (periodFilter) {
                HistoryPeriodFilter.TODAY -> s.dateMetier == todayStr
                HistoryPeriodFilter.SEVEN_DAYS -> s.dateMetier >= min7Days
                HistoryPeriodFilter.THIRTY_DAYS -> s.dateMetier >= min30Days
                HistoryPeriodFilter.ALL -> true
            }
            val matchesStatus = statusFilter == null || s.statut == statusFilter
            val matchesSeller = sellerFilterId == null || s.vendeurId == sellerFilterId
            matchesQ && matchesPeriod && matchesStatus && matchesSeller
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher par numéro de ticket (ex: V-...)...") },
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
                    .testTag("history_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryPeriodFilter.entries.forEach { pf ->
                    FilterChip(
                        selected = periodFilter == pf,
                        onClick = { periodFilter = pf },
                        label = { Text(pf.label) }
                    )
                }
                FilterChip(
                    selected = statusFilter == StatutVente.VALIDEE,
                    onClick = {
                        statusFilter = if (statusFilter == StatutVente.VALIDEE) null else StatutVente.VALIDEE
                    },
                    label = { Text("Validées") }
                )
                FilterChip(
                    selected = statusFilter == StatutVente.ANNULEE,
                    onClick = {
                        statusFilter = if (statusFilter == StatutVente.ANNULEE) null else StatutVente.ANNULEE
                    },
                    label = { Text("Annulées") }
                )
                if (isAdmin) {
                    users.forEach { u ->
                        FilterChip(
                            selected = sellerFilterId == u.id,
                            onClick = {
                                sellerFilterId = if (sellerFilterId == u.id) null else u.id
                            },
                            label = { Text(u.nom) }
                        )
                    }
                }
            }

            Text(
                text = "${filteredSales.size} vente(s) • Total validé : ${
                    Formatters.formatFcfa(
                        filteredSales.filter { it.statut == StatutVente.VALIDEE }.sumOf { it.total }
                    )
                }",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (filteredSales.isEmpty()) {
                EmptyStatePanel(
                    icon = Icons.Default.ReceiptLong,
                    title = "Aucune vente trouvée",
                    subtitle = "Modifiez la période ou les filtres pour consulter d'autres tickets."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredSales, key = { it.id }) { sale ->
                        val selected = selectedSaleId == sale.id
                        val isCancelled = sale.statut == StatutVente.ANNULEE
                        SectionCard(
                            containerColor = if (isCancelled) RuptureBg.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sale_row_${sale.numero}")
                                .clickable { selectedSaleId = sale.id }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = sale.numero,
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${Formatters.formatDateTimeUtc(sale.dateHeureUtc, settings.fuseauHoraire)} • ${sale.vendeurNom}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${sale.modePaiement.libelle} • ${sale.nombreUnites} u.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = Formatters.formatFcfa(sale.total),
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    SaleStatusBadge(sale.statut)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (widthClass == AppWidthClass.EXPANDED && selectedDetail != null) {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .padding(top = 12.dp, end = 16.dp, bottom = 12.dp)
            ) {
                SaleDetailPanel(
                    detail = selectedDetail!!,
                    settings = settings,
                    isAdmin = isAdmin,
                    onPreviewReceipt = { receiptPreviewDetail = selectedDetail },
                    onPrint = { onPrintReceipt(selectedDetail!!) },
                    onRequestCancel = { cancellingSale = selectedDetail },
                    onClose = { selectedSaleId = null }
                )
            }
        }
    }

    if (widthClass != AppWidthClass.EXPANDED && selectedDetail != null) {
        Dialog(onDismissRequest = { selectedSaleId = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                SaleDetailPanel(
                    detail = selectedDetail!!,
                    settings = settings,
                    isAdmin = isAdmin,
                    onPreviewReceipt = { receiptPreviewDetail = selectedDetail },
                    onPrint = { onPrintReceipt(selectedDetail!!) },
                    onRequestCancel = { cancellingSale = selectedDetail },
                    onClose = { selectedSaleId = null }
                )
            }
        }
    }

    receiptPreviewDetail?.let { detail ->
        ReceiptPreviewDialog(
            detail = detail,
            settings = storeSettingsOrDefault(settings),
            onPrint = { onPrintReceipt(detail) },
            onDismiss = { receiptPreviewDetail = null }
        )
    }

    cancellingSale?.let { target ->
        CancelSaleDialog(
            detail = target,
            onDismiss = { cancellingSale = null },
            onConfirm = { motif ->
                onCancelSale(target.vente.id, motif) { ok, updated, _ ->
                    if (ok) {
                        cancellingSale = null
                        if (updated != null) {
                            selectedDetail = updated
                        }
                    }
                }
            }
        )
    }
}

private fun storeSettingsOrDefault(settings: StoreSettings): StoreSettings = settings

@Composable
private fun SaleDetailPanel(
    detail: VenteDetail,
    settings: StoreSettings,
    isAdmin: Boolean,
    onPreviewReceipt: () -> Unit,
    onPrint: () -> Unit,
    onRequestCancel: () -> Unit,
    onClose: () -> Unit
) {
    val v = detail.vente
    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Détail de la vente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(v.numero, fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fermer le détail")
            }
        }

        SaleStatusBadge(v.statut)

        if (detail.annulation != null) {
            Surface(
                color = RuptureBg,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, RuptureBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Annulée par ${detail.annulation.auteurNom}",
                        color = RuptureText,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = Formatters.formatDateTimeUtc(detail.annulation.dateHeureUtc, settings.fuseauHoraire),
                        color = RuptureText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Motif : ${detail.annulation.motif}",
                        color = RuptureText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Text(
            text = "${Formatters.formatDateTimeUtc(v.dateHeureUtc, settings.fuseauHoraire)} • Vendeur : ${v.vendeurNom}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        detail.lignes.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.nomSnapshot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${line.referenceSnapshot} • ${line.quantite} × ${Formatters.formatFcfa(line.prixUnitaire)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    Formatters.formatFcfa(line.sousTotal),
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                Formatters.formatFcfa(v.total),
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mode (${v.modePaiement.libelle})", style = MaterialTheme.typography.bodySmall)
            Text(
                "Reçu : ${Formatters.formatFcfa(v.montantRecu)} • Monnaie : ${Formatters.formatFcfa(v.monnaieRendue)}",
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPreviewReceipt,
                modifier = Modifier
                    .weight(1f)
                    .testTag("preview_receipt_button")
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Aperçu")
            }
            Button(
                onClick = onPrint,
                modifier = Modifier
                    .weight(1f)
                    .testTag("reprint_receipt_button")
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Réimprimer")
            }
        }

        if (isAdmin && v.statut == StatutVente.VALIDEE) {
            OutlinedButton(
                onClick = onRequestCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RuptureText),
                border = BorderStroke(1.dp, RuptureBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cancel_sale_button")
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Annuler intégralement la vente")
            }
        }
    }
}

@Composable
private fun CancelSaleDialog(
    detail: VenteDetail,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var motif by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Annuler la vente ${detail.vente.numero}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Cette opération est irréversible : la vente restera conservée comme annulée et les quantités seront réintégrées en stock.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = motif,
                    onValueChange = {
                        motif = it
                        error = null
                    },
                    label = { Text("Motif obligatoire *") },
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_sale_motif_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (motif.isBlank()) {
                        error = "Le motif d'annulation est obligatoire."
                    } else {
                        onConfirm(motif)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RuptureText),
                modifier = Modifier.testTag("confirm_cancel_sale_button")
            ) {
                Text("Confirmer l'annulation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}
