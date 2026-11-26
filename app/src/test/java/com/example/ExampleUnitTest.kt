package com.example

import com.example.ai.AiProviderKeyStore
import com.example.ai.AiProviderType
import com.example.ai.ApiKeyEntry
import com.example.ai.ApiKeyHealth
import com.example.ai.EngineTier
import com.example.ai.LocalVoiceParser
import com.example.ai.MultiProviderAiRouter
import com.example.ai.RoutingStrategy
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.LedgerMember
import com.example.model.SplitType
import com.example.model.VoiceActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    private val selfMember = LedgerMember(id = "user_me", name = "You", selfProfile = true)
    private val memberTanmay = LedgerMember(id = "user_tanmay", name = "Tanmay", selfProfile = false)
    private val memberParth = LedgerMember(id = "user_parth", name = "Parth", selfProfile = false)
    private val allMembers = listOf(selfMember, memberTanmay, memberParth)

    @Test
    fun testDirectIouParsing_neverSplitsTwoWays() {
        val result = LocalVoiceParser.parse(
            rawInput = "Tanmay owes me 500 rupees",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.DIRECT_IOU, result.actionType)
        assertEquals(SplitType.EXACT, result.splitType)
        assertEquals(50000L, result.totalMinorUnits)
        assertEquals("user_me", result.paidByMemberId)
        assertEquals(1, result.participants.size)
        assertEquals("user_tanmay", result.participants.first().memberId)
        assertEquals(50000L, result.participants.first().amountMinorUnits)
    }

    @Test
    fun testReverseIouParsing_iOweFriendFull() {
        val result = LocalVoiceParser.parse(
            rawInput = "I owe Tanmay 350 for coffee",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.DIRECT_IOU, result.actionType)
        assertEquals(SplitType.EXACT, result.splitType)
        assertEquals(35000L, result.totalMinorUnits)
        assertEquals("user_tanmay", result.paidByMemberId)
        assertEquals(1, result.participants.size)
        assertEquals("user_me", result.participants.first().memberId)
        assertEquals(35000L, result.participants.first().amountMinorUnits)
        assertEquals(ExpenseCategory.FOOD, result.category)
    }

    @Test
    fun testHinglishDirectIouParsing() {
        val friendOwes = LocalVoiceParser.parse(
            rawInput = "Tanmay se 500 lena hai",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.DIRECT_IOU, friendOwes.actionType)
        assertEquals("user_me", friendOwes.paidByMemberId)
        assertEquals(1, friendOwes.participants.size)
        assertEquals("user_tanmay", friendOwes.participants.first().memberId)
        assertEquals(50000L, friendOwes.participants.first().amountMinorUnits)

        val iOweFriend = LocalVoiceParser.parse(
            rawInput = "Tanmay ko 500 dena hai",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.DIRECT_IOU, iOweFriend.actionType)
        assertEquals("user_tanmay", iOweFriend.paidByMemberId)
        assertEquals(1, iOweFriend.participants.size)
        assertEquals("user_me", iOweFriend.participants.first().memberId)
        assertEquals(50000L, iOweFriend.participants.first().amountMinorUnits)
    }

    @Test
    fun testWakePhraseStripping() {
        val result = LocalVoiceParser.parse(
            rawInput = "Hey SplitFlow Tanmay owes me 500 rupees",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.DIRECT_IOU, result.actionType)
        assertEquals(50000L, result.totalMinorUnits)
        assertEquals(1, result.participants.size)
        assertEquals("user_tanmay", result.participants.first().memberId)
    }

    @Test
    fun testPercentageVoiceSplitParsing() {
        val result = LocalVoiceParser.parse(
            rawInput = "Split 2000 with Tanmay 70 30 for hotel",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.SHARED_EXPENSE, result.actionType)
        assertEquals(SplitType.PERCENTAGE, result.splitType)
        assertEquals(200000L, result.totalMinorUnits)
        assertEquals(2, result.participants.size)
        val shares = result.participants.map { it.amountMinorUnits }.sorted()
        assertEquals(listOf(60000L, 140000L), shares)
    }

    @Test
    fun testSettlementParsing() {
        val result = LocalVoiceParser.parse(
            rawInput = "Tanmay paid me back 500 rupees",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.SETTLEMENT, result.actionType)
        assertEquals(50000L, result.totalMinorUnits)
        assertEquals("user_tanmay", result.settlementFromMemberId)
        assertEquals("user_me", result.settlementToMemberId)
    }

    @Test
    fun testIndianCategoryKeywords() {
        val result = LocalVoiceParser.parse(
            rawInput = "Paid 450 for swiggy with Tanmay",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(ExpenseCategory.FOOD, result.category)
    }

    @Test
    fun testShorthandIouParsing() {
        val result = LocalVoiceParser.parse(
            rawInput = "Tanmay 500",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.DIRECT_IOU, result.actionType)
        assertEquals(50000L, result.totalMinorUnits)
        assertEquals("user_tanmay", result.participants.first().memberId)
    }

    @Test
    fun testLowercaseTranscriptParsing() {
        val result = LocalVoiceParser.parse(
            rawInput = "split 1200 with tanmay and parth for dinner",
            members = allMembers,
            groups = emptyList(),
            defaultCurrency = CurrencyCode.INR
        )
        assertEquals(VoiceActionType.SHARED_EXPENSE, result.actionType)
        assertEquals(120000L, result.totalMinorUnits)
        assertEquals(3, result.participants.size)
        assertEquals(ExpenseCategory.FOOD, result.category)
    }

    @Test
    fun testMultiProviderRouter_roundRobinAndCooldownFailover() = runBlocking {
        val store = AiProviderKeyStore(context = null)
        store.setRoutingStrategy(RoutingStrategy.BYOK_FIRST_THEN_FIREBASE)
        store.addOrUpdateKey(
            ApiKeyEntry(
                id = "k_groq",
                provider = AiProviderType.GROQ,
                label = "Groq #1",
                apiKey = "gsk_test_key_1234567890"
            )
        )
        store.addOrUpdateKey(
            ApiKeyEntry(
                id = "k_or",
                provider = AiProviderType.OPENROUTER,
                label = "OpenRouter #1",
                apiKey = "sk-or-v1-test_key_9876543210"
            )
        )

        val calledIds = mutableListOf<String>()
        var failGroqWith429 = false
        val router = MultiProviderAiRouter(
            keyStore = store,
            openAiTransport = { entry, _, _, _ ->
                calledIds += entry.id
                if (failGroqWith429 && entry.id == "k_groq") {
                    MultiProviderAiRouter.HttpResponsePayload(
                        statusCode = 429,
                        body = "",
                        errorMessage = "Rate limit exceeded"
                    )
                } else {
                    MultiProviderAiRouter.HttpResponsePayload(
                        statusCode = 200,
                        body = """{"choices":[{"message":{"content":"{\"ok\":true,\"by\":\"${entry.id}\"}"}}]}"""
                    )
                }
            },
            firebaseTextTransport = { _, _, _ -> """{"ok":true,"by":"firebase"}""" }
        )

        // First two calls rotate cleanly across Groq -> OpenRouter
        val first = router.generateStructuredOrText("sys", "req1", expectJson = true)
        val second = router.generateStructuredOrText("sys", "req2", expectJson = true)
        assertEquals(EngineTier.BYOK_ROUND_ROBIN, first.tier)
        assertEquals(EngineTier.BYOK_ROUND_ROBIN, second.tier)
        assertEquals(listOf("k_groq", "k_or"), calledIds)

        // Now rate-limit Groq (429): router must mark Groq COOLDOWN and seamlessly fail over to OpenRouter
        failGroqWith429 = true
        calledIds.clear()
        val third = router.generateStructuredOrText("sys", "req3", expectJson = true)
        assertEquals(EngineTier.BYOK_ROUND_ROBIN, third.tier)
        assertTrue(third.text.contains("k_or"))
        assertEquals(listOf("k_groq", "k_or"), calledIds)
        assertEquals(ApiKeyHealth.COOLDOWN, store.keys.value.first { it.id == "k_groq" }.health)

        // Fourth call skips cooled-down Groq completely and hits OpenRouter directly
        calledIds.clear()
        val fourth = router.generateStructuredOrText("sys", "req4", expectJson = true)
        assertTrue(fourth.text.contains("k_or"))
        assertEquals(listOf("k_or"), calledIds)
    }

    @Test
    fun testMultiProviderRouter_gemini38To35LiteFailover() = runBlocking {
        val store = AiProviderKeyStore(context = null)
        store.setRoutingStrategy(RoutingStrategy.FIREBASE_ONLY)
        val attemptedModels = mutableListOf<String>()

        val router = MultiProviderAiRouter(
            keyStore = store,
            firebaseTextTransport = { modelName, _, _ ->
                attemptedModels += modelName
                if (modelName == MultiProviderAiRouter.PRIMARY_GEMINI_MODEL) {
                    throw RuntimeException("429 RESOURCE_EXHAUSTED high demand")
                }
                """{"ok":true,"model":"$modelName"}"""
            }
        )

        val res = router.generateStructuredOrText("sys", "parse dinner", expectJson = true)
        assertEquals(
            listOf(
                MultiProviderAiRouter.PRIMARY_GEMINI_MODEL,
                MultiProviderAiRouter.LITE_GEMINI_MODEL
            ),
            attemptedModels
        )
        assertEquals(EngineTier.FIREBASE_GEMINI_LITE, res.tier)
        assertTrue(res.text.contains(MultiProviderAiRouter.LITE_GEMINI_MODEL))
    }
}
