package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.CurrencyCode
import com.example.model.DietPreference
import com.example.model.ExpenseGroup
import com.example.model.ExpenseRecord
import com.example.model.LedgerMember
import com.example.model.PayerEntry
import com.example.model.ReceiptLineItem
import com.example.model.SettlementRecord
import com.example.model.SplitAllocation
import com.example.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Common data-access contract for both Cloud Firestore (Google Sign-In)
 * and persistent local Guest Mode (zero unauthenticated Firestore calls).
 */
interface LedgerRepository {
    fun observeProfile(userId: String): Flow<UserProfile?>
    fun observeMembers(userId: String): Flow<List<LedgerMember>>
    fun observeGroups(userId: String): Flow<List<ExpenseGroup>>
    fun observeExpenses(userId: String): Flow<List<ExpenseRecord>>
    fun observeSettlements(userId: String): Flow<List<SettlementRecord>>

    suspend fun ensureInitialized(userId: String): Result<Unit>
    suspend fun clearAllLedgerData(userId: String): Result<Unit>

    suspend fun updateProfile(
        userId: String,
        displayName: String,
        upiVpa: String?,
        defaultCurrency: CurrencyCode,
        darkMode: Boolean
    ): Result<Unit>

    suspend fun addMember(userId: String, member: LedgerMember): Result<String>

    suspend fun updateMemberPreferences(
        userId: String,
        memberId: String,
        dietPreference: DietPreference,
        drinksAlcohol: Boolean
    ): Result<Unit>

    suspend fun claimGuestProfile(
        userId: String,
        memberId: String,
        verifiedName: String,
        verifiedEmail: String,
        upiVpa: String?,
        claimedDateLabel: String
    ): Result<Unit>

    suspend fun addGroup(userId: String, group: ExpenseGroup): Result<String>
    suspend fun updateGroupMembers(userId: String, groupId: String, memberIds: List<String>): Result<Unit>
    suspend fun updateGroupBudget(userId: String, groupId: String, budgetMinorUnits: Long?): Result<Unit>
    suspend fun updateGroupMemory(userId: String, groupId: String, memoryNotes: String?): Result<Unit>
    suspend fun addExpense(userId: String, expense: ExpenseRecord, preferredId: String? = null): Result<String>
    suspend fun updateExpense(userId: String, expenseId: String, expense: ExpenseRecord): Result<Unit>
    suspend fun deleteExpense(userId: String, expenseId: String): Result<Unit>
    suspend fun addSettlement(userId: String, settlement: SettlementRecord, preferredId: String? = null): Result<String>
    suspend fun updateSettlement(userId: String, settlementId: String, settlement: SettlementRecord): Result<Unit>
    suspend fun deleteSettlement(userId: String, settlementId: String): Result<Unit>
}

/**
 * Snapshot of user-authored local Guest Mode data used to migrate seamlessly into
 * Cloud Firestore when a guest upgrades to a Google Sign-In account.
 */
data class GuestMigrationPayload(
    val upiVpa: String?,
    val members: List<LedgerMember>,
    val groups: List<ExpenseGroup>,
    val expenses: List<ExpenseRecord>,
    val settlements: List<SettlementRecord>
) {
    val totalRecords: Int get() = members.size + groups.size + expenses.size + settlements.size
    val hasUserData: Boolean get() = totalRecords > 0
}

/**
 * Manages the on-device guest session lifecycle and local JSON persistence
 * so guest splits, groups, friends, and settlements persist across app restarts without
 * calling unauthenticated Firestore writes.
 */
class GuestSessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGuestSessionActive(): Boolean = prefs.getBoolean(KEY_ACTIVE, false)

    fun getOrCreateGuestSessionId(): String {
        val existing = prefs.getString(KEY_GUEST_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = "guest_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        prefs.edit().putString(KEY_GUEST_ID, created).apply()
        return created
    }

    fun startGuestSession(): String {
        val id = getOrCreateGuestSessionId()
        prefs.edit().putBoolean(KEY_ACTIVE, true).apply()
        return id
    }

    fun endGuestSession() {
        prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
    }

    fun readLedgerJson(): String? = prefs.getString(KEY_LEDGER_JSON, null)

    fun writeLedgerJson(json: String) {
        prefs.edit().putString(KEY_LEDGER_JSON, json).apply()
    }

    fun clearPersistedLedger() {
        prefs.edit().remove(KEY_LEDGER_JSON).apply()
    }

    companion object {
        private const val PREFS_NAME = "splitflow_local_guest_session"
        private const val KEY_ACTIVE = "guest_session_active"
        private const val KEY_GUEST_ID = "guest_session_id"
        private const val KEY_LEDGER_JSON = "guest_ledger_json"
    }
}

