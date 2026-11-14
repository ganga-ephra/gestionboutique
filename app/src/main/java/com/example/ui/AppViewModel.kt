package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DatabaseProvider
import com.example.data.local.entity.CategorieEntity
import com.example.data.local.entity.IndicateursJour
import com.example.data.local.entity.JournalAuditAvecAuteur
import com.example.data.local.entity.ModePaiement
import com.example.data.local.entity.MouvementAvecDetails
import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.RapportProduitRow
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.UtilisateurEntity
import com.example.data.local.entity.VenteDetail
import com.example.data.local.entity.VenteResume
import com.example.data.local.entity.VentesAggregate
import com.example.domain.model.BackupEntry
import com.example.domain.model.CartLine
import com.example.domain.model.SaleValidationResult
import com.example.domain.model.ServiceResult
import com.example.domain.model.StoreSettings
import com.example.domain.service.BackupService
import com.example.domain.service.StoreService
import com.example.util.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class FeedbackBanner(
    val text: String,
    val isError: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val storeService = StoreService { DatabaseProvider.getDatabase(appContext) }
    private val backupService = BackupService(appContext)

    private val _hasAnyAdmin = MutableStateFlow<Boolean?>(null)
    val hasAnyAdmin: StateFlow<Boolean?> = _hasAnyAdmin.asStateFlow()

    private val _currentUser = MutableStateFlow<UtilisateurEntity?>(null)
    val currentUser: StateFlow<UtilisateurEntity?> = _currentUser.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackBanner?>(null)
    val feedback: StateFlow<FeedbackBanner?> = _feedback.asStateFlow()

    val storeSettings: StateFlow<StoreSettings> = DatabaseProvider.dbEpoch
        .flatMapLatest { storeService.observeStoreSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreSettings())

    val products: StateFlow<List<ProduitAvecStock>> = DatabaseProvider.dbEpoch
        .flatMapLatest { storeService.observeProducts() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategorieEntity>> = DatabaseProvider.dbEpoch
        .flatMapLatest { storeService.observeCategories() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sales: StateFlow<List<VenteResume>> = combine(
        DatabaseProvider.dbEpoch,
        _currentUser
    ) { _, user -> user }
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else storeService.observeSalesForActor(user)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movements: StateFlow<List<MouvementAvecDetails>> = DatabaseProvider.dbEpoch
        .flatMapLatest { storeService.observeMovements() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<UtilisateurEntity>> = DatabaseProvider.dbEpoch
        .flatMapLatest { storeService.observeUsers() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<JournalAuditAvecAuteur>> = DatabaseProvider.dbEpoch
        .flatMapLatest { storeService.observeAuditLogs() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _backups = MutableStateFlow<List<BackupEntry>>(emptyList())
    val backups: StateFlow<List<BackupEntry>> = _backups.asStateFlow()

    private val _dailyIndicators = MutableStateFlow<IndicateursJour?>(null)
    val dailyIndicators: StateFlow<IndicateursJour?> = _dailyIndicators.asStateFlow()

    // POS / Nouvelle vente state
    private val _cart = MutableStateFlow<List<CartLine>>(emptyList())
    val cart: StateFlow<List<CartLine>> = _cart.asStateFlow()

    private val _isValidatingSale = MutableStateFlow(false)
    val isValidatingSale: StateFlow<Boolean> = _isValidatingSale.asStateFlow()

    private var currentAttemptToken: String = UUID.randomUUID().toString()

    private val _priceChangedPrompt = MutableStateFlow<SaleValidationResult.PriceChanged?>(null)
    val priceChangedPrompt: StateFlow<SaleValidationResult.PriceChanged?> =
        _priceChangedPrompt.asStateFlow()

    private val _lastCompletedSale = MutableStateFlow<VenteDetail?>(null)
    val lastCompletedSale: StateFlow<VenteDetail?> = _lastCompletedSale.asStateFlow()

    // Reports state
    private val _reportSummary = MutableStateFlow<VentesAggregate?>(null)
    val reportSummary: StateFlow<VentesAggregate?> = _reportSummary.asStateFlow()

    private val _reportRows = MutableStateFlow<List<RapportProduitRow>>(emptyList())
    val reportRows: StateFlow<List<RapportProduitRow>> = _reportRows.asStateFlow()

    init {
        refreshAdminExistenceAndAutoBackup()
        viewModelScope.launch {
            combine(products, sales, _currentUser, storeSettings) { prodList, _, user, settings ->
                Triple(prodList, user, settings.fuseauHoraire)
            }.collect { (prodList, user, zoneId) ->
                if (user != null) {
                    val today = Formatters.businessDateFromUtc(System.currentTimeMillis(), zoneId)
                    when (val res = storeService.getDailyIndicators(user.id, today, prodList)) {
                        is ServiceResult.Success -> _dailyIndicators.value = res.data
                        is ServiceResult.Error -> Unit
                    }
                } else {
                    _dailyIndicators.value = null
                }
            }
        }
    }

    private fun refreshAdminExistenceAndAutoBackup() {
        viewModelScope.launch {
            _hasAnyAdmin.value = storeService.hasAnyAdmin()
            val zoneId = storeService.getStoreTimeZone()
            backupService.runDailyAutoBackupIfNeeded(zoneId)
            _backups.value = backupService.listBackups()
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun showMessage(text: String, isError: Boolean = false) {
        _feedback.value = FeedbackBanner(text, isError)
    }

    fun createFirstAdmin(
        nom: String,
        identifiant: String,
        motDePasse: String,
        nomMagasin: String,
        onFieldError: (String?, String) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = storeService.createFirstAdmin(nom, identifiant, motDePasse, nomMagasin)) {
                is ServiceResult.Success -> {
                    _hasAnyAdmin.value = true
                    _currentUser.value = res.data
                    showMessage(res.message)
                }
                is ServiceResult.Error -> {
                    onFieldError(res.field, res.message)
                }
            }
        }
    }

    fun login(
        identifiant: String,
        motDePasse: String,
        onError: (String?, String) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = storeService.authenticate(identifiant, motDePasse)) {
                is ServiceResult.Success -> {
                    _currentUser.value = res.data
                    _cart.value = emptyList()
                    currentAttemptToken = UUID.randomUUID().toString()
                    showMessage(res.message)
                }
                is ServiceResult.Error -> {
                    onError(res.field, res.message)
                }
            }
        }
    }

    fun logout() {
        val actor = _currentUser.value
        viewModelScope.launch {
            if (actor != null) {
                storeService.recordLogout(actor.id)
            }
            _currentUser.value = null
            _cart.value = emptyList()
            _lastCompletedSale.value = null
            _priceChangedPrompt.value = null
            _reportSummary.value = null
            _reportRows.value = emptyList()
        }
    }

    // Products & Categories
    fun createCategory(nom: String, onDone: (Boolean, String) -> Unit) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.createCategory(actorId, nom)) {
                is ServiceResult.Success -> {
                    showMessage(res.message)
                    onDone(true, res.message)
                }
                is ServiceResult.Error -> onDone(false, res.message)
            }
        }
    }

    fun createProduct(
        reference: String,
        nom: String,
        categorieId: Long?,
        prixVente: Long,
        seuilAlerte: Int,
        stockInitial: Int,
        onDone: (Boolean, String?, String) -> Unit
    ) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (
                val res = storeService.createProduct(
                    actorId, reference, nom, categorieId, prixVente, seuilAlerte, stockInitial
                )
            ) {
                is ServiceResult.Success -> {
                    showMessage(res.message)
                    onDone(true, null, res.message)
                }
                is ServiceResult.Error -> onDone(false, res.field, res.message)
            }
        }
    }

    fun updateProduct(
        productId: Long,
        reference: String,
        nom: String,
        categorieId: Long?,
        prixVente: Long,
        seuilAlerte: Int,
        onDone: (Boolean, String?, String) -> Unit
    ) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (
                val res = storeService.updateProduct(
                    actorId, productId, reference, nom, categorieId, prixVente, seuilAlerte
                )
            ) {
                is ServiceResult.Success -> {
                    showMessage(res.message)
                    onDone(true, null, res.message)
                }
                is ServiceResult.Error -> onDone(false, res.field, res.message)
            }
        }
    }

    fun setProductActive(productId: Long, active: Boolean) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.setProductActive(actorId, productId, active)) {
                is ServiceResult.Success -> showMessage(res.message)
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    // Stock movements
    fun restockProduct(
        productId: Long,
        quantite: Int,
        motif: String,
        onDone: (Boolean, String?, String) -> Unit
    ) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.restockProduct(actorId, productId, quantite, motif)) {
                is ServiceResult.Success -> {
                    showMessage(res.message)
                    onDone(true, null, res.message)
                }
                is ServiceResult.Error -> onDone(false, res.field, res.message)
            }
        }
    }

    fun correctInventory(
        productId: Long,
        quantiteComptee: Int,
        motif: String,
        onDone: (Boolean, String?, String) -> Unit
    ) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.correctInventory(actorId, productId, quantiteComptee, motif)) {
                is ServiceResult.Success -> {
                    showMessage(res.message)
                    onDone(true, null, res.message)
                }
                is ServiceResult.Error -> onDone(false, res.field, res.message)
            }
        }
    }

    // Cart / Nouvelle vente
    fun addToCart(product: ProduitAvecStock) {
        if (!product.actif) {
            showMessage("Impossible d'ajouter '${product.nom}' : produit archivé.", isError = true)
            return
        }
        if (product.statut == StatutDisponibilite.EN_RUPTURE || product.stockDisponible <= 0) {
            showMessage("Impossible d'ajouter '${product.nom}' : produit en rupture de stock.", isError = true)
            return
        }
        val current = _cart.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.produit.id == product.id }
        if (existingIdx >= 0) {
            val existing = current[existingIdx]
            val nextQty = existing.quantite + 1
            if (nextQty > product.stockDisponible) {
                showMessage(
                    "Quantité maximale atteinte pour '${product.nom}' (stock disponible : ${product.stockDisponible}).",
                    isError = true
                )
                return
            }
            current[existingIdx] = existing.copy(
                produit = product,
                quantite = nextQty,
                prixUnitaireCapture = product.prixVente
            )
        } else {
            current.add(
                CartLine(
                    produit = product,
                    quantite = 1,
                    prixUnitaireCapture = product.prixVente
                )
            )
        }
        _cart.value = current
        currentAttemptToken = UUID.randomUUID().toString()
    }

    fun updateCartQuantity(productId: Long, newQuantity: Int) {
        if (newQuantity <= 0) {
            removeFromCart(productId)
            return
        }
        val current = _cart.value.toMutableList()
        val idx = current.indexOfFirst { it.produit.id == productId }
        if (idx >= 0) {
            val line = current[idx]
            val latestProduct = products.value.find { it.id == productId } ?: line.produit
            if (newQuantity > latestProduct.stockDisponible) {
                showMessage(
                    "Stock disponible pour '${latestProduct.nom}' : ${latestProduct.stockDisponible}.",
                    isError = true
                )
                return
            }
            current[idx] = line.copy(produit = latestProduct, quantite = newQuantity)
            _cart.value = current
            currentAttemptToken = UUID.randomUUID().toString()
        }
    }

    fun removeFromCart(productId: Long) {
        _cart.value = _cart.value.filterNot { it.produit.id == productId }
        currentAttemptToken = UUID.randomUUID().toString()
    }

    fun clearCart() {
        _cart.value = emptyList()
        currentAttemptToken = UUID.randomUUID().toString()
    }

    fun validateSale(modePaiement: ModePaiement, montantRecu: Long) {
        val actorId = _currentUser.value?.id ?: return
        if (_isValidatingSale.value) return

        val snapshotCart = _cart.value
        val attemptToken = currentAttemptToken

        viewModelScope.launch {
            _isValidatingSale.value = true
            try {
                when (
                    val res = storeService.validateSale(
                        actorId = actorId,
                        items = snapshotCart,
                        modePaiement = modePaiement,
                        montantRecuInput = montantRecu,
                        jetonValidation = attemptToken
                    )
                ) {
                    is SaleValidationResult.Success -> {
                        _cart.value = emptyList()
                        currentAttemptToken = UUID.randomUUID().toString()
                        _lastCompletedSale.value = res.detail
                        showMessage(res.message)
                    }
                    is SaleValidationResult.PriceChanged -> {
                        // Refresh cart captured prices so user can confirm new total
                        _cart.value = _cart.value.map { line ->
                            val freshPrice = res.updatedPrices[line.produit.id]
                            if (freshPrice != null) {
                                line.copy(
                                    prixUnitaireCapture = freshPrice,
                                    produit = line.produit.copy(prixVente = freshPrice)
                                )
                            } else line
                        }
                        currentAttemptToken = UUID.randomUUID().toString()
                        _priceChangedPrompt.value = res
                    }
                    is SaleValidationResult.InsufficientStock -> {
                        showMessage(res.message, isError = true)
                    }
                    is SaleValidationResult.Error -> {
                        showMessage(res.message, isError = true)
                    }
                }
            } finally {
                _isValidatingSale.value = false
            }
        }
    }

    fun dismissPriceChangedPrompt() {
        _priceChangedPrompt.value = null
    }

    fun dismissCompletedSaleModal() {
        _lastCompletedSale.value = null
    }

    suspend fun loadSaleDetail(venteId: Long): VenteDetail? = storeService.getSaleDetail(venteId)

    fun cancelSale(venteId: Long, motif: String, onDone: (Boolean, VenteDetail?, String) -> Unit) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.cancelSale(actorId, venteId, motif)) {
                is ServiceResult.Success -> {
                    showMessage(res.message)
                    onDone(true, res.data, res.message)
                }
                is ServiceResult.Error -> {
                    showMessage(res.message, isError = true)
                    onDone(false, null, res.message)
                }
            }
        }
    }

    // Reports & CSV
    fun loadPeriodReport(startDate: String, endDate: String) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.getPeriodReport(actorId, startDate, endDate)) {
                is ServiceResult.Success -> {
                    _reportSummary.value = res.data.first
                    _reportRows.value = res.data.second
                }
                is ServiceResult.Error -> {
                    showMessage(res.message, isError = true)
                }
            }
        }
    }

    fun prepareSalesCsv(
        filteredSales: List<VenteResume>,
        onReady: (String) -> Unit
    ) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.exportSalesCsv(actorId, filteredSales)) {
                is ServiceResult.Success -> onReady(res.data)
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun prepareStockCsv(
        filteredProducts: List<ProduitAvecStock>,
        onReady: (String) -> Unit
    ) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.exportStockCsv(actorId, filteredProducts)) {
                is ServiceResult.Success -> onReady(res.data)
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    // Administration: Users, Settings, Backup/Restore
    fun createUser(
        nom: String,
        identifiant: String,
        motDePasse: String,
        role: RoleUtilisateur,
        onDone: (Boolean, String?, String) -> Unit
    ) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.createUser(actorId, nom, identifiant, motDePasse, role)) {
                is ServiceResult.Success -> {
                    showMessage(res.message)
                    onDone(true, null, res.message)
                }
                is ServiceResult.Error -> onDone(false, res.field, res.message)
            }
        }
    }

    fun setUserActive(targetUserId: Long, active: Boolean) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.setUserActive(actorId, targetUserId, active)) {
                is ServiceResult.Success -> showMessage(res.message)
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun resetUserPassword(targetUserId: Long, newPassword: String, onDone: (Boolean, String) -> Unit) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = storeService.resetUserPassword(actorId, targetUserId, newPassword)) {
                is ServiceResult.Success -> {
                    showMessage(res.message)
                    onDone(true, res.message)
                }
                is ServiceResult.Error -> onDone(false, res.message)
            }
        }
    }

    fun updateStoreSettings(
        nomLogiciel: String,
        nomMagasin: String,
        coordonneesMagasin: String,
        fuseauHoraire: String
    ) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (
                val res = storeService.updateStoreSettings(
                    actorId, nomLogiciel, nomMagasin, coordonneesMagasin, fuseauHoraire
                )
            ) {
                is ServiceResult.Success -> showMessage(res.message)
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun updateStoreLogoFromPicker(uri: Uri) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val saved = backupService.savePickedLogo(uri)) {
                is ServiceResult.Success -> {
                    val s = storeSettings.value
                    storeService.updateStoreSettings(
                        actorId, s.nomLogiciel, s.nomMagasin, s.coordonneesMagasin, s.fuseauHoraire,
                        logoUri = saved.data
                    )
                    showMessage(saved.message)
                }
                is ServiceResult.Error -> showMessage(saved.message, isError = true)
            }
        }
    }

    fun removeStoreLogo() {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            val s = storeSettings.value
            storeService.updateStoreSettings(
                actorId, s.nomLogiciel, s.nomMagasin, s.coordonneesMagasin, s.fuseauHoraire,
                logoUri = ""
            )
            showMessage("Logo supprimé.")
        }
    }

    fun createManualBackup() {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = backupService.createManualBackup(actorId)) {
                is ServiceResult.Success -> {
                    _backups.value = backupService.listBackups()
                    showMessage(res.message)
                }
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun exportBackupToUri(sourcePath: String, targetUri: Uri) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = backupService.exportBackupToUri(actorId, sourcePath, targetUri)) {
                is ServiceResult.Success -> showMessage(res.message)
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun restoreFromLocalBackup(filePath: String) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = backupService.restoreFromLocalFile(actorId, filePath)) {
                is ServiceResult.Success -> {
                    _backups.value = backupService.listBackups()
                    _hasAnyAdmin.value = storeService.hasAnyAdmin()
                    _currentUser.value = null
                    _cart.value = emptyList()
                    showMessage(res.message)
                }
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun restoreFromExternalUri(sourceUri: Uri) {
        val actorId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val res = backupService.restoreFromUri(actorId, sourceUri)) {
                is ServiceResult.Success -> {
                    _backups.value = backupService.listBackups()
                    _hasAnyAdmin.value = storeService.hasAnyAdmin()
                    _currentUser.value = null
                    _cart.value = emptyList()
                    showMessage(res.message)
                }
                is ServiceResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }
}
