package com.yujian.minis.data.repository

import com.yujian.minis.data.db.compositeEntryKey
import com.yujian.minis.data.db.toProviderConfig
import com.yujian.minis.data.db.toSnapshot
import com.yujian.minis.data.model.LLMModel
import com.yujian.minis.data.model.ModelEntry
import com.yujian.minis.data.model.ModelOverrides
import com.yujian.minis.data.model.ProviderConfig
import com.yujian.minis.data.model.ProviderCredential
import com.yujian.minis.data.model.ProviderInstance
import com.yujian.minis.data.model.ProviderType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [T-android-model-absence-grace-persist] `absentSince` must survive the
 * database mirror, or the grace window in [ProviderRepository.replaceEntries]
 * can never elapse.
 *
 * ## The bug this pins down
 *
 * The JSON blob carried the mark correctly — `ModelEntry` is `@Serializable`
 * and the config is encoded with `encodeDefaults = true`. The Room mirror did
 * not: `provider_model_entries` had no `absent_since` column, and
 * `ProviderConfigMapping` neither wrote nor read the field.
 *
 * The config is rebuilt FROM THE DATABASE on launch. So every cold start
 * reset `absentSince` to null, `replaceEntries` read that as "first time I
 * have seen this missing", stamped `nowMs`, and the 7-day window was
 * recomputed from scratch forever. A model dropped from the provider's catalog
 * stayed in the list permanently — the user's report that refreshing "only
 * adds models and never removes the unrelated ones".
 *
 * [ModelAbsenceGraceTest] did not catch this because it restates the decision
 * rule by hand instead of round-tripping it through the mapping layer. This
 * test drives the actual mapping, so a field that is present in the model but
 * missing from the schema now fails here.
 */
class ModelAbsencePersistenceRoundTripTest {

    /**
     * Deliberately identical to the instance ProviderRepository builds
     * (`ignoreUnknownKeys` / `encodeDefaults` / `coerceInputValues`). A test
     * Json with different flags exercises a codec the app never uses, so a
     * field whose round trip depends on one of the other flags would pass here
     * and fail on device.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val instanceId = "inst-1"
    private val t0 = 1_800_000_000_000L

    private fun instance() = ProviderInstance(
        id = instanceId,
        label = "Relay",
        providerType = ProviderType.openAI,
        credentialType = ProviderCredential.apiKey,
    )

    private fun entry(
        modelId: String,
        absentSince: Long? = null,
        contextWindow: Int? = null,
        isCustom: Boolean = false,
        isHidden: Boolean = false,
        uuid: String = modelId,
    ) = ModelEntry(
        providerInstanceId = instanceId,
        baseModel = LLMModel(
            id = modelId,
            displayName = modelId,
            provider = "openAI",
            contextWindow = 1_048_576,
        ),
        overrides = ModelOverrides(contextWindow = contextWindow),
        isCustom = isCustom,
        isHidden = isHidden,
        uuid = uuid,
        absentSince = absentSince,
    )

    private fun configOf(vararg entries: ModelEntry): ProviderConfig {
        val cfg = ProviderConfig()
        cfg.instances.add(instance())
        cfg.modelEntries.addAll(entries)
        return cfg
    }

    /** The exact write → read cycle a cold start performs. */
    private fun ProviderConfig.persistAndReload(): ProviderConfig =
        toSnapshot(json).toProviderConfig(json)

    private fun ProviderConfig.find(modelId: String) =
        modelEntries.firstOrNull { it.baseModel.id == modelId }

    // ─── The regression itself ──────────────────────────────────────────────

    @Test
    fun `an absence mark survives the database round trip`() {
        val restored = configOf(entry("gpt-4o"), entry("gemini-3.8-flash-high", absentSince = t0))
            .persistAndReload()

        val reloaded = restored.find("gemini-3.8-flash-high")
        assertNotNull("entry must still be there", reloaded)
        assertEquals(
            "absentSince must not be recomputed on every launch — a null here " +
                "restarts the grace window and the model is never pruned",
            t0,
            reloaded!!.absentSince,
        )
    }

    @Test
    fun `a listed model round trips as listed, not as absent since epoch`() {
        val restored = configOf(entry("gpt-4o", absentSince = null)).persistAndReload()
        val reloaded = restored.find("gpt-4o")
        assertNotNull(reloaded)
        assertNull("null must stay null", reloaded!!.absentSince)
        assertFalse(
            "and must not read as unavailable — a DEFAULT 0 column would make " +
                "every model look absent since 1970",
            reloaded.isUnavailableFromProvider,
        )
    }

