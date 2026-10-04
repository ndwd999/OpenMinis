package com.openminis.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.openminis.app.data.model.ProviderCredential
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.model.ProviderType
import com.openminis.app.data.repository.ProviderRepository
import com.openminis.app.R
import java.util.UUID
import com.openminis.app.ui.components.MinisButton
import com.openminis.app.ui.components.RowLabel
import com.openminis.app.ui.components.SectionTextField

private enum class AddProviderStep {
    CHOOSE_TYPE,
    CONFIGURE,
}

@Composable
fun AddProviderScreen(
    providerRepository: ProviderRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var step by remember { mutableStateOf(AddProviderStep.CHOOSE_TYPE) }
    var selectedType by remember { mutableStateOf<ProviderType?>(null) }
    // [T-android-provider-voice] Non-null when the flow was entered from a
    // Voice Chat Provider template row — preseeds type/base URL/label/appendV1
    // on the configure step (mirrors iOS applyVoiceTemplate).
    var selectedVoiceTemplate by remember {
        mutableStateOf<com.openminis.app.data.model.VoiceProviderTemplate?>(null)
    }

    // Unified back handler: reuse each step's onBack so predictive-back gesture
    // and the top-bar arrow behave identically (go back to prior step, not exit).
    val handleBack: () -> Unit = {
        when (step) {
            AddProviderStep.CHOOSE_TYPE -> onBack()
            AddProviderStep.CONFIGURE -> {
                step = AddProviderStep.CHOOSE_TYPE
                selectedType = null
                selectedVoiceTemplate = null
            }
        }
    }

    // Intercept system back on inner steps; let outer handler pop the route on step 0.
    BackHandler(enabled = step != AddProviderStep.CHOOSE_TYPE) { handleBack() }

    when (step) {
        AddProviderStep.CHOOSE_TYPE -> ChooseProviderScreen(
            onBack = handleBack,
            onSelect = { type ->
                selectedType = type
                step = AddProviderStep.CONFIGURE
            },
            onSelectVoiceTemplate = { template ->
                // Mirror iOS applyVoiceTemplate: pick the underlying protocol
                // and jump straight to configure.
                selectedVoiceTemplate = template
                selectedType = template.providerType
                step = AddProviderStep.CONFIGURE
            },
        )
        AddProviderStep.CONFIGURE -> ConfigureProviderScreen(
            providerType = selectedType!!,
            providerRepository = providerRepository,
            voiceTemplate = selectedVoiceTemplate,
            onBack = handleBack,
            onSaved = onSaved,
        )
    }
}

/**
 * The provider types this build offers for creation, in display order.
 *
 * Three API shapes, three entries: the OpenAI-compatible dialect — which also
 * covers every relay and third-party vendor via a custom base URL — Anthropic,
 * and Gemini. `ProviderType` still carries OpenRouter / xAI / Kimi Code /
 * GitHub Copilot / openAIResponses as DECODE-ONLY cases so a config written by
 * another build still loads; see the enum's KDoc. They are deliberately absent
 * here: this list is what the user can create, and every one of those vendors
 * is reachable as an OpenAI-compatible instance with its own base URL.
 */
private val addableProviderTypes = listOf(
    ProviderType.openAI,
    ProviderType.anthropic,
    ProviderType.gemini,
)

/** Icon and color per provider type, matching iOS SF Symbols. */
private fun providerIcon(type: ProviderType): Pair<ImageVector, Color> = when (type) {
    ProviderType.openAI -> Icons.Default.Hub to Color(0xFF4CAF50)           // green
    ProviderType.anthropic -> Icons.Default.AutoAwesome to Color(0xFFAB47BC) // purple
    ProviderType.gemini -> Icons.Default.Diamond to Color(0xFF42A5F5)        // blue
    // [T-android-provider-type-parity] Types that arrive only from an iOS
    // package / newer build; never offered in addableProviderTypes, but the
    // icon helper is also used to render an already-restored instance.
    ProviderType.openRouter,
    ProviderType.xAI,
    ProviderType.kimiCode,
    ProviderType.githubCopilot,
    ProviderType.openAIResponses,
    ProviderType.antigravity,
    ProviderType.unsupported -> Icons.Default.Cloud to Color(0xFF9E9E9E)
}

