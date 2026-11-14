package com.example.domain.service

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.ParametreKeys
import com.example.data.local.entity.AnnulationVenteEntity
import com.example.data.local.entity.CategorieEntity
import com.example.data.local.entity.IndicateursJour
import com.example.data.local.entity.JournalAuditAvecAuteur
import com.example.data.local.entity.JournalAuditEntity
import com.example.data.local.entity.LigneVenteEntity
import com.example.data.local.entity.ModePaiement
import com.example.data.local.entity.MouvementAvecDetails
import com.example.data.local.entity.MouvementStockEntity
import com.example.data.local.entity.ParametreEntity
import com.example.data.local.entity.ProduitAvecStock
import com.example.data.local.entity.ProduitEntity
import com.example.data.local.entity.RapportProduitRow
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutDisponibilite
import com.example.data.local.entity.StatutVente
import com.example.data.local.entity.TypeMouvement
import com.example.data.local.entity.UtilisateurEntity
import com.example.data.local.entity.VenteDetail
import com.example.data.local.entity.VenteEntity
import com.example.data.local.entity.VenteResume
import com.example.data.local.entity.VentesAggregate
import com.example.domain.model.CartLine
import com.example.domain.model.SaleValidationResult
import com.example.domain.model.ServiceResult
import com.example.domain.model.StoreSettings
import com.example.domain.security.PasswordHasher
import com.example.util.Formatters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StoreService(
    private val dbProvider: () -> AppDatabase
) {
    private val db: AppDatabase
        get() = dbProvider()

    fun observeProducts(): Flow<List<ProduitAvecStock>> = db.produitDao().observeAllWithStock()

    fun observeCategories(): Flow<List<CategorieEntity>> = db.categorieDao().observeAll()

    fun observeUsers(): Flow<List<UtilisateurEntity>> = db.utilisateurDao().observeAll()

    fun observeMovements(limit: Int = 300): Flow<List<MouvementAvecDetails>> =
        db.mouvementStockDao().observeRecent(limit)

    fun observeMovementsForProduct(productId: Long): Flow<List<MouvementAvecDetails>> =
        db.mouvementStockDao().observeForProduct(productId)

    fun observeAuditLogs(limit: Int = 300): Flow<List<JournalAuditAvecAuteur>> =
        db.journalAuditDao().observeRecent(limit)

    fun observeSalesForActor(actor: UtilisateurEntity): Flow<List<VenteResume>> {
        return if (actor.role == RoleUtilisateur.ADMINISTRATEUR) {
            db.venteDao().observeAllResumes()
        } else {
            db.venteDao().observeResumesForSeller(actor.id)
        }
    }

    fun observeStoreSettings(): Flow<StoreSettings> {
        return db.parametreDao().observeAll().map { rows ->
            val map = rows.associate { it.cle to it.valeur }
            StoreSettings(
                nomLogiciel = map[ParametreKeys.NOM_LOGICIEL]?.ifBlank { null } ?: "Gestion Magasin",
                nomMagasin = map[ParametreKeys.NOM_MAGASIN]?.ifBlank { null } ?: "Mon Magasin",
                coordonneesMagasin = map[ParametreKeys.COORDONNEES_MAGASIN] ?: "",
                logoUri = map[ParametreKeys.LOGO_URI] ?: "",
                devise = map[ParametreKeys.DEVISE]?.ifBlank { null } ?: "FCFA XAF",
                fuseauHoraire = map[ParametreKeys.FUSEAU_HORAIRE]?.ifBlank { null }
                    ?: "Africa/Brazzaville",
                derniereSauvegardeAutoDate = map[ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_DATE] ?: "",
                derniereSauvegardeAutoHeureUtc =
                    map[ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_HEURE_UTC]?.toLongOrNull(),
                derniereSauvegardeAutoResultat =
                    map[ParametreKeys.DERNIERE_SAUVEGARDE_AUTO_RESULTAT] ?: "",
                derniereSauvegardeManuelleUtc =
                    map[ParametreKeys.DERNIERE_SAUVEGARDE_MANUELLE_UTC]?.toLongOrNull(),
                retentionSauvegardes =
                    map[ParametreKeys.RETENTION_SAUVEGARDES]?.toIntOrNull()?.coerceIn(1, 30) ?: 7
            )
        }
    }

    suspend fun getStoreTimeZone(): String {
        return db.parametreDao().getValue(ParametreKeys.FUSEAU_HORAIRE)
            ?.ifBlank { null } ?: "Africa/Brazzaville"
    }

    suspend fun hasAnyAdmin(): Boolean =
        db.utilisateurDao().countActiveByRole(RoleUtilisateur.ADMINISTRATEUR) > 0

    suspend fun createFirstAdmin(
        nom: String,
        identifiant: String,
        motDePasse: String,
        nomMagasin: String? = null
    ): ServiceResult<UtilisateurEntity> {
        val trimmedName = nom.trim()
        val trimmedId = identifiant.trim()
        if (trimmedName.isBlank()) {
            return ServiceResult.Error("Le nom complet est obligatoire.", field = "nom")
        }
        if (trimmedId.length < 3) {
            return ServiceResult.Error(
                "L'identifiant doit contenir au moins 3 caractères.",
                field = "identifiant"
            )
        }
        if (motDePasse.length < 4) {
            return ServiceResult.Error(
                "Le mot de passe doit contenir au moins 4 caractères.",
                field = "motDePasse"
            )
        }
        if (hasAnyAdmin()) {
            return ServiceResult.Error("Un administrateur existe déjà.")
        }

        val hashed = PasswordHasher.hashPassword(motDePasse)
        return db.withTransaction {
            val existing = db.utilisateurDao().findByIdentifiant(trimmedId)
            if (existing != null) {
                return@withTransaction ServiceResult.Error(
                    "Cet identifiant est déjà utilisé.",
                    field = "identifiant"
                )
            }
            val user = UtilisateurEntity(
                nom = trimmedName,
                identifiant = trimmedId,
                motDePasseHash = hashed.hashHex,
                sel = hashed.saltHex,
                parametresHachage = hashed.parameters,
                role = RoleUtilisateur.ADMINISTRATEUR,
                actif = true
            )
            val id = db.utilisateurDao().insert(user)
            val saved = user.copy(id = id)
            if (!nomMagasin.isNullOrBlank()) {
                db.parametreDao().upsert(
                    ParametreEntity(ParametreKeys.NOM_MAGASIN, nomMagasin.trim())
                )
            }
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = id,
                    dateHeureUtc = System.currentTimeMillis(),
                    action = "CREATION_PREMIER_ADMIN",
                    entite = "Utilisateur",
                    entiteId = id,
                    details = "Initialisation du premier administrateur ($trimmedId)"
                )
            )
            ServiceResult.Success(saved, "Compte administrateur '$trimmedName' créé.")
        }
    }

    suspend fun authenticate(
        identifiant: String,
        motDePasse: String
    ): ServiceResult<UtilisateurEntity> {
        val trimmedId = identifiant.trim()
        if (trimmedId.isBlank()) {
            return ServiceResult.Error("Veuillez saisir votre identifiant.", field = "identifiant")
        }
        if (motDePasse.isBlank()) {
            return ServiceResult.Error("Veuillez saisir votre mot de passe.", field = "motDePasse")
        }
        val user = db.utilisateurDao().findByIdentifiant(trimmedId)
        val now = System.currentTimeMillis()
        if (user == null) {
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = null,
                    dateHeureUtc = now,
                    action = "CONNEXION_ECHOUEE",
                    entite = "Utilisateur",
                    entiteId = null,
                    details = "Tentative de connexion avec identifiant inconnu: $trimmedId"
                )
            )
            return ServiceResult.Error("Identifiant ou mot de passe incorrect.")
        }
        if (!user.actif) {
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = user.id,
                    dateHeureUtc = now,
                    action = "CONNEXION_REFUSEE_INACTIF",
                    entite = "Utilisateur",
                    entiteId = user.id,
                    details = "Tentative de connexion sur compte désactivé (${user.identifiant})"
                )
            )
            return ServiceResult.Error("Ce compte est désactivé. Contactez un administrateur.")
        }
        val valid = PasswordHasher.verifyPassword(
            password = motDePasse,
            expectedHashHex = user.motDePasseHash,
            saltHex = user.sel,
            parameters = user.parametresHachage
        )
        if (!valid) {
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = user.id,
                    dateHeureUtc = now,
                    action = "CONNEXION_ECHOUEE",
                    entite = "Utilisateur",
                    entiteId = user.id,
                    details = "Mot de passe incorrect pour ${user.identifiant}"
                )
            )
            return ServiceResult.Error("Identifiant ou mot de passe incorrect.")
        }
        db.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = user.id,
                dateHeureUtc = now,
                action = "CONNEXION_REUSSIE",
                entite = "Utilisateur",
                entiteId = user.id,
                details = "Connexion réussie (${user.role.libelle})"
            )
        )
        return ServiceResult.Success(user, "Bienvenue, ${user.nom}")
    }

    suspend fun recordLogout(actorId: Long) {
        val user = db.utilisateurDao().findById(actorId) ?: return
        db.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = user.id,
                dateHeureUtc = System.currentTimeMillis(),
                action = "DECONNEXION",
                entite = "Utilisateur",
                entiteId = user.id,
                details = "Déconnexion de ${user.identifiant}"
            )
        )
    }

    private suspend fun requireAdmin(actorId: Long): ServiceResult<UtilisateurEntity> {
        val actor = db.utilisateurDao().findById(actorId)
            ?: return ServiceResult.Error("Accès refusé : utilisateur introuvable.")
        if (!actor.actif) {
            return ServiceResult.Error("Accès refusé : compte désactivé.")
        }
        if (actor.role != RoleUtilisateur.ADMINISTRATEUR) {
            return ServiceResult.Error("Accès refusé : action réservée à l'administrateur.")
        }
        return ServiceResult.Success(actor, "OK")
    }

    private suspend fun requireActiveUser(actorId: Long): ServiceResult<UtilisateurEntity> {
        val actor = db.utilisateurDao().findById(actorId)
            ?: return ServiceResult.Error("Accès refusé : utilisateur introuvable.")
        if (!actor.actif) {
            return ServiceResult.Error("Accès refusé : compte désactivé.")
        }
        return ServiceResult.Success(actor, "OK")
    }

    suspend fun createCategory(actorId: Long, nom: String): ServiceResult<CategorieEntity> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth
        val trimmed = nom.trim()
        if (trimmed.isBlank()) {
            return ServiceResult.Error("Le nom de la catégorie est obligatoire.", field = "nom")
        }
        val existing = db.categorieDao().findByNom(trimmed)
        if (existing != null) {
            return ServiceResult.Error("Cette catégorie existe déjà.", field = "nom")
        }
        val id = db.categorieDao().insert(CategorieEntity(nom = trimmed))
        val created = CategorieEntity(id = id, nom = trimmed)
        db.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = actorId,
                dateHeureUtc = System.currentTimeMillis(),
                action = "CREATION_CATEGORIE",
                entite = "Categorie",
                entiteId = id,
                details = "Catégorie créée : $trimmed"
            )
        )
        return ServiceResult.Success(created, "Catégorie '$trimmed' créée.")
    }

    suspend fun createProduct(
        actorId: Long,
        reference: String,
        nom: String,
        categorieId: Long?,
        prixVente: Long,
        seuilAlerte: Int,
        stockInitial: Int
    ): ServiceResult<ProduitAvecStock> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth

        val ref = reference.trim()
        val name = nom.trim()
        if (ref.isBlank()) {
            return ServiceResult.Error("La référence est obligatoire.", field = "reference")
        }
        if (name.isBlank()) {
            return ServiceResult.Error("Le nom du produit est obligatoire.", field = "nom")
        }
        if (prixVente <= 0) {
            return ServiceResult.Error(
                "Le prix de vente doit être strictement positif.",
                field = "prixVente"
            )
        }
        if (seuilAlerte < 0) {
            return ServiceResult.Error(
                "Le seuil d'alerte doit être positif ou nul.",
                field = "seuilAlerte"
            )
        }
        if (stockInitial < 0) {
            return ServiceResult.Error(
                "Le stock initial ne peut pas être négatif.",
                field = "stockInitial"
            )
        }

        return db.withTransaction {
            val existing = db.produitDao().findByReference(ref)
            if (existing != null) {
                return@withTransaction ServiceResult.Error(
                    "La référence '$ref' est déjà utilisée.",
                    field = "reference"
                )
            }
            val now = System.currentTimeMillis()
            val product = ProduitEntity(
                reference = ref,
                nom = name,
                categorieId = categorieId,
                prixVente = prixVente,
                seuilAlerte = seuilAlerte,
                actif = true,
                dateCreationUtc = now
            )
            val productId = db.produitDao().insert(product)
            if (stockInitial > 0) {
                db.mouvementStockDao().insert(
                    MouvementStockEntity(
                        produitId = productId,
                        auteurId = actorId,
                        dateHeureUtc = now,
                        type = TypeMouvement.STOCK_INITIAL,
                        quantiteSignee = stockInitial,
                        motif = "Stock initial à la création",
                        ancienneQuantite = 0,
                        nouvelleQuantite = stockInitial
                    )
                )
            }
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = actorId,
                    dateHeureUtc = now,
                    action = "CREATION_PRODUIT",
                    entite = "Produit",
                    entiteId = productId,
                    details = "Produit $ref ($name) créé, prix=$prixVente FCFA, stock initial=$stockInitial"
                )
            )
            val loaded = db.produitDao().getByIdWithStock(productId)
                ?: return@withTransaction ServiceResult.Error("Erreur lors du chargement du produit.")
            ServiceResult.Success(loaded, "Produit '$name' ($ref) créé avec succès.")
        }
    }

    suspend fun updateProduct(
        actorId: Long,
        productId: Long,
        reference: String,
        nom: String,
        categorieId: Long?,
        prixVente: Long,
        seuilAlerte: Int
    ): ServiceResult<ProduitAvecStock> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth

        val ref = reference.trim()
        val name = nom.trim()
        if (ref.isBlank()) {
            return ServiceResult.Error("La référence est obligatoire.", field = "reference")
        }
        if (name.isBlank()) {
            return ServiceResult.Error("Le nom du produit est obligatoire.", field = "nom")
        }
        if (prixVente <= 0) {
            return ServiceResult.Error(
                "Le prix de vente doit être strictement positif.",
                field = "prixVente"
            )
        }
        if (seuilAlerte < 0) {
            return ServiceResult.Error(
                "Le seuil d'alerte doit être positif ou nul.",
                field = "seuilAlerte"
            )
        }

        return db.withTransaction {
            val current = db.produitDao().findById(productId)
                ?: return@withTransaction ServiceResult.Error("Produit introuvable.")
            val refConflict = db.produitDao().findByReference(ref)
            if (refConflict != null && refConflict.id != productId) {
                return@withTransaction ServiceResult.Error(
                    "La référence '$ref' est déjà utilisée par un autre produit.",
                    field = "reference"
                )
            }
            val updated = current.copy(
                reference = ref,
                nom = name,
                categorieId = categorieId,
                prixVente = prixVente,
                seuilAlerte = seuilAlerte
            )
            db.produitDao().update(updated)
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = actorId,
                    dateHeureUtc = System.currentTimeMillis(),
                    action = "MODIFICATION_PRODUIT",
                    entite = "Produit",
                    entiteId = productId,
                    details = "Produit $ref ($name) modifié : prix=${Formatters.formatFcfa(prixVente)}, seuil=$seuilAlerte"
                )
            )
            val loaded = db.produitDao().getByIdWithStock(productId)
                ?: return@withTransaction ServiceResult.Error("Erreur lors du rechargement.")
            ServiceResult.Success(loaded, "Produit '$name' mis à jour.")
        }
    }

    suspend fun setProductActive(
        actorId: Long,
        productId: Long,
        active: Boolean
    ): ServiceResult<ProduitAvecStock> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth

        return db.withTransaction {
            val current = db.produitDao().findById(productId)
                ?: return@withTransaction ServiceResult.Error("Produit introuvable.")
            if (current.actif == active) {
                val loaded = db.produitDao().getByIdWithStock(productId)!!
                return@withTransaction ServiceResult.Success(loaded, "Statut inchangé.")
            }
            db.produitDao().update(current.copy(actif = active))
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = actorId,
                    dateHeureUtc = System.currentTimeMillis(),
                    action = if (active) "REACTIVATION_PRODUIT" else "ARCHIVAGE_PRODUIT",
                    entite = "Produit",
                    entiteId = productId,
                    details = "${if (active) "Réactivation" else "Archivage"} de ${current.reference} (${current.nom})"
                )
            )
            val loaded = db.produitDao().getByIdWithStock(productId)!!
            val label = if (active) "Produit '${current.nom}' réactivé." else "Produit '${current.nom}' archivé."
            ServiceResult.Success(loaded, label)
        }
    }

    suspend fun restockProduct(
        actorId: Long,
        productId: Long,
        quantite: Int,
        motif: String
    ): ServiceResult<ProduitAvecStock> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth

        if (quantite <= 0) {
            return ServiceResult.Error(
                "La quantité de réapprovisionnement doit être strictement positive.",
                field = "quantite"
            )
        }
        val cleanMotif = motif.trim()
        if (cleanMotif.isBlank()) {
            return ServiceResult.Error("Le motif ou la référence d'entrée est obligatoire.", field = "motif")
        }

        return db.withTransaction {
            val product = db.produitDao().findById(productId)
                ?: return@withTransaction ServiceResult.Error("Produit introuvable.")
            if (!product.actif) {
                return@withTransaction ServiceResult.Error(
                    "Produit archivé : réactivez le produit avant de le réapprovisionner."
                )
            }
            val oldStock = db.produitDao().getStockForProduct(productId)
            val newStock = oldStock + quantite
            val now = System.currentTimeMillis()
            db.mouvementStockDao().insert(
                MouvementStockEntity(
                    produitId = productId,
                    auteurId = actorId,
                    dateHeureUtc = now,
                    type = TypeMouvement.REAPPROVISIONNEMENT,
                    quantiteSignee = quantite,
                    motif = cleanMotif,
                    ancienneQuantite = oldStock,
                    nouvelleQuantite = newStock
                )
            )
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = actorId,
                    dateHeureUtc = now,
                    action = "REAPPROVISIONNEMENT",
                    entite = "Produit",
                    entiteId = productId,
                    details = "${product.reference} (+$quantite) : $oldStock -> $newStock | $cleanMotif"
                )
            )
            val loaded = db.produitDao().getByIdWithStock(productId)!!
            ServiceResult.Success(
                loaded,
                "Réapprovisionnement enregistré (+$quantite sur ${product.nom}, nouveau stock : $newStock)."
            )
        }
    }

    suspend fun correctInventory(
        actorId: Long,
        productId: Long,
        quantiteComptee: Int,
        motif: String
    ): ServiceResult<ProduitAvecStock> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth

        if (quantiteComptee < 0) {
            return ServiceResult.Error(
                "La quantité comptée ne peut pas être négative.",
                field = "quantiteComptee"
            )
        }
        val cleanMotif = motif.trim()
        if (cleanMotif.isBlank()) {
            return ServiceResult.Error("Le motif de correction est obligatoire.", field = "motif")
        }

        return db.withTransaction {
            val product = db.produitDao().findById(productId)
                ?: return@withTransaction ServiceResult.Error("Produit introuvable.")
            val oldStock = db.produitDao().getStockForProduct(productId)
            val ecart = quantiteComptee - oldStock
            if (ecart == 0) {
                return@withTransaction ServiceResult.Error(
                    "La quantité comptée ($quantiteComptee) est identique au stock actuel."
                )
            }
            val now = System.currentTimeMillis()
            db.mouvementStockDao().insert(
                MouvementStockEntity(
                    produitId = productId,
                    auteurId = actorId,
                    dateHeureUtc = now,
                    type = TypeMouvement.CORRECTION_INVENTAIRE,
                    quantiteSignee = ecart,
                    motif = cleanMotif,
                    ancienneQuantite = oldStock,
                    nouvelleQuantite = quantiteComptee
                )
            )
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = actorId,
                    dateHeureUtc = now,
                    action = "CORRECTION_INVENTAIRE",
                    entite = "Produit",
                    entiteId = productId,
                    details = "${product.reference} : ancien=$oldStock, compté=$quantiteComptee, écart=${if (ecart > 0) "+$ecart" else "$ecart"} | Motif: $cleanMotif"
                )
            )
            val loaded = db.produitDao().getByIdWithStock(productId)!!
            val signedLabel = if (ecart > 0) "+$ecart" else "$ecart"
            ServiceResult.Success(
                loaded,
                "Correction enregistrée ($signedLabel) pour ${product.nom}. Nouveau stock : $quantiteComptee."
            )
        }
    }

    suspend fun validateSale(
        actorId: Long,
        items: List<CartLine>,
        modePaiement: ModePaiement,
        montantRecuInput: Long,
        jetonValidation: String,
        simulateFailureHook: (() -> Unit)? = null
    ): SaleValidationResult {
        val auth = requireActiveUser(actorId)
        if (auth is ServiceResult.Error) {
            return SaleValidationResult.Error(auth.message)
        }
        if (items.isEmpty()) {
            return SaleValidationResult.Error("Le panier est vide.")
        }
        if (items.any { it.quantite <= 0 }) {
            return SaleValidationResult.Error("Toutes les quantités doivent être strictement positives.")
        }

        // Merge duplicate product lines defensively
        val consolidated = items.groupBy { it.produit.id }.map { (_, lines) ->
            val first = lines.first()
            first.copy(quantite = lines.sumOf { it.quantite })
        }

        val zoneId = getStoreTimeZone()

        return try {
            val savedId = db.withTransaction {
                // Idempotency guard (T08): never create two sales for the same attempt token
                val existingSale = db.venteDao().findByJeton(jetonValidation)
                if (existingSale != null) {
                    return@withTransaction existingSale.id
                }

                val updatedPrices = mutableMapOf<Long, Long>()
                val offendingStock = mutableListOf<String>()
                val authoritativeLines = mutableListOf<Triple<ProduitEntity, Int, Long>>()

                for (line in consolidated) {
                    val freshProduct = db.produitDao().findById(line.produit.id)
                    if (freshProduct == null || !freshProduct.actif) {
                        offendingStock.add("${line.produit.nom} (produit indisponible/archivé)")
                        continue
                    }
                    val freshStock = db.produitDao().getStockForProduct(freshProduct.id)
                    if (freshStock <= 0 || freshStock < line.quantite) {
                        offendingStock.add(
                            "${freshProduct.nom} (demandé: ${line.quantite}, disponible: $freshStock)"
                        )
                    }
                    if (freshProduct.prixVente != line.prixUnitaireCapture) {
                        updatedPrices[freshProduct.id] = freshProduct.prixVente
                    }
                    authoritativeLines.add(Triple(freshProduct, line.quantite, freshProduct.prixVente))
                }

                if (offendingStock.isNotEmpty()) {
                    throw SaleAbortException.Stock(offendingStock)
                }

                val authoritativeTotal = authoritativeLines.sumOf { (_, qty, price) ->
                    qty.toLong() * price
                }

                if (updatedPrices.isNotEmpty()) {
                    throw SaleAbortException.Price(updatedPrices, authoritativeTotal)
                }

                val effectiveReceived: Long
                val change: Long
                if (modePaiement == ModePaiement.ESPECES) {
                    if (montantRecuInput < authoritativeTotal) {
                        throw SaleAbortException.Generic(
                            "Somme remise insuffisante : total de ${Formatters.formatFcfa(authoritativeTotal)}."
                        )
                    }
                    effectiveReceived = montantRecuInput
                    change = montantRecuInput - authoritativeTotal
                } else {
                    effectiveReceived = authoritativeTotal
                    change = 0L
                }

                val now = System.currentTimeMillis()
                val dateMetier = Formatters.businessDateFromUtc(now, zoneId)
                val seqStart = db.venteDao().countSalesOnBusinessDate(dateMetier) + 1
                val numero = "V-${Formatters.compactDateForNumber(dateMetier)}-${seqStart.toString().padStart(4, '0')}"

                val venteId = db.venteDao().insertVente(
                    VenteEntity(
                        numero = numero,
                        jetonValidation = jetonValidation,
                        dateHeureUtc = now,
                        dateMetier = dateMetier,
                        vendeurId = actorId,
                        total = authoritativeTotal,
                        modePaiement = modePaiement,
                        montantRecu = effectiveReceived,
                        monnaieRendue = change,
                        statut = StatutVente.VALIDEE
                    )
                )

                for ((product, qty, unitPrice) in authoritativeLines) {
                    val sousTotal = qty.toLong() * unitPrice
                    val ligneId = db.venteDao().insertLigne(
                        LigneVenteEntity(
                            venteId = venteId,
                            produitId = product.id,
                            referenceSnapshot = product.reference,
                            nomSnapshot = product.nom,
                            quantite = qty,
                            prixUnitaire = unitPrice,
                            sousTotal = sousTotal
                        )
                    )
                    db.mouvementStockDao().insert(
                        MouvementStockEntity(
                            produitId = product.id,
                            auteurId = actorId,
                            dateHeureUtc = now,
                            type = TypeMouvement.VENTE_VALIDEE,
                            quantiteSignee = -qty,
                            motif = "Vente $numero",
                            ligneVenteId = ligneId
                        )
                    )
                }

                // Hook for T16 transaction rollback verification
                simulateFailureHook?.invoke()

                db.journalAuditDao().insert(
                    JournalAuditEntity(
                        auteurId = actorId,
                        dateHeureUtc = now,
                        action = "VENTE_VALIDEE",
                        entite = "Vente",
                        entiteId = venteId,
                        details = "Vente $numero validée (${modePaiement.libelle}) : ${Formatters.formatFcfa(authoritativeTotal)}"
                    )
                )
                venteId
            }

            val detail = getSaleDetail(savedId)
                ?: return SaleValidationResult.Error("Vente enregistrée mais détail introuvable.")
            SaleValidationResult.Success(
                venteId = savedId,
                detail = detail,
                message = "Vente ${detail.vente.numero} enregistrée (${Formatters.formatFcfa(detail.vente.total)})."
            )
        } catch (abort: SaleAbortException.Stock) {
            SaleValidationResult.InsufficientStock(
                offendingProducts = abort.products,
                message = "Stock insuffisant : ${abort.products.joinToString(", ")}"
            )
        } catch (abort: SaleAbortException.Price) {
            SaleValidationResult.PriceChanged(
                updatedPrices = abort.prices,
                newTotal = abort.newTotal,
                message = "Un ou plusieurs prix ont changé. Nouveau total : ${Formatters.formatFcfa(abort.newTotal)}. Veuillez confirmer."
            )
        } catch (abort: SaleAbortException.Generic) {
            SaleValidationResult.Error(abort.reason)
        } catch (ex: Exception) {
            SaleValidationResult.Error(
                "Échec d'enregistrement (transaction annulée) : ${ex.message ?: "erreur base"}"
            )
        }
    }

    suspend fun getSaleDetail(venteId: Long): VenteDetail? {
        val resume = db.venteDao().getResumeById(venteId) ?: return null
        val lignes = db.venteDao().getLignesForVente(venteId)
        val annulation = db.venteDao().getAnnulationForVente(venteId)
        return VenteDetail(resume, lignes, annulation)
    }

    suspend fun cancelSale(
        actorId: Long,
        venteId: Long,
        motif: String
    ): ServiceResult<VenteDetail> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth

        val cleanMotif = motif.trim()
        if (cleanMotif.isBlank()) {
            return ServiceResult.Error("Le motif d'annulation est obligatoire.", field = "motif")
        }

        return db.withTransaction {
            val vente = db.venteDao().findById(venteId)
                ?: return@withTransaction ServiceResult.Error("Vente introuvable.")
            if (vente.statut == StatutVente.ANNULEE) {
                return@withTransaction ServiceResult.Error(
                    "Cette vente est déjà annulée. Une seconde annulation est refusée."
                )
            }
            val now = System.currentTimeMillis()
            db.venteDao().updateVente(vente.copy(statut = StatutVente.ANNULEE))
            val annulationId = db.venteDao().insertAnnulation(
                AnnulationVenteEntity(
                    venteId = venteId,
                    auteurId = actorId,
                    dateHeureUtc = now,
                    motif = cleanMotif
                )
            )
            val lignes = db.venteDao().getLignesForVente(venteId)
            for (ligne in lignes) {
                db.mouvementStockDao().insert(
                    MouvementStockEntity(
                        produitId = ligne.produitId,
                        auteurId = actorId,
                        dateHeureUtc = now,
                        type = TypeMouvement.ANNULATION_VENTE,
                        quantiteSignee = ligne.quantite,
                        motif = "Annulation ${vente.numero} : $cleanMotif",
                        ligneVenteId = ligne.id,
                        annulationId = annulationId
                    )
                )
            }
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = actorId,
                    dateHeureUtc = now,
                    action = "ANNULATION_VENTE",
                    entite = "Vente",
                    entiteId = venteId,
                    details = "Annulation vente ${vente.numero} (${Formatters.formatFcfa(vente.total)}) | Motif: $cleanMotif"
                )
            )
            val detail = getSaleDetail(venteId)!!
            ServiceResult.Success(detail, "Vente ${vente.numero} annulée et stock rétabli.")
        }
    }

    suspend fun getDailyIndicators(
        actorId: Long,
        dateMetier: String,
        products: List<ProduitAvecStock>
    ): ServiceResult<IndicateursJour> {
        val auth = requireActiveUser(actorId)
        if (auth is ServiceResult.Error) return auth
        val user = (auth as ServiceResult.Success).data
        val sellerFilter = if (user.role == RoleUtilisateur.ADMINISTRATEUR) null else user.id
        val agg = db.rapportDao().getSalesAggregate(dateMetier, dateMetier, sellerFilter)
        val cancelled = db.rapportDao().countCancelledSales(dateMetier, dateMetier, sellerFilter)
        val ruptures = products.count { it.actif && it.statut == StatutDisponibilite.EN_RUPTURE }
        val lowStock = products.count { it.actif && it.statut == StatutDisponibilite.STOCK_FAIBLE }
        return ServiceResult.Success(
            IndicateursJour(
                dateMetier = dateMetier,
                montantVentes = agg.montantVentes,
                nombreVentes = agg.nombreVentes,
                unitesVendues = agg.unitesVendues,
                referencesRupture = ruptures,
                referencesStockFaible = lowStock,
                ventesAnnuleesJour = cancelled
            ),
            "OK"
        )
    }

    suspend fun getPeriodReport(
        actorId: Long,
        startDate: String,
        endDate: String
    ): ServiceResult<Pair<VentesAggregate, List<RapportProduitRow>>> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth
        if (startDate > endDate) {
            return ServiceResult.Error("La date de début doit précéder ou égaler la date de fin.")
        }
        val summary = db.rapportDao().getSalesAggregate(startDate, endDate, null)
        val rows = db.rapportDao().getProductSalesReport(startDate, endDate)
        return ServiceResult.Success(summary to rows, "Rapport généré.")
    }

    suspend fun exportSalesCsv(
        actorId: Long,
        sales: List<VenteResume>
    ): ServiceResult<String> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth
        val zoneId = getStoreTimeZone()
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for accent readability
        sb.appendLine("Numéro,Date métier,Date et heure,Vendeur,Mode de paiement,Montant (FCFA),Montant reçu,Monnaie rendue,Unités,Statut")
        for (s in sales) {
            sb.appendLine(
                listOf(
                    Formatters.csvSafeCell(s.numero),
                    Formatters.csvSafeCell(s.dateMetier),
                    Formatters.csvSafeCell(Formatters.formatDateTimeUtc(s.dateHeureUtc, zoneId)),
                    Formatters.csvSafeCell(s.vendeurNom),
                    Formatters.csvSafeCell(s.modePaiement.libelle),
                    s.total.toString(),
                    s.montantRecu.toString(),
                    s.monnaieRendue.toString(),
                    s.nombreUnites.toString(),
                    Formatters.csvSafeCell(s.statut.libelle)
                ).joinToString(",")
            )
        }
        db.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = actorId,
                dateHeureUtc = System.currentTimeMillis(),
                action = "EXPORT_CSV_VENTES",
                entite = "Vente",
                entiteId = null,
                details = "Export CSV de ${sales.size} ventes"
            )
        )
        return ServiceResult.Success(sb.toString(), "Export CSV des ventes prêt (${sales.size} lignes).")
    }

    suspend fun exportStockCsv(
        actorId: Long,
        products: List<ProduitAvecStock>
    ): ServiceResult<String> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine("Référence,Nom,Catégorie,Prix de vente (FCFA),Stock disponible,Seuil d'alerte,Statut,État catalogue")
        for (p in products) {
            sb.appendLine(
                listOf(
                    Formatters.csvSafeCell(p.reference),
                    Formatters.csvSafeCell(p.nom),
                    Formatters.csvSafeCell(p.categorieNom ?: "Sans catégorie"),
                    p.prixVente.toString(),
                    p.stockDisponible.toString(),
                    p.seuilAlerte.toString(),
                    Formatters.csvSafeCell(p.statut.libelle),
                    Formatters.csvSafeCell(if (p.actif) "Actif" else "Archivé")
                ).joinToString(",")
            )
        }
        db.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = actorId,
                dateHeureUtc = System.currentTimeMillis(),
                action = "EXPORT_CSV_STOCK",
                entite = "Produit",
                entiteId = null,
                details = "Export CSV du stock de ${products.size} produits"
            )
        )
        return ServiceResult.Success(sb.toString(), "Export CSV du stock prêt (${products.size} lignes).")
    }

    suspend fun createUser(
        actorId: Long,
        nom: String,
        identifiant: String,
        motDePasse: String,
        role: RoleUtilisateur
    ): ServiceResult<UtilisateurEntity> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth
        val trimmedName = nom.trim()
        val trimmedId = identifiant.trim()
        if (trimmedName.isBlank()) {
            return ServiceResult.Error("Le nom est obligatoire.", field = "nom")
        }
        if (trimmedId.length < 3) {
            return ServiceResult.Error("L'identifiant doit contenir au moins 3 caractères.", field = "identifiant")
        }
        if (motDePasse.length < 4) {
            return ServiceResult.Error("Le mot de passe doit contenir au moins 4 caractères.", field = "motDePasse")
        }

        val hashed = PasswordHasher.hashPassword(motDePasse)
        return db.withTransaction {
            if (db.utilisateurDao().findByIdentifiant(trimmedId) != null) {
                return@withTransaction ServiceResult.Error(
                    "Cet identifiant existe déjà.",
                    field = "identifiant"
                )
            }
            val entity = UtilisateurEntity(
                nom = trimmedName,
                identifiant = trimmedId,
                motDePasseHash = hashed.hashHex,
                sel = hashed.saltHex,
                parametresHachage = hashed.parameters,
                role = role,
                actif = true
            )
            val id = db.utilisateurDao().insert(entity)
            val saved = entity.copy(id = id)
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = actorId,
                    dateHeureUtc = System.currentTimeMillis(),
                    action = "CREATION_UTILISATEUR",
                    entite = "Utilisateur",
                    entiteId = id,
                    details = "Création compte $trimmedId (${role.libelle})"
                )
            )
            ServiceResult.Success(saved, "Compte '$trimmedName' (${role.libelle}) créé.")
        }
    }

    suspend fun setUserActive(
        actorId: Long,
        targetUserId: Long,
        active: Boolean
    ): ServiceResult<UtilisateurEntity> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth

        return db.withTransaction {
            val target = db.utilisateurDao().findById(targetUserId)
                ?: return@withTransaction ServiceResult.Error("Compte introuvable.")
            if (!active && target.role == RoleUtilisateur.ADMINISTRATEUR && target.actif) {
                val activeAdmins = db.utilisateurDao().countActiveByRole(RoleUtilisateur.ADMINISTRATEUR)
                if (activeAdmins <= 1) {
                    return@withTransaction ServiceResult.Error(
                        "Impossible de désactiver le dernier administrateur actif."
                    )
                }
            }
            val updated = target.copy(actif = active)
            db.utilisateurDao().update(updated)
            db.journalAuditDao().insert(
                JournalAuditEntity(
                    auteurId = actorId,
                    dateHeureUtc = System.currentTimeMillis(),
                    action = if (active) "ACTIVATION_UTILISATEUR" else "DESACTIVATION_UTILISATEUR",
                    entite = "Utilisateur",
                    entiteId = targetUserId,
                    details = "${if (active) "Activation" else "Désactivation"} de ${target.identifiant}"
                )
            )
            ServiceResult.Success(
                updated,
                if (active) "Compte '${target.nom}' activé." else "Compte '${target.nom}' désactivé."
            )
        }
    }

    suspend fun resetUserPassword(
        actorId: Long,
        targetUserId: Long,
        newPassword: String
    ): ServiceResult<Unit> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth
        if (newPassword.length < 4) {
            return ServiceResult.Error("Le mot de passe doit contenir au moins 4 caractères.")
        }
        val target = db.utilisateurDao().findById(targetUserId)
            ?: return ServiceResult.Error("Compte introuvable.")
        val hashed = PasswordHasher.hashPassword(newPassword)
        db.utilisateurDao().update(
            target.copy(
                motDePasseHash = hashed.hashHex,
                sel = hashed.saltHex,
                parametresHachage = hashed.parameters
            )
        )
        db.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = actorId,
                dateHeureUtc = System.currentTimeMillis(),
                action = "REINITIALISATION_MDP",
                entite = "Utilisateur",
                entiteId = targetUserId,
                details = "Mot de passe réinitialisé pour ${target.identifiant}"
            )
        )
        return ServiceResult.Success(Unit, "Mot de passe mis à jour pour '${target.nom}'.")
    }

    suspend fun updateStoreSettings(
        actorId: Long,
        nomLogiciel: String,
        nomMagasin: String,
        coordonneesMagasin: String,
        fuseauHoraire: String,
        logoUri: String? = null
    ): ServiceResult<Unit> {
        val auth = requireAdmin(actorId)
        if (auth is ServiceResult.Error) return auth
        if (nomMagasin.isBlank()) {
            return ServiceResult.Error("Le nom du magasin est obligatoire.")
        }
        val items = mutableListOf(
            ParametreEntity(ParametreKeys.NOM_LOGICIEL, nomLogiciel.trim().ifBlank { "Gestion Magasin" }),
            ParametreEntity(ParametreKeys.NOM_MAGASIN, nomMagasin.trim()),
            ParametreEntity(ParametreKeys.COORDONNEES_MAGASIN, coordonneesMagasin.trim()),
            ParametreEntity(ParametreKeys.FUSEAU_HORAIRE, fuseauHoraire.trim().ifBlank { "Africa/Brazzaville" })
        )
        if (logoUri != null) {
            items.add(ParametreEntity(ParametreKeys.LOGO_URI, logoUri))
        }
        db.parametreDao().upsertAll(items)
        db.journalAuditDao().insert(
            JournalAuditEntity(
                auteurId = actorId,
                dateHeureUtc = System.currentTimeMillis(),
                action = "MODIFICATION_PARAMETRES",
                entite = "Parametre",
                entiteId = null,
                details = "Paramètres magasin mis à jour ($nomMagasin)"
            )
        )
        return ServiceResult.Success(Unit, "Paramètres du magasin enregistrés.")
    }

    private sealed class SaleAbortException : RuntimeException() {
        class Stock(val products: List<String>) : SaleAbortException()
        class Price(val prices: Map<Long, Long>, val newTotal: Long) : SaleAbortException()
        class Generic(val reason: String) : SaleAbortException()
    }
}
