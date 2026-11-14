package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AnnulationDetail
import com.example.data.local.entity.AnnulationVenteEntity
import com.example.data.local.entity.CategorieEntity
import com.example.data.local.entity.JournalAuditAvecAuteur
import com.example.data.local.entity.JournalAuditEntity
import com.example.data.local.entity.LigneVenteEntity
import com.example.data.local.entity.MouvementAvecDetails
import com.example.data.local.entity.MouvementStockEntity
import com.example.data.local.entity.ParametreEntity
import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.ProduitEntity
import com.example.data.local.entity.RapportProduitRow
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutVente
import com.example.data.local.entity.UtilisateurEntity
import com.example.data.local.entity.VenteEntity
import com.example.data.local.entity.VenteResume
import com.example.data.local.entity.VentesAggregate
import com.example.data.local.entity.VersionSchemaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UtilisateurDao {
    @Query("SELECT COUNT(*) FROM utilisateur WHERE role = :role AND actif = 1")
    suspend fun countActiveByRole(role: RoleUtilisateur = RoleUtilisateur.ADMINISTRATEUR): Int

    @Query("SELECT COUNT(*) FROM utilisateur WHERE role = :role AND actif = 1")
    fun observeActiveAdminCount(role: RoleUtilisateur = RoleUtilisateur.ADMINISTRATEUR): Flow<Int>

    @Query("SELECT * FROM utilisateur ORDER BY role ASC, nom ASC")
    fun observeAll(): Flow<List<UtilisateurEntity>>

    @Query("SELECT * FROM utilisateur WHERE identifiant = :identifiant LIMIT 1")
    suspend fun findByIdentifiant(identifiant: String): UtilisateurEntity?

    @Query("SELECT * FROM utilisateur WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UtilisateurEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(utilisateur: UtilisateurEntity): Long

    @Update
    suspend fun update(utilisateur: UtilisateurEntity)
}

@Dao
interface CategorieDao {
    @Query("SELECT * FROM categorie ORDER BY nom ASC")
    fun observeAll(): Flow<List<CategorieEntity>>

    @Query("SELECT * FROM categorie WHERE LOWER(nom) = LOWER(:nom) LIMIT 1")
    suspend fun findByNom(nom: String): CategorieEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(categorie: CategorieEntity): Long
}

@Dao
interface ProduitDao {
    @Query(
        """
        SELECT p.id, p.reference, p.nom, p.categorieId, c.nom AS categorieNom,
               p.prixVente, p.seuilAlerte, p.actif, p.dateCreationUtc,
               COALESCE(SUM(m.quantiteSignee), 0) AS stockDisponible
        FROM produit p
        LEFT JOIN categorie c ON c.id = p.categorieId
        LEFT JOIN mouvement_stock m ON m.produitId = p.id
        GROUP BY p.id
        ORDER BY p.nom ASC
        """
    )
    fun observeAllWithStock(): Flow<List<ProduitAvecStock>>

    @Query(
        """
        SELECT p.id, p.reference, p.nom, p.categorieId, c.nom AS categorieNom,
               p.prixVente, p.seuilAlerte, p.actif, p.dateCreationUtc,
               COALESCE(SUM(m.quantiteSignee), 0) AS stockDisponible
        FROM produit p
        LEFT JOIN categorie c ON c.id = p.categorieId
        LEFT JOIN mouvement_stock m ON m.produitId = p.id
        WHERE p.id = :id
        GROUP BY p.id
        LIMIT 1
        """
    )
    suspend fun getByIdWithStock(id: Long): ProduitAvecStock?

    @Query("SELECT * FROM produit WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ProduitEntity?

    @Query("SELECT * FROM produit WHERE LOWER(reference) = LOWER(:reference) LIMIT 1")
    suspend fun findByReference(reference: String): ProduitEntity?

    @Query("SELECT COALESCE(SUM(quantiteSignee), 0) FROM mouvement_stock WHERE produitId = :produitId")
    suspend fun getStockForProduct(produitId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(produit: ProduitEntity): Long

    @Update
    suspend fun update(produit: ProduitEntity)
}

@Dao
interface VenteDao {
    @Query(
        """
        SELECT v.id, v.numero, v.dateHeureUtc, v.dateMetier, v.vendeurId,
               u.nom AS vendeurNom, v.total, v.modePaiement, v.montantRecu,
               v.monnaieRendue, v.statut,
               COALESCE((SELECT SUM(l.quantite) FROM ligne_vente l WHERE l.venteId = v.id), 0) AS nombreUnites
        FROM vente v
        INNER JOIN utilisateur u ON u.id = v.vendeurId
        ORDER BY v.dateHeureUtc DESC
        """
    )
    fun observeAllResumes(): Flow<List<VenteResume>>

    @Query(
        """
        SELECT v.id, v.numero, v.dateHeureUtc, v.dateMetier, v.vendeurId,
               u.nom AS vendeurNom, v.total, v.modePaiement, v.montantRecu,
               v.monnaieRendue, v.statut,
               COALESCE((SELECT SUM(l.quantite) FROM ligne_vente l WHERE l.venteId = v.id), 0) AS nombreUnites
        FROM vente v
        INNER JOIN utilisateur u ON u.id = v.vendeurId
        WHERE v.vendeurId = :vendeurId
        ORDER BY v.dateHeureUtc DESC
        """
    )
    fun observeResumesForSeller(vendeurId: Long): Flow<List<VenteResume>>

    @Query("SELECT * FROM vente WHERE jetonValidation = :jeton LIMIT 1")
    suspend fun findByJeton(jeton: String): VenteEntity?

    @Query("SELECT * FROM vente WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): VenteEntity?

    @Query("SELECT COUNT(*) FROM vente WHERE dateMetier = :dateMetier")
    suspend fun countSalesOnBusinessDate(dateMetier: String): Int

    @Query(
        """
        SELECT v.id, v.numero, v.dateHeureUtc, v.dateMetier, v.vendeurId,
               u.nom AS vendeurNom, v.total, v.modePaiement, v.montantRecu,
               v.monnaieRendue, v.statut,
               COALESCE((SELECT SUM(l.quantite) FROM ligne_vente l WHERE l.venteId = v.id), 0) AS nombreUnites
        FROM vente v
        INNER JOIN utilisateur u ON u.id = v.vendeurId
        WHERE v.id = :venteId
        LIMIT 1
        """
    )
    suspend fun getResumeById(venteId: Long): VenteResume?

    @Query("SELECT * FROM ligne_vente WHERE venteId = :venteId ORDER BY id ASC")
    suspend fun getLignesForVente(venteId: Long): List<LigneVenteEntity>

    @Query(
        """
        SELECT a.id, a.venteId, a.auteurId, u.nom AS auteurNom, a.dateHeureUtc, a.motif
        FROM annulation_vente a
        INNER JOIN utilisateur u ON u.id = a.auteurId
        WHERE a.venteId = :venteId
        LIMIT 1
        """
    )
    suspend fun getAnnulationForVente(venteId: Long): AnnulationDetail?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVente(vente: VenteEntity): Long

    @Update
    suspend fun updateVente(vente: VenteEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLigne(ligne: LigneVenteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnnulation(annulation: AnnulationVenteEntity): Long
}

@Dao
interface MouvementStockDao {
    @Query(
        """
        SELECT m.id, m.produitId, p.reference AS produitReference, p.nom AS produitNom,
               m.auteurId, u.nom AS auteurNom, m.dateHeureUtc, m.type, m.quantiteSignee,
               m.motif, m.ligneVenteId, m.annulationId, m.ancienneQuantite, m.nouvelleQuantite
        FROM mouvement_stock m
        INNER JOIN produit p ON p.id = m.produitId
        INNER JOIN utilisateur u ON u.id = m.auteurId
        ORDER BY m.dateHeureUtc DESC, m.id DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int = 300): Flow<List<MouvementAvecDetails>>

    @Query(
        """
        SELECT m.id, m.produitId, p.reference AS produitReference, p.nom AS produitNom,
               m.auteurId, u.nom AS auteurNom, m.dateHeureUtc, m.type, m.quantiteSignee,
               m.motif, m.ligneVenteId, m.annulationId, m.ancienneQuantite, m.nouvelleQuantite
        FROM mouvement_stock m
        INNER JOIN produit p ON p.id = m.produitId
        INNER JOIN utilisateur u ON u.id = m.auteurId
        WHERE m.produitId = :produitId
        ORDER BY m.dateHeureUtc DESC, m.id DESC
        """
    )
    fun observeForProduct(produitId: Long): Flow<List<MouvementAvecDetails>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(mouvement: MouvementStockEntity): Long
}

@Dao
interface JournalAuditDao {
    @Query(
        """
        SELECT j.id, j.auteurId, u.nom AS auteurNom, j.dateHeureUtc,
               j.action, j.entite, j.entiteId, j.details
        FROM journal_audit j
        LEFT JOIN utilisateur u ON u.id = j.auteurId
        ORDER BY j.dateHeureUtc DESC, j.id DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int = 300): Flow<List<JournalAuditAvecAuteur>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: JournalAuditEntity): Long
}

@Dao
interface ParametreDao {
    @Query("SELECT * FROM parametre")
    fun observeAll(): Flow<List<ParametreEntity>>

    @Query("SELECT valeur FROM parametre WHERE cle = :cle LIMIT 1")
    suspend fun getValue(cle: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(parametre: ParametreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(parametres: List<ParametreEntity>)

    @Query("SELECT * FROM version_schema ORDER BY numero DESC LIMIT 1")
    suspend fun getLatestSchemaVersion(): VersionSchemaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchemaVersion(version: VersionSchemaEntity)
}

@Dao
interface RapportDao {
    @Query(
        """
        SELECT COALESCE(SUM(v.total), 0) AS montantVentes,
               COUNT(DISTINCT v.id) AS nombreVentes,
               COALESCE((
                   SELECT SUM(l.quantite)
                   FROM ligne_vente l
                   INNER JOIN vente v2 ON v2.id = l.venteId
                   WHERE v2.statut = :statutValidee
                     AND v2.dateMetier BETWEEN :startDate AND :endDate
                     AND (:vendeurId IS NULL OR v2.vendeurId = :vendeurId)
               ), 0) AS unitesVendues
        FROM vente v
        WHERE v.statut = :statutValidee
          AND v.dateMetier BETWEEN :startDate AND :endDate
          AND (:vendeurId IS NULL OR v.vendeurId = :vendeurId)
        """
    )
    suspend fun getSalesAggregate(
        startDate: String,
        endDate: String,
        vendeurId: Long?,
        statutValidee: StatutVente = StatutVente.VALIDEE
    ): VentesAggregate

    @Query(
        """
        SELECT COUNT(*) FROM vente
        WHERE statut = :statutAnnulee
          AND dateMetier BETWEEN :startDate AND :endDate
          AND (:vendeurId IS NULL OR vendeurId = :vendeurId)
        """
    )
    suspend fun countCancelledSales(
        startDate: String,
        endDate: String,
        vendeurId: Long?,
        statutAnnulee: StatutVente = StatutVente.ANNULEE
    ): Int

    @Query(
        """
        SELECT l.produitId AS produitId,
               p.reference AS reference,
               p.nom AS nom,
               SUM(l.quantite) AS unitesVendues,
               SUM(l.sousTotal) AS montantVendu
        FROM ligne_vente l
        INNER JOIN vente v ON v.id = l.venteId
        INNER JOIN produit p ON p.id = l.produitId
        WHERE v.statut = :statutValidee
          AND v.dateMetier BETWEEN :startDate AND :endDate
        GROUP BY l.produitId
        ORDER BY montantVendu DESC, unitesVendues DESC
        """
    )
    suspend fun getProductSalesReport(
        startDate: String,
        endDate: String,
        statutValidee: StatutVente = StatutVente.VALIDEE
    ): List<RapportProduitRow>
}
