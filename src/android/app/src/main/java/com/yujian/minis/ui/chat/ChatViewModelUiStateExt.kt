package com.yujian.minis.ui.chat

// [T-android-split-chat] Small UI-state toggle methods extracted from
// ChatViewModel as extension functions (verbatim): tool-detail sheet,
// browser sheet, memory sheet, attachment list. The 4 backing state fields
// were flipped private->internal. No logic change.

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.LazyListState
import com.yujian.minis.agent.Level
import com.yujian.minis.agent.ToolLoopDetector
import com.yujian.minis.browser.BrowserActionInput
import com.yujian.minis.browser.BrowserTabPool
import com.yujian.minis.data.db.MessageEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import com.yujian.minis.data.BPETokenizer
import com.yujian.minis.data.ContextOffload
import com.yujian.minis.data.ContextPolicy
import com.yujian.minis.logging.AppLogger
import com.yujian.minis.data.FileMentionIndex
import com.yujian.minis.data.db.CompactMarkerEntity
import com.yujian.minis.data.model.AgentContentPart
import com.yujian.minis.data.model.AgentToolDefinition
import com.yujian.minis.data.model.LLMMessage
import com.yujian.minis.data.model.LLMModel
import com.yujian.minis.data.model.LLMStreamChunk
import com.yujian.minis.data.model.LLMUsage
import com.yujian.minis.data.model.ModelGroup
import com.yujian.minis.data.model.ThinkingLevel
import com.yujian.minis.R
import com.yujian.minis.data.repository.ChatRepository
import com.yujian.minis.data.repository.MemoryRepository
import com.yujian.minis.data.repository.ProviderRepository
import com.yujian.minis.provider.ImageBudget
import com.yujian.minis.provider.LLMProvider
import com.yujian.minis.provider.ProviderFactory
import com.yujian.minis.sandbox.ExecutionCoordinator
import com.yujian.minis.terminal.MinisOpenUrlBroker
import com.yujian.minis.terminal.MinisUrlMarker
import com.yujian.minis.tools.AgentTools
import com.yujian.minis.tools.FileEditTool
import com.yujian.minis.tools.FileReadTool
import com.yujian.minis.tools.FileWriteTool
import com.yujian.minis.tools.MemoryTools
import com.yujian.minis.tools.ReadImageTool
import com.yujian.minis.tools.ToolExecutionResult
import com.yujian.minis.offload.OffloadPermissionManager
import com.yujian.minis.service.SessionActivityTracker
import com.yujian.minis.service.SessionConcurrencyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal fun ChatViewModel.openToolDetail(toolBlockId: String) {
    _selectedToolDetailId.value = toolBlockId
}

internal fun ChatViewModel.closeToolDetail() {
    _selectedToolDetailId.value = null
}

internal fun ChatViewModel.toggleBrowserSheet() {
    val opening = !_showBrowserSheet.value
    if (opening) browserTabPool.ensureTabForUI()
    _showBrowserSheet.value = opening
}

internal fun ChatViewModel.dismissBrowserSheet() {
    _showBrowserSheet.value = false
}

/**
 * Open the session browser sheet, focused on the tab whose URL matches
 * [url]. If no pool tab currently has that URL, a new tab is created and
 * loaded. Used by the tool-call preview's globe button so the agent's
 * existing browser_use page is reused when available instead of spawning
 * a duplicate tab.
 */
internal fun ChatViewModel.openBrowserSheetForUrl(url: String) {
    if (url.isBlank()) {
        browserTabPool.ensureTabForUI()
    } else {
        browserTabPool.selectOrCreateTabForURL(url)
    }
    _showBrowserSheet.value = true
}

internal fun ChatViewModel.toggleMemorySheet() {
    _showMemorySheet.value = !_showMemorySheet.value
}

internal fun ChatViewModel.dismissMemorySheet() {
    _showMemorySheet.value = false
}

internal fun ChatViewModel.addAttachment(attachment: InputAttachment) {
    _attachments.value = _attachments.value + attachment
}

internal fun ChatViewModel.removeAttachment(id: String) {
    _attachments.value = _attachments.value.filter { it.id != id }
}

internal fun ChatViewModel.clearAttachments() {
    _attachments.value = emptyList()
}
