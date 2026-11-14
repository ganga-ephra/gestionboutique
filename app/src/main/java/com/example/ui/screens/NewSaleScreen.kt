package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import com.example.data.local.entity.ModePaiement
import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.VenteDetail
import com.example.domain.model.AppWidthClass
import com.example.domain.model.CartLine
import com.example.domain.model.SaleValidationResult
import com.example.domain.model.StoreSettings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.ReceiptPreviewDialog
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
fun NewSaleScreen(
    widthClass: AppWidthClass,
    products: List<ProduitAvecStock>,
    cart: List<CartLine>,
    isValidatingSale: Boolean,
    priceChangedPrompt: SaleValidationResult.PriceChanged?,
    lastCompletedSale: VenteDetail?,
    storeSettings: StoreSettings,
    onAddToCart: (ProduitAvecStock) -> Unit,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRemoveLine: (Long) -> Unit,
    onClearCart: () -> Unit,
    onValidateSale: (ModePaiement, Long) -> Unit,
    onDismissPricePrompt: () -> Unit,
    onDismissCompletedSale: () -> Unit,
    onPrintReceipt: (VenteDetail) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(ModePaiement.ESPECES) }
    var cashGivenText by remember { mutableStateOf("") }
    var compactTab by remember { mutableIntStateOf(0) } // 0 = Catalogue, 1 = Panier

    val activeProducts = remember(products, searchQuery) {
        val q = searchQuery.trim().lowercase()
        products.filter { p ->
            p.actif && (q.isEmpty() || p.nom.lowercase().contains(q) || p.reference.lowercase().contains(q))
        }.sortedBy { it.nom.lowercase() }
    }

    val total = cart.sumOf { it.sousTotal }
    val totalUnits = cart.sumOf { it.quantite }
    val cashGiven = cashGivenText.toLongOrNull() ?: 0L
    val cashInsufficient = selectedMode == ModePaiement.ESPECES && cashGiven < total
    val changeDue = if (selectedMode == ModePaiement.ESPECES && cashGiven >= total) {
        cashGiven - total
    } else 0L

    if (widthClass == AppWidthClass.COMPACT) {
        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryTabRow(selectedTabIndex = compactTab) {
                Tab(
                    selected = compactTab == 0,
                    onClick = { compactTab = 0 },
                    text = { Text("Catalogue") },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.testTag("pos_tab_catalog")
                )
                Tab(
                    selected = compactTab == 1,
                    onClick = { compactTab = 1 },
                    text = {
                        Text(
                            if (cart.isEmpty()) "Panier"
                            else "Panier ($totalUnits) • ${Formatters.formatFcfa(total)}"
                        )
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (totalUnits > 0) {
                                    Badge { Text(totalUnits.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        }
                    },
                    modifier = Modifier.testTag("pos_tab_cart")
                )
            }

            if (compactTab == 0) {
                Box(modifier = Modifier.weight(1f)) {
                    PosCatalogPane(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        products = activeProducts,
                        cart = cart,
                        onAddToCart = onAddToCart
                    )
                }
                if (cart.isNotEmpty()) {
                    Surface(
                        tonalElevation = 3.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("$totalUnits article(s)", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    Formatters.formatFcfa(total),
                                    fontFamily = JetBrainsMono,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = { compactTab = 1 },
                                modifier = Modifier.testTag("pos_go_to_cart_button")
                            ) {
                                Text("Passer en caisse")
                            }
                        }
                    }
                }
            } else {
                PosCartPane(
                    cart = cart,
                    total = total,
                    selectedMode = selectedMode,
                    onSelectMode = { selectedMode = it },
                    cashGivenText = cashGivenText,
                    onCashGivenChange = { cashGivenText = it.filter { ch -> ch.isDigit() } },
                    cashInsufficient = cashInsufficient,
                    changeDue = changeDue,
                    isValidatingSale = isValidatingSale,
                    onUpdateQuantity = onUpdateQuantity,
                    onRemoveLine = onRemoveLine,
                    onClearCart = onClearCart,
                    onValidate = {
                        val received = if (selectedMode == ModePaiement.ESPECES) cashGiven else total
                        onValidateSale(selectedMode, received)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        // Adaptive split-pane POS for Medium & Expanded screens
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
            ) {
                PosCatalogPane(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    products = activeProducts,
                    cart = cart,
                    onAddToCart = onAddToCart
                )
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                PosCartPane(
                    cart = cart,
                    total = total,
                    selectedMode = selectedMode,
                    onSelectMode = { selectedMode = it },
                    cashGivenText = cashGivenText,
                    onCashGivenChange = { cashGivenText = it.filter { ch -> ch.isDigit() } },
                    cashInsufficient = cashInsufficient,
                    changeDue = changeDue,
                    isValidatingSale = isValidatingSale,
                    onUpdateQuantity = onUpdateQuantity,
                    onRemoveLine = onRemoveLine,
                    onClearCart = onClearCart,
                    onValidate = {
                        val received = if (selectedMode == ModePaiement.ESPECES) cashGiven else total
                        onValidateSale(selectedMode, received)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (priceChangedPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissPricePrompt,
            title = { Text("Mise à jour des prix") },
            text = {
                Text(
                    "Les prix catalogue ont été modifiés pendant la saisie. Le nouveau total du panier est de ${Formatters.formatFcfa(priceChangedPrompt.newTotal)}. Voulez-vous valider au nouveau tarif ?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDismissPricePrompt()
                    val updatedTotal = cart.sumOf { it.sousTotal }
                    val received = if (selectedMode == ModePaiement.ESPECES) cashGiven else updatedTotal
                    onValidateSale(selectedMode, received)
                }) {
                    Text("Confirmer et valider")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPricePrompt) {
                    Text("Annuler")
                }
            }
        )
    }

    if (lastCompletedSale != null) {
        ReceiptPreviewDialog(
            detail = lastCompletedSale,
            settings = storeSettings,
            onPrint = { onPrintReceipt(lastCompletedSale) },
            onDismiss = onDismissCompletedSale
        )
    }
}

@Composable
private fun PosCatalogPane(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    products: List<ProduitAvecStock>,
    cart: List<CartLine>,
    onAddToCart: (ProduitAvecStock) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Rechercher un produit (réf. ou nom)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pos_search_input")
        )

        if (products.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Default.AddShoppingCart,
                title = "Aucun produit disponible",
                subtitle = "Aucun produit actif ne correspond à votre recherche."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    val isRupture = product.statut == StatutDisponibilite.EN_RUPTURE || product.stockDisponible <= 0
                    val inCartQty = cart.find { it.produit.id == product.id }?.quantite ?: 0

                    SectionCard(
                        containerColor = if (isRupture) RuptureBg else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isRupture) RuptureBorder else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_product_${product.reference}")
                            .clickable(enabled = !isRupture) { onAddToCart(product) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "${product.reference} • ${product.nom}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isRupture) RuptureText else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Formatters.formatFcfa(product.prixVente),
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRupture) RuptureText else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Stock : ${product.stockDisponible}",
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isRupture) RuptureText else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (inCartQty > 0) {
                                        Text(
                                            text = "($inCartQty au panier)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isRupture) {
                                StockStatusBadge(StatutDisponibilite.EN_RUPTURE)
                            } else {
                                Button(
                                    onClick = { onAddToCart(product) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("pos_add_${product.reference}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Ajouter au panier", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ajouter")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosCartPane(
    cart: List<CartLine>,
    total: Long,
    selectedMode: ModePaiement,
    onSelectMode: (ModePaiement) -> Unit,
    cashGivenText: String,
    onCashGivenChange: (String) -> Unit,
    cashInsufficient: Boolean,
    changeDue: Long,
    isValidatingSale: Boolean,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRemoveLine: (Long) -> Unit,
    onClearCart: () -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Panier (${cart.sumOf { it.quantite }} u.)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (cart.isNotEmpty()) {
                TextButton(onClick = onClearCart) {
                    Text("Vider")
                }
            }
        }

        if (cart.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyStatePanel(
                    icon = Icons.Default.ShoppingCart,
                    title = "Panier vide",
                    subtitle = "Sélectionnez des articles disponibles dans le catalogue pour préparer la vente."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cart, key = { it.produit.id }) { line ->
                    SectionCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.produit.nom,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${Formatters.formatFcfa(line.prixUnitaireCapture)} × ${line.quantite} = ${Formatters.formatFcfa(line.sousTotal)}",
                                    fontFamily = JetBrainsMono,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onUpdateQuantity(line.produit.id, line.quantite - 1) },
                                    modifier = Modifier.testTag("cart_minus_${line.produit.reference}")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuer")
                                }
                                Text(
                                    text = line.quantite.toString(),
                                    fontFamily = JetBrainsMono,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = { onUpdateQuantity(line.produit.id, line.quantite + 1) },
                                    modifier = Modifier.testTag("cart_plus_${line.produit.reference}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Augmenter")
                                }
                                IconButton(
                                    onClick = { onRemoveLine(line.produit.id) },
                                    modifier = Modifier.testTag("cart_remove_${line.produit.reference}")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Retirer la ligne",
                                        tint = RuptureText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Payment & Totals section (scrollable safely if keyboard opens)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOTAL À PAYER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = Formatters.formatFcfa(total),
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("pos_total_amount")
                )
            }

            Text("Mode de paiement *", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModePaiement.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onSelectMode(mode) },
                        label = { Text(mode.libelle) },
                        modifier = Modifier.testTag("payment_mode_${mode.name.lowercase()}")
                    )
                }
            }

            if (selectedMode == ModePaiement.ESPECES) {
                OutlinedTextField(
                    value = cashGivenText,
                    onValueChange = onCashGivenChange,
                    label = { Text("Somme remise en espèces (FCFA) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = cart.isNotEmpty() && cashGivenText.isNotEmpty() && cashInsufficient,
                    supportingText = if (cart.isNotEmpty() && cashGivenText.isNotEmpty() && cashInsufficient) {
                        { Text("Somme remise insuffisante pour couvrir ${Formatters.formatFcfa(total)}.") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pos_cash_received_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Monnaie à rendre", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = Formatters.formatFcfa(changeDue),
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AvailableText,
                        modifier = Modifier.testTag("pos_change_due")
                    )
                }
            }

            val canValidate = cart.isNotEmpty() &&
                !isValidatingSale &&
                (selectedMode != ModePaiement.ESPECES || (cashGivenText.isNotEmpty() && !cashInsufficient))

            Button(
                onClick = onValidate,
                enabled = canValidate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("pos_validate_sale_button")
            ) {
                if (isValidatingSale) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Validation en cours...")
                } else {
                    Text("Valider la vente")
                }
            }
        }
    }
}