/**
 * Synchronous on-device theme preference store so Warm Obsidian Dark / Warm Alabaster Light
 * persists across restarts with zero startup flash.
 */
class ThemePreferenceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE_V5, true)

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE_V5, dark).apply()
    }

    companion object {
        private const val PREFS_NAME = "splitflow_theme_prefs"
        private const val KEY_DARK_MODE_V5 = "splitflow_warm_obsidian_v5_dark"
    }
}

/**
 * Persists first-time onboarding slides and interactive Home coachmarks progress locally
 * so new users get guided walkthroughs and can replay either tour anytime from Profile.
 */
class OnboardingPreferenceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingCompleted(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun hasCompletedCoachmarks(): Boolean = prefs.getBoolean(KEY_COACHMARKS_DONE, false)

    fun setCoachmarksCompleted(done: Boolean) {
        prefs.edit().putBoolean(KEY_COACHMARKS_DONE, done).apply()
    }

    companion object {
        private const val PREFS_NAME = "splitflow_onboarding_prefs"
        private const val KEY_ONBOARDING_DONE = "onboarding_walkthrough_done_v1"
        private const val KEY_COACHMARKS_DONE = "home_coachmarks_done_v1"
    }
}

/**
 * Persistent device-local repository for Guest Mode.
 * Starts completely empty (only the user's own self profile) with zero hardcoded demo data.
 */
