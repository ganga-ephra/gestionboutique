package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.RapportProduitRow
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.UtilisateurEntity
import com.example.data.local.entity.VenteResume
import com.example.data.local.entity.VentesAggregate
import com.example.domain.model.StoreSettings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.SectionCard
import com.example.ui.theme.JetBrainsMono
import com.example.util.Formatters

private enum class ReportPreset(val label: String) {
    TODAY("Aujourd'hui"),
    SEVEN_DAYS("7 jours"),
    THIRTY_DAYS("30 jours"),
    CUSTOM("Personnalisé")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    currentUser: UtilisateurEntity,
    settings: StoreSettings,
    reportSummary: VentesAggregate?,
    reportRows: List<RapportProduitRow>,
    sales: List<VenteResume>,
    products: List<ProduitAvecStock>,
    onLoadReport: (String, String) -> Unit,
    onPrepareSalesCsv: (List<VenteResume>, (String) -> Unit) -> Unit,
    onPrepareStockCsv: (List<ProduitAvecStock>, (String) -> Unit) -> Unit,
    onNotify: (String, Boolean) -> Unit
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
                subtitle = "Les rapports globaux et exports CSV ne sont accessibles qu'avec un compte Administrateur."
            )
        }
        return
    }

    val context = LocalContext.current
    val today = Formatters.businessDateFromUtc(System.currentTimeMillis(), settings.fuseauHoraire)
    var preset by remember { mutableStateOf(ReportPreset.TODAY) }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    var pendingCsvContent by remember { mutableStateOf<String?>(null) }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val content = pendingCsvContent
        pendingCsvContent = null
        if (uri != null && content != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                onNotify("Export CSV enregistré avec succès.", false)
            } catch (ex: Exception) {
                onNotify("Erreur lors de l'écriture CSV : ${ex.message}", true)
            }
        }
    }

    LaunchedEffect(startDate, endDate, sales) {
        onLoadReport(startDate, endDate)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Période d'analyse",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReportPreset.entries.forEach { item ->
                            FilterChip(
                                selected = preset == item,
                                onClick = {
                                    preset = item
                                    val now = System.currentTimeMillis()
                                    when (item) {
                                        ReportPreset.TODAY -> {
                                            startDate = Formatters.businessDateFromUtc(now, settings.fuseauHoraire)
                                            endDate = startDate
                                        }
                                        ReportPreset.SEVEN_DAYS -> {
                                            startDate = Formatters.businessDateFromUtc(
                                                now - 6L * 86_400_000L,
                                                settings.fuseauHoraire
                                            )
                                            endDate = Formatters.businessDateFromUtc(now, settings.fuseauHoraire)
                                        }
                                        ReportPreset.THIRTY_DAYS -> {
                                            startDate = Formatters.businessDateFromUtc(
                                                now - 29L * 86_400_000L,
                                                settings.fuseauHoraire
                                            )
                                            endDate = Formatters.businessDateFromUtc(now, settings.fuseauHoraire)
                                        }
                                        ReportPreset.CUSTOM -> Unit
                                    }
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                preset = ReportPreset.CUSTOM
                                pickingStart = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Du : ${Formatters.formatDisplayDate(startDate)}")
                        }
                        OutlinedButton(
                            onClick = {
                                preset = ReportPreset.CUSTOM
                                pickingEnd = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Au : ${Formatters.formatDisplayDate(endDate)}")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val periodSales = sales.filter { it.dateMetier in startDate..endDate }
                                onPrepareSalesCsv(periodSales) { csv ->
                                    pendingCsvContent = csv
                                    saveCsvLauncher.launch("ventes_${startDate}_${endDate}.csv")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_sales_csv_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV Ventes")
                        }
                        OutlinedButton(
                            onClick = {
                                onPrepareStockCsv(products) { csv ->
                                    pendingCsvContent = csv
                                    saveCsvLauncher.launch("stock_${today}.csv")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_stock_csv_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV Stock")
                        }
                    }
                }
            }
        }

        // Summary KPIs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Chiffre d'affaires", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = Formatters.formatFcfa(reportSummary?.montantVentes ?: 0L),
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                SectionCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Ventes / Unités", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${reportSummary?.nombreVentes ?: 0} / ${reportSummary?.unitesVendues ?: 0} u.",
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Produits vendus sur la période (${reportRows.size})",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (reportRows.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Default.Assessment,
                    title = "Aucune vente validée sur cette période",
                    subtitle = "Les ventes annulées sont automatiquement exclues des montants et quantités vendues."
                )
            }
        } else {
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Réf. / Produit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.6f))
                        Text("Unités", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.weight(0.6f))
                        Text("Montant", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }
            }

            items(reportRows, key = { it.produitId }) { row ->
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.6f)) {
                            Text(
                                text = row.nom,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = row.reference,
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${row.unitesVendues}",
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.6f)
                        )
                        Text(
                            text = Formatters.formatFcfa(row.montantVendu),
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (pickingStart) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Formatters.businessDateFromUtc(millis, "UTC")
                        startDate = picked
                        if (endDate < startDate) endDate = startDate
                    }
                    pickingStart = false
                }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (pickingEnd) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Formatters.businessDateFromUtc(millis, "UTC")
                        endDate = picked
                        if (startDate > endDate) startDate = endDate
                    }
                    pickingEnd = false
                }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEnd = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}
