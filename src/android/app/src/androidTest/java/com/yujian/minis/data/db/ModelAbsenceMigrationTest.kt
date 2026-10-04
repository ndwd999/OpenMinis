package com.yujian.minis.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * [T-android-model-absence-grace-persist][T-android-downgrade-compat] The
 * 4 ⇄ 5 migration pair for `provider.db`, exercised against a real SQLite file.
 *
 * ## What changed
 *
 * Version 4's `provider_model_entries` had no `absent_since` column, so the
 * grace window in [com.yujian.minis.data.repository.ProviderRepository.replaceEntries]
 * could never elapse — the mark was recomputed on every cold start. Version 5
 * adds the column.
 *
 * ## Why not MigrationTestHelper
 *
 * Same reason as [MessageAttributionMigrationTest]: `exportSchema` is false for
 * this database, so there is no `4.json` for the helper to build the starting
 * database from. Driving the two migration lambdas directly against a
 * v4-shaped table tests the two things that matter:
 *
 * 1. **Upgrade must not lose data.** `ADD COLUMN` on a nullable column is an
 *    O(1) metadata change; a migration that rebuilds the table drops rows.
 * 2. **Downgrade must not crash and must not delete.** Room resolves a
 *    downgrade through `onUpgrade`, so an unregistered path throws and the app
 *    cannot start. The no-op keeps the column, which is what stops an older
 *    build from silently resetting the window when the user upgrades back.
 *
 * ## Why the column is nullable with no DEFAULT
 *
 * `NULL` means "the provider is currently listing this model". A
 * `NOT NULL DEFAULT 0` would read as "absent since 1970", i.e. past the 7-day
 * window on sight, so the first refresh after upgrading would delete every
 * catalog entry — along with the user's overrides. The tests below assert the
 * NULL explicitly, because that is the property the safety depends on.
 */
@RunWith(AndroidJUnit4::class)
class ModelAbsenceMigrationTest {

    private lateinit var dbFile: File
    private lateinit var db: SQLiteDatabase
    private lateinit var helper: androidx.sqlite.db.SupportSQLiteOpenHelper
    private lateinit var supportDb: androidx.sqlite.db.SupportSQLiteDatabase

    /** The subset of the v4 `provider_model_entries` schema this test needs. */
    private fun createV4Schema(d: SQLiteDatabase) {
        d.execSQL(
            """
            CREATE TABLE provider_model_entries (
                id TEXT NOT NULL PRIMARY KEY,
                provider_instance_id TEXT NOT NULL,
                base_model_json TEXT NOT NULL,
                overrides_json TEXT,
                is_custom INTEGER NOT NULL DEFAULT 0,
                is_hidden INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0,
                user_modified_at INTEGER
            )
            """.trimIndent()
        )
        d.version = 4
    }

    /** Verbatim from ProviderDatabase: the real migrations under test. */
    private fun upgrade() = ProviderDatabase.MIGRATION_4_5.migrate(supportDb)

    private fun downgrade() = ProviderDatabase.MIGRATION_5_4.migrate(supportDb)

    private fun columnNames(): Set<String> =
        db.rawQuery("PRAGMA table_info(provider_model_entries)", null).use { c ->
            buildSet {
                val idx = c.getColumnIndex("name")
                while (c.moveToNext()) add(c.getString(idx))
            }
        }

