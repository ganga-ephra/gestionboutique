package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.StatutVente
import com.example.data.local.entity.VenteDetail
import com.example.domain.model.StoreSettings
import com.example.ui.theme.ArchivedBg
import com.example.ui.theme.ArchivedText
import com.example.ui.theme.AvailableBg
import com.example.ui.theme.AvailableBorder
import com.example.ui.theme.AvailableText
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LowStockBg
import com.example.ui.theme.LowStockBorder
import com.example.ui.theme.LowStockText
import com.example.ui.theme.RuptureBg
import com.example.ui.theme.RuptureBorder
import com.example.ui.theme.RuptureText
import com.example.util.Formatters

@Composable
fun StockStatusBadge(
    statut: StatutDisponibilite,
    modifier: Modifier = Modifier
) {
    val (bg, fg, border) = when (statut) {
        StatutDisponibilite.EN_RUPTURE -> Triple(RuptureBg, RuptureText, RuptureBorder)
        StatutDisponibilite.STOCK_FAIBLE -> Triple(LowStockBg, LowStockText, LowStockBorder)
        StatutDisponibilite.DISPONIBLE -> Triple(AvailableBg, AvailableText, AvailableBorder)
        StatutDisponibilite.ARCHIVE -> Triple(ArchivedBg, ArchivedText, Color(0xFFCBD5E1))
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = statut.libelle,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SaleStatusBadge(
    statut: StatutVente,
    modifier: Modifier = Modifier
) {
    val (bg, fg, border) = when (statut) {
        StatutVente.VALIDEE -> Triple(AvailableBg, AvailableText, AvailableBorder)
        StatutVente.ANNULEE -> Triple(RuptureBg, RuptureText, RuptureBorder)
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = statut.libelle,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector = Icons.Default.Inventory2,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun InlineFeedbackBanner(
    text: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isError) RuptureBg else AvailableBg
    val fg = if (isError) RuptureText else AvailableText
    val border = if (isError) RuptureBorder else AvailableBorder
    val icon = if (isError) Icons.Default.Error else Icons.Default.CheckCircle

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("feedback_banner"),
        color = bg,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = fg)
            Text(
                text = text,
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = fg)
            }
        }
    }
}

@Composable
fun ReceiptPreviewDialog(
    detail: VenteDetail,
    settings: StoreSettings,
    onPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    val v = detail.vente
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = settings.nomMagasin,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    if (settings.coordonneesMagasin.isNotBlank()) {
                        Text(
                            text = settings.coordonneesMagasin,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (v.statut == StatutVente.ANNULEE) {
                    Surface(
                        color = RuptureBg,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, RuptureBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = RuptureText)
                            Text(
                                text = "VENTE ANNULÉE${detail.annulation?.motif?.let { " — $it" } ?: ""}",
                                color = RuptureText,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ticket", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v.numero, fontFamily = JetBrainsMono, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Formatters.formatDateTimeUtc(v.dateHeureUtc, settings.fuseauHoraire),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vendeur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v.vendeurNom, style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Paiement", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v.modePaiement.libelle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }

                HorizontalDivider()

                detail.lignes.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.nomSnapshot,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${line.referenceSnapshot} • ${line.quantite} × ${Formatters.formatFcfa(line.prixUnitaire)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Formatters.formatFcfa(line.sousTotal),
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        Formatters.formatFcfa(v.total),
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Montant reçu", style = MaterialTheme.typography.bodySmall)
                    Text(Formatters.formatFcfa(v.montantRecu), fontFamily = JetBrainsMono, style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monnaie rendue", style = MaterialTheme.typography.bodySmall)
                    Text(Formatters.formatFcfa(v.monnaieRendue), fontFamily = JetBrainsMono, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("close_receipt_button")
                    ) {
                        Text("Fermer")
                    }
                    Button(
                        onClick = onPrint,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("print_receipt_button")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Imprimer")
                    }
                }
            }
        }
    }
}
