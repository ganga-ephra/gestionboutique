package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.UtilisateurEntity
import com.example.domain.model.AppWidthClass
import com.example.domain.model.StoreSettings
import com.example.ui.AppViewModel
import com.example.ui.components.InlineFeedbackBanner
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.NewSaleScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.StocksScreen
import com.example.util.ReceiptPrinter
import kotlinx.coroutines.launch
import java.io.File

enum class AppDestination(
    val route: String,
    val label: String,
    val shortLabel: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false
) {
    DASHBOARD("dashboard", "Tableau de bord", "Accueil", Icons.Default.Dashboard),
    NEW_SALE("new_sale", "Nouvelle vente", "Vente", Icons.Default.AddShoppingCart),
    PRODUCTS("products", "Produits", "Produits", Icons.Default.Inventory2),
    STOCKS("stocks", "Stocks", "Stocks", Icons.Default.Inventory),
    HISTORY("history", "Historique", "Historique", Icons.Default.History),
    REPORTS("reports", "Rapports", "Rapports", Icons.Default.Assessment, adminOnly = true),
    ADMIN("admin", "Administration", "Admin", Icons.Default.AdminPanelSettings, adminOnly = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreAppRoot(viewModel: AppViewModel) {
    val context = LocalContext.current
    val hasAnyAdmin by viewModel.hasAnyAdmin.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    if (currentUser == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (feedback != null) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    InlineFeedbackBanner(
                        text = feedback!!.text,
                        isError = feedback!!.isError,
                        onDismiss = viewModel::clearFeedback
                    )
                }
            }
            AuthScreen(
                hasAnyAdmin = hasAnyAdmin,
                storeSettings = storeSettings,
                onCreateFirstAdmin = viewModel::createFirstAdmin,
                onLogin = viewModel::login
            )
        }
        return
    }

    val user = currentUser!!
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val dailyIndicators by viewModel.dailyIndicators.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val isValidatingSale by viewModel.isValidatingSale.collectAsStateWithLifecycle()
    val priceChangedPrompt by viewModel.priceChangedPrompt.collectAsStateWithLifecycle()
    val lastCompletedSale by viewModel.lastCompletedSale.collectAsStateWithLifecycle()
    val reportSummary by viewModel.reportSummary.collectAsStateWithLifecycle()
    val reportRows by viewModel.reportRows.collectAsStateWithLifecycle()

    var currentDestination by remember(user.id) { mutableStateOf(AppDestination.DASHBOARD) }
    var stockInitialFilter by remember { mutableStateOf<StatutDisponibilite?>(null) }
    var historyTodayOnly by remember { mutableStateOf(false) }

    val availableDestinations = remember(user.role) {
        AppDestination.entries.filter { dest ->
            !dest.adminOnly || user.role == RoleUtilisateur.ADMINISTRATEUR
        }
    }

    LaunchedEffect(user.role, currentDestination) {
        if (currentDestination.adminOnly && user.role != RoleUtilisateur.ADMINISTRATEUR) {
            currentDestination = AppDestination.DASHBOARD
        }
    }

    if (currentDestination != AppDestination.DASHBOARD) {
        BackHandler {
            currentDestination = AppDestination.DASHBOARD
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthClass = when {
            maxWidth < 600.dp -> AppWidthClass.COMPACT
            maxWidth < 840.dp -> AppWidthClass.MEDIUM
            else -> AppWidthClass.EXPANDED
        }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val mainContent: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = currentDestination.label,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${storeSettings.nomMagasin} • ${user.nom} (${user.role.libelle})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            if (widthClass == AppWidthClass.COMPACT) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_menu_button")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Ouvrir le menu")
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = viewModel::logout,
                                modifier = Modifier.testTag("logout_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Déconnexion"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (widthClass == AppWidthClass.COMPACT) {
                        val bottomItems = availableDestinations.take(4)
                        NavigationBar {
                            bottomItems.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentDestination == dest,
                                    onClick = { currentDestination = dest },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.shortLabel, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (widthClass == AppWidthClass.MEDIUM) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            availableDestinations.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentDestination == dest,
                                    onClick = { currentDestination = dest },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.shortLabel, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (feedback != null) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                InlineFeedbackBanner(
                                    text = feedback!!.text,
                                    isError = feedback!!.isError,
                                    onDismiss = viewModel::clearFeedback
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (currentDestination) {
                                AppDestination.DASHBOARD -> DashboardScreen(
                                    widthClass = widthClass,
                                    currentUser = user,
                                    settings = storeSettings,
                                    indicators = dailyIndicators,
                                    products = products,
                                    recentSales = sales,
                                    onOpenNewSale = { currentDestination = AppDestination.NEW_SALE },
                                    onOpenRuptures = {
                                        stockInitialFilter = StatutDisponibilite.EN_RUPTURE
                                        currentDestination = AppDestination.STOCKS
                                    },
                                    onOpenLowStock = {
                                        stockInitialFilter = StatutDisponibilite.STOCK_FAIBLE
                                        currentDestination = AppDestination.STOCKS
                                    },
                                    onOpenSalesToday = {
                                        historyTodayOnly = true
                                        currentDestination = AppDestination.HISTORY
                                    },
                                    onOpenReports = { currentDestination = AppDestination.REPORTS }
                                )

                                AppDestination.NEW_SALE -> NewSaleScreen(
                                    widthClass = widthClass,
                                    products = products,
                                    cart = cart,
                                    isValidatingSale = isValidatingSale,
                                    priceChangedPrompt = priceChangedPrompt,
                                    lastCompletedSale = lastCompletedSale,
                                    storeSettings = storeSettings,
                                    onAddToCart = viewModel::addToCart,
                                    onUpdateQuantity = viewModel::updateCartQuantity,
                                    onRemoveLine = viewModel::removeFromCart,
                                    onClearCart = viewModel::clearCart,
                                    onValidateSale = viewModel::validateSale,
                                    onDismissPricePrompt = viewModel::dismissPriceChangedPrompt,
                                    onDismissCompletedSale = viewModel::dismissCompletedSaleModal,
                                    onPrintReceipt = { detail ->
                                        ReceiptPrinter.printReceipt(context, detail, storeSettings) { err ->
                                            viewModel.showMessage(err, isError = true)
                                        }
                                    }
                                )

                                AppDestination.PRODUCTS -> ProductsScreen(
                                    widthClass = widthClass,
                                    currentUser = user,
                                    products = products,
                                    categories = categories,
                                    onCreateCategory = viewModel::createCategory,
                                    onCreateProduct = viewModel::createProduct,
                                    onUpdateProduct = viewModel::updateProduct,
                                    onSetProductActive = viewModel::setProductActive
                                )

                                AppDestination.STOCKS -> StocksScreen(
                                    currentUser = user,
                                    settings = storeSettings,
                                    products = products,
                                    movements = movements,
                                    initialStatusFilter = stockInitialFilter,
                                    onConsumeInitialFilter = { stockInitialFilter = null },
                                    onRestock = viewModel::restockProduct,
                                    onCorrectInventory = viewModel::correctInventory
                                )

                                AppDestination.HISTORY -> HistoryScreen(
                                    widthClass = widthClass,
                                    currentUser = user,
                                    settings = storeSettings,
                                    sales = sales,
                                    users = users,
                                    initialTodayOnly = historyTodayOnly,
                                    onConsumeInitialToday = { historyTodayOnly = false },
                                    onLoadSaleDetail = viewModel::loadSaleDetail,
                                    onCancelSale = viewModel::cancelSale,
                                    onPrintReceipt = { detail ->
                                        ReceiptPrinter.printReceipt(context, detail, storeSettings) { err ->
                                            viewModel.showMessage(err, isError = true)
                                        }
                                    }
                                )

                                AppDestination.REPORTS -> ReportsScreen(
                                    currentUser = user,
                                    settings = storeSettings,
                                    reportSummary = reportSummary,
                                    reportRows = reportRows,
                                    sales = sales,
                                    products = products,
                                    onLoadReport = viewModel::loadPeriodReport,
                                    onPrepareSalesCsv = viewModel::prepareSalesCsv,
                                    onPrepareStockCsv = viewModel::prepareStockCsv,
                                    onNotify = viewModel::showMessage
                                )

                                AppDestination.ADMIN -> AdminScreen(
                                    currentUser = user,
                                    settings = storeSettings,
                                    users = users,
                                    backups = backups,
                                    auditLogs = auditLogs,
                                    onCreateUser = viewModel::createUser,
                                    onSetUserActive = viewModel::setUserActive,
                                    onResetUserPassword = viewModel::resetUserPassword,
                                    onUpdateStoreSettings = viewModel::updateStoreSettings,
                                    onPickLogoUri = viewModel::updateStoreLogoFromPicker,
                                    onRemoveLogo = viewModel::removeStoreLogo,
                                    onCreateManualBackup = viewModel::createManualBackup,
                                    onExportBackup = viewModel::exportBackupToUri,
                                    onRestoreLocalBackup = viewModel::restoreFromLocalBackup,
                                    onRestoreExternalBackup = viewModel::restoreFromExternalUri
                                )
                            }
                        }
                    }
                }
            }
        }

        if (widthClass == AppWidthClass.EXPANDED) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(260.dp)) {
                        DrawerMenuContent(
                            settings = storeSettings,
                            user = user,
                            destinations = availableDestinations,
                            currentDestination = currentDestination,
                            onSelectDestination = { currentDestination = it },
                            onLogout = viewModel::logout
                        )
                    }
                },
                content = mainContent
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        DrawerMenuContent(
                            settings = storeSettings,
                            user = user,
                            destinations = availableDestinations,
                            currentDestination = currentDestination,
                            onSelectDestination = { dest ->
                                currentDestination = dest
                                scope.launch { drawerState.close() }
                            },
                            onLogout = {
                                scope.launch { drawerState.close() }
                                viewModel.logout()
                            }
                        )
                    }
                },
                content = mainContent
            )
        }
    }
}

@Composable
private fun DrawerMenuContent(
    settings: StoreSettings,
    user: UtilisateurEntity,
    destinations: List<AppDestination>,
    currentDestination: AppDestination,
    onSelectDestination: (AppDestination) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (settings.logoUri.isNotBlank() && File(settings.logoUri).exists()) {
                AsyncImage(
                    model = File(settings.logoUri),
                    contentDescription = "Logo magasin",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = settings.nomMagasin,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${user.nom} (${user.role.libelle})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        destinations.forEach { dest ->
            NavigationDrawerItem(
                label = { Text(dest.label) },
                icon = { Icon(dest.icon, contentDescription = null) },
                selected = currentDestination == dest,
                onClick = { onSelectDestination(dest) },
                modifier = Modifier.testTag("drawer_nav_${dest.route}")
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            label = { Text("Déconnexion") },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            selected = false,
            onClick = onLogout,
            modifier = Modifier.testTag("drawer_logout_button")
        )
    }
}
