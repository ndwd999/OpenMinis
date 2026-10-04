package com.yujian.minis.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yujian.minis.R
import com.yujian.minis.data.model.ProviderCredential
import com.yujian.minis.data.model.ProviderInstance
import com.yujian.minis.data.model.ProviderType
import com.yujian.minis.data.model.VoiceProviderTemplate
import com.yujian.minis.data.repository.ProviderRepository
import com.yujian.minis.ui.components.MinisButton
import com.yujian.minis.ui.components.RowLabel
import com.yujian.minis.ui.components.SectionTextField
import java.util.UUID

/**
 * [T-android-add-provider-single-page] Add a provider — one page.
 *
 * This used to be a two-step wizard: pick a protocol from a list of three, then
 * configure it. With only three protocols the first screen was almost pure
 * navigation — three rows, each one a tap that revealed the same form with one
 * thing pre-set, and every field the user actually fills in lived on the second
 * screen. The protocol is a segmented control at the top of the form now, so the
 * choice and the configuration it affects are visible together, and switching
 * protocol keeps whatever was already typed wherever that still applies.
 *
 * The voice-chat presets stay a separate section: they are not protocols but
 * pre-filled vendor templates (base URL + seeded voice models), and burying them
 * in a dropdown would hide the only thing that makes them useful.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProviderScreen(
    providerRepository: ProviderRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var providerType by remember { mutableStateOf(ProviderType.openAI) }
    var voiceTemplate by remember { mutableStateOf<VoiceProviderTemplate?>(null) }

    SettingsScaffold(
        title = stringResource(R.string.provider_list_add_provider),
        onBack = onBack,
    ) {
        AddProviderForm(
            providerType = providerType,
            voiceTemplate = voiceTemplate,
            onProviderTypeChange = { type ->
                // [T-android-add-provider-single-page] Switching protocol drops
                // the template: its base URL and v1 policy belong to ITS vendor,
                // and carrying them onto another protocol would send the request
                // to the wrong host. Label and key are typed values, so they
                // survive — losing those on a mis-tap would be worse.
                if (type != providerType) {
                    providerType = type
                    voiceTemplate = null
                }
            },
            onVoiceTemplateChange = { template ->
                // [T-android-provider-voice] Mirror iOS applyVoiceTemplate: adopt
                // the template's protocol along with its base URL, so picking a
                // vendor is a single tap rather than protocol-then-vendor.
                voiceTemplate = template
                if (template != null) providerType = template.providerType
            },
            providerRepository = providerRepository,
            onSaved = onSaved,
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The provider protocols this build offers for creation, in display order.
 *
 * Three API shapes, three entries: the OpenAI-compatible dialect — which also
 * covers every relay and third-party vendor via a custom base URL — Anthropic,
 * and Gemini. `ProviderType` still carries OpenRouter / xAI / Kimi Code /
 * GitHub Copilot / openAIResponses as DECODE-ONLY cases so a config written by
 * another build still loads; see the enum's KDoc. They are deliberately absent
 * here: this is what the user can create, and every one of those vendors is
 * reachable as an OpenAI-compatible instance with its own base URL.
 */
