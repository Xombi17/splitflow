package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.LocalAiEngineState
import com.example.ai.LocalSplitAiService
import com.example.data.DeviceContact
import com.example.data.GuestLedgerRepository
import com.example.data.GuestSessionStore
import com.example.data.LedgerRepository
import com.example.data.ReceiptPhotoStore
import com.example.data.SplitFlowRepository
import com.example.engine.FinanceEngine
import com.example.model.AsyncUiState
import com.example.model.BatchItemStatus
import com.example.model.BatchReceiptItem
import com.example.model.CurrencyCode
import com.example.model.DebtSimplificationReport
import com.example.model.DietPreference
import com.example.model.ExpenseGroup
import com.example.model.ExpenseRecord
import com.example.model.GroupCategory
import com.example.model.LedgerMember
import com.example.model.ParsedReceipt
import com.example.model.ParsedVoiceSplit
import com.example.model.PayerEntry
import com.example.model.PaymentMethod
import com.example.model.RecurringFrequency
import com.example.model.SettlementRecord
import com.example.model.SpendCoachReport
import com.example.model.SplitAllocation
import com.example.model.SplitType
import com.example.model.TripStoryNarrative
import com.example.model.UserProfile
import com.example.model.VoiceActionType
import com.example.model.VoiceExecutionResult
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "SplitFlowVM"

data class LedgerSnapshot(
    val profile: UserProfile? = null,
    val members: List<LedgerMember> = emptyList(),
    val groups: List<ExpenseGroup> = emptyList(),
    val expenses: List<ExpenseRecord> = emptyList(),
    val settlements: List<SettlementRecord> = emptyList()
) {
    val selfMember: LedgerMember? get() = members.firstOrNull { it.selfProfile } ?: members.firstOrNull()
    val defaultCurrency: CurrencyCode get() = profile?.currencyEnum ?: CurrencyCode.INR
    val darkMode: Boolean get() = profile?.darkMode ?: true

    val globalDebtReport: DebtSimplificationReport
        get() = FinanceEngine.computeDebtSimplification(
            members = members,
            expenses = expenses,
            settlements = settlements,
            groupIdFilter = null,
            currency = defaultCurrency
        )

    val selfNetMinorUnits: Long
        get() {
            val selfId = selfMember?.id ?: return 0L
            return globalDebtReport.balances.firstOrNull { it.memberId == selfId }?.netMinorUnits ?: 0L
        }

    val totalOwedToSelfMinor: Long
        get() {
            val selfId = selfMember?.id ?: return 0L
            return globalDebtReport.simplifiedTransfers
                .filter { it.toMemberId == selfId }
                .sumOf { it.amountMinorUnits }
        }

    val totalSelfOwesMinor: Long
        get() {
            val selfId = selfMember?.id ?: return 0L
            return globalDebtReport.simplifiedTransfers
                .filter { it.fromMemberId == selfId }
                .sumOf { it.amountMinorUnits }
        }

    val recurringExpenses: List<ExpenseRecord>
        get() = expenses
            .filter { it.recurringEnum != RecurringFrequency.NONE }
            .sortedBy { it.nextDueDateLabel ?: it.dateLabel }

    val receiptExpenses: List<ExpenseRecord>
        get() = expenses
            .filter { !it.receiptPhotoPath.isNullOrBlank() }
            .sortedByDescending { it.dateLabel }
}

sealed interface LedgerUiState {
    data object Loading : LedgerUiState
    data class Ready(val snapshot: LedgerSnapshot) : LedgerUiState
    data class Error(val message: String) : LedgerUiState
}

data class ToastBannerEvent(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val actionLabel: String? = null,
    val isError: Boolean = false
)

