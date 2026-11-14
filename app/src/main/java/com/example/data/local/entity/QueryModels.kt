package com.example.data.local.entity

enum class StatutDisponibilite(val libelle: String) {
    EN_RUPTURE("En rupture"),
    STOCK_FAIBLE("Stock faible"),
    DISPONIBLE("Disponible"),
    ARCHIVE("Archivé")
}

data class ProduitAvecStock(
    val id: Long,
    val reference: String,
    val nom: String,
    val categorieId: Long?,
    val categorieNom: String?,
    val prixVente: Long,
    val seuilAlerte: Int,
    val actif: Boolean,
    val dateCreationUtc: Long,
    val stockDisponible: Int
) {
    val statut: StatutDisponibilite
        get() = when {
            !actif -> StatutDisponibilite.ARCHIVE
            stockDisponible <= 0 -> StatutDisponibilite.EN_RUPTURE
            seuilAlerte > 0 && stockDisponible <= seuilAlerte -> StatutDisponibilite.STOCK_FAIBLE
            else -> StatutDisponibilite.DISPONIBLE
        }
}

data class VenteResume(
    val id: Long,
    val numero: String,
    val dateHeureUtc: Long,
    val dateMetier: String,
    val vendeurId: Long,
    val vendeurNom: String,
    val total: Long,
    val modePaiement: ModePaiement,
    val montantRecu: Long,
    val monnaieRendue: Long,
    val statut: StatutVente,
    val nombreUnites: Int
)

data class AnnulationDetail(
    val id: Long,
    val venteId: Long,
    val auteurId: Long,
    val auteurNom: String,
    val dateHeureUtc: Long,
    val motif: String
)

data class VenteDetail(
    val vente: VenteResume,
    val lignes: List<LigneVenteEntity>,
    val annulation: AnnulationDetail?
)

data class MouvementAvecDetails(
    val id: Long,
    val produitId: Long,
    val produitReference: String,
    val produitNom: String,
    val auteurId: Long,
    val auteurNom: String,
    val dateHeureUtc: Long,
    val type: TypeMouvement,
    val quantiteSignee: Int,
    val motif: String,
    val ligneVenteId: Long?,
    val annulationId: Long?,
    val ancienneQuantite: Int?,
    val nouvelleQuantite: Int?
)

data class JournalAuditAvecAuteur(
    val id: Long,
    val auteurId: Long?,
    val auteurNom: String?,
    val dateHeureUtc: Long,
    val action: String,
    val entite: String,
    val entiteId: Long?,
    val details: String
)

data class RapportProduitRow(
    val produitId: Long,
    val reference: String,
    val nom: String,
    val unitesVendues: Int,
    val montantVendu: Long
)

data class IndicateursJour(
    val dateMetier: String,
    val montantVentes: Long,
    val nombreVentes: Int,
    val unitesVendues: Int,
    val referencesRupture: Int,
    val referencesStockFaible: Int,
    val ventesAnnuleesJour: Int
)

data class VentesAggregate(
    val montantVentes: Long,
    val nombreVentes: Int,
    val unitesVendues: Int
)