class GuestLedgerRepository(
    private val sessionStore: GuestSessionStore
) : LedgerRepository {

    constructor(context: Context) : this(GuestSessionStore(context))

    private val _profile = MutableStateFlow<UserProfile?>(null)
    private val _members = MutableStateFlow<List<LedgerMember>>(emptyList())
    private val _groups = MutableStateFlow<List<ExpenseGroup>>(emptyList())
    private val _expenses = MutableStateFlow<List<ExpenseRecord>>(emptyList())
    private val _settlements = MutableStateFlow<List<SettlementRecord>>(emptyList())

    override fun observeProfile(userId: String): Flow<UserProfile?> = _profile.asStateFlow()

    override fun observeMembers(userId: String): Flow<List<LedgerMember>> =
        _members.map { list ->
            list.sortedWith(compareByDescending<LedgerMember> { it.selfProfile }.thenBy { it.name.lowercase() })
        }

    override fun observeGroups(userId: String): Flow<List<ExpenseGroup>> = _groups.asStateFlow()

    override fun observeExpenses(userId: String): Flow<List<ExpenseRecord>> =
        _expenses.map { list -> list.sortedByDescending { it.dateLabel } }

    override fun observeSettlements(userId: String): Flow<List<SettlementRecord>> =
        _settlements.map { list -> list.sortedByDescending { it.dateLabel } }

    override suspend fun ensureInitialized(userId: String): Result<Unit> {
        if (_profile.value != null) return Result.success(Unit)
        val restored = runCatching { restoreFromDisk(userId) }.getOrDefault(false)
        if (!restored) {
            initCleanLocal(userId)
        }
        return Result.success(Unit)
    }

    override suspend fun clearAllLedgerData(userId: String): Result<Unit> {
        val currentProfile = _profile.value
        val displayName = currentProfile?.displayName?.takeIf { it.isNotBlank() } ?: "You"
        val currentSelf = _members.value.firstOrNull { it.selfProfile } ?: createSelfMember(
            userId = userId,
            displayName = displayName,
            upiVpa = currentProfile?.upiVpa
        )
        _profile.value = (currentProfile ?: UserProfile(
            id = userId,
            userId = userId,
            displayName = displayName,
            defaultCurrency = CurrencyCode.INR.name,
            darkMode = true
        )).copy(seededDemo = false)
        _members.value = listOf(currentSelf.copy(id = SELF_MEMBER_ID, userId = userId, selfProfile = true))
        _groups.value = emptyList()
        _expenses.value = emptyList()
        _settlements.value = emptyList()
        persistToDisk()
        return Result.success(Unit)
    }

    private fun initCleanLocal(userId: String) {
        val displayName = "You"
        _profile.value = UserProfile(
            id = userId,
            userId = userId,
            displayName = displayName,
            email = null,
            upiVpa = null,
            defaultCurrency = CurrencyCode.INR.name,
            darkMode = true,
            seededDemo = false
        )
        _members.value = listOf(createSelfMember(userId = userId, displayName = displayName, upiVpa = null))
        _groups.value = emptyList()
        _expenses.value = emptyList()
        _settlements.value = emptyList()
        persistToDisk()
    }

    private fun createSelfMember(
        userId: String,
        displayName: String,
        upiVpa: String?
    ): LedgerMember = LedgerMember(
        id = SELF_MEMBER_ID,
        userId = userId,
        name = displayName,
        email = null,
        upiVpa = upiVpa,
        avatarColorHex = "#0E6F4E",
        selfProfile = true,
        guestProfile = false,
        dietPreference = DietPreference.NON_VEG.name,
        drinksAlcohol = true
    )

    override suspend fun updateProfile(
        userId: String,
        displayName: String,
        upiVpa: String?,
        defaultCurrency: CurrencyCode,
        darkMode: Boolean
    ): Result<Unit> {
        val cleanName = displayName.trim().ifBlank { "You" }.take(80)
        val cleanUpi = upiVpa?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
        _profile.update { current ->
            (current ?: UserProfile(id = userId, userId = userId)).copy(
                displayName = cleanName,
                upiVpa = cleanUpi,
                defaultCurrency = defaultCurrency.name,
                darkMode = darkMode,
                seededDemo = false
            )
        }
        _members.update { list ->
            val hasSelf = list.any { it.selfProfile }
            if (hasSelf) {
                list.map { m ->
                    if (m.selfProfile) {
                        m.copy(name = cleanName, upiVpa = cleanUpi)
                    } else m
                }
            } else {
                listOf(createSelfMember(userId, cleanName, cleanUpi)) + list
            }
        }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun addMember(userId: String, member: LedgerMember): Result<String> {
        val id = "mem_${UUID.randomUUID().toString().take(8)}"
        val saved = member.copy(id = id, userId = userId)
        _members.update { it + saved }
        persistToDisk()
        return Result.success(id)
    }

    override suspend fun updateMemberPreferences(
        userId: String,
        memberId: String,
        dietPreference: DietPreference,
        drinksAlcohol: Boolean
    ): Result<Unit> {
        _members.update { list ->
            list.map { m ->
                if (m.id == memberId) {
                    m.copy(dietPreference = dietPreference.name, drinksAlcohol = drinksAlcohol)
                } else m
            }
        }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun claimGuestProfile(
        userId: String,
        memberId: String,
        verifiedName: String,
        verifiedEmail: String,
        upiVpa: String?,
        claimedDateLabel: String
    ): Result<Unit> {
        _members.update { list ->
            list.map { m ->
                if (m.id == memberId) {
                    m.copy(
                        name = verifiedName.trim().ifBlank { "Verified Friend" }.take(80),
                        email = verifiedEmail.trim().take(120),
                        upiVpa = upiVpa?.trim()?.takeIf { it.isNotEmpty() }?.take(80) ?: m.upiVpa,
                        guestProfile = false,
                        claimedAtLabel = claimedDateLabel.take(60)
                    )
                } else m
            }
        }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun addGroup(userId: String, group: ExpenseGroup): Result<String> {
        val id = "grp_${UUID.randomUUID().toString().take(8)}"
        val saved = group.copy(id = id, userId = userId)
        _groups.update { listOf(saved) + it }
        persistToDisk()
        return Result.success(id)
    }

    override suspend fun updateGroupMembers(
        userId: String,
        groupId: String,
        memberIds: List<String>
    ): Result<Unit> {
        _groups.update { list ->
            list.map { g -> if (g.id == groupId) g.copy(memberIds = memberIds.distinct().take(50)) else g }
        }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun updateGroupBudget(
        userId: String,
        groupId: String,
        budgetMinorUnits: Long?
    ): Result<Unit> {
        _groups.update { list ->
            list.map { g -> if (g.id == groupId) g.copy(budgetMinorUnits = budgetMinorUnits?.coerceAtLeast(0L)) else g }
        }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun updateGroupMemory(
        userId: String,
        groupId: String,
        memoryNotes: String?
    ): Result<Unit> {
        val clean = memoryNotes?.trim()?.takeIf { it.isNotEmpty() }?.take(400)
        _groups.update { list ->
            list.map { g -> if (g.id == groupId) g.copy(splitMemoryNotes = clean) else g }
        }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun addExpense(
        userId: String,
        expense: ExpenseRecord,
        preferredId: String?
    ): Result<String> {
        val id = preferredId?.takeIf { it.isNotBlank() } ?: "exp_${UUID.randomUUID().toString().take(8)}"
        val saved = expense.copy(id = id, userId = userId)
        _expenses.update { list -> listOf(saved) + list.filterNot { it.id == id } }
        persistToDisk()
        return Result.success(id)
    }

    override suspend fun updateExpense(
        userId: String,
        expenseId: String,
        expense: ExpenseRecord
    ): Result<Unit> {
        _expenses.update { list ->
            list.map { e -> if (e.id == expenseId) expense.copy(id = expenseId, userId = userId) else e }
        }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun deleteExpense(userId: String, expenseId: String): Result<Unit> {
        _expenses.update { list -> list.filterNot { it.id == expenseId } }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun addSettlement(
        userId: String,
        settlement: SettlementRecord,
        preferredId: String?
    ): Result<String> {
        val id = preferredId?.takeIf { it.isNotBlank() } ?: "set_${UUID.randomUUID().toString().take(8)}"
        val saved = settlement.copy(id = id, userId = userId)
        _settlements.update { listOf(saved) + it.filterNot { existing -> existing.id == id } }
        persistToDisk()
        return Result.success(id)
    }

    override suspend fun updateSettlement(
        userId: String,
        settlementId: String,
        settlement: SettlementRecord
    ): Result<Unit> {
        _settlements.update { list ->
            list.map { s -> if (s.id == settlementId) settlement.copy(id = settlementId, userId = userId) else s }
        }
        persistToDisk()
        return Result.success(Unit)
    }

    override suspend fun deleteSettlement(userId: String, settlementId: String): Result<Unit> {
        _settlements.update { list -> list.filterNot { it.id == settlementId } }
        persistToDisk()
        return Result.success(Unit)
    }

    // --- Local JSON Persistence ---

    private fun persistToDisk() {
        val root = JSONObject()
        root.put("schemaVersion", CLEAN_SCHEMA_VERSION)
        _profile.value?.let { p ->
            root.put(
                "profile",
                JSONObject().apply {
                    put("id", p.id)
                    put("userId", p.userId)
                    put("displayName", p.displayName)
                    put("email", p.email ?: JSONObject.NULL)
                    put("upiVpa", p.upiVpa ?: JSONObject.NULL)
                    put("defaultCurrency", p.defaultCurrency)
                    put("darkMode", p.darkMode)
                    put("seededDemo", false)
                }
            )
        }
        root.put(
            "members",
            JSONArray().apply {
                _members.value.forEach { m ->
                    put(
                        JSONObject().apply {
                            put("id", m.id)
                            put("userId", m.userId)
                            put("name", m.name)
                            put("email", m.email ?: JSONObject.NULL)
                            put("upiVpa", m.upiVpa ?: JSONObject.NULL)
                            put("avatarColorHex", m.avatarColorHex)
                            put("selfProfile", m.selfProfile)
                            put("guestProfile", m.guestProfile)
                            put("claimedAtLabel", m.claimedAtLabel ?: JSONObject.NULL)
                            put("dietPreference", m.dietPreference ?: JSONObject.NULL)
                            put("drinksAlcohol", m.drinksAlcohol ?: JSONObject.NULL)
                        }
                    )
                }
            }
        )
        root.put(
            "groups",
            JSONArray().apply {
                _groups.value.forEach { g ->
                    put(
                        JSONObject().apply {
                            put("id", g.id)
                            put("userId", g.userId)
                            put("name", g.name)
                            put("category", g.category)
                            put("currency", g.currency)
                            put("memberIds", JSONArray(g.memberIds))
                            put("accentColorHex", g.accentColorHex)
                            put("archived", g.archived)
                            put("budgetMinorUnits", g.budgetMinorUnits ?: JSONObject.NULL)
                            put("splitMemoryNotes", g.splitMemoryNotes ?: JSONObject.NULL)
                        }
                    )
                }
            }
        )
        root.put(
            "expenses",
            JSONArray().apply {
                _expenses.value.forEach { e ->
                    put(
                        JSONObject().apply {
                            put("id", e.id)
                            put("userId", e.userId)
                            put("groupId", e.groupId ?: JSONObject.NULL)
                            put("title", e.title)
                            put("notes", e.notes ?: JSONObject.NULL)
                            put("category", e.category)
                            put("currency", e.currency)
                            put("totalMinorUnits", e.totalMinorUnits)
                            put("splitType", e.splitType)
                            put("dateLabel", e.dateLabel)
                            put("shareCode", e.shareCode)
                            put("aiExtracted", e.aiExtracted)
                            put("aiConfidenceNote", e.aiConfidenceNote ?: JSONObject.NULL)
                            put("settled", e.settled)
                            put("taxMinorUnits", e.taxMinorUnits)
                            put("serviceChargeMinorUnits", e.serviceChargeMinorUnits)
                            put("discountMinorUnits", e.discountMinorUnits)
                            put("recurringFrequency", e.recurringFrequency ?: JSONObject.NULL)
                            put("nextDueDateLabel", e.nextDueDateLabel ?: JSONObject.NULL)
                            put("receiptPhotoPath", e.receiptPhotoPath ?: JSONObject.NULL)
                            put("fxRateToBaseMicros", e.fxRateToBaseMicros ?: JSONObject.NULL)
                            put(
                                "payers",
                                JSONArray().apply {
                                    e.payers.forEach { p ->
                                        put(JSONObject().put("memberId", p.memberId).put("amountMinorUnits", p.amountMinorUnits))
                                    }
                                }
                            )
                            put(
                                "allocations",
                                JSONArray().apply {
                                    e.allocations.forEach { a ->
                                        put(
                                            JSONObject()
                                                .put("memberId", a.memberId)
                                                .put("amountMinorUnits", a.amountMinorUnits)
                                                .put("percentageBps", a.percentageBps)
                                                .put("shares", a.shares)
                                        )
                                    }
                                }
                            )
                            put(
                                "lineItems",
                                JSONArray().apply {
                                    e.lineItems.forEach { li ->
                                        put(
                                            JSONObject()
                                                .put("itemId", li.itemId)
                                                .put("name", li.name)
                                                .put("quantity", li.quantity)
                                                .put("unitPriceMinor", li.unitPriceMinor)
                                                .put("totalPriceMinor", li.totalPriceMinor)
                                                .put("assignedMemberIds", JSONArray(li.assignedMemberIds))
                                                .put("itemTag", li.itemTag ?: JSONObject.NULL)
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            }
        )
        root.put(
            "settlements",
            JSONArray().apply {
                _settlements.value.forEach { s ->
                    put(
                        JSONObject().apply {
                            put("id", s.id)
                            put("userId", s.userId)
                            put("groupId", s.groupId ?: JSONObject.NULL)
                            put("fromMemberId", s.fromMemberId)
                            put("toMemberId", s.toMemberId)
                            put("amountMinorUnits", s.amountMinorUnits)
                            put("currency", s.currency)
                            put("paymentMethod", s.paymentMethod)
                            put("note", s.note ?: JSONObject.NULL)
                            put("paymentProofPath", s.paymentProofPath ?: JSONObject.NULL)
                            put("dateLabel", s.dateLabel)
                        }
                    )
                }
            }
        )
        sessionStore.writeLedgerJson(root.toString())
    }

    private fun restoreFromDisk(expectedUserId: String): Boolean {
        val raw = sessionStore.readLedgerJson() ?: return false
        val parsed = parsePersistedJson(raw, expectedUserId) ?: return false
        // Auto-purge legacy demo-seeded local caches so production starts completely clean
        if (parsed.isLegacyDemo) {
            sessionStore.clearPersistedLedger()
            return false
        }
        _profile.value = parsed.profile
        _members.value = parsed.members.ifEmpty {
            listOf(createSelfMember(expectedUserId, parsed.profile.displayName, parsed.profile.upiVpa))
        }
        _groups.value = parsed.groups
        _expenses.value = parsed.expenses
        _settlements.value = parsed.settlements
        return true
    }

    private data class ParsedGuestStore(
        val isLegacyDemo: Boolean,
        val profile: UserProfile,
        val members: List<LedgerMember>,
        val groups: List<ExpenseGroup>,
        val expenses: List<ExpenseRecord>,
        val settlements: List<SettlementRecord>
    )

    companion object {
        const val SELF_MEMBER_ID = "mem_self"
        private const val CLEAN_SCHEMA_VERSION = 2

        /**
         * Reads any user-created local guest ledger records for automatic migration
         * into Cloud Firestore when a guest signs in with Google.
         */
        fun readMigrationPayload(sessionStore: GuestSessionStore): GuestMigrationPayload? {
            val raw = sessionStore.readLedgerJson() ?: return null
            val parsed = runCatching { parsePersistedJson(raw, "guest") }.getOrNull() ?: return null
            if (parsed.isLegacyDemo) {
                sessionStore.clearPersistedLedger()
                return null
            }
            val nonSelfMembers = parsed.members.filter { !it.selfProfile }
            val payload = GuestMigrationPayload(
                upiVpa = parsed.profile.upiVpa,
                members = nonSelfMembers,
                groups = parsed.groups,
                expenses = parsed.expenses,
                settlements = parsed.settlements
            )
            return payload.takeIf { it.hasUserData }
        }

        private fun parsePersistedJson(raw: String, expectedUserId: String): ParsedGuestStore? {
            val root = JSONObject(raw)
            val schemaVersion = root.optInt("schemaVersion", 1)
            val pObj = root.optJSONObject("profile") ?: return null
            val wasDemo = pObj.optBoolean("seededDemo", false) || schemaVersion < CLEAN_SCHEMA_VERSION
            val profile = UserProfile(
                id = pObj.optString("id", expectedUserId),
                userId = pObj.optString("userId", expectedUserId),
                displayName = pObj.optString("displayName", "You"),
                email = pObj.optNullableString("email"),
                upiVpa = pObj.optNullableString("upiVpa"),
                defaultCurrency = pObj.optString("defaultCurrency", CurrencyCode.INR.name),
                darkMode = pObj.optBoolean("darkMode", true),
                seededDemo = false
            )
            val members = buildList {
                val arr = root.optJSONArray("members") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        LedgerMember(
                            id = o.optString("id"),
                            userId = o.optString("userId", expectedUserId),
                            name = o.optString("name"),
                            email = o.optNullableString("email"),
                            upiVpa = o.optNullableString("upiVpa"),
                            avatarColorHex = o.optString("avatarColorHex", "#0E6F4E"),
                            selfProfile = o.optBoolean("selfProfile", false),
                            guestProfile = o.optBoolean("guestProfile", false),
                            claimedAtLabel = o.optNullableString("claimedAtLabel"),
                            dietPreference = o.optNullableString("dietPreference"),
                            drinksAlcohol = if (o.isNull("drinksAlcohol")) null else o.optBoolean("drinksAlcohol", true)
                        )
                    )
                }
            }
            val groups = buildList {
                val arr = root.optJSONArray("groups") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ExpenseGroup(
                            id = o.optString("id"),
                            userId = o.optString("userId", expectedUserId),
                            name = o.optString("name"),
                            category = o.optString("category"),
                            currency = o.optString("currency", CurrencyCode.INR.name),
                            memberIds = o.optStringList("memberIds"),
                            accentColorHex = o.optString("accentColorHex", "#14B8A6"),
                            archived = o.optBoolean("archived", false),
                            budgetMinorUnits = if (o.isNull("budgetMinorUnits")) null else o.optLong("budgetMinorUnits").takeIf { it > 0L },
                            splitMemoryNotes = o.optNullableString("splitMemoryNotes")
                        )
                    )
                }
            }
            val expenses = buildList {
                val arr = root.optJSONArray("expenses") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val payers = buildList {
                        val pArr = o.optJSONArray("payers") ?: JSONArray()
                        for (j in 0 until pArr.length()) {
                            val p = pArr.getJSONObject(j)
                            add(PayerEntry(p.optString("memberId"), p.optLong("amountMinorUnits")))
                        }
                    }
                    val allocations = buildList {
                        val aArr = o.optJSONArray("allocations") ?: JSONArray()
                        for (j in 0 until aArr.length()) {
                            val a = aArr.getJSONObject(j)
                            add(
                                SplitAllocation(
                                    memberId = a.optString("memberId"),
                                    amountMinorUnits = a.optLong("amountMinorUnits"),
                                    percentageBps = a.optLong("percentageBps"),
                                    shares = a.optLong("shares", 1L)
                                )
                            )
                        }
                    }
                    val lineItems = buildList {
                        val liArr = o.optJSONArray("lineItems") ?: JSONArray()
                        for (j in 0 until liArr.length()) {
                            val li = liArr.getJSONObject(j)
                            add(
                                ReceiptLineItem(
                                    itemId = li.optString("itemId"),
                                    name = li.optString("name"),
                                    quantity = li.optLong("quantity", 1L),
                                    unitPriceMinor = li.optLong("unitPriceMinor"),
                                    totalPriceMinor = li.optLong("totalPriceMinor"),
                                    assignedMemberIds = li.optStringList("assignedMemberIds"),
                                    itemTag = li.optNullableString("itemTag")
                                )
                            )
                        }
                    }
                    add(
                        ExpenseRecord(
                            id = o.optString("id"),
                            userId = o.optString("userId", expectedUserId),
                            groupId = o.optNullableString("groupId"),
                            title = o.optString("title"),
                            notes = o.optNullableString("notes"),
                            category = o.optString("category"),
                            currency = o.optString("currency", CurrencyCode.INR.name),
                            totalMinorUnits = o.optLong("totalMinorUnits"),
                            splitType = o.optString("splitType"),
                            dateLabel = o.optString("dateLabel"),
                            shareCode = o.optString("shareCode"),
                            aiExtracted = o.optBoolean("aiExtracted", false),
                            aiConfidenceNote = o.optNullableString("aiConfidenceNote"),
                            settled = o.optBoolean("settled", false),
                            payers = payers,
                            allocations = allocations,
                            lineItems = lineItems,
                            taxMinorUnits = o.optLong("taxMinorUnits"),
                            serviceChargeMinorUnits = o.optLong("serviceChargeMinorUnits"),
                            discountMinorUnits = o.optLong("discountMinorUnits"),
                            recurringFrequency = o.optNullableString("recurringFrequency"),
                            nextDueDateLabel = o.optNullableString("nextDueDateLabel"),
                            receiptPhotoPath = o.optNullableString("receiptPhotoPath"),
                            fxRateToBaseMicros = if (o.isNull("fxRateToBaseMicros")) null else o.optLong("fxRateToBaseMicros").takeIf { it >= 1L }
                        )
                    )
                }
            }
            val settlements = buildList {
                val arr = root.optJSONArray("settlements") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SettlementRecord(
                            id = o.optString("id"),
                            userId = o.optString("userId", expectedUserId),
                            groupId = o.optNullableString("groupId"),
                            fromMemberId = o.optString("fromMemberId"),
                            toMemberId = o.optString("toMemberId"),
                            amountMinorUnits = o.optLong("amountMinorUnits"),
                            currency = o.optString("currency", CurrencyCode.INR.name),
                            paymentMethod = o.optString("paymentMethod"),
                            note = o.optNullableString("note"),
                            paymentProofPath = o.optNullableString("paymentProofPath"),
                            dateLabel = o.optString("dateLabel")
                        )
                    )
                }
            }
            return ParsedGuestStore(
                isLegacyDemo = wasDemo,
                profile = profile,
                members = members,
                groups = groups,
                expenses = expenses,
                settlements = settlements
            )
        }

        private fun JSONObject.optNullableString(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun JSONObject.optStringList(key: String): List<String> {
            val arr = optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i)
                    if (v.isNotBlank()) add(v)
                }
            }
        }
    }
}