private val addableProviderTypes = listOf(
    ProviderType.openAI,
    ProviderType.anthropic,
    ProviderType.gemini,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderForm(
    providerType: ProviderType,
    voiceTemplate: VoiceProviderTemplate?,
    onProviderTypeChange: (ProviderType) -> Unit,
    onVoiceTemplateChange: (VoiceProviderTemplate?) -> Unit,
    providerRepository: ProviderRepository,
    onSaved: () -> Unit,
) {
    // Compute default label with auto-increment (e.g. "OpenAI", "OpenAI 2", ...)
    // A voice template preseeds its vendor name instead of the protocol name.
    val config by providerRepository.config.collectAsState()
    val defaultLabel = remember(config, voiceTemplate) {
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
    // recomputation makes the field appear to reject Chinese — the iOS root
    // cause a user reported, with the same Android equivalent here.
    var labelEdited by remember { mutableStateOf(false) }
    LaunchedEffect(defaultLabel, labelEdited) {
        if (!labelEdited) label = defaultLabel
    }
    var apiKey by remember { mutableStateOf("") }

    // A template owns its endpoint: it arrives with a base URL already filled in
    // and a v1 policy that matches its vendor, so the field shows the template's
    // value until the user types. Editing it means the user is pointing the
    // template somewhere else, which is a legitimate thing to do and no reason
    // to stand in the way — the text field is therefore the single source of
    // truth rather than a read-only display plus a separate editor buffer,
    // which would desync the moment the selected template changed.
    var customBaseURL by remember(voiceTemplate) { mutableStateOf(voiceTemplate?.baseURL ?: "") }

    // ── Protocol ────────────────────────────────────────────────────────
    // The choice the old first screen existed for, now one tap away from the
    // fields it configures. A segmented row rather than three list rows: the
    // labels are short, and it keeps the form the same width as the rest of
    // settings instead of spending a whole screen on a three-way pick.
    SettingsSection(
        header = stringResource(R.string.add_provider_choose_provider),
        footer = stringResource(R.string.add_provider_you_can_add_multiple_instances_of_the_sa),
    ) {
        SettingsCardBlock {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                addableProviderTypes.forEachIndexed { index, type ->
                    SegmentedButton(
                        // [T-android-add-provider-single-page] Nothing is
                        // selected while a template is active: what the user
                        // chose is the vendor, not the wire format, and
                        // lighting up "OpenAI" next to an ElevenLabs preset
                        // reads as a conflict.
                        selected = providerType == type && voiceTemplate == null,
                        onClick = { onProviderTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = addableProviderTypes.size,
                        ),
                        icon = {
                            // The check rides INSIDE the segment rather than
                            // after it, so the three labels stay on a fixed
                            // grid instead of shifting sideways on selection.
                            if (providerType == type && voiceTemplate == null) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                )
                            }
                        },
                    ) {
                        Text(providerTypeLabel(type))
                    }
                }
            }
        }
    }

    // ── Voice chat presets ──────────────────────────────────────────────
    // [T-android-provider-voice] Not protocols but pre-filled vendor templates
    // (base URL + seeded voice models), so they stay a list.
    val templates = VoiceProviderTemplate.all
    if (templates.isNotEmpty()) {
        SettingsSection(
            header = stringResource(R.string.add_provider_voice_chat_providers),
            footer = (
                listOf(stringResource(R.string.add_provider_voice_templates_footer)) +
                    templates.mapNotNull { it.note }
                ).joinToString("\n"),
        ) {
            templates.forEachIndexed { index, template ->
                val capabilityRes = when (template.capability) {
                    VoiceProviderTemplate.Capability.TTS ->
                        R.string.add_provider_voice_capability_tts
                    VoiceProviderTemplate.Capability.ASR ->
                        R.string.add_provider_voice_capability_asr
                    VoiceProviderTemplate.Capability.BOTH ->
                        R.string.add_provider_voice_capability_both
                }
                SettingsRow(
                    title = template.name,
                    subtitle = stringResource(capabilityRes),
                    icon = Icons.Outlined.GraphicEq,
                    onClick = {
                        onVoiceTemplateChange(if (voiceTemplate == template) null else template)
                    },
                    showDivider = index < templates.size - 1,
                )
            }
        }
    }

    // ── Identity ────────────────────────────────────────────────────────
    // Label only. Each provider auto-suggests a unique label so users don't have
    // to type one for the common case.
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

    // ── Credential ──────────────────────────────────────────────────────
    // API key is the only credential this build has.
    var showApiKeyPlaintext by remember { mutableStateOf(false) }
    val keyPlaceholder = when (providerType) {
        ProviderType.anthropic -> "sk-ant-..."
        ProviderType.openAI -> "sk-..."
        ProviderType.gemini -> stringResource(R.string.add_provider_key_placeholder_gemini)
        else -> stringResource(R.string.add_provider_key_placeholder_default)
    }
    SettingsSection(
        header = stringResource(R.string.add_provider_credential),
        footer = stringResource(R.string.add_provider_your_key_is_stored_securely_in_encrypted),
    ) {
        SettingsCardBlock {
            RowLabel(text = stringResource(R.string.provider_list_api_key))
            SectionTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = keyPlaceholder,
                singleLine = true,
                visualTransformation = if (showApiKeyPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKeyPlaintext = !showApiKeyPlaintext }) {
                        Icon(
                            if (showApiKeyPlaintext) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKeyPlaintext) stringResource(R.string.common_hide) else stringResource(R.string.common_show),
                        )
                    }
                },
            )
        }
    }

    // ── Endpoint ────────────────────────────────────────────────────────
    // [T-android-provider-voice] A template's policy wins over the type
    // default: ElevenLabs hosts its API at the root and must NOT get /v1
    // appended, while most OpenAI-compatible relays need it. Keyed on the
    // template so switching either one restores the other's policy instead of
    // leaving a stale toggle behind.
    var appendV1Suffix by remember(voiceTemplate, providerType) {
        mutableStateOf(voiceTemplate?.appendV1 ?: (providerType != ProviderType.gemini))
    }
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
    val baseUrlFooter = when {
        providerType == ProviderType.gemini ->
            stringResource(R.string.add_provider_endpoint_hint_gemini)
        providerType == ProviderType.anthropic ->
            stringResource(R.string.add_provider_endpoint_anthropic_hint)
        appendV1Suffix ->
            stringResource(R.string.add_provider_endpoint_hint_v1)
        else ->
            stringResource(R.string.add_provider_endpoint_hint_verbatim)
    }
    SettingsSection(
        header = stringResource(R.string.add_provider_endpoint),
        footer = baseUrlFooter,
    ) {
        SettingsCardBlock {
            RowLabel(text = stringResource(R.string.add_provider_custom_api_base_optional))
            SectionTextField(
                value = customBaseURL,
                onValueChange = { customBaseURL = it },
                placeholder = defaultUrl,
                singleLine = true,
            )
        }
        // Auto Append "/v1" toggle (not for Gemini — Gemini uses full path).
        // Also not for a template whose endpoint the vendor fixes: the toggle
        // would offer to break a configuration that arrived correct.
        if (providerType != ProviderType.gemini && voiceTemplate?.appendV1 == null) {
            SettingsSwitchRow(
                title = stringResource(R.string.add_provider_auto_append_v1_quoted),
                checked = appendV1Suffix,
                onCheckedChange = { appendV1Suffix = it },
                showDivider = false,
            )
        }
    }

    // ── API Format (OpenAI only) ────────────────────────────────────────
    // OpenAI API Format: false = Chat Completions, true = Responses API
    var useResponsesAPI by remember { mutableStateOf(false) }
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

    // ── Save (outside any section — terminal action) ────────────────────
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

/**
 * Short label for the protocol segmented control.
 *
 * [T-android-add-provider-single-page] Deliberately NOT the `add_provider_type_*`
 * strings: those read "OpenAI / Compatible API" and blow out three equal
 * columns. The compatibility detail is not lost — the section footer under the
 * control still says it, and the base-URL field is right there on the same page.
 */
@Composable
private fun providerTypeLabel(type: ProviderType): String = when (type) {
    ProviderType.openAI -> stringResource(R.string.add_provider_type_openai_short)
    ProviderType.anthropic -> stringResource(R.string.add_provider_type_anthropic_short)
    ProviderType.gemini -> stringResource(R.string.add_provider_type_gemini_short)
    else -> type.displayName
}
