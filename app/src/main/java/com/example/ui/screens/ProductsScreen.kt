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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.local.entity.CategorieEntity
import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.UtilisateurEntity
import com.example.domain.model.AppWidthClass
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.SectionCard
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.RuptureBg
import com.example.ui.theme.RuptureBorder
import com.example.ui.theme.RuptureText
import com.example.util.Formatters

private enum class ProductActiveFilter(val label: String) {
    ACTIFS("Actifs"),
    ARCHIVES("Archivés"),
    TOUS("Tous")
}

private enum class ProductSort(val label: String) {
    NOM("Nom (A-Z)"),
    REFERENCE("Référence"),
    PRIX_DESC("Prix décroissant"),
    STOCK_ASC("Stock croissant")
}

@Composable
fun ProductsScreen(
    widthClass: AppWidthClass,
    currentUser: UtilisateurEntity,
    products: List<ProduitAvecStock>,
    categories: List<CategorieEntity>,
    onCreateCategory: (String, (Boolean, String) -> Unit) -> Unit,
    onCreateProduct: (
        ref: String,
        nom: String,
        catId: Long?,
        prix: Long,
        seuil: Int,
        stockInit: Int,
        onDone: (Boolean, String?, String) -> Unit
    ) -> Unit,
    onUpdateProduct: (
        id: Long,
        ref: String,
        nom: String,
        catId: Long?,
        prix: Long,
        seuil: Int,
        onDone: (Boolean, String?, String) -> Unit
    ) -> Unit,
    onSetProductActive: (Long, Boolean) -> Unit
) {
    val isAdmin = currentUser.role == RoleUtilisateur.ADMINISTRATEUR

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ProductActiveFilter.ACTIFS) }
    var statusFilter by remember { mutableStateOf<StatutDisponibilite?>(null) }
    var categoryFilterId by remember { mutableStateOf<Long?>(null) }
    var sortOrder by remember { mutableStateOf(ProductSort.NOM) }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProduitAvecStock?>(null) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    val filteredProducts = remember(
        products,
        searchQuery,
        activeFilter,
        statusFilter,
        categoryFilterId,
        sortOrder
    ) {
        val q = searchQuery.trim().lowercase()
        val list = products.filter { p ->
            val matchesQuery = q.isEmpty() ||
                p.nom.lowercase().contains(q) ||
                p.reference.lowercase().contains(q)
            val matchesActive = when (activeFilter) {
                ProductActiveFilter.ACTIFS -> p.actif
                ProductActiveFilter.ARCHIVES -> !p.actif
                ProductActiveFilter.TOUS -> true
            }
            val matchesStatus = statusFilter == null || p.statut == statusFilter
            val matchesCat = categoryFilterId == null || p.categorieId == categoryFilterId
            matchesQuery && matchesActive && matchesStatus && matchesCat
        }
        when (sortOrder) {
            ProductSort.NOM -> list.sortedBy { it.nom.lowercase() }
            ProductSort.REFERENCE -> list.sortedBy { it.reference.lowercase() }
            ProductSort.PRIX_DESC -> list.sortedByDescending { it.prixVente }
            ProductSort.STOCK_ASC -> list.sortedBy { it.stockDisponible }
        }
    }

    val selectedProduct = products.find { it.id == selectedProductId }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("add_product_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nouveau produit")
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search & quick sort bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher par référence ou nom...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer la recherche")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("products_search_input")
                    )

                    var sortMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { sortMenuOpen = true },
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text("Tri : ${sortOrder.label}")
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            ProductSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    onClick = {
                                        sortOrder = sort
                                        sortMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Filter chips row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProductActiveFilter.entries.forEach { f ->
                        FilterChip(
                            selected = activeFilter == f,
                            onClick = { activeFilter = f },
                            label = { Text(f.label) }
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    listOf(
                        StatutDisponibilite.EN_RUPTURE,
                        StatutDisponibilite.STOCK_FAIBLE,
                        StatutDisponibilite.DISPONIBLE
                    ).forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = if (statusFilter == st) null else st },
                            label = { Text(st.libelle) }
                        )
                    }
                    categories.forEach { cat ->
                        FilterChip(
                            selected = categoryFilterId == cat.id,
                            onClick = {
                                categoryFilterId = if (categoryFilterId == cat.id) null else cat.id
                            },
                            label = { Text(cat.nom) }
                        )
                    }
                    if (isAdmin) {
                        TextButton(onClick = { showNewCategoryDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Catégorie")
                        }
                    }
                }

                Text(
                    text = "${filteredProducts.size} produit(s) affiché(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (filteredProducts.isEmpty()) {
                    EmptyStatePanel(
                        icon = Icons.Default.Inventory2,
                        title = "Aucun produit trouvé",
                        subtitle = if (products.isEmpty()) {
                            "Le catalogue est vide. Ajoutez votre premier produit avec le bouton +."
                        } else {
                            "Aucun article ne correspond aux critères de recherche ou filtres actifs."
                        },
                        actionLabel = if (isAdmin && products.isEmpty()) "Créer un produit" else null,
                        onAction = if (isAdmin && products.isEmpty()) ({ showCreateDialog = true }) else null
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductRowCard(
                                product = product,
                                selected = selectedProductId == product.id,
                                onClick = { selectedProductId = product.id }
                            )
                        }
                    }
                }
            }

            // Expanded detail pane
            if (widthClass == AppWidthClass.EXPANDED && selectedProduct != null) {
                Surface(
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxSize()
                        .padding(top = 12.dp, end = 16.dp, bottom = 12.dp),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    ProductDetailContent(
                        product = selectedProduct,
                        isAdmin = isAdmin,
                        onEdit = { editingProduct = selectedProduct },
                        onToggleActive = { onSetProductActive(selectedProduct.id, !selectedProduct.actif) },
                        onClose = { selectedProductId = null }
                    )
                }
            }
        }
    }

    // Detail dialog for Compact / Medium screens
    if (widthClass != AppWidthClass.EXPANDED && selectedProduct != null) {
        Dialog(onDismissRequest = { selectedProductId = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                ProductDetailContent(
                    product = selectedProduct,
                    isAdmin = isAdmin,
                    onEdit = {
                        editingProduct = selectedProduct
                    },
                    onToggleActive = {
                        onSetProductActive(selectedProduct.id, !selectedProduct.actif)
                    },
                    onClose = { selectedProductId = null }
                )
            }
        }
    }

    if (showCreateDialog) {
        ProductFormDialog(
            title = "Nouveau produit",
            existing = null,
            categories = categories,
            onDismiss = { showCreateDialog = false },
            onSave = { ref, nom, catId, prix, seuil, stockInit, onErr ->
                onCreateProduct(ref, nom, catId, prix, seuil, stockInit) { ok, field, msg ->
                    if (ok) showCreateDialog = false else onErr(field, msg)
                }
            }
        )
    }

    editingProduct?.let { prod ->
        ProductFormDialog(
            title = "Modifier ${prod.reference}",
            existing = prod,
            categories = categories,
            onDismiss = { editingProduct = null },
            onSave = { ref, nom, catId, prix, seuil, _, onErr ->
                onUpdateProduct(prod.id, ref, nom, catId, prix, seuil) { ok, field, msg ->
                    if (ok) editingProduct = null else onErr(field, msg)
                }
            }
        )
    }

    if (showNewCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var catError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("Nouvelle catégorie") },
            text = {
                OutlinedTextField(
                    value = catName,
                    onValueChange = {
                        catName = it
                        catError = null
                    },
                    label = { Text("Nom de la catégorie *") },
                    isError = catError != null,
                    supportingText = catError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCreateCategory(catName) { ok, msg ->
                        if (ok) showNewCategoryDialog = false else catError = msg
                    }
                }) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun ProductRowCard(
    product: ProduitAvecStock,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isRupture = product.statut == StatutDisponibilite.EN_RUPTURE
    val containerColor = if (isRupture) RuptureBg else MaterialTheme.colorScheme.surface
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        isRupture -> RuptureBorder
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (selected) 2.dp else 1.dp

    SectionCard(
        containerColor = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_row_${product.reference}")
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = product.reference,
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isRupture) RuptureText else MaterialTheme.colorScheme.primary
                    )
                    if (!product.categorieNom.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = product.categorieNom,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = product.nom,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isRupture) RuptureText else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Seuil : ${product.seuilAlerte}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRupture) RuptureText.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = Formatters.formatFcfa(product.prixVente),
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isRupture) RuptureText else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Stock : ${product.stockDisponible}",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isRupture) RuptureText else MaterialTheme.colorScheme.onSurface
                    )
                    StockStatusBadge(product.statut)
                }
            }
        }
    }
}

