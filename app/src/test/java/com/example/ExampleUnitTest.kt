package com.example

import com.example.ai.LocalVoiceParser
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.LedgerMember
import com.example.model.VoiceActionType
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  private val selfMember = LedgerMember(id = "user_me", name = "You", selfProfile = true)
  private val memberTanmay = LedgerMember(id = "user_tanmay", name = "Tanmay", selfProfile = false)
  private val memberParth = LedgerMember(id = "user_parth", name = "Parth", selfProfile = false)
  private val allMembers = listOf(selfMember, memberTanmay, memberParth)

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testDirectIouParsing() {
    val result = LocalVoiceParser.parse(
      rawInput = "Tanmay owes me 500 rupees",
      members = allMembers,
      groups = emptyList(),
      defaultCurrency = CurrencyCode.INR
    )
    assertEquals(VoiceActionType.DIRECT_IOU, result.actionType)
    assertEquals(50000L, result.totalMinorUnits) // 500 INR in paise
    assertEquals("user_me", result.paidByMemberId)
    assertEquals("user_tanmay", result.participants.first().memberId)
  }

  @Test
  fun testReverseIouParsing() {
    val result = LocalVoiceParser.parse(
      rawInput = "I owe Tanmay 350 for coffee",
      members = allMembers,
      groups = emptyList(),
      defaultCurrency = CurrencyCode.INR
    )
    assertEquals(VoiceActionType.DIRECT_IOU, result.actionType)
    assertEquals(35000L, result.totalMinorUnits)
    assertEquals("user_tanmay", result.paidByMemberId)
    assertEquals(ExpenseCategory.FOOD, result.category)
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
}
