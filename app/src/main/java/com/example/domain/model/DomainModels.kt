package com.example.domain.model

import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.VenteDetail

enum class AppWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

data class CartLine(
    val produit: ProduitAvecStock,
    val quantite: Int,
    val prixUnitaireCapture: Long
) {
    val sousTotal: Long get() = quantite.toLong() * prixUnitaireCapture
}

sealed interface ServiceResult<out T> {
    data class Success<T>(val data: T, val message: String) : ServiceResult<T>
    data class Error(val message: String, val field: String? = null) : ServiceResult<Nothing>
}

sealed interface SaleValidationResult {
    data class Success(val venteId: Long, val detail: VenteDetail, val message: String) :
        SaleValidationResult

    data class PriceChanged(
        val updatedPrices: Map<Long, Long>,
        val newTotal: Long,
        val message: String
    ) : SaleValidationResult

    data class InsufficientStock(
        val offendingProducts: List<String>,
        val message: String
    ) : SaleValidationResult

    data class Error(val message: String) : SaleValidationResult
}

data class StoreSettings(
    val nomLogiciel: String = "Gestion Magasin",
    val nomMagasin: String = "Mon Magasin",
    val coordonneesMagasin: String = "Point de vente principal",
    val logoUri: String = "",
    val devise: String = "FCFA XAF",
    val fuseauHoraire: String = "Africa/Brazzaville",
    val derniereSauvegardeAutoDate: String = "",
    val derniereSauvegardeAutoHeureUtc: Long? = null,
    val derniereSauvegardeAutoResultat: String = "",
    val derniereSauvegardeManuelleUtc: Long? = null,
    val retentionSauvegardes: Int = 7
)

data class BackupEntry(
    val fileName: String,
    val filePath: String,
    val createdAtUtc: Long,
    val sizeBytes: Long,
    val isAutomatic: Boolean
)
