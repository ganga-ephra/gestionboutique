package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.IndicateursJour
import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.UtilisateurEntity
import com.example.data.local.entity.VenteResume
import com.example.domain.model.AppWidthClass
import com.example.domain.model.StoreSettings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.SaleStatusBadge
import com.example.ui.components.SectionCard
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.AvailableBg
import com.example.ui.theme.AvailableText
import com.example.ui.theme.CobaltContainerLight
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LowStockBg
import com.example.ui.theme.LowStockBorder
import com.example.ui.theme.LowStockText
import com.example.ui.theme.OnCobaltContainerLight
import com.example.ui.theme.RuptureBg
import com.example.ui.theme.RuptureBorder
import com.example.ui.theme.RuptureText
import com.example.util.Formatters

@Composable
fun DashboardScreen(
    widthClass: AppWidthClass,
    currentUser: UtilisateurEntity,
    settings: StoreSettings,
    indicators: IndicateursJour?,
    products: List<ProduitAvecStock>,
    recentSales: List<VenteResume>,
    onOpenNewSale: () -> Unit,
    onOpenRuptures: () -> Unit,
    onOpenLowStock: () -> Unit,
    onOpenSalesToday: () -> Unit,
    onOpenReports: () -> Unit
) {
    val todayDate = indicators?.dateMetier
        ?: Formatters.businessDateFromUtc(System.currentTimeMillis(), settings.fuseauHoraire)
    val alertProducts = products.filter {
        it.actif && (it.statut == StatutDisponibilite.EN_RUPTURE || it.statut == StatutDisponibilite.STOCK_FAIBLE)
    }.sortedBy { it.stockDisponible }
    val todaySales = recentSales.filter { it.dateMetier == todayDate }.take(6)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Journée métier : ${Formatters.formatDisplayDate(todayDate)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (currentUser.role == RoleUtilisateur.ADMINISTRATEUR) {
                                "Vue globale du magasin — ${settings.nomMagasin}"
                            } else {
                                "Vos ventes du jour (${currentUser.nom}) — ${settings.nomMagasin}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick action row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onOpenNewSale,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("dashboard_new_sale_button")
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nouvelle vente")
                }
                FilledTonalButton(
                    onClick = onOpenRuptures,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("dashboard_ruptures_button")
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if ((indicators?.referencesRupture ?: 0) > 0) RuptureText else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ruptures (${indicators?.referencesRupture ?: 0})")
                }
            }
        }

        // KPI Grid adaptive
        item {
            val kpiItems = listOf(
                KpiSpec(
                    tag = "kpi_sales_amount",
                    label = if (currentUser.role == RoleUtilisateur.ADMINISTRATEUR) "Ventes validées du jour" else "Mes ventes du jour",
                    value = Formatters.formatFcfa(indicators?.montantVentes ?: 0L),
                    icon = Icons.Default.Payments,
                    accentBg = AvailableBg,
                    accentFg = AvailableText,
                    onClick = onOpenSalesToday
                ),
                KpiSpec(
                    tag = "kpi_sales_count",
                    label = "Nombre de ventes",
                    value = "${indicators?.nombreVentes ?: 0}",
                    icon = Icons.Default.ReceiptLong,
                    accentBg = CobaltContainerLight,
                    accentFg = OnCobaltContainerLight,
                    onClick = onOpenSalesToday
                ),
                KpiSpec(
                    tag = "kpi_units_sold",
                    label = "Unités vendues",
                    value = Formatters.formatInt(indicators?.unitesVendues ?: 0),
                    icon = Icons.Default.ShoppingBag,
                    accentBg = CobaltContainerLight,
                    accentFg = OnCobaltContainerLight,
                    onClick = if (currentUser.role == RoleUtilisateur.ADMINISTRATEUR) onOpenReports else onOpenSalesToday
                ),
                KpiSpec(
                    tag = "kpi_ruptures",
                    label = "Références en rupture",
                    value = "${indicators?.referencesRupture ?: 0}",
                    icon = Icons.Default.ErrorOutline,
                    accentBg = RuptureBg,
                    accentFg = RuptureText,
                    borderColor = if ((indicators?.referencesRupture ?: 0) > 0) RuptureBorder else null,
                    onClick = onOpenRuptures
                ),
                KpiSpec(
                    tag = "kpi_low_stock",
                    label = "Stocks faibles",
                    value = "${indicators?.referencesStockFaible ?: 0}",
                    icon = Icons.Default.WarningAmber,
                    accentBg = LowStockBg,
                    accentFg = LowStockText,
                    borderColor = if ((indicators?.referencesStockFaible ?: 0) > 0) LowStockBorder else null,
                    onClick = onOpenLowStock
                )
            )

            val columns = when (widthClass) {
                AppWidthClass.COMPACT -> 2
                AppWidthClass.MEDIUM -> 3
                AppWidthClass.EXPANDED -> 5
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                kpiItems.chunked(columns).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { kpi ->
                            KpiCard(spec = kpi, modifier = Modifier.weight(1f))
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Alerts section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alertes de stock (${alertProducts.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                if (alertProducts.isNotEmpty()) {
                    FilledTonalButton(onClick = onOpenRuptures) {
                        Text("Voir dans Stocks")
                    }
                }
            }
        }

        if (alertProducts.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Default.Inventory,
                    title = "Aucune alerte de stock",
                    subtitle = "Tous les produits actifs disposent d'un stock supérieur au seuil d'alerte."
                )
            }
        } else {
            items(alertProducts.take(6), key = { "alert_${it.id}" }) { product ->
                val isRupture = product.statut == StatutDisponibilite.EN_RUPTURE
                SectionCard(
                    containerColor = if (isRupture) RuptureBg else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isRupture) RuptureBorder else LowStockBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isRupture) onOpenRuptures() else onOpenLowStock()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = product.nom,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isRupture) RuptureText else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${product.reference} • Seuil : ${product.seuilAlerte}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRupture) RuptureText.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${product.stockDisponible} u.",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = if (isRupture) RuptureText else LowStockText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            StockStatusBadge(product.statut)
                        }
                    }
                }
            }
        }

        // Today's sales section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ventes du jour",
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalButton(onClick = onOpenSalesToday) {
                    Text("Historique")
                }
            }
        }

        if (todaySales.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Default.ReceiptLong,
                    title = "Aucune vente enregistrée aujourd'hui",
                    subtitle = "Appuyez sur Nouvelle vente pour enregistrer un premier encaissement.",
                    actionLabel = "Ouvrir la caisse",
                    onAction = onOpenNewSale
                )
            }
        } else {
            items(todaySales, key = { "dash_sale_${it.id}" }) { sale ->
                SectionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSalesToday)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sale.numero,
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${Formatters.formatTimeUtc(sale.dateHeureUtc, settings.fuseauHoraire)} • ${sale.vendeurNom} • ${sale.modePaiement.libelle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Formatters.formatFcfa(sale.total),
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            SaleStatusBadge(sale.statut)
                        }
                    }
                }
            }
        }
    }
}

private data class KpiSpec(
    val tag: String,
    val label: String,
    val value: String,
    val icon: ImageVector,
    val accentBg: Color,
    val accentFg: Color,
    val borderColor: Color? = null,
    val onClick: () -> Unit
)

@Composable
private fun KpiCard(spec: KpiSpec, modifier: Modifier = Modifier) {
    SectionCard(
        border = BorderStroke(1.dp, spec.borderColor ?: MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .testTag(spec.tag)
            .clickable(onClick = spec.onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = spec.accentBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = spec.accentFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = spec.value,
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