class SplitFlowViewModel(
    private val repository: LedgerRepository,
    private val localAiService: LocalSplitAiService,
    private val currentUserId: String
) : ViewModel() {

    val localAiEngineState: StateFlow<LocalAiEngineState> = localAiService.engineState

    fun installWhistleModel() {
        viewModelScope.launch {
            val ok = localAiService.downloadWhistleModel()
            if (ok) {
                val slug = localAiEngineState.value.whistleActiveSlug ?: "Whistle"
                showToast("Cactus '$slug' ready on-device")
            } else {
                val err = localAiEngineState.value.whistleError ?: localAiEngineState.value.whistleNote
                showToast("Cactus STT failed: $err", isError = true)
            }
        }
    }

    fun installMirageModel() {
        viewModelScope.launch {
            val ok = localAiService.downloadMirageModel()
            if (ok) {
                val slug = localAiEngineState.value.mirageActiveSlug ?: "Mirage"
                showToast("Cactus '$slug' OCR ready on-device")
            } else {
                val err = localAiEngineState.value.mirageError ?: localAiEngineState.value.mirageNote
                showToast("Cactus OCR failed: $err", isError = true)
            }
        }
    }

    private var lastDeletedExpense: ExpenseRecord? = null

    private val _toastEvent = MutableStateFlow<ToastBannerEvent?>(null)
    val toastEvent: StateFlow<ToastBannerEvent?> = _toastEvent.asStateFlow()

    private val _isActionBusy = MutableStateFlow(false)
    val isActionBusy: StateFlow<Boolean> = _isActionBusy.asStateFlow()

    private val _receiptLensState = MutableStateFlow<AsyncUiState<ParsedReceipt>>(AsyncUiState.Idle)
    val receiptLensState: StateFlow<AsyncUiState<ParsedReceipt>> = _receiptLensState.asStateFlow()

    private val _voiceSplitState = MutableStateFlow<AsyncUiState<VoiceExecutionResult>>(AsyncUiState.Idle)
    val voiceSplitState: StateFlow<AsyncUiState<VoiceExecutionResult>> = _voiceSplitState.asStateFlow()

    private val _spendCoachState = MutableStateFlow<AsyncUiState<SpendCoachReport>>(AsyncUiState.Idle)
    val spendCoachState: StateFlow<AsyncUiState<SpendCoachReport>> = _spendCoachState.asStateFlow()

    private val _coachFollowUpAnswer = MutableStateFlow("")
    val coachFollowUpAnswer: StateFlow<String> = _coachFollowUpAnswer.asStateFlow()

    private val _isCoachStreaming = MutableStateFlow(false)
    val isCoachStreaming: StateFlow<Boolean> = _isCoachStreaming.asStateFlow()

    private var coachStreamJob: Job? = null

    private val _isPrivacyBlurEnabled = MutableStateFlow(false)
    val isPrivacyBlurEnabled: StateFlow<Boolean> = _isPrivacyBlurEnabled.asStateFlow()

    private val _batchItems = MutableStateFlow<List<BatchReceiptItem>>(emptyList())
    val batchItems: StateFlow<List<BatchReceiptItem>> = _batchItems.asStateFlow()

    private val _tripStoryState = MutableStateFlow<AsyncUiState<TripStoryNarrative>>(AsyncUiState.Idle)
    val tripStoryState: StateFlow<AsyncUiState<TripStoryNarrative>> = _tripStoryState.asStateFlow()

    private var batchJob: Job? = null

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    val ledgerState: StateFlow<LedgerUiState> = combine(
        repository.observeProfile(currentUserId),
        repository.observeMembers(currentUserId),
        repository.observeGroups(currentUserId),
        repository.observeExpenses(currentUserId),
        repository.observeSettlements(currentUserId)
    ) { profile, members, groups, expenses, settlements ->
        LedgerSnapshot(
            profile = profile,
            members = members,
            groups = groups,
            expenses = expenses,
            settlements = settlements
        )
    }
        .map<LedgerSnapshot, LedgerUiState> { LedgerUiState.Ready(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing SplitFlow ledger", rootError)
            emit(LedgerUiState.Error(rootError.localizedMessage ?: "Failed to sync ledger"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = LedgerUiState.Loading
        )

    init {
        viewModelScope.launch {
            val result = repository.ensureInitialized(currentUserId)
            result.onFailure { err ->
                showToast(err.localizedMessage ?: "Could not initialize workspace", isError = true)
            }
        }
    }

    /**
     * Automatically migrates any local Guest Mode data into the authenticated user's
     * Cloud Firestore database when signing in with Google.
     */
    fun migrateGuestDataIfPresent(guestSessionStore: GuestSessionStore) {
        val cloudRepo = repository as? SplitFlowRepository ?: return
        val payload = GuestLedgerRepository.readMigrationPayload(guestSessionStore) ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = cloudRepo.migrateGuestDataToCloud(currentUserId, payload)
            _isActionBusy.value = false
            result.onSuccess { count ->
                guestSessionStore.clearPersistedLedger()
                if (count > 0) {
                    showToast("Synced $count local ${if (count == 1) "item" else "items"} to your Google cloud ledger")
                }
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Could not sync local guest splits", isError = true)
            }
        }
    }

    fun updateOfflineStatus(offline: Boolean) {
        _isOffline.value = offline
    }

    fun togglePrivacyBlur() {
        val next = !_isPrivacyBlurEnabled.value
        _isPrivacyBlurEnabled.value = next
        showToast(if (next) "Privacy Blur enabled — figures hidden" else "Privacy Blur disabled")
    }

    fun dismissToast() {
        _toastEvent.value = null
    }

    fun showToast(message: String, actionLabel: String? = null, isError: Boolean = false) {
        _toastEvent.value = ToastBannerEvent(
            message = message,
            actionLabel = actionLabel,
            isError = isError
        )
    }

    fun todayIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // --- AI RECEIPT LENS ---

    fun analyzeReceiptBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _receiptLensState.value = AsyncUiState.Loading
            try {
                val parsed = localAiService.analyzeReceipt(bitmap)
                _receiptLensState.value = AsyncUiState.Success(parsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Receipt Lens failed", e)
                _receiptLensState.value = AsyncUiState.Error(
                    e.localizedMessage ?: "Could not extract receipt items. Please try again."
                )
            }
        }
    }

    fun resetReceiptLens() {
        _receiptLensState.value = AsyncUiState.Idle
    }

    // --- AI VOICE ASSISTANT (MULTIMODAL AUDIO + DIRECT ACCOUNT MUTATION) ---

    fun processVoiceAudio(
        wavAudioBytes: ByteArray,
        selectedGroupId: String?,
        autoApply: Boolean = true
    ) {
        val currentSnapshot = (ledgerState.value as? LedgerUiState.Ready)?.snapshot ?: return
        if (wavAudioBytes.size <= 44) {
            _voiceSplitState.value = AsyncUiState.Error("Recording was too short. Tap the mic and speak clearly.")
            return
        }
        val targetGroup = selectedGroupId?.let { gid -> currentSnapshot.groups.firstOrNull { it.id == gid } }

        viewModelScope.launch {
            _voiceSplitState.value = AsyncUiState.Loading
            try {
                val parsed = localAiService.transcribeAndParseVoiceAudio(
                    wavAudioBytes = wavAudioBytes,
                    members = currentSnapshot.members,
                    groups = currentSnapshot.groups,
                    defaultCurrency = targetGroup?.currencyEnum ?: currentSnapshot.defaultCurrency,
                    groupMemoryNotes = targetGroup?.splitMemoryNotes
                )
                handleParsedVoiceCommand(
                    parsed = parsed,
                    currentSnapshot = currentSnapshot,
                    explicitGroupId = selectedGroupId,
                    autoApply = autoApply
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Voice Audio processing failed", e)
                _voiceSplitState.value = AsyncUiState.Error(
                    e.localizedMessage ?: "Could not transcribe or understand audio. Please try again."
                )
            }
        }
    }

    fun parseNaturalLanguageSplit(
        transcript: String,
        targetGroupId: String?,
        autoApply: Boolean = true,
        fromDeviceSpeechFallback: Boolean = false
    ) {
        val currentSnapshot = (ledgerState.value as? LedgerUiState.Ready)?.snapshot ?: return
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) {
            _voiceSplitState.value = AsyncUiState.Error("Please speak or type a command first.")
            return
        }
        val targetGroup = targetGroupId?.let { gid -> currentSnapshot.groups.firstOrNull { it.id == gid } }

        viewModelScope.launch {
            _voiceSplitState.value = AsyncUiState.Loading
            try {
                val parsed = localAiService.parseVoiceCommand(
                    transcript = trimmed,
                    members = currentSnapshot.members,
                    groups = currentSnapshot.groups,
                    defaultCurrency = targetGroup?.currencyEnum ?: currentSnapshot.defaultCurrency,
                    groupMemoryNotes = targetGroup?.splitMemoryNotes,
                    fromDeviceSpeechFallback = fromDeviceSpeechFallback
                )
                handleParsedVoiceCommand(
                    parsed = parsed,
                    currentSnapshot = currentSnapshot,
                    explicitGroupId = targetGroupId,
                    autoApply = autoApply
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Voice Split failed", e)
                _voiceSplitState.value = AsyncUiState.Error(
                    e.localizedMessage ?: "Could not parse voice command. Please try again."
                )
            }
        }
    }

    private suspend fun handleParsedVoiceCommand(
        parsed: ParsedVoiceSplit,
        currentSnapshot: LedgerSnapshot,
        explicitGroupId: String?,
        autoApply: Boolean
    ) {
        // Prefer an explicitly spoken group name, else user's chosen scope pill (null = Personal)
        val resolvedGroupId = parsed.matchedGroupId ?: explicitGroupId
        if (!autoApply) {
            _voiceSplitState.value = AsyncUiState.Success(
                VoiceExecutionResult(
                    parsed = parsed,
                    applied = false,
                    resolvedGroupId = resolvedGroupId,
                    statusMessage = "Review and confirm to update your accounts"
                )
            )
            return
        }

        val execution = applyVoiceActionToLedger(
            parsed = parsed,
            currentSnapshot = currentSnapshot,
            targetGroupId = resolvedGroupId,
            overrideTitle = null
        )
        execution.onSuccess { result ->
            _voiceSplitState.value = AsyncUiState.Success(result)
            showToast(result.parsed.ledgerSummaryLine.ifBlank { "Updated account via AI Voice" })
        }.onFailure { err ->
            Log.e(TAG, "Failed to apply voice action to ledger", err)
            _voiceSplitState.value = AsyncUiState.Error(
                err.localizedMessage ?: "Parsed command, but could not save to your account."
            )
        }
    }

    fun commitPendingVoiceResult(
        result: VoiceExecutionResult,
        overrideTitle: String? = null,
        overrideGroupId: String? = result.resolvedGroupId,
        onComplete: () -> Unit = {}
    ) {
        val currentSnapshot = (ledgerState.value as? LedgerUiState.Ready)?.snapshot ?: return
        viewModelScope.launch {
            _isActionBusy.value = true
            val execution = applyVoiceActionToLedger(
                parsed = result.parsed,
                currentSnapshot = currentSnapshot,
                targetGroupId = overrideGroupId,
                overrideTitle = overrideTitle?.trim()?.takeIf { it.isNotEmpty() }
            )
            _isActionBusy.value = false
            execution.onSuccess { applied ->
                _voiceSplitState.value = AsyncUiState.Success(applied)
                showToast(applied.parsed.ledgerSummaryLine.ifBlank { "Saved via AI Voice" })
                onComplete()
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Could not save voice entry", isError = true)
            }
        }
    }

    fun undoVoiceExecution(result: VoiceExecutionResult) {
        val expenseId = result.savedExpenseId ?: return
        viewModelScope.launch {
            val res = repository.deleteExpense(currentUserId, expenseId)
            res.onSuccess {
                _voiceSplitState.value = AsyncUiState.Idle
                showToast("Undid \"${result.parsed.ledgerSummaryLine.ifBlank { result.parsed.title }}\"")
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Could not undo entry", isError = true)
            }
        }
    }

    private suspend fun applyVoiceActionToLedger(
        parsed: ParsedVoiceSplit,
        currentSnapshot: LedgerSnapshot,
        targetGroupId: String?,
        overrideTitle: String?
    ): Result<VoiceExecutionResult> {
        // 1. Collect & auto-create any new friends mentioned by voice (e.g. "Tanmay")
        val existingMembers = currentSnapshot.members
        val newNamesToCreate = linkedSetOf<String>()
        parsed.newMemberNames.forEach { if (it.isNotBlank()) newNamesToCreate.add(it.trim()) }
        if (parsed.paidByMemberId.startsWith("NEW:")) {
            newNamesToCreate.add(parsed.paidByMemberId.removePrefix("NEW:").trim())
        }
        parsed.participants.forEach { p ->
            if (p.memberId.startsWith("NEW:")) {
                newNamesToCreate.add(p.memberId.removePrefix("NEW:").trim())
            }
        }
        parsed.settlementFromMemberId?.takeIf { it.startsWith("NEW:") }?.let {
            newNamesToCreate.add(it.removePrefix("NEW:").trim())
        }
        parsed.settlementToMemberId?.takeIf { it.startsWith("NEW:") }?.let {
            newNamesToCreate.add(it.removePrefix("NEW:").trim())
        }

        val palette = listOf("#0E6F4E", "#2C4C7E", "#B45333", "#C5962C", "#5B4B8A", "#0284C7")
        val resolvedNewIds = mutableMapOf<String, String>() // lowercase name -> real memberId
        val createdNames = mutableListOf<String>()

        for (rawName in newNamesToCreate) {
            if (rawName.isBlank()) continue
            val existingMatch = existingMembers.firstOrNull {
                it.name.equals(rawName, ignoreCase = true) ||
                    it.name.split(" ").first().equals(rawName, ignoreCase = true)
            }
            if (existingMatch != null) {
                resolvedNewIds[rawName.lowercase()] = existingMatch.id
            } else {
                val color = palette[(existingMembers.size + createdNames.size) % palette.size]
                val newMember = LedgerMember(
                    userId = currentUserId,
                    name = rawName,
                    avatarColorHex = color,
                    selfProfile = false,
                    guestProfile = false
                )
                val addRes = repository.addMember(currentUserId, newMember)
                val newId = addRes.getOrElse { return Result.failure(it) }
                resolvedNewIds[rawName.lowercase()] = newId
                createdNames.add(rawName)
            }
        }

        fun resolveId(rawId: String): String {
            if (!rawId.startsWith("NEW:")) return rawId
            val nameKey = rawId.removePrefix("NEW:").trim().lowercase()
            return resolvedNewIds[nameKey] ?: currentSnapshot.selfMember?.id.orEmpty()
        }

        val selfId = currentSnapshot.selfMember?.id.orEmpty()
        val resolvedPaidBy = resolveId(parsed.paidByMemberId).ifBlank { selfId }
        val resolvedParticipants = parsed.participants.map { p ->
            p.copy(memberId = resolveId(p.memberId))
        }.filter { it.memberId.isNotBlank() }

        val updatedParsed = parsed.copy(
            title = overrideTitle ?: parsed.title,
            paidByMemberId = resolvedPaidBy,
            participants = resolvedParticipants,
            settlementFromMemberId = parsed.settlementFromMemberId?.let { resolveId(it) },
            settlementToMemberId = parsed.settlementToMemberId?.let { resolveId(it) }
        )

        // 2. Persist Settlement OR Expense to Firestore / local repository
        if (updatedParsed.actionType == VoiceActionType.SETTLEMENT) {
            val fromId = updatedParsed.settlementFromMemberId?.takeIf { it.isNotBlank() }
                ?: resolvedParticipants.firstOrNull { it.memberId != selfId }?.memberId
                ?: resolvedPaidBy
            val toId = updatedParsed.settlementToMemberId?.takeIf { it.isNotBlank() }
                ?: selfId
            val settlement = SettlementRecord(
                userId = currentUserId,
                groupId = targetGroupId,
                fromMemberId = fromId,
                toMemberId = toId,
                amountMinorUnits = updatedParsed.totalMinorUnits,
                currency = updatedParsed.currency.name,
                paymentMethod = PaymentMethod.UPI.name,
                note = "AI Voice: \"${updatedParsed.transcript}\"",
                dateLabel = todayIso()
            )
            val saveRes = repository.addSettlement(currentUserId, settlement)
            val savedId = saveRes.getOrElse { return Result.failure(it) }
            return Result.success(
                VoiceExecutionResult(
                    parsed = updatedParsed,
                    applied = true,
                    createdMemberNames = createdNames,
                    savedSettlementId = savedId,
                    resolvedGroupId = targetGroupId,
                    statusMessage = "Settlement applied to your account"
                )
            )
        } else {
            val finalAllocations = if (resolvedParticipants.isNotEmpty()) {
                resolvedParticipants.map { p ->
                    SplitAllocation(
                        memberId = p.memberId,
                        amountMinorUnits = p.amountMinorUnits,
                        percentageBps = p.percentageBps,
                        shares = p.shares
                    )
                }
            } else {
                listOf(SplitAllocation(memberId = selfId, amountMinorUnits = updatedParsed.totalMinorUnits))
            }

            val record = ExpenseRecord(
                userId = currentUserId,
                groupId = targetGroupId,
                title = overrideTitle ?: updatedParsed.title,
                notes = "AI Voice: \"${updatedParsed.transcript}\"",
                category = updatedParsed.category.name,
                currency = updatedParsed.currency.name,
                totalMinorUnits = updatedParsed.totalMinorUnits,
                splitType = updatedParsed.splitType.name,
                dateLabel = todayIso(),
                shareCode = "voice-${UUID.randomUUID().toString().take(8)}",
                aiExtracted = true,
                aiConfidenceNote = updatedParsed.ledgerSummaryLine.ifBlank { updatedParsed.aiConfidenceNote },
                settled = false,
                payers = listOf(PayerEntry(resolvedPaidBy, updatedParsed.totalMinorUnits)),
                allocations = finalAllocations,
                lineItems = emptyList()
            )
            val saveRes = repository.addExpense(currentUserId, record)
            val savedId = saveRes.getOrElse { return Result.failure(it) }
            return Result.success(
                VoiceExecutionResult(
                    parsed = updatedParsed,
                    applied = true,
                    createdMemberNames = createdNames,
                    savedExpenseId = savedId,
                    resolvedGroupId = targetGroupId,
                    statusMessage = "Account & Activity updated automatically"
                )
            )
        }
    }

    fun resetVoiceSplit() {
        _voiceSplitState.value = AsyncUiState.Idle
    }

    // --- BATCH MULTI-RECEIPT TRIP IMPORTER ---

    fun startBatchReceiptExtraction(
        items: List<Pair<String, Bitmap>>,
        context: Context
    ) {
        if (items.isEmpty()) return
        batchJob?.cancel()
        val initial = items.mapIndexed { idx, (label, _) ->
            BatchReceiptItem(
                id = "batch_${System.currentTimeMillis()}_$idx",
                label = label,
                status = BatchItemStatus.SCANNING
            )
        }
        _batchItems.value = initial

        batchJob = viewModelScope.launch {
            coroutineScope {
                initial.zip(items).map { (entry, pair) ->
                    async {
                        val savedPath = withContext(Dispatchers.IO) {
                            ReceiptPhotoStore.saveBitmap(context, pair.second)
                        }
                        try {
                            val parsed = localAiService.analyzeReceipt(pair.second)
                            _batchItems.update { list ->
                                list.map {
                                    if (it.id == entry.id) {
                                        it.copy(
                                            savedPhotoPath = savedPath,
                                            status = BatchItemStatus.READY,
                                            parsed = parsed
                                        )
                                    } else it
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Batch receipt failed for ${entry.label}", e)
                            _batchItems.update { list ->
                                list.map {
                                    if (it.id == entry.id) {
                                        it.copy(
                                            savedPhotoPath = savedPath,
                                            status = BatchItemStatus.ERROR,
                                            errorMessage = e.localizedMessage ?: "Could not extract receipt"
                                        )
                                    } else it
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun approveBatchItem(
        itemId: String,
        groupId: String?,
        paidByMemberId: String
    ) {
        val currentSnapshot = (ledgerState.value as? LedgerUiState.Ready)?.snapshot ?: return
        val item = _batchItems.value.firstOrNull { it.id == itemId } ?: return
        val parsed = item.parsed ?: return

        val group = groupId?.let { gid -> currentSnapshot.groups.firstOrNull { it.id == gid } }
        val participantIds = group?.memberIds?.ifEmpty { null }
            ?: currentSnapshot.members.take(3).map { it.id }.ifEmpty { listOf(paidByMemberId) }
        val targetBaseCurrency = group?.currencyEnum ?: currentSnapshot.defaultCurrency
        val fxMicros = if (parsed.currency != targetBaseCurrency) {
            FinanceEngine.getFxRateMicros(parsed.currency, targetBaseCurrency)
        } else null
        val allocs = FinanceEngine.distributeEqual(parsed.grandTotalMinor, participantIds)

        val record = ExpenseRecord(
            userId = currentUserId,
            groupId = groupId,
            title = parsed.merchantName,
            notes = "Batch imported via AI Receipt Queue (${parsed.lineItems.size} items)",
            category = parsed.category.name,
            currency = parsed.currency.name,
            totalMinorUnits = parsed.grandTotalMinor,
            splitType = SplitType.EQUAL.name,
            dateLabel = parsed.date.ifBlank { todayIso() },
            shareCode = "batch-${UUID.randomUUID().toString().take(8)}",
            aiExtracted = true,
            aiConfidenceNote = "Local OCR extracted ${parsed.lineItems.size} line items",
            settled = false,
            payers = listOf(PayerEntry(paidByMemberId, parsed.grandTotalMinor)),
            allocations = allocs,
            lineItems = parsed.lineItems.map { it.copy(assignedMemberIds = participantIds) },
            taxMinorUnits = parsed.taxMinor,
            serviceChargeMinorUnits = parsed.serviceChargeMinor,
            discountMinorUnits = parsed.discountMinor,
            receiptPhotoPath = item.savedPhotoPath,
            fxRateToBaseMicros = fxMicros
        )

        viewModelScope.launch {
            val res = repository.addExpense(currentUserId, record)
            res.onSuccess {
                _batchItems.update { list ->
                    list.map { if (it.id == itemId) it.copy(status = BatchItemStatus.APPROVED) else it }
                }
                showToast("Approved \"${parsed.merchantName}\"")
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to save batch receipt", isError = true)
            }
        }
    }

    fun skipBatchItem(itemId: String) {
        _batchItems.update { list ->
            list.map { if (it.id == itemId) it.copy(status = BatchItemStatus.SKIPPED) else it }
        }
    }

    fun resetBatchImporter() {
        batchJob?.cancel()
        _batchItems.value = emptyList()
    }

    // --- AI TRIP WRAP-UP & LEDGER STORY ---

    fun generateTripStory(groupId: String) {
        val currentSnapshot = (ledgerState.value as? LedgerUiState.Ready)?.snapshot ?: return
        val group = currentSnapshot.groups.firstOrNull { it.id == groupId } ?: return
        val grpExpenses = currentSnapshot.expenses.filter { it.groupId == groupId }
        val grpSettlements = currentSnapshot.settlements.filter { it.groupId == groupId }
        val groupMembers = currentSnapshot.members.filter { it.id in group.memberIds }
        val metrics = FinanceEngine.computeTripWrapUpMetrics(group, groupMembers, grpExpenses, grpSettlements)
        val budgetStatus = FinanceEngine.computeGroupBudgetStatus(group, grpExpenses)
        val digest = buildString {
            appendLine("Group: ${metrics.scopeName} (${group.categoryEnum.label})")
            appendLine("Currency: ${metrics.currency.name} (${metrics.currency.symbol})")
            appendLine("Total Spend: ${FinanceEngine.formatMinorUnits(metrics.totalSpendMinor, metrics.currency)} across ${metrics.expenseCount} bills")
            appendLine("Top Spender: ${metrics.topSpenderName} (${FinanceEngine.formatMinorUnits(metrics.topSpenderMinor, metrics.currency)})")
            appendLine("Biggest Category: ${metrics.biggestCategoryLabel} (${metrics.biggestCategoryPercent}% of spend)")
            appendLine("Daily Burn Rate: ${FinanceEngine.formatMinorUnits(metrics.avgDailyMinor, metrics.currency)}/day")
            appendLine("Debt Simplification: Saved ${metrics.transfersSaved} settlement transfers")
            budgetStatus.budgetMinorUnits?.let { cap ->
                appendLine("Budget Cap: ${FinanceEngine.formatMinorUnits(cap, metrics.currency)} (${(budgetStatus.progressRatio * 100f).toInt()}% used, ${budgetStatus.health.name})")
            }
            if (!group.splitMemoryNotes.isNullOrBlank()) {
                appendLine("Group Rules: ${group.splitMemoryNotes}")
            }
        }

        viewModelScope.launch {
            _tripStoryState.value = AsyncUiState.Loading
            try {
                val story = localAiService.generateTripStory(digest)
                _tripStoryState.value = AsyncUiState.Success(story)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Trip Story failed", e)
                _tripStoryState.value = AsyncUiState.Error(
                    e.localizedMessage ?: "Could not generate AI Trip Story. Tap Retry."
                )
            }
        }
    }

    fun resetTripStory() {
        _tripStoryState.value = AsyncUiState.Idle
    }

    // --- AI MONTHLY SPEND COACH ---

    private fun buildLedgerDigest(snapshot: LedgerSnapshot, groupIdFilter: String?): String {
        val scopedGroup = groupIdFilter?.let { gid -> snapshot.groups.firstOrNull { it.id == gid } }
        val baseCurrency = scopedGroup?.currencyEnum ?: snapshot.defaultCurrency
        val scopedExpenses = if (groupIdFilter != null) {
            snapshot.expenses.filter { it.groupId == groupIdFilter }
        } else {
            snapshot.expenses
        }
        val memberMap = snapshot.members.associateBy { it.id }
        val totalMinor = scopedExpenses.sumOf { exp ->
            if (exp.currencyEnum == baseCurrency) exp.totalMinorUnits
            else FinanceEngine.convertMinorUnits(exp.totalMinorUnits, exp.currencyEnum, baseCurrency)
        }
        val categoryBreakdown = FinanceEngine.computeCategoryBreakdown(scopedExpenses)
        val sb = StringBuilder()
        sb.appendLine("Scope: ${scopedGroup?.name ?: "All Groups & Personal Ledger"} (Base Currency: ${baseCurrency.name} ${baseCurrency.symbol})")
        sb.appendLine("Total Tracked Spend: ${FinanceEngine.formatMinorUnits(totalMinor, baseCurrency)} across ${scopedExpenses.size} expenses")
        sb.appendLine("Self Net Position: ${FinanceEngine.formatMinorUnits(snapshot.selfNetMinorUnits, snapshot.defaultCurrency, showPlusSign = true)}")
        if (scopedGroup != null) {
            val status = FinanceEngine.computeGroupBudgetStatus(scopedGroup, scopedExpenses)
            val cap = status.budgetMinorUnits
            if (cap != null) {
                val pct = (status.progressRatio * 100f).toInt()
                sb.appendLine("Group Budget: ${FinanceEngine.formatMinorUnits(status.spentMinorUnits, baseCurrency)} / ${FinanceEngine.formatMinorUnits(cap, baseCurrency)} ($pct%, status=${status.health.name})")
            }
        } else {
            snapshot.groups.forEach { g ->
                val grpExps = snapshot.expenses.filter { it.groupId == g.id }
                val st = FinanceEngine.computeGroupBudgetStatus(g, grpExps)
                val cap = st.budgetMinorUnits
                if (cap != null) {
                    val pct = (st.progressRatio * 100f).toInt()
                    sb.appendLine("Budget [${g.name}]: ${FinanceEngine.formatMinorUnits(st.spentMinorUnits, g.currencyEnum)} / ${FinanceEngine.formatMinorUnits(cap, g.currencyEnum)} ($pct%, ${st.health.name})")
                }
            }
        }
        sb.appendLine("Top Categories:")
        categoryBreakdown.take(5).forEach { cat ->
            sb.appendLine("- ${cat.category.label}: ${FinanceEngine.formatMinorUnits(cat.totalMinorUnits, baseCurrency)} (${cat.percentage.toInt()}%)")
        }
        val recurring = scopedExpenses.filter { it.recurringEnum != RecurringFrequency.NONE }
        if (recurring.isNotEmpty()) {
            sb.appendLine("Recurring Bills:")
            recurring.forEach { r ->
                sb.appendLine("- ${r.title}: ${FinanceEngine.formatMinorUnits(r.totalMinorUnits, r.currencyEnum)} (${r.recurringEnum.label}, next due ${r.nextDueDateLabel ?: r.dateLabel})")
            }
        }
        sb.appendLine("Recent Expenses:")
        scopedExpenses.take(10).forEach { e ->
            val payerNames = e.payers.mapNotNull { memberMap[it.memberId]?.name }.joinToString(", ")
            sb.appendLine("- ${e.dateLabel} | ${e.title} | ${e.categoryEnum.label} | ${FinanceEngine.formatMinorUnits(e.totalMinorUnits, e.currencyEnum)} | paid by $payerNames")
        }
        return sb.toString()
    }

    fun generateSpendCoachReport(groupIdFilter: String? = null) {
        val currentSnapshot = (ledgerState.value as? LedgerUiState.Ready)?.snapshot ?: return
        val digest = buildLedgerDigest(currentSnapshot, groupIdFilter)
        viewModelScope.launch {
            _spendCoachState.value = AsyncUiState.Loading
            try {
                val report = localAiService.generateMonthlySpendCoach(digest)
                _spendCoachState.value = AsyncUiState.Success(report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Spend Coach failed", e)
                _spendCoachState.value = AsyncUiState.Error(
                    e.localizedMessage ?: "Could not generate spend coaching insights. Please retry."
                )
            }
        }
    }

    fun askSpendCoachFollowUp(question: String, groupIdFilter: String? = null) {
        val trimmed = question.trim()
        if (trimmed.isBlank()) return
        val currentSnapshot = (ledgerState.value as? LedgerUiState.Ready)?.snapshot ?: return
        val digest = buildLedgerDigest(currentSnapshot, groupIdFilter)
        coachStreamJob?.cancel()
        coachStreamJob = viewModelScope.launch {
            _isCoachStreaming.value = true
            _coachFollowUpAnswer.value = ""
            try {
                withTimeout(60_000L) {
                    val acc = StringBuilder()
                    localAiService.streamCoachFollowUp(digest, trimmed).collect { chunk ->
                        acc.append(chunk)
                        _coachFollowUpAnswer.value = acc.toString()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Coach follow-up failed", e)
                _coachFollowUpAnswer.value = "Error: ${e.localizedMessage ?: "Failed to stream coaching answer."}"
            } finally {
                _isCoachStreaming.value = false
            }
        }
    }

    fun resetSpendCoach() {
        coachStreamJob?.cancel()
        _spendCoachState.value = AsyncUiState.Idle
        _coachFollowUpAnswer.value = ""
        _isCoachStreaming.value = false
    }

    // --- LEDGER MUTATIONS ---

    fun saveExpense(expense: ExpenseRecord, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = if (expense.id.isBlank()) {
                repository.addExpense(currentUserId, expense)
            } else {
                repository.updateExpense(currentUserId, expense.id, expense).map { expense.id }
            }
            _isActionBusy.value = false
            result.onSuccess {
                showToast("Saved \"${expense.title}\"")
                onComplete()
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to save expense", isError = true)
            }
        }
    }

    fun deleteExpenseWithUndo(expense: ExpenseRecord) {
        viewModelScope.launch {
            lastDeletedExpense = expense
            val result = repository.deleteExpense(currentUserId, expense.id)
            result.onSuccess {
                showToast("Deleted \"${expense.title}\"", actionLabel = "UNDO")
            }.onFailure { err ->
                lastDeletedExpense = null
                showToast(err.localizedMessage ?: "Failed to delete expense", isError = true)
            }
        }
    }

    fun undoLastDelete() {
        val toRestore = lastDeletedExpense ?: return
        lastDeletedExpense = null
        viewModelScope.launch {
            val result = repository.addExpense(currentUserId, toRestore, preferredId = toRestore.id.takeIf { it.isNotBlank() })
            result.onSuccess {
                showToast("Restored \"${toRestore.title}\"")
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Could not restore expense", isError = true)
            }
        }
    }

    fun toggleExpenseSettled(expense: ExpenseRecord) {
        viewModelScope.launch {
            val updated = expense.copy(settled = !expense.settled)
            val result = repository.updateExpense(currentUserId, expense.id, updated)
            result.onSuccess {
                val msg = if (updated.settled) "Marked settled" else "Reopened expense"
                showToast(msg)
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to update status", isError = true)
            }
        }
    }

    /**
     * Used inside the interactive Shared Split View (`#/share/:splitId`) when a guest or member
     * taps an itemized receipt line to claim/unclaim their share live.
     */
    fun toggleLineItemClaim(expense: ExpenseRecord, itemId: String, memberId: String) {
        val updatedItems = expense.lineItems.map { item ->
            if (item.itemId != itemId) item
            else {
                val current = item.assignedMemberIds.toMutableList()
                if (memberId in current) current.remove(memberId) else current.add(memberId)
                item.copy(assignedMemberIds = current)
            }
        }
        val breakdown = FinanceEngine.computeItemizedBreakdown(
            lineItems = updatedItems,
            taxMinor = expense.taxMinorUnits,
            serviceChargeMinor = expense.serviceChargeMinorUnits,
            discountMinor = expense.discountMinorUnits,
            expectedGrandTotalMinor = expense.totalMinorUnits
        )
        val newAllocs = if (breakdown.allocations.isNotEmpty()) breakdown.allocations else expense.allocations
        val updatedExpense = expense.copy(
            lineItems = updatedItems,
            allocations = newAllocs
        )
        viewModelScope.launch {
            val result = repository.updateExpense(currentUserId, expense.id, updatedExpense)
            result.onSuccess {
                showToast("Updated item claim & recalculated prorated taxes")
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Could not update claim", isError = true)
            }
        }
    }

    fun recordSettlement(
        fromMemberId: String,
        toMemberId: String,
        amountMinorUnits: Long,
        currency: CurrencyCode,
        paymentMethod: PaymentMethod,
        groupId: String?,
        note: String?,
        paymentProofPath: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val record = SettlementRecord(
                userId = currentUserId,
                groupId = groupId,
                fromMemberId = fromMemberId,
                toMemberId = toMemberId,
                amountMinorUnits = amountMinorUnits,
                currency = currency.name,
                paymentMethod = paymentMethod.name,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                paymentProofPath = paymentProofPath?.trim()?.takeIf { it.isNotEmpty() },
                dateLabel = todayIso()
            )
            val result = repository.addSettlement(currentUserId, record)
            _isActionBusy.value = false
            result.onSuccess {
                val proofSuffix = if (!paymentProofPath.isNullOrBlank()) " with proof" else ""
                showToast("Settlement logged (${FinanceEngine.formatMinorUnits(amountMinorUnits, currency)})$proofSuffix")
                onComplete()
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to record settlement", isError = true)
            }
        }
    }

    fun logNextRecurringCycle(
        expense: ExpenseRecord,
        overrideAmountMinor: Long? = null
    ) {
        val freq = expense.recurringEnum
        if (freq == RecurringFrequency.NONE) return
        val today = todayIso()
        val currentDue = expense.nextDueDateLabel ?: expense.dateLabel
        val nextDue = FinanceEngine.advanceDueDateIso(currentDue, freq)
        val finalAmount = overrideAmountMinor?.takeIf { it > 0L } ?: expense.totalMinorUnits

        // Proportionally rescale payers & allocations when user logs a variable utility amount
        val scaledPayers = if (finalAmount == expense.totalMinorUnits || expense.payers.isEmpty()) {
            expense.payers
        } else {
            val weightMap = expense.payers.associate { it.memberId to it.amountMinorUnits.coerceAtLeast(1L) }
            val totalWeight = weightMap.values.sum().coerceAtLeast(1L)
            val raw = expense.payers.map { p ->
                val exact = (finalAmount * p.amountMinorUnits) / totalWeight
                p.memberId to exact
            }.toMutableList()
            val rem = finalAmount - raw.sumOf { it.second }
            if (raw.isNotEmpty() && rem != 0L) {
                raw[0] = raw[0].first to (raw[0].second + rem).coerceAtLeast(1L)
            }
            raw.map { PayerEntry(it.first, it.second) }
        }

        val scaledAllocs = if (finalAmount == expense.totalMinorUnits || expense.allocations.isEmpty()) {
            expense.allocations
        } else {
            val weights = expense.allocations.associate { it.memberId to it.amountMinorUnits.coerceAtLeast(1L) }
            FinanceEngine.distributeByShares(finalAmount, weights)
        }

        val clonedCycle = expense.copy(
            id = "",
            totalMinorUnits = finalAmount,
            payers = scaledPayers,
            allocations = scaledAllocs,
            dateLabel = today,
            shareCode = "rec-${UUID.randomUUID().toString().take(8)}",
            settled = false,
            recurringFrequency = RecurringFrequency.NONE.name,
            nextDueDateLabel = null
        )
        val updatedTemplate = expense.copy(
            nextDueDateLabel = nextDue
        )
        viewModelScope.launch {
            _isActionBusy.value = true
            val addRes = repository.addExpense(currentUserId, clonedCycle)
            val updRes = repository.updateExpense(currentUserId, expense.id, updatedTemplate)
            _isActionBusy.value = false
            if (addRes.isSuccess && updRes.isSuccess) {
                showToast("Logged \"${expense.title}\" (${FinanceEngine.formatMinorUnits(finalAmount, expense.currencyEnum)}) • next due $nextDue")
            } else {
                val err = addRes.exceptionOrNull() ?: updRes.exceptionOrNull()
                showToast(err?.localizedMessage ?: "Could not log recurring cycle", isError = true)
            }
        }
    }

    fun createGroup(
        name: String,
        category: GroupCategory,
        currency: CurrencyCode,
        memberIds: List<String>,
        accentColorHex: String,
        budgetMinorUnits: Long? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val group = ExpenseGroup(
                userId = currentUserId,
                name = name.trim(),
                category = category.name,
                currency = currency.name,
                memberIds = memberIds.distinct(),
                accentColorHex = accentColorHex,
                archived = false,
                budgetMinorUnits = budgetMinorUnits?.takeIf { it > 0L }
            )
            val result = repository.addGroup(currentUserId, group)
            _isActionBusy.value = false
            result.onSuccess {
                showToast("Created group \"${group.name}\"")
                onComplete()
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to create group", isError = true)
            }
        }
    }

    fun updateGroupBudget(
        groupId: String,
        budgetMinorUnits: Long?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = repository.updateGroupBudget(currentUserId, groupId, budgetMinorUnits)
            _isActionBusy.value = false
            result.onSuccess {
                val msg = if (budgetMinorUnits != null && budgetMinorUnits > 0L) {
                    "Updated group budget cap"
                } else {
                    "Removed group budget cap"
                }
                showToast(msg)
                onComplete()
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to update budget cap", isError = true)
            }
        }
    }

    fun updateGroupMemory(
        groupId: String,
        memoryNotes: String?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = repository.updateGroupMemory(currentUserId, groupId, memoryNotes)
            _isActionBusy.value = false
            result.onSuccess {
                val msg = if (!memoryNotes.isNullOrBlank()) {
                    "Saved Smart Split Memory for group"
                } else {
                    "Cleared group Split Memory"
                }
                showToast(msg)
                onComplete()
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to save Split Memory", isError = true)
            }
        }
    }

    fun addMember(
        name: String,
        email: String?,
        upiVpa: String?,
        isGuest: Boolean,
        avatarColorHex: String,
        dietPreference: DietPreference = DietPreference.NON_VEG,
        drinksAlcohol: Boolean = true,
        onComplete: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val member = LedgerMember(
                userId = currentUserId,
                name = name.trim(),
                email = if (isGuest) null else email?.trim()?.takeIf { it.isNotEmpty() },
                upiVpa = upiVpa?.trim()?.takeIf { it.isNotEmpty() },
                avatarColorHex = avatarColorHex,
                selfProfile = false,
                guestProfile = isGuest,
                dietPreference = dietPreference.name,
                drinksAlcohol = drinksAlcohol
            )
            val result = repository.addMember(currentUserId, member)
            _isActionBusy.value = false
            result.onSuccess { newId ->
                val label = if (isGuest) "Added shadow Guest \"${member.name}\"" else "Added \"${member.name}\""
                showToast(label)
                onComplete(newId)
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to add member", isError = true)
            }
        }
    }

    fun importDeviceContacts(
        contacts: List<DeviceContact>,
        onComplete: (Int) -> Unit = {}
    ) {
        if (contacts.isEmpty()) {
            onComplete(0)
            return
        }
        val existingMembers = (ledgerState.value as? LedgerUiState.Ready)?.snapshot?.members.orEmpty()
        val existingNames = existingMembers.map { it.name.trim().lowercase() }.toMutableSet()
        val existingEmails = existingMembers
            .mapNotNull { it.email?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .toMutableSet()
        val palette = listOf("#0E6F4E", "#2C4C7E", "#B45333", "#C5962C", "#5B4B8A", "#1F6E78")

        viewModelScope.launch {
            _isActionBusy.value = true
            var importedCount = 0
            var lastError: Throwable? = null
            for (contact in contacts) {
                val trimmedName = contact.displayName.trim()
                if (trimmedName.isEmpty()) continue
                val lowerName = trimmedName.lowercase()
                val lowerEmail = contact.email?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
                if (lowerName in existingNames || (lowerEmail != null && lowerEmail in existingEmails)) {
                    continue
                }
                val colorHex = palette[kotlin.math.abs(trimmedName.hashCode()) % palette.size]
                val member = LedgerMember(
                    userId = currentUserId,
                    name = trimmedName,
                    email = contact.email?.trim()?.takeIf { it.isNotEmpty() },
                    upiVpa = null,
                    avatarColorHex = colorHex,
                    selfProfile = false,
                    guestProfile = false,
                    dietPreference = DietPreference.NON_VEG.name,
                    drinksAlcohol = true
                )
                val res = repository.addMember(currentUserId, member)
                if (res.isSuccess) {
                    importedCount++
                    existingNames.add(lowerName)
                    if (lowerEmail != null) existingEmails.add(lowerEmail)
                } else {
                    lastError = res.exceptionOrNull()
                }
            }
            _isActionBusy.value = false
            when {
                importedCount > 0 -> {
                    val label = if (importedCount == 1) {
                        "Imported ${contacts.first().displayName.trim()} from Contacts"
                    } else {
                        "Imported $importedCount friends from Contacts"
                    }
                    showToast(label)
                    onComplete(importedCount)
                }
                lastError != null -> {
                    showToast(lastError.localizedMessage ?: "Failed to import contacts", isError = true)
                }
                else -> {
                    showToast("Selected contacts are already in your People list")
                    onComplete(0)
                }
            }
        }
    }

    fun updateMemberPreferences(
        memberId: String,
        dietPreference: DietPreference,
        drinksAlcohol: Boolean
    ) {
        viewModelScope.launch {
            val result = repository.updateMemberPreferences(
                userId = currentUserId,
                memberId = memberId,
                dietPreference = dietPreference,
                drinksAlcohol = drinksAlcohol
            )
            result.onSuccess {
                val drinkText = if (drinksAlcohol) "Drinks" else "Zero-Proof"
                showToast("Updated preference: ${dietPreference.label} · $drinkText")
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to update dining preference", isError = true)
            }
        }
    }

    fun claimGuestProfile(
        memberId: String,
        verifiedName: String,
        verifiedEmail: String,
        upiVpa: String?,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = repository.claimGuestProfile(
                userId = currentUserId,
                memberId = memberId,
                verifiedName = verifiedName,
                verifiedEmail = verifiedEmail,
                upiVpa = upiVpa,
                claimedDateLabel = todayIso()
            )
            _isActionBusy.value = false
            result.onSuccess {
                showToast("Merged Guest history into verified profile \"$verifiedName\"")
                onComplete()
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Could not claim profile", isError = true)
            }
        }
    }

    fun updateProfile(
        displayName: String,
        upiVpa: String?,
        defaultCurrency: CurrencyCode,
        darkMode: Boolean
    ) {
        viewModelScope.launch {
            val result = repository.updateProfile(
                userId = currentUserId,
                displayName = displayName,
                upiVpa = upiVpa,
                defaultCurrency = defaultCurrency,
                darkMode = darkMode
            )
            result.onSuccess {
                showToast("Saved profile preferences")
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to update profile", isError = true)
            }
        }
    }

    fun toggleDarkMode() {
        val profile = (ledgerState.value as? LedgerUiState.Ready)?.snapshot?.profile ?: return
        updateProfile(
            displayName = profile.displayName,
            upiVpa = profile.upiVpa,
            defaultCurrency = profile.currencyEnum,
            darkMode = !profile.darkMode
        )
    }

    fun clearAllLedgerData() {
        viewModelScope.launch {
            _isActionBusy.value = true
            val result = repository.clearAllLedgerData(currentUserId)
            _isActionBusy.value = false
            result.onSuccess {
                showToast("All groups, splits, and contacts cleared")
            }.onFailure { err ->
                showToast(err.localizedMessage ?: "Failed to clear ledger data", isError = true)
            }
        }
    }
}