// -- Step 1: Choose Provider Type --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseProviderScreen(
    onBack: () -> Unit,
    onSelect: (ProviderType) -> Unit,
    onSelectVoiceTemplate: (com.openminis.app.data.model.VoiceProviderTemplate) -> Unit = {},
) {
    SettingsScaffold(
        title = stringResource(R.string.provider_list_add_provider),
        onBack = onBack,
    ) {
        SettingsSection(
            header = stringResource(R.string.add_provider_choose_provider),
            footer = stringResource(R.string.add_provider_you_can_add_multiple_instances_of_the_sa),
        ) {
            addableProviderTypes.forEachIndexed { index, type ->
                val displayTitle = when (type) {
                    ProviderType.openAI -> "OpenAI / Compatible API"
                    ProviderType.anthropic -> "Anthropic / Compatible API"
                    ProviderType.gemini -> "Google Gemini"
                    else -> type.displayName
                }
                // Describe which vendors each protocol supports, rather than a
                // raw built-in model count.
                val subtitleRes = when (type) {
                    ProviderType.openAI -> R.string.add_provider_subtitle_openai
                    ProviderType.anthropic -> R.string.add_provider_subtitle_anthropic
                    ProviderType.gemini -> R.string.add_provider_subtitle_gemini
                    else -> R.string.add_provider_subtitle_openai
                }
                val (icon, iconColor) = providerIcon(type)
                SettingsRow(
                    title = displayTitle,
                    subtitle = stringResource(subtitleRes),
                    icon = icon,
                    iconColor = iconColor,
                    onClick = { onSelect(type) },
                    showDivider = index < addableProviderTypes.size - 1,
                )
            }
        }

        // [T-android-provider-voice] Voice Chat Providers — one row per voice
        // vendor template. Tapping prefills the underlying protocol + base URL
        // and jumps to configure (mirrors iOS voiceProviderSection).
        val templates = com.openminis.app.data.model.VoiceProviderTemplate.all
        val templateNotes = templates.mapNotNull { it.note }
        SettingsSection(
            header = stringResource(R.string.add_provider_voice_chat_providers),
            footer = (
                listOf(stringResource(R.string.add_provider_voice_templates_footer)) + templateNotes
                ).joinToString("\n"),
        ) {
            templates.forEachIndexed { index, template ->
                val capabilityRes = when (template.capability) {
                    com.openminis.app.data.model.VoiceProviderTemplate.Capability.TTS ->
                        R.string.add_provider_voice_capability_tts
                    com.openminis.app.data.model.VoiceProviderTemplate.Capability.ASR ->
                        R.string.add_provider_voice_capability_asr
                    com.openminis.app.data.model.VoiceProviderTemplate.Capability.BOTH ->
                        R.string.add_provider_voice_capability_both
                }
                SettingsRow(
                    title = template.name,
                    subtitle = stringResource(capabilityRes),
                    icon = Icons.Outlined.GraphicEq,
                    onClick = { onSelectVoiceTemplate(template) },
                    showDivider = index < templates.size - 1,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// -- Step 2: Configure & Save --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureProviderScreen(
    providerType: ProviderType,
    providerRepository: ProviderRepository,
    voiceTemplate: com.openminis.app.data.model.VoiceProviderTemplate? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    // Compute default label with auto-increment (e.g. "OpenAI", "OpenAI 2", ...)
    // A voice template preseeds its vendor name instead of the protocol name.
    val config by providerRepository.config.collectAsState()
    val defaultLabel = remember(config) {
        val baseName = voiceTemplate?.name ?: providerType.displayName
        val existingLabels = config.instances.map { it.label }.toSet()
        if (baseName !in existingLabels) baseName
        else {
            var n = 2
            while ("$baseName $n" in existingLabels) n++
            "$baseName $n"
        }
    }

    var label by remember { mutableStateOf(defaultLabel) }
    // [T-provider-name-chinese, port iOS 7b283951] Flips true the
    // first time the user types into the label field. While `false`, the
    // LaunchedEffect below keeps `label` glued to the auto-incremented
    // default, so adding a second OpenAI instance picks up "OpenAI 2"
    // automatically. Once the user edits the label (even just to clear
    // it for typing — including non-ASCII like CJK / kana / emoji),
    // the seed is suppressed so further provider-list changes never
    // clobber what they typed. Without this gate the `remember(config)`
    // recomputation, combined with Compose tearing down + recreating
    // ConfigureProviderScreen on step navigation, makes the field
    // appear to reject Chinese — the iOS root cause a user
    // reported, with the same Android equivalent here.
    var labelEdited by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(defaultLabel, labelEdited) {
        if (!labelEdited) label = defaultLabel
    }
    var apiKey by remember { mutableStateOf("") }
    var customBaseURL by remember { mutableStateOf(voiceTemplate?.baseURL ?: "") }

    SettingsScaffold(
        title = stringResource(R.string.add_provider_configure_provider, providerType.displayName),
        onBack = onBack,
    ) {
        // Identity section — Label only. Each provider auto-suggests a
        // unique label so users don't have to type one for the common case.
        SettingsSection(
            header = stringResource(R.string.add_provider_identity),
            footer = stringResource(R.string.add_provider_the_label_is_shown_in_the_provider_list_),
        ) {
            SettingsCardBlock {
                RowLabel(text = stringResource(R.string.provider_detail_label))
                SectionTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        labelEdited = true
                    },
                    placeholder = providerType.displayName,
                    singleLine = true,
                )
            }
        }

        // API key is the only credential this build has.
        ApiKeyConfigSection(
            providerType = providerType,
            label = label,
            apiKey = apiKey,
            onApiKeyChange = { apiKey = it },
            customBaseURL = customBaseURL,
            onCustomBaseURLChange = { customBaseURL = it },
            providerRepository = providerRepository,
            initialAppendV1 = voiceTemplate?.appendV1,
            onSaved = onSaved,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ColumnScope.ApiKeyConfigSection(
    providerType: ProviderType,
    label: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    customBaseURL: String,
    onCustomBaseURLChange: (String) -> Unit,
    providerRepository: ProviderRepository,
    // [T-android-provider-voice] Voice templates carry their own v1-suffix
    // policy (e.g. MiMo appends /v1, ElevenLabs must not). null = type default.
    initialAppendV1: Boolean? = null,
    onSaved: () -> Unit,
) {
    var showApiKeyPlaintext by remember { mutableStateOf(false) }
    var appendV1Suffix by remember {
        mutableStateOf(initialAppendV1 ?: (providerType != ProviderType.gemini))
    }
    // OpenAI API Format: false = Chat Completions, true = Responses API
    var useResponsesAPI by remember { mutableStateOf(false) }

    // ── Credential ──────────────────────────────────────────────────────
    val keyPlaceholder = when (providerType) {
        ProviderType.anthropic -> "sk-ant-..."
        ProviderType.openAI -> "sk-..."
        ProviderType.gemini -> "Gemini API Key..."
        else -> "API Key..."
    }
    SettingsSection(
        header = stringResource(R.string.add_provider_credential),
        footer = stringResource(R.string.add_provider_your_key_is_stored_securely_in_encrypted),
    ) {
        SettingsCardBlock {
            RowLabel(text = stringResource(R.string.provider_list_api_key))
            SectionTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                placeholder = keyPlaceholder,
                singleLine = true,
                visualTransformation = if (showApiKeyPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKeyPlaintext = !showApiKeyPlaintext }) {
                        Icon(
                            if (showApiKeyPlaintext) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKeyPlaintext) "Hide" else "Show",
                        )
                    }
                },
            )
        }
    }

    // ── Endpoint ────────────────────────────────────────────────────────
    val defaultUrl = when (providerType) {
        ProviderType.gemini -> "https://generativelanguage.googleapis.com/v1beta"
        ProviderType.anthropic -> "https://api.anthropic.com"
        ProviderType.openAI -> "https://api.openai.com"
        else -> "https://api.example.com"
    }
    // T-mimo-anthropic-endpoint-android: Anthropic third-party
    // compatible services (e.g. Mimo) frequently host the Anthropic
    // surface under a path suffix like "/anthropic" rather than at
    // the host root. Users routinely paste just the host
    // (https://token-plan-cn.xiaomimimo.com) and hit 404 because
    // /v1/messages then resolves to the wrong route. Surface a hint
    // on the Anthropic endpoint footer so this can be discovered
    // without scraping issue threads. Default Anthropic + other
    // provider types keep their original footer copy.
    val baseUrlFooter = if (providerType == ProviderType.gemini) {
        "Leave empty to use the default Google endpoint. Enter the full base URL including version path."
    } else if (providerType == ProviderType.anthropic) {
        stringResource(R.string.add_provider_endpoint_anthropic_hint)
    } else if (appendV1Suffix) {
        "Leave empty to use the default endpoint. \"/v1\" is appended automatically — enter the base host only."
    } else {
        "The URL is used verbatim. Include the full path up to (but not including) the endpoint."
    }
    SettingsSection(
        header = stringResource(R.string.add_provider_endpoint),
        footer = baseUrlFooter,
    ) {
        SettingsCardBlock {
            RowLabel(text = stringResource(R.string.add_provider_custom_api_base_optional))
            SectionTextField(
                value = customBaseURL,
                onValueChange = onCustomBaseURLChange,
                placeholder = defaultUrl,
                singleLine = true,
            )
        }
        // Auto Append "/v1" toggle (not for Gemini — Gemini uses full path)
        if (providerType != ProviderType.gemini) {
            SettingsSwitchRow(
                title = stringResource(R.string.add_provider_auto_append_v1_quoted),
                checked = appendV1Suffix,
                onCheckedChange = { appendV1Suffix = it },
                showDivider = false,
            )
        }
    }

    // ── API Format (OpenAI only) ────────────────────────────────────────
    if (providerType == ProviderType.openAI) {
        SettingsSection(
            header = stringResource(R.string.provider_detail_api_format),
            footer = if (useResponsesAPI) {
                stringResource(R.string.provider_detail_api_format_footer_responses)
            } else {
                stringResource(R.string.provider_detail_api_format_footer_chat)
            },
        ) {
            SettingsCardBlock {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !useResponsesAPI,
                        onClick = { useResponsesAPI = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.provider_detail_chat_completions)) }
                    SegmentedButton(
                        selected = useResponsesAPI,
                        onClick = { useResponsesAPI = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.provider_detail_responses_api)) }
                }
            }
        }
    }

    // ── Save button (outside any section — terminal action) ────────────
    Spacer(Modifier.height(20.dp))
    MinisButton(
        onClick = {
            val trimmedBase = customBaseURL.trim()
            val instance = ProviderInstance(
                id = UUID.randomUUID().toString(),
                label = label.ifBlank { providerType.displayName },
                providerType = providerType,
                credentialType = ProviderCredential.apiKey,
                customBaseURL = trimmedBase.ifEmpty { null },
                appendV1Suffix = appendV1Suffix,
                // Only OpenAI-family providers expose the Responses API toggle.
                useResponsesAPI = providerType == ProviderType.openAI && useResponsesAPI,
            )
            providerRepository.addInstance(instance)
            // [T-empty-key-compat-endpoints] Never persist an empty string as
            // the key — usableApiKey() treats absent as the keyless-valid case.
            if (apiKey.isNotBlank()) {
                providerRepository.saveApiKey(instance.id, apiKey.trim())
            }
            // Auto-refresh models (fetches from API or falls back to models.dev).
            // [T-provider-refresh-outlives-screen] (GH#265) On the repository's
            // scope: onSaved() pops this screen on the next line, which cancels
            // rememberCoroutineScope() and used to kill the fetch before it ran.
            providerRepository.triggerAsyncModelReconcile(instance.id)
            onSaved()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        // [T-empty-key-compat-endpoints] An empty key is a valid input when
        // targeting a third-party OpenAI/Anthropic-compatible endpoint
        // (custom base URL filled in) — ollama / LM Studio / LiteLLM /
        // private relays need no key. Official endpoints keep requiring a
        // credential. Mirrors iOS AddProviderView.
        enabled = apiKey.isNotBlank() || (
            customBaseURL.isNotBlank() &&
                (providerType == ProviderType.openAI || providerType == ProviderType.anthropic)
        ),
    ) {
        Text(stringResource(R.string.provider_list_add_provider))
    }
}
