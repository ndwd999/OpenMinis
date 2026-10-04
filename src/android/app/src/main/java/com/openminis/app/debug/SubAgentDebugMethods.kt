package com.openminis.app.debug

import android.content.Context
import com.openminis.app.MinisApp
import com.openminis.app.data.model.SubAgentDefinition
import com.openminis.app.data.model.ThinkingLevel
import com.openminis.app.data.repository.ProviderRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-only drivers for the custom sub agent roster, so a roster can be
 * inspected and seeded on a device without the UI.
 *
 * These used to live in `BackupDebugMethods` and were reached over the same
 * RPC surface; they moved here when the backup/restore feature was removed,
 * which took the backup half of that object with it. The `debug.subAgents.*`
 * method names are unchanged, so any existing caller keeps working.
 */
internal object SubAgentDebugMethods {

    private fun app(context: Context): MinisApp =
        context.applicationContext as? MinisApp ?: throw RPCException(-32000, "MinisApp not initialized")

    private fun repo(context: Context): ProviderRepository {
        val r = app(context).providerRepositoryOrNull ?: throw RPCException(-32000, "provider repository unavailable")
        r.ensureConfigLoaded()
        return r
    }

    private fun SubAgentDefinition.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("instructions", instructions)
        put("modelGroupId", modelGroupId ?: JSONObject.NULL)
        put("thinkingLevelOverride", thinkingLevelOverride?.name ?: JSONObject.NULL)
        put("isBuiltIn", isBuiltIn)
        put("sortOrder", sortOrder)
        put("updatedAt", updatedAt)
    }

    fun subAgentsList(context: Context): JSONObject =
        JSONObject().put("subAgents", JSONArray(repo(context).subAgents.map { it.toJson() }))

    fun subAgentsUpsert(context: Context, params: JSONObject): JSONObject {
        val r = repo(context)
        val name = params.optString("name").ifBlank { throw RPCException(-32602, "Missing 'name'") }
        val existing = params.optString("id").takeIf { it.isNotBlank() }?.let { id -> r.subAgents.firstOrNull { it.id == id } }
        val level = params.optString("thinkingLevelOverride").takeIf { it.isNotBlank() }
            ?.let { ThinkingLevel.parseOrNull(it) ?: throw RPCException(-32602, "bad thinkingLevelOverride '$it'") }
        val def = (existing ?: SubAgentDefinition(name = name, description = "")).copy(
            name = name,
            description = params.optString("description", existing?.description ?: ""),
            instructions = params.optString("instructions", existing?.instructions ?: ""),
            thinkingLevelOverride = level ?: existing?.thinkingLevelOverride,
        )
        r.upsertSubAgent(def)
        return JSONObject().put("id", def.id).put("subAgents", JSONArray(r.subAgents.map { it.toJson() }))
    }

    fun subAgentsDelete(context: Context, params: JSONObject): JSONObject {
        val r = repo(context)
        val id = params.optString("id").ifBlank { throw RPCException(-32602, "Missing 'id'") }
        r.deleteSubAgent(id)
        return JSONObject().put("deleted", id).put("remaining", r.subAgents.size)
    }
}