    /**
     * `PRAGMA table_info` row for one column: (type, notnull, dflt_value).
     * Read directly so the "nullable, no DEFAULT" contract is asserted rather
     * than assumed from the fact that the ALTER ran.
     */
    private fun columnSpec(name: String): Triple<String, Int, String?>? =
        db.rawQuery("PRAGMA table_info(provider_model_entries)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            val typeIdx = c.getColumnIndex("type")
            val notNullIdx = c.getColumnIndex("notnull")
            val dfltIdx = c.getColumnIndex("dflt_value")
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == name) {
                    return@use Triple(
                        c.getString(typeIdx),
                        c.getInt(notNullIdx),
                        if (c.isNull(dfltIdx)) null else c.getString(dfltIdx),
                    )
                }
            }
            null
        }

    private fun insertLegacyRow(id: String, modelJson: String = "{\"id\":\"gpt-4o\"}") {
        db.execSQL(
            "INSERT INTO provider_model_entries " +
                "(id, provider_instance_id, base_model_json, overrides_json, is_custom, " +
                "is_hidden, sort_order, user_modified_at) " +
                "VALUES ('$id','inst-1','$modelJson',NULL,0,0,0,NULL)"
        )
    }

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        dbFile = File(ctx.cacheDir, "migration-model-absence-test.db")
        dbFile.delete()
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        createV4Schema(db)
        db.close()

        val cfg = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
            .builder(ctx)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(d: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                override fun onUpgrade(
                    d: androidx.sqlite.db.SupportSQLiteDatabase, old: Int, new: Int,
                ) = Unit
            })
            .build()
        helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(cfg)
        supportDb = helper.writableDatabase
        db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
    }

    @After
    fun tearDown() {
        db.close()
        helper.close()
        dbFile.delete()
    }

    // ─── Upgrade ────────────────────────────────────────────────────────────

    @Test
    fun upgrade4To5_addsTheAbsentSinceColumn() {
        upgrade()
        assertTrue(
            "absent_since must exist after the upgrade",
            columnNames().contains("absent_since"),
        )
    }

    @Test
    fun upgrade4To5_columnIsNullableWithNoDefault() {
        upgrade()
        val spec = columnSpec("absent_since")
        assertTrue("column must exist", spec != null)
        val (type, notNull, dflt) = spec!!
        assertEquals("stored as epoch millis", "INTEGER", type.uppercase())
        assertEquals("must be nullable", 0, notNull)
        assertEquals(
            "must have NO DEFAULT — a DEFAULT 0 reads as 'absent since 1970' " +
                "and would make the next refresh delete every model",
            null,
            dflt,
        )
    }

    @Test
    fun upgrade4To5_preservesExistingRows() {
        insertLegacyRow("row-1")
        upgrade()

        db.rawQuery(
            "SELECT provider_instance_id, base_model_json FROM provider_model_entries WHERE id='row-1'",
            null,
        ).use { c ->
            assertTrue("the pre-existing row must still be there", c.moveToFirst())
            assertEquals("inst-1", c.getString(0))
            assertEquals("{\"id\":\"gpt-4o\"}", c.getString(1))
        }
    }

    @Test
    fun upgrade4To5_legacyRowsReadAsListedNotAbsent() {
        // The single most important assertion here: every row that predates the
        // column must come back NULL. If it came back 0, the grace check
        // (`nowMs - 0 > 7 days`) is true on the very first refresh after
        // upgrading and the user loses every catalog entry at once.
        insertLegacyRow("row-1")
        upgrade()

        db.rawQuery("SELECT absent_since FROM provider_model_entries WHERE id='row-1'", null).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("upgraded rows must read as listed", c.isNull(0))
        }
    }

    @Test
    fun upgrade4To5_columnIsWritableAndRoundTrips() {
        upgrade()
        db.execSQL(
            "INSERT INTO provider_model_entries " +
                "(id, provider_instance_id, base_model_json, is_custom, is_hidden, sort_order, " +
                "absent_since) VALUES ('row-2','inst-1','{}',0,0,0,1800000000000)"
        )
        db.rawQuery("SELECT absent_since FROM provider_model_entries WHERE id='row-2'", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1_800_000_000_000L, c.getLong(0))
        }
    }

    @Test
    fun upgrade4To5_absentAndPresentRowsCoexist() {
        // The mixed state the UI actually sees: one model the provider still
        // lists (NULL) and one it no longer does (a timestamp). A column that
        // collapsed both to the same value would break the prune row's count.
        upgrade()
        db.execSQL(
            "INSERT INTO provider_model_entries " +
                "(id, provider_instance_id, base_model_json, is_custom, is_hidden, sort_order, " +
                "absent_since) VALUES ('present','inst-1','{}',0,0,0,NULL)"
        )
        db.execSQL(
            "INSERT INTO provider_model_entries " +
                "(id, provider_instance_id, base_model_json, is_custom, is_hidden, sort_order, " +
                "absent_since) VALUES ('absent','inst-1','{}',0,0,0,1800000000000)"
        )
        db.rawQuery(
            "SELECT COUNT(*) FROM provider_model_entries WHERE absent_since IS NULL",
            null,
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.rawQuery(
            "SELECT COUNT(*) FROM provider_model_entries WHERE absent_since IS NOT NULL",
            null,
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    // ─── Downgrade ──────────────────────────────────────────────────────────

    @Test
    fun downgrade5To4_runsWithoutLoss() {
        insertLegacyRow("row-1")
        upgrade()
        db.execSQL("UPDATE provider_model_entries SET absent_since=1800000000000 WHERE id='row-1'")
        downgrade()

        db.rawQuery("SELECT COUNT(*) FROM provider_model_entries", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("downgrade must not drop rows", 1, c.getInt(0))
        }
    }

    @Test
    fun downgrade5To4_keepsTheColumnSoTheWindowIsNotReset() {
        // Keeping the column is the whole point of a no-op here. If the
        // downgrade dropped it, an older build would rewrite the table without
        // absent_since; upgrading back would then see NULL everywhere and the
        // 7-day clock would start over — the original bug, reintroduced by the
        // downgrade path.
        insertLegacyRow("row-1")
        upgrade()
        db.execSQL("UPDATE provider_model_entries SET absent_since=1800000000000 WHERE id='row-1'")
        downgrade()

        assertTrue("the column must survive the downgrade", columnNames().contains("absent_since"))
        db.rawQuery("SELECT absent_since FROM provider_model_entries WHERE id='row-1'", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "the mark must still be the original miss",
                1_800_000_000_000L,
                c.getLong(0),
            )
        }
    }

    @Test
    fun downgrade5To4_oldStyleInsertStillWorks() {
        // A v4 build's INSERT names none of the newer columns. It only succeeds
        // because absent_since is nullable with no DEFAULT.
        upgrade()
        downgrade()

        insertLegacyRow("row-3")
        db.rawQuery("SELECT absent_since FROM provider_model_entries WHERE id='row-3'", null).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("unset column must be NULL", c.isNull(0))
        }
        assertTrue(columnNames().contains("absent_since"))
    }

    @Test
    fun downgrade5To4_selectStarStillReadsOldColumns() {
        upgrade()
        downgrade()
        insertLegacyRow("row-4")

        db.rawQuery("SELECT * FROM provider_model_entries WHERE id='row-4'", null).use { c ->
            assertTrue(c.moveToFirst())
            // Resolved by NAME, exactly as Room's generated code does — which is
            // why a trailing extra column is invisible to an older build.
            assertEquals("row-4", c.getString(c.getColumnIndexOrThrow("id")))
            assertEquals("inst-1", c.getString(c.getColumnIndexOrThrow("provider_instance_id")))
            assertTrue(c.isNull(c.getColumnIndexOrThrow("absent_since")))
        }
    }

    @Test
    fun upgrade5AfterDowngrade_isIdempotent() {
        // Round trip: upgrade → downgrade → upgrade must leave exactly one
        // absent_since column. A migration that re-ALTERs would fail here with
        // "duplicate column name".
        insertLegacyRow("row-1")
        upgrade()
        downgrade()
        upgrade()

        assertEquals(
            "exactly one absent_since column",
            1,
            columnNames().count { it == "absent_since" },
        )
        db.rawQuery("SELECT COUNT(*) FROM provider_model_entries", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    // ─── The other tables must be untouched ─────────────────────────────────

    @Test
    fun upgrade4To5_doesNotTouchOtherTables() {
        // The migration is one ALTER on one table. provider.db is the only
        // structured copy of provider state, so a migration that rebuilt or
        // touched instance/group tables would be a much larger change than
        // this bug justifies.
        db.execSQL(
            "CREATE TABLE provider_instances (" +
                "id TEXT NOT NULL PRIMARY KEY, label TEXT NOT NULL, provider_type TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO provider_instances VALUES ('inst-1','Relay','openAI')"
        )
        upgrade()

        db.rawQuery("SELECT label FROM provider_instances WHERE id='inst-1'", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Relay", c.getString(0))
        }
    }
}