@Composable
private fun ProductDetailContent(
    product: ProduitAvecStock,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fiche produit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fermer la fiche")
            }
        }

        StockStatusBadge(product.statut)

        DetailLine("Référence", product.reference, mono = true)
        DetailLine("Nom", product.nom)
        DetailLine("Catégorie", product.categorieNom ?: "Sans catégorie")
        DetailLine("Prix de vente", Formatters.formatFcfa(product.prixVente), mono = true)
        DetailLine("Stock disponible", "${product.stockDisponible} unités", mono = true)
        DetailLine("Seuil d'alerte", "${product.seuilAlerte} unités", mono = true)
        DetailLine("État", if (product.actif) "Actif" else "Archivé")

        Text(
            text = "Le stock est calculé automatiquement à partir des mouvements (onglet Stocks).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isAdmin) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_product_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Modifier")
                }
                OutlinedButton(
                    onClick = onToggleActive,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("archive_product_button")
                ) {
                    Icon(
                        if (product.actif) Icons.Default.Archive else Icons.Default.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (product.actif) "Archiver" else "Réactiver")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontFamily = if (mono) JetBrainsMono else null,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormDialog(
    title: String,
    existing: ProduitAvecStock?,
    categories: List<CategorieEntity>,
    onDismiss: () -> Unit,
    onSave: (
        ref: String,
        nom: String,
        catId: Long?,
        prix: Long,
        seuil: Int,
        stockInit: Int,
        onError: (String?, String) -> Unit
    ) -> Unit
) {
    var reference by remember { mutableStateOf(existing?.reference ?: "") }
    var nom by remember { mutableStateOf(existing?.nom ?: "") }
    var selectedCategoryId by remember { mutableStateOf(existing?.categorieId) }
    var prixText by remember { mutableStateOf(existing?.prixVente?.toString() ?: "") }
    var seuilText by remember { mutableStateOf(existing?.seuilAlerte?.toString() ?: "5") }
    var stockInitText by remember { mutableStateOf("0") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    var refError by remember { mutableStateOf<String?>(null) }
    var nomError by remember { mutableStateOf<String?>(null) }
    var prixError by remember { mutableStateOf<String?>(null) }
    var seuilError by remember { mutableStateOf<String?>(null) }
    var stockError by remember { mutableStateOf<String?>(null) }
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
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = reference,
                    onValueChange = {
                        reference = it
                        refError = null
                    },
                    label = { Text("Référence unique *") },
                    isError = refError != null,
                    supportingText = refError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_ref_input")
                )

                OutlinedTextField(
                    value = nom,
                    onValueChange = {
                        nom = it
                        nomError = null
                    },
                    label = { Text("Nom du produit *") },
                    isError = nomError != null,
                    supportingText = nomError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )

                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it }
                ) {
                    val catLabel = categories.find { it.id == selectedCategoryId }?.nom ?: "Sans catégorie"
                    OutlinedTextField(
                        value = catLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Catégorie (facultative)") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sans catégorie") },
                            onClick = {
                                selectedCategoryId = null
                                categoryDropdownExpanded = false
                            }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.nom) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = prixText,
                    onValueChange = {
                        prixText = it.filter { ch -> ch.isDigit() }
                        prixError = null
                    },
                    label = { Text("Prix de vente (FCFA) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = prixError != null,
                    supportingText = prixError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_price_input")
                )

                OutlinedTextField(
                    value = seuilText,
                    onValueChange = {
                        seuilText = it.filter { ch -> ch.isDigit() }
                        seuilError = null
                    },
                    label = { Text("Seuil d'alerte *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = seuilError != null,
                    supportingText = seuilError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_threshold_input")
                )

                if (existing == null) {
                    OutlinedTextField(
                        value = stockInitText,
                        onValueChange = {
                            stockInitText = it.filter { ch -> ch.isDigit() }
                            stockError = null
                        },
                        label = { Text("Stock initial (0 possible)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = stockError != null,
                        supportingText = stockError?.let { msg -> { Text(msg) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_initial_stock_input")
                    )
                }

                if (generalError != null) {
                    Text(
                        text = generalError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = {
                            refError = null
                            nomError = null
                            prixError = null
                            seuilError = null
                            stockError = null
                            generalError = null

                            val prix = prixText.toLongOrNull()
                            val seuil = seuilText.toIntOrNull()
                            val stockInit = if (existing == null) stockInitText.toIntOrNull() ?: 0 else 0

                            if (prix == null || prix <= 0) {
                                prixError = "Saisissez un prix entier strictement positif."
                                return@Button
                            }
                            if (seuil == null || seuil < 0) {
                                seuilError = "Saisissez un seuil positif ou nul."
                                return@Button
                            }
                            onSave(reference, nom, selectedCategoryId, prix, seuil, stockInit) { field, msg ->
                                when (field) {
                                    "reference" -> refError = msg
                                    "nom" -> nomError = msg
                                    "prixVente" -> prixError = msg
                                    "seuilAlerte" -> seuilError = msg
                                    "stockInitial" -> stockError = msg
                                    else -> generalError = msg
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_product_button")
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}
