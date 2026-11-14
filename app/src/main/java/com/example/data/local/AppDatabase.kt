package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CategorieDao
import com.example.data.local.dao.JournalAuditDao
import com.example.data.local.dao.MouvementStockDao
import com.example.data.local.dao.ParametreDao
import com.example.data.local.dao.ProduitDao
import com.example.data.local.dao.RapportDao
import com.example.data.local.dao.UtilisateurDao
import com.example.data.local.dao.VenteDao
import com.example.data.local.entity.AnnulationVenteEntity
import com.example.data.local.entity.CategorieEntity
import com.example.data.local.entity.JournalAuditEntity
import com.example.data.local.entity.LigneVenteEntity
import com.example.data.local.entity.ModePaiement
import com.example.data.local.entity.MouvementStockEntity
import com.example.data.local.entity.ParametreEntity
import com.example.data.local.entity.ProduitEntity
import com.example.data.local.entity.RoleUtilisateur
import com.example.data.local.entity.StatutVente
import com.example.data.local.entity.TypeMouvement
import com.example.data.local.entity.UtilisateurEntity
import com.example.data.local.entity.VenteEntity
import com.example.data.local.entity.VersionSchemaEntity

class StoreTypeConverters {
    @TypeConverter
    fun fromRole(value: RoleUtilisateur): String = value.name

    @TypeConverter
    fun toRole(value: String): RoleUtilisateur = RoleUtilisateur.valueOf(value)

    @TypeConverter
    fun fromModePaiement(value: ModePaiement): String = value.name

    @TypeConverter
    fun toModePaiement(value: String): ModePaiement = ModePaiement.valueOf(value)

    @TypeConverter
    fun fromStatutVente(value: StatutVente): String = value.name

    @TypeConverter
    fun toStatutVente(value: String): StatutVente = StatutVente.valueOf(value)

    @TypeConverter
    fun fromTypeMouvement(value: TypeMouvement): String = value.name

    @TypeConverter
    fun toTypeMouvement(value: String): TypeMouvement = TypeMouvement.valueOf(value)
}

object ParametreKeys {
    const val NOM_LOGICIEL = "nom_logiciel"
    const val NOM_MAGASIN = "nom_magasin"
    const val COORDONNEES_MAGASIN = "coordonnees_magasin"
    const val LOGO_URI = "logo_uri"
    const val DEVISE = "devise"
    const val FUSEAU_HORAIRE = "fuseau_horaire"
    const val DERNIERE_SAUVEGARDE_AUTO_DATE = "derniere_sauvegarde_auto_date"
    const val DERNIERE_SAUVEGARDE_AUTO_HEURE_UTC = "derniere_sauvegarde_auto_heure_utc"
    const val DERNIERE_SAUVEGARDE_AUTO_RESULTAT = "derniere_sauvegarde_auto_resultat"
    const val DERNIERE_SAUVEGARDE_MANUELLE_UTC = "derniere_sauvegarde_manuelle_utc"
    const val RETENTION_SAUVEGARDES = "retention_sauvegardes"
}

@Database(
    entities = [
        UtilisateurEntity::class,
        CategorieEntity::class,
        ProduitEntity::class,
        VenteEntity::class,
        LigneVenteEntity::class,
        AnnulationVenteEntity::class,
        MouvementStockEntity::class,
        JournalAuditEntity::class,
        ParametreEntity::class,
        VersionSchemaEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(StoreTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun utilisateurDao(): UtilisateurDao
    abstract fun categorieDao(): CategorieDao
    abstract fun produitDao(): ProduitDao
    abstract fun venteDao(): VenteDao
    abstract fun mouvementStockDao(): MouvementStockDao
    abstract fun journalAuditDao(): JournalAuditDao
    abstract fun parametreDao(): ParametreDao
    abstract fun rapportDao(): RapportDao

    companion object {
        const val DB_NAME = "gestion_magasin.db"
        const val SCHEMA_VERSION = 1

        fun build(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            }
            return builder
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(DatabaseIntegrityCallback())
                .build()
        }
    }
}

private class DatabaseIntegrityCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO version_schema (numero, dateApplicationUtc, description)
            VALUES (1, $now, 'Schéma initial Gestion Magasin v1.0')
            """.trimIndent()
        )
        val defaults = listOf(
            ParametreKeys.NOM_LOGICIEL to "Gestion Magasin",
            ParametreKeys.NOM_MAGASIN to "Mon Magasin",
            ParametreKeys.COORDONNEES_MAGASIN to "Point de vente principal",
            ParametreKeys.LOGO_URI to "",
            ParametreKeys.DEVISE to "FCFA XAF",
            ParametreKeys.FUSEAU_HORAIRE to "Africa/Brazzaville",
            ParametreKeys.RETENTION_SAUVEGARDES to "7"
        )
        defaults.forEach { (k, v) ->
            db.execSQL(
                "INSERT OR IGNORE INTO parametre (cle, valeur) VALUES (?, ?)",
                arrayOf(k, v)
            )
        }

        // Integrity triggers complementing Room schema constraints (Cahier des Charges Section 7)
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_produit_check_insert
            BEFORE INSERT ON produit
            BEGIN
                SELECT CASE
                    WHEN NEW.prixVente <= 0 THEN RAISE(ABORT, 'Le prix de vente doit être strictement positif')
                    WHEN NEW.seuilAlerte < 0 THEN RAISE(ABORT, 'Le seuil d''alerte ne peut pas être négatif')
                END;
            END;
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_produit_check_update
            BEFORE UPDATE ON produit
            BEGIN
                SELECT CASE
                    WHEN NEW.prixVente <= 0 THEN RAISE(ABORT, 'Le prix de vente doit être strictement positif')
                    WHEN NEW.seuilAlerte < 0 THEN RAISE(ABORT, 'Le seuil d''alerte ne peut pas être négatif')
                END;
            END;
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_ligne_vente_check_insert
            BEFORE INSERT ON ligne_vente
            BEGIN
                SELECT CASE
                    WHEN NEW.quantite <= 0 THEN RAISE(ABORT, 'La quantité vendue doit être strictement positive')
                    WHEN NEW.prixUnitaire <= 0 THEN RAISE(ABORT, 'Le prix unitaire doit être strictement positif')
                END;
            END;
            """.trimIndent()
        )
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON;")
    }
}
