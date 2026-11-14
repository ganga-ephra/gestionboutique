package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RoleUtilisateur(val libelle: String) {
    ADMINISTRATEUR("Administrateur"),
    VENDEUR("Vendeur")
}

enum class ModePaiement(val libelle: String) {
    ESPECES("Espèces"),
    MOBILE_MONEY("Mobile Money"),
    CARTE("Carte")
}

enum class StatutVente(val libelle: String) {
    VALIDEE("Validée"),
    ANNULEE("Annulée")
}

enum class TypeMouvement(val libelle: String) {
    STOCK_INITIAL("Stock initial"),
    REAPPROVISIONNEMENT("Réapprovisionnement"),
    VENTE_VALIDEE("Vente validée"),
    ANNULATION_VENTE("Annulation de vente"),
    CORRECTION_INVENTAIRE("Correction d'inventaire")
}

@Entity(
    tableName = "utilisateur",
    indices = [Index(value = ["identifiant"], unique = true)]
)
data class UtilisateurEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val identifiant: String,
    val motDePasseHash: String,
    val sel: String,
    val parametresHachage: String,
    val role: RoleUtilisateur,
    val actif: Boolean = true
)

@Entity(
    tableName = "categorie",
    indices = [Index(value = ["nom"], unique = true)]
)
data class CategorieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String
)

@Entity(
    tableName = "produit",
    foreignKeys = [
        ForeignKey(
            entity = CategorieEntity::class,
            parentColumns = ["id"],
            childColumns = ["categorieId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["reference"], unique = true),
        Index(value = ["nom"]),
        Index(value = ["categorieId"])
    ]
)
data class ProduitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reference: String,
    val nom: String,
    val categorieId: Long? = null,
    val prixVente: Long,
    val seuilAlerte: Int,
    val actif: Boolean = true,
    val dateCreationUtc: Long
)

@Entity(
    tableName = "vente",
    foreignKeys = [
        ForeignKey(
            entity = UtilisateurEntity::class,
            parentColumns = ["id"],
            childColumns = ["vendeurId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["numero"], unique = true),
        Index(value = ["jetonValidation"], unique = true),
        Index(value = ["dateMetier", "statut", "vendeurId"]),
        Index(value = ["vendeurId"])
    ]
)
data class VenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numero: String,
    val jetonValidation: String,
    val dateHeureUtc: Long,
    val dateMetier: String,
    val vendeurId: Long,
    val total: Long,
    val modePaiement: ModePaiement,
    val montantRecu: Long,
    val monnaieRendue: Long,
    val statut: StatutVente = StatutVente.VALIDEE
)

@Entity(
    tableName = "ligne_vente",
    foreignKeys = [
        ForeignKey(
            entity = VenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["venteId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProduitEntity::class,
            parentColumns = ["id"],
            childColumns = ["produitId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["venteId", "produitId"], unique = true),
        Index(value = ["produitId"])
    ]
)
data class LigneVenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venteId: Long,
    val produitId: Long,
    val referenceSnapshot: String,
    val nomSnapshot: String,
    val quantite: Int,
    val prixUnitaire: Long,
    val sousTotal: Long
)

@Entity(
    tableName = "annulation_vente",
    foreignKeys = [
        ForeignKey(
            entity = VenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["venteId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UtilisateurEntity::class,
            parentColumns = ["id"],
            childColumns = ["auteurId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["venteId"], unique = true),
        Index(value = ["auteurId"])
    ]
)
data class AnnulationVenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venteId: Long,
    val auteurId: Long,
    val dateHeureUtc: Long,
    val motif: String
)

@Entity(
    tableName = "mouvement_stock",
    foreignKeys = [
        ForeignKey(
            entity = ProduitEntity::class,
            parentColumns = ["id"],
            childColumns = ["produitId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UtilisateurEntity::class,
            parentColumns = ["id"],
            childColumns = ["auteurId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LigneVenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["ligneVenteId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AnnulationVenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["annulationId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["produitId", "dateHeureUtc"]),
        Index(value = ["auteurId"]),
        Index(value = ["ligneVenteId", "type"], unique = true),
        Index(value = ["annulationId"])
    ]
)
data class MouvementStockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val produitId: Long,
    val auteurId: Long,
    val dateHeureUtc: Long,
    val type: TypeMouvement,
    val quantiteSignee: Int,
    val motif: String,
    val ligneVenteId: Long? = null,
    val annulationId: Long? = null,
    val ancienneQuantite: Int? = null,
    val nouvelleQuantite: Int? = null
)

@Entity(
    tableName = "journal_audit",
    foreignKeys = [
        ForeignKey(
            entity = UtilisateurEntity::class,
            parentColumns = ["id"],
            childColumns = ["auteurId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["dateHeureUtc"]),
        Index(value = ["auteurId"])
    ]
)
data class JournalAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val auteurId: Long? = null,
    val dateHeureUtc: Long,
    val action: String,
    val entite: String,
    val entiteId: Long? = null,
    val details: String
)

@Entity(tableName = "parametre")
data class ParametreEntity(
    @PrimaryKey val cle: String,
    val valeur: String
)

@Entity(tableName = "version_schema")
data class VersionSchemaEntity(
    @PrimaryKey val numero: Int,
    val dateApplicationUtc: Long,
    val description: String
)
