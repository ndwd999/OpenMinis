package com.yujian.minis.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Standalone Room database for provider config. Lives in `provider.db`,
 * separate from `minis.db` (sessions/messages/etc), so that downgrading
 * to a version that doesn't know about these tables does NOT crash on
 * `minis.db`. Old builds simply ignore provider.db and continue to read
 * provider config from the legacy SharedPreferences JSON mirror — which
 * we keep writing on every save so it's never stale.
 *
 * On re-upgrade, ProviderRepository compares a stored hash of the JSON
 * mirror against the live mirror to detect "old build wrote JSON behind
 * our back during the downgrade window" and re-imports if needed; that
 * way provider.db can never become authoritative-but-stale relative to
 * what the user did while downgraded.
 *
 * Schema starts at version 1; future column adds use Migration like
 * AppDatabase does. We deliberately do NOT enable
 * fallbackToDestructiveMigration: provider.db is the only copy of
 * structured provider state, and the JSON mirror is the safety net, not
 * a substitute for proper migrations.
 */
@Database(
    entities = [
        ProviderInstanceEntity::class,
        ProviderModelEntryEntity::class,
        ProviderModelGroupEntity::class,
        ProviderAgentLoopIdEntity::class,
        ProviderConfigMetaEntity::class,
        ProviderThinkingRuleEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class ProviderDatabase : RoomDatabase() {
    abstract fun providerConfigDao(): ProviderConfigDao

    companion object {
        @Volatile
        private var INSTANCE: ProviderDatabase? = null

        /**
         * [T-android-azure-openai] Add the Azure OpenAI mode column. Pure
         * additive ALTER with NOT NULL DEFAULT 0 so every existing provider row
         * backfills to "off" — no data rewrite, no provider drop. This is the
         * first migration on provider.db (introduced at v1 with all columns
         * inline); older builds that don't know the column keep reading the JSON
         * mirror, and re-upgrade re-imports if they wrote behind our back.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE provider_instances ADD COLUMN azure_mode INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * [GH#68 T-android-image-endpoint-persist] Add the image-endpoint
         * picker columns that the Room migration of the provider store
         * (4dd24ecf) missed — the JSON model carried them but every Room
         * round-trip dropped the value, snapping the picker back to Auto.
         * Pure additive nullable TEXT ALTERs; existing rows read as null →
         * auto / no cached probe, no data rewrite, no provider drop.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE provider_instances ADD COLUMN image_endpoint_mode TEXT")
                db.execSQL("ALTER TABLE provider_instances ADD COLUMN image_endpoint_resolved TEXT")
            }
        }

        /**
         * [T-android-thinking-rules-phase2] Add the custom-thinking-rules table.
         * Pure additive CREATE TABLE — no existing row is touched, and a provider with
         * no custom rules has an empty table, so resolution stays byte-identical to
         * pre-migration (the resolver prepends an empty list above the built-ins).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS provider_thinking_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        provider_instance_id TEXT NOT NULL,
                        label TEXT NOT NULL,
                        scope_kind TEXT NOT NULL,
                        scope_pattern TEXT,
                        wire_format_json TEXT,
                        reasoning_echo_json TEXT,
                        sort_order INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_provider_thinking_rules_provider_instance_id " +
                        "ON provider_thinking_rules(provider_instance_id)",
                )
            }
        }


        /**
         * [T-android-model-absence-grace-persist] Persist the per-entry absence
         * mark so the 7-day grace window actually elapses across restarts.
         *
         * Pure additive nullable INTEGER ALTER — an O(1) metadata change in
         * SQLite, no table rebuild and no row rewrite. Existing rows read back
         * NULL, which is exactly right: nothing had been observed absent before
         * this build could record it.
         *
         * Without the column the read path returned absentSince = null on every
         * cold start, so replaceEntries re-stamped `nowMs` on each refresh and
         * the window never elapsed — unlisted models were never pruned.
         *
         * NOT NULL DEFAULT 0 would be wrong here: 0 would mean "absent since
         * 1970", i.e. past the window on first sight, which would delete every
         * model a stale row mentions.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE provider_model_entries ADD COLUMN absent_since INTEGER")
            }
        }

        /**
         * [T-android-model-absence-grace-persist] Downgrade 5 → 4. Deliberately
         * a NO-OP, the same contract AppDatabase.MIGRATION_12_11 uses: the
         * absent_since column stays in place.
         *
         * Registering any 5 → 4 path is enough for Room to proceed — it does not
         * inspect what the migration does. Dropping the column instead would
         * erase the absence marks a newer build recorded, so a user who
         * downgrades and re-upgrades would restart every window: the very bug
         * this column fixes. Keeping it costs 8 bytes per absent row.
         *
         * Room binds by column NAME, never by position, and validation only
         * requires the columns the entity declares — extras are ignored, so an
         * older build reads and writes this table without knowing the column
         * exists.
         */
        val MIGRATION_5_4 = object : Migration(5, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Intentionally empty — the column is left in place.
            }
        }


        fun getInstance(context: Context): ProviderDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProviderDatabase::class.java,
                    "provider.db",
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_4,
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
