package com.yujian.minis.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-row model entry. Primary key is the composite "{instanceId}/{modelId}"
 * to align with iOS ProviderConfigDB and keep group/agent-loop references
 * portable across platforms. The legacy random-UUID id form is rewritten
 * to this shape during the first JSON→DB migration; group memberEntryIds
 * and agentLoopModelEntryIds are rewritten in lockstep so refs don't dangle.
 *
 * baseModel + overrides are stored as serialized JSON strings (single
 * source of truth via the same kotlinx.serialization Json instance used
 * by the legacy mirror).
 */
@Entity(
    tableName = "provider_model_entries",
    foreignKeys = [ForeignKey(
        entity = ProviderInstanceEntity::class,
        parentColumns = ["id"],
        childColumns = ["provider_instance_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("provider_instance_id")],
)
data class ProviderModelEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "provider_instance_id") val providerInstanceId: String,
    @ColumnInfo(name = "base_model_json") val baseModelJson: String,
    @ColumnInfo(name = "overrides_json") val overridesJson: String? = null,
    @ColumnInfo(name = "is_custom") val isCustom: Int = 0,
    @ColumnInfo(name = "is_hidden") val isHidden: Int = 0,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "user_modified_at") val userModifiedAt: Long? = null,
    /**
     * [T-android-model-absence-grace-persist] Epoch millis since the provider
     * stopped listing this model, or null while it is listed. Backs the
     * [com.yujian.minis.data.repository.ProviderRepository.MODEL_ABSENCE_GRACE_MS]
     * window in replaceEntries: an entry that stays absent past the window is
     * pruned. Nullable with no DEFAULT on purpose — null means "listed", so a
     * DEFAULT 0 would read as "absent since 1970" and delete good models.
     *
     * The column was missing from the first Room port, so the mark was
     * recomputed on every cold start and the window could never elapse.
     */
    @ColumnInfo(name = "absent_since") val absentSince: Long? = null,
)
