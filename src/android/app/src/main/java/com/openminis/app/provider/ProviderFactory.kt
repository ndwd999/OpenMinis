package com.openminis.app.provider

import android.content.Context
import com.openminis.app.data.model.LLMModel
import com.openminis.app.data.model.ModelOverrides
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.model.ProviderType
import com.openminis.app.provider.anthropic.AnthropicProvider
import com.openminis.app.provider.gemini.GeminiProvider
import com.openminis.app.provider.openai.OpenAIProvider

object ProviderFactory {
    /**
     * Create a provider for [instance], authenticating with [apiKey].
     *
     * This build speaks three request dialects and nothing else — the mapping
     * from a [ProviderType] to one of them is [ProviderType.wireFormat]. There
     * is no OAuth path: [context] is now only carried for symmetry with
     * callers that already hold one, and is unused.
     *
     * [sessionId] is the PERSISTED id of the conversation this provider will
     * serve, and exists only for [OpenCodeSessionHeader] — see there for why
     * a draft placeholder must never be passed. Null (the default) is correct
     * for every caller with no single conversation behind it: quick tests,
     * vision sub-model calls, model-use offload, voice correction.
     *
     * Providers are cached per chat ViewModel and reused across turns, so the
     * id is stored on the provider here and re-read when each request is built
     * — not baked into a header at construction. That is what lets a draft
     * chat, whose real id does not exist yet, start sending the header as soon
     * as `ensureSession()` mints one.
     */
    fun create(
        instance: ProviderInstance,
        apiKey: String,
        model: LLMModel,
        @Suppress("UNUSED_PARAMETER") context: Context? = null,
        sessionId: String? = null,
        /**
         * [T-android-model-custom-params] The user's per-model overrides
         * (temperature / top_p / custom headers / extra body params) for the
         * entry being served. Optional and defaulted so every existing caller
         * compiles unchanged; callers that have a ModelEntry in hand should
         * pass `entry.overrides` so the settings actually reach the wire.
         */
        overrides: ModelOverrides? = null,
    ): LLMProvider {
        // T174: route through ProviderInstance.effectiveBaseURL instead of
        // re-implementing the trim-+-endsWith dance inline. The previous
        // version did `url.endsWith("/v1")` on the raw, untrimmed string,
        // so a customBaseURL of "https://api.deepseek.com/v1/" (trailing
        // slash) failed the check and the code appended a second "/v1",
        // producing requests to "/v1//v1/chat/completions" -> HTTP 404.
        // Likewise "https://api.deepseek.com/" was concatenated as-is to
        // ".../" + "/v1/chat/completions" = ".//v1/chat/completions",
        // which DeepSeek tolerated only by accident. effectiveBaseURL
        // trimEnd('/')'s the input first, so all four customBaseURL
        // shapes (no slash, trailing slash, /v1, /v1/) now collapse to
        // the same canonical "https://host/v1" string. The /chat/
        // completions endpoint suffix at OpenAIProvider.kt:710 then
        // produces a single-slash join.
        val basePath = instance.effectiveBaseURL
        val provider: LLMProvider = when (instance.providerType.wireFormat) {
            ProviderType.WireFormat.anthropic -> {
                // [T-provider-custom-user-agent] Only meaningful for custom-base
                // (relay) instances; on the official direct path it's null.
                if (basePath != null) AnthropicProvider(apiKey, model, basePath, customUserAgent = instance.customUserAgent)
                else AnthropicProvider(apiKey, model)
            }

            ProviderType.WireFormat.gemini -> {
                if (basePath != null) GeminiProvider(apiKey, model, basePath)
                else GeminiProvider(apiKey, model)
            }

            ProviderType.WireFormat.openAI -> {
                // [T-android-provider-type-parity] openAIResponses shares this
                // branch: on iOS it is "OpenAI with forceResponsesAPI = true", and
                // the Responses endpoint is already reachable here through the
                // instance's useResponsesAPI flag (forced on below for that type).
                //
                // Every other OpenAI-compatible vendor (OpenRouter, xAI, Kimi
                // Code) used to get a branch here that hardcoded its base URL
                // and headers. They now flow through the same path as a plain
                // OpenAI instance with a custom base URL, so adding a relay is
                // a matter of configuring an instance rather than shipping a
                // provider stack.
                val base = basePath
                    ?: if (instance.providerType == ProviderType.openRouter) OPENROUTER_BASE
                    else DEFAULT_OPENAI_BASE
                OpenAIProvider(
                    apiKey = apiKey,
                    model = model,
                    basePath = base,
                    // [T-android-provider-type-parity] The dedicated
                    // Responses type forces the endpoint regardless of the
                    // per-instance flag — that IS its definition, and an
                    // instance imported from another build carries no
                    // Android-side useResponsesAPI value to have set.
                    useResponsesAPI = instance.useResponsesAPI ||
                        instance.providerType == ProviderType.openAIResponses,
                    // [T-provider-custom-user-agent] Covers both chat and
                    // /responses for custom-base OpenAI-compat relays; null
                    // on the official direct path.
                    customUserAgent = instance.customUserAgent,
                    // OpenRouter identifies the calling app by convention; a
                    // generic relay has no such header, so it is only attached
                    // for that base.
                    extraHeaders = if (base == OPENROUTER_BASE) openRouterHeaders() else emptyMap(),
                    // [T-android-azure-openai] Azure auths with api-key +
                    // deployments-path URL. Pass the RAW customBaseURL (not
                    // the /v1-appended, query-stripped effectiveBaseURL) so
                    // azureUrl() can preserve the ?api-version query.
                    isAzure = instance.azureMode,
                    azureBase = instance.customBaseURL,
                ).also { p ->
                    // [T-android-xai-priority] xAI offers a Priority Processing
                    // tier. This is a CAPABILITY flag only — whether the tier is
                    // actually requested is the user's global Fast Mode toggle
                    // (FastModePrefs), read by the body builders at request
                    // time. Set only for xAI, so the xAI-specific
                    // `service_tier` key can never leak into another vendor's
                    // body; a strict OpenAI-compatible relay 400s on it.
                    if (instance.providerType == ProviderType.xAI) {
                        p.supportsPriorityProcessing = true
                    }
                }
            }

            // Types this build can decode but not drive: an instance restored
            // from another build whose dialect Android does not speak. Fail
            // with a clear credential error rather than constructing a
            // provider that would emit malformed requests.
            ProviderType.WireFormat.unsupported -> {
                throw com.openminis.app.data.model.LLMError.InvalidApiKey()
            }
        }
        // [T-android-thinking-rules-phase2] Tag OpenAI-family providers with their
        // owning instance id so the thinking resolver can look up this instance's
        // user-authored custom rules. Only OpenAIProvider consults the resolver's
        // custom-rule path (Gemini/Anthropic use their own emitters), so this is the
        // only type that needs it.
        (provider as? OpenAIProvider)?.thinkingRuleInstanceId = instance.id
        // [T-android-opencode-session-header] Hand the conversation id to the
        // provider; it derives OpenCode Go's `x-opencode-session` per request
        // (gated on host + on the id being persisted, so nothing else sees it).
        // Set here rather than per-branch so every OpenAI-compatible path is
        // covered by one line, and left null for callers that serve no single
        // conversation (quick test, vision, model-use offload, voice).
        (provider as? OpenAIProvider)?.sessionId = sessionId
        // [T-android-model-custom-params] Per-model request tuning. Set here,
        // beside the other post-construction wiring, so every OpenAI-compatible
        // branch is covered by one line. `takeIf { !it.isEmpty }` keeps the
        // field null in the overwhelmingly common no-override case, so the
        // merge helpers early-return and the built body stays byte-identical to
        // before this change.
        (provider as? OpenAIProvider)?.modelOverrides = overrides?.takeIf { !it.isEmpty }
        return provider
    }

    private const val DEFAULT_OPENAI_BASE = "https://api.openai.com/v1"
    private const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"

    private fun openRouterHeaders() = mapOf(
        "HTTP-Referer" to "https://github.com/OpenMinis/OpenMinis",
        "X-Title" to "Minis App",
    )
}
