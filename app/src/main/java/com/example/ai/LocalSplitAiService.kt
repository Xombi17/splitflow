package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import com.example.model.CurrencyCode
import com.example.model.ExpenseGroup
import com.example.model.LedgerMember
import com.example.model.ParsedReceipt
import com.example.model.ParsedVoiceSplit
import com.example.model.SpendCoachReport
import com.example.model.TripStoryNarrative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 100% on-device AI service for SplitFlow.
 * Powered by Cactus Compute Whistle (STT) & Mirage (Vision/OCR), with instant local fallbacks
 * (Android SpeechRecognizer + ML Kit Text Recognition + local financial parsers).
 * Makes zero cloud Gemini / AI Studio requests and never hits API rate limits.
 */
class LocalSplitAiService(
    private val appContext: Context
) {
    val engineState: StateFlow<LocalAiEngineState> = CactusLocalModelManager.state

    init {
        CactusLocalModelManager.initContext(appContext)
    }

    suspend fun downloadWhistleModel(): Boolean =
        CactusLocalModelManager.prepareWhistle(appContext)

    suspend fun downloadMirageModel(): Boolean =
        CactusLocalModelManager.prepareMirage(appContext)

    suspend fun analyzeReceipt(bitmap: Bitmap): ParsedReceipt {
        return LocalReceiptOcrEngine.analyzeReceiptBitmap(appContext, bitmap)
    }

    suspend fun transcribeAndParseVoiceAudio(
        wavAudioBytes: ByteArray,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup> = emptyList(),
        defaultCurrency: CurrencyCode,
        groupMemoryNotes: String? = null
    ): ParsedVoiceSplit = withContext(Dispatchers.IO) {
        val wavFile = File(appContext.cacheDir, "whistle_voice_${System.currentTimeMillis()}.wav")
        try {
            wavFile.writeBytes(wavAudioBytes)
            val transcript = CactusLocalModelManager.transcribeWavWithWhistle(appContext, wavFile)
                ?: throw IllegalStateException(
                    "Cactus Whistle could not transcribe this recording. Tap \"Install Whistle\" or use live voice/text input below."
                )
            val slug = CactusLocalModelManager.state.value.whistleActiveSlug ?: "Whistle"
            LocalVoiceParser.parse(
                rawInput = transcript,
                members = members,
                groups = groups,
                defaultCurrency = defaultCurrency,
                engineLabel = "Cactus $slug"
            )
        } finally {
            wavFile.delete()
        }
    }

    suspend fun parseVoiceCommand(
        transcript: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup> = emptyList(),
        defaultCurrency: CurrencyCode,
        groupMemoryNotes: String? = null
    ): ParsedVoiceSplit = withContext(Dispatchers.Default) {
        val label = CactusLocalModelManager.state.value.whistleActiveSlug?.let { "Cactus $it" } ?: "On-Device Engine"
        LocalVoiceParser.parse(
            rawInput = transcript,
            members = members,
            groups = groups,
            defaultCurrency = defaultCurrency,
            engineLabel = label
        )
    }

    suspend fun generateMonthlySpendCoach(ledgerDigest: String): SpendCoachReport =
        withContext(Dispatchers.Default) {
            LocalFinanceInsightsEngine.generateSpendCoach(ledgerDigest)
        }

    fun streamCoachFollowUp(ledgerDigest: String, question: String): Flow<String> =
        LocalFinanceInsightsEngine.streamCoachFollowUp(ledgerDigest, question)

    suspend fun generateTripStory(metricsDigest: String): TripStoryNarrative =
        withContext(Dispatchers.Default) {
            LocalFinanceInsightsEngine.generateTripStory(metricsDigest)
        }
}
