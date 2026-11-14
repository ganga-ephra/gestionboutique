package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ModePaiement
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.StatutVente
import com.example.domain.model.CartLine
import com.example.domain.model.SaleValidationResult
import com.example.domain.model.ServiceResult
import com.example.domain.service.BackupService
import com.example.domain.service.StoreService
import com.example.util.Formatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoreServiceAcceptanceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var service: StoreService
    private var adminId: Long = 0L
    private var sellerId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.build(context, inMemory = true)
        service = StoreService { db }

        val adminRes = service.createFirstAdmin("Jean Gavin", "admin", "secret123", "Magasin Test")
        adminId = (adminRes as ServiceResult.Success).data.id

        val sellerRes = service.createUser(adminId, "Vendeur Un", "vendeur1", "pass1234", RoleUtilisateur.VENDEUR)
        sellerId = (sellerRes as ServiceResult.Success).data.id
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `T01 T02 T03 T04 T05 T06 T09 T10 T11 T12 full acceptance rules`() = runBlocking {
        // T01: Create product with 10 units
        val prodRes = service.createProduct(adminId, "REF-01", "Savon Luxe", null, 2000L, 5, 10)
        assertTrue(prodRes is ServiceResult.Success)
        val prod = (prodRes as ServiceResult.Success).data
        assertEquals(10, prod.stockDisponible)

        // T02: Duplicate reference rejected
        val dupRes = service.createProduct(adminId, "REF-01", "Autre Nom", null, 1500L, 2, 5)
        assertTrue(dupRes is ServiceResult.Error)

        // T03: Sell 3 units at 2000 FCFA -> 6000 FCFA, stock 7
        val sale1 = service.validateSale(
            actorId = sellerId,
            items = listOf(CartLine(prod, 3, prod.prixVente)),
            modePaiement = ModePaiement.ESPECES,
            montantRecuInput = 6000L,
            jetonValidation = UUID.randomUUID().toString()
        )
        assertTrue(sale1 is SaleValidationResult.Success)
        val detail1 = (sale1 as SaleValidationResult.Success).detail
        assertEquals(6000L, detail1.vente.total)
        assertEquals(7, db.produitDao().getStockForProduct(prod.id))

        // T04: Sell 8 units when stock = 7 -> blocked, no partial write
        val saleFail = service.validateSale(
            actorId = sellerId,
            items = listOf(CartLine(prod, 8, prod.prixVente)),
            modePaiement = ModePaiement.CARTE,
            montantRecuInput = 16000L,
            jetonValidation = UUID.randomUUID().toString()
        )
        assertTrue(saleFail is SaleValidationResult.InsufficientStock)
        assertEquals(7, db.produitDao().getStockForProduct(prod.id))

        // T05: Sell remaining 7 units -> stock = 0, EN_RUPTURE, next sale blocked
        val sale2 = service.validateSale(
            actorId = sellerId,
            items = listOf(CartLine(prod, 7, prod.prixVente)),
            modePaiement = ModePaiement.MOBILE_MONEY,
            montantRecuInput = 14000L,
            jetonValidation = UUID.randomUUID().toString()
        )
        assertTrue(sale2 is SaleValidationResult.Success)
        val prodAfterZero = db.produitDao().getByIdWithStock(prod.id)!!
        assertEquals(0, prodAfterZero.stockDisponible)
        assertEquals(StatutDisponibilite.EN_RUPTURE, prodAfterZero.statut)

        val saleAtZero = service.validateSale(
            actorId = sellerId,
            items = listOf(CartLine(prodAfterZero, 1, prodAfterZero.prixVente)),
            modePaiement = ModePaiement.ESPECES,
            montantRecuInput = 2000L,
            jetonValidation = UUID.randomUUID().toString()
        )
        assertTrue(saleAtZero is SaleValidationResult.InsufficientStock)

        // T06: Restock +5 with threshold 5 -> stock 5, STOCK_FAIBLE
        val restock = service.restockProduct(adminId, prod.id, 5, "BL-001")
        assertTrue(restock is ServiceResult.Success)
        val prodAfterRestock = (restock as ServiceResult.Success).data
        assertEquals(5, prodAfterRestock.stockDisponible)
        assertEquals(StatutDisponibilite.STOCK_FAIBLE, prodAfterRestock.statut)

        // T09: Cancel sale1 once -> stock restored (+3 => 8); second cancel refused
        val cancel1 = service.cancelSale(adminId, detail1.vente.id, "Erreur caisse")
        assertTrue(cancel1 is ServiceResult.Success)
        assertEquals(StatutVente.ANNULEE, (cancel1 as ServiceResult.Success).data.vente.statut)
        assertEquals(8, db.produitDao().getStockForProduct(prod.id))

        val cancel2 = service.cancelSale(adminId, detail1.vente.id, "Double annulation")
        assertTrue(cancel2 is ServiceResult.Error)
        assertEquals(8, db.produitDao().getStockForProduct(prod.id))

        // T10: Change price & name -> old ticket snapshot unchanged
        val updateRes = service.updateProduct(adminId, prod.id, "REF-01", "Savon Premium", null, 2500L, 5)
        assertTrue(updateRes is ServiceResult.Success)
        val reloadedDetail1 = service.getSaleDetail(detail1.vente.id)!!
        assertEquals("Savon Luxe", reloadedDetail1.lignes.first().nomSnapshot)
        assertEquals(2000L, reloadedDetail1.lignes.first().prixUnitaire)

        // T11: Report excludes cancelled sale1 (only sale2 = 14000 FCFA, 7 units counted)
        val today = Formatters.businessDateFromUtc(System.currentTimeMillis(), "Africa/Brazzaville")
        val reportRes = service.getPeriodReport(adminId, today, today)
        assertTrue(reportRes is ServiceResult.Success)
        val (summary, _) = (reportRes as ServiceResult.Success).data
        assertEquals(14000L, summary.montantVentes)
        assertEquals(1, summary.nombreVentes)
        assertEquals(7, summary.unitesVendues)

        // T12: Insufficient cash blocked, sufficient cash accepted with exact change
        val refreshedProd = db.produitDao().getByIdWithStock(prod.id)!!
        val cashLow = service.validateSale(
            actorId = sellerId,
            items = listOf(CartLine(refreshedProd, 1, 2500L)),
            modePaiement = ModePaiement.ESPECES,
            montantRecuInput = 2000L,
            jetonValidation = UUID.randomUUID().toString()
        )
        assertTrue(cashLow is SaleValidationResult.Error)

        val cashOk = service.validateSale(
            actorId = sellerId,
            items = listOf(CartLine(refreshedProd, 1, 2500L)),
            modePaiement = ModePaiement.ESPECES,
            montantRecuInput = 5000L,
            jetonValidation = UUID.randomUUID().toString()
        )
        assertTrue(cashOk is SaleValidationResult.Success)
        assertEquals(2500L, (cashOk as SaleValidationResult.Success).detail.vente.monnaieRendue)
    }

    @Test
    fun `T07 T08 T14 T15 T16 T18 idempotency rollback role enforcement and csv safety`() = runBlocking {
        val p1 = (service.createProduct(adminId, "A-1", "Article =Test", null, 1000L, 2, 5) as ServiceResult.Success).data
        val p2 = (service.createProduct(adminId, "A-2", "+Café", null, 500L, 1, 1) as ServiceResult.Success).data

        // T07: Multi-item sale with 1 invalid line -> nothing saved
        val multiFail = service.validateSale(
            actorId = sellerId,
            items = listOf(
                CartLine(p1, 2, p1.prixVente),
                CartLine(p2, 4, p2.prixVente)
            ),
            modePaiement = ModePaiement.ESPECES,
            montantRecuInput = 5000L,
            jetonValidation = UUID.randomUUID().toString()
        )
        assertTrue(multiFail is SaleValidationResult.InsufficientStock)
        assertEquals(5, db.produitDao().getStockForProduct(p1.id))
        assertEquals(1, db.produitDao().getStockForProduct(p2.id))

        // T08: Duplicate jetonValidation creates only 1 sale and 1 exit
        val token = UUID.randomUUID().toString()
        val first = service.validateSale(sellerId, listOf(CartLine(p1, 2, p1.prixVente)), ModePaiement.CARTE, 2000L, token)
        val second = service.validateSale(sellerId, listOf(CartLine(p1, 2, p1.prixVente)), ModePaiement.CARTE, 2000L, token)
        assertTrue(first is SaleValidationResult.Success)
        assertTrue(second is SaleValidationResult.Success)
        assertEquals((first as SaleValidationResult.Success).venteId, (second as SaleValidationResult.Success).venteId)
        assertEquals(3, db.produitDao().getStockForProduct(p1.id))

        // T16: Simulated DB failure rolls back transaction completely
        val rollbackRes = service.validateSale(
            actorId = sellerId,
            items = listOf(CartLine(p1, 1, p1.prixVente)),
            modePaiement = ModePaiement.CARTE,
            montantRecuInput = 1000L,
            jetonValidation = UUID.randomUUID().toString(),
            simulateFailureHook = { throw IllegalStateException("Erreur SQLite simulée") }
        )
        assertTrue(rollbackRes is SaleValidationResult.Error)
        assertEquals(3, db.produitDao().getStockForProduct(p1.id))

        // T14: Vendeur denied administrative actions at service layer
        assertTrue(service.createProduct(sellerId, "X-1", "Interdit", null, 100L, 0, 1) is ServiceResult.Error)
        assertTrue(service.restockProduct(sellerId, p1.id, 5, "Test") is ServiceResult.Error)
        assertTrue(service.cancelSale(sellerId, first.venteId, "Motif") is ServiceResult.Error)
        assertTrue(service.exportStockCsv(sellerId, listOf(p1)) is ServiceResult.Error)

        // T15: Corrupt backup file rejected cleanly
        val invalidFile = File(context.cacheDir, "invalid_backup.db").apply {
            writeText("NOT A SQLITE FILE")
        }
        val validationErr = BackupService(context).validateBackupFile(invalidFile)
        assertTrue(validationErr != null)
        invalidFile.delete()

        // T18: CSV export protects formula characters & preserves UTF-8 accents
        val currentProducts = service.observeProducts().first()
        val csvRes = service.exportStockCsv(adminId, currentProducts)
        assertTrue(csvRes is ServiceResult.Success)
        val csv = (csvRes as ServiceResult.Success).data
        assertTrue(csv.contains("'+Café"))
        assertTrue(csv.contains("Article =Test"))
    }
}