    @Test
    fun `the grace window keeps running across a restart`() {
        // The behaviour the fix exists for. Stamp on the first miss, persist,
        // relaunch, then measure the window against the reloaded entry using
        // the SAME rule replaceEntries applies. Before the fix the reloaded
        // entry came back with absentSince == null, so `since` fell back to
        // `nowMs` and the elapsed time was always ~0.
        val grace = ProviderRepository.MODEL_ABSENCE_GRACE_MS
        val laterMs = t0 + grace + 60_000

        val reloaded = configOf(entry("gone", absentSince = t0)).persistAndReload()
        val afterRestart = reloaded.find("gone")!!

        /** Verbatim from replaceEntries: `val since = entry.absentSince ?: nowMs`. */
        fun elapsed(sinceField: Long?, nowMs: Long) = nowMs - (sinceField ?: nowMs)

        assertTrue(
            "the reloaded entry must be past due",
            elapsed(afterRestart.absentSince, laterMs) > grace,
        )
        assertTrue(
            "a reload-reset mark must look brand new — this is the defect, " +
                "and it is asserted so the test cannot pass on the fixed code alone",
            elapsed(null, laterMs) <= grace,
        )
    }

    @Test
    fun `user overrides and the absence mark survive together`() {
        // The pair is what makes the bug expensive: the entry is kept BECAUSE of
        // the mark, and the override is what it is being kept FOR. Losing the
        // mark loses both, since replaceEntries then deletes the entry.
        val restored = configOf(
            entry("gemini-3.8-flash-high", absentSince = t0, contextWindow = 400_000),
        ).persistAndReload()

        val reloaded = restored.find("gemini-3.8-flash-high")!!
        assertEquals(t0, reloaded.absentSince)
        assertEquals(400_000, reloaded.overrides.contextWindow)
    }

    @Test
    fun `a custom entry keeps its absence state untouched across a reload`() {
        // Custom entries are exempt from pruning; the field must round-trip
        // anyway rather than becoming a catalog-only column.
        val restored = configOf(entry("my-custom", isCustom = true, absentSince = t0))
            .persistAndReload()
        val reloaded = restored.find("my-custom")!!
        assertTrue(reloaded.isCustom)
        assertEquals(t0, reloaded.absentSince)
    }

    @Test
    fun `hidden state and absence are independent across a reload`() {
        val restored = configOf(
            entry("hidden-and-gone", isHidden = true, absentSince = t0),
            entry("hidden-but-listed", isHidden = true),
        ).persistAndReload()

        val gone = restored.find("hidden-and-gone")!!
        val listed = restored.find("hidden-but-listed")!!
        assertTrue("hidden survives", gone.isHidden && listed.isHidden)
        assertEquals("absence is not derived from isHidden", t0, gone.absentSince)
        assertNull("…and a hidden model can still be listed", listed.absentSince)
    }

    @Test
    fun `absence marks do not leak between entries of the same instance`() {
        val restored = configOf(
            entry("absent", absentSince = t0),
            entry("present"),
            entry("absent-too", absentSince = t0 + 5_000),
        ).persistAndReload()

        assertEquals(t0, restored.find("absent")!!.absentSince)
        assertNull(restored.find("present")!!.absentSince)
        assertEquals(t0 + 5_000, restored.find("absent-too")!!.absentSince)
    }

    // ─── Guard rails on the mapping's shape ─────────────────────────────────

    @Test
    fun `the mark is carried on the entity that the repository will read back`() {
        // Asserts the intermediate row, not just the round trip, so a future
        // refactor that drops the field on ONE side fails with a message that
        // names which side.
        val snapshot = configOf(entry("gone", absentSince = t0)).toSnapshot(json)
        val row = snapshot.entries.single()
        assertEquals(
            "the write mapping must populate absentSince",
            t0,
            row.absentSince,
        )
        // And the row id must be the composite key, so the read path can resolve
        // it — a change here would break every entry, not just this field.
        assertEquals(compositeEntryKey(instanceId, "gone"), row.id)
    }

    @Test
    fun `a database written before the column existed reloads as listed`() {
        // Simulates the upgrade path: rows created by a build that had no
        // absent_since column arrive with the field unset. They must read back
        // as "listed", which is what null means — not as absent.
        val snapshot = configOf(entry("old-row", absentSince = t0)).toSnapshot(json)
        val legacy = snapshot.copy(
            entries = snapshot.entries.map { it.copy(absentSince = null) },
        )
        val restored = legacy.toProviderConfig(json)
        val row = restored.find("old-row")!!
        assertNull(row.absentSince)
        assertFalse(
            "an upgraded database must not prune everything it finds",
            row.isUnavailableFromProvider,
        )
    }

    @Test
    fun `round tripping twice is idempotent`() {
        // A value that drifts on each save would still pass a single round trip
        // and still be wrong after three launches.
        val once = configOf(entry("gone", absentSince = t0)).persistAndReload()
        val twice = once.persistAndReload()
        assertEquals(t0, twice.find("gone")!!.absentSince)
    }
}
