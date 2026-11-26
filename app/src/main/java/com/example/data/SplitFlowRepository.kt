package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.CurrencyCode
import com.example.model.DietPreference
import com.example.model.ExpenseGroup
import com.example.model.ExpenseRecord
import com.example.model.LedgerMember
import com.example.model.SettlementRecord
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SplitFlowRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()
) : LedgerRepository {
    constructor(context: Context) : this(
        db = runCatching {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        }.getOrElse { FirebaseFirestore.getInstance() },
        auth = runCatching { Firebase.auth }.getOrNull()
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth?.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user access." }
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun membersCol(uid: String) = userDoc(uid).collection("members")
    private fun groupsCol(uid: String) = userDoc(uid).collection("groups")
    private fun expensesCol(uid: String) = userDoc(uid).collection("expenses")
    private fun settlementsCol(uid: String) = userDoc(uid).collection("settlements")

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        userDoc(userId).snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error -> logFlowErrorAndRethrow(error, userId, OperationType.GET, "users/$userId") }

    override fun observeMembers(userId: String): Flow<List<LedgerMember>> =
        membersCol(userId).snapshots()
            .map { snap ->
                snap.toObjects(LedgerMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<LedgerMember> { it.selfProfile }.thenBy { it.name.lowercase() })
            }
            .catch { error -> logFlowErrorAndRethrow(error, userId, OperationType.LIST, "users/$userId/members") }

    override fun observeGroups(userId: String): Flow<List<ExpenseGroup>> =
        groupsCol(userId).snapshots()
            .map { snap ->
                snap.toObjects(ExpenseGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error -> logFlowErrorAndRethrow(error, userId, OperationType.LIST, "users/$userId/groups") }

    override fun observeExpenses(userId: String): Flow<List<ExpenseRecord>> =
        expensesCol(userId).snapshots()
            .map { snap ->
                snap.toObjects(ExpenseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<ExpenseRecord> { it.dateLabel }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
            }
            .catch { error -> logFlowErrorAndRethrow(error, userId, OperationType.LIST, "users/$userId/expenses") }

    override fun observeSettlements(userId: String): Flow<List<SettlementRecord>> =
        settlementsCol(userId).snapshots()
            .map { snap ->
                snap.toObjects(SettlementRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareByDescending<SettlementRecord> { it.dateLabel }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
            }
            .catch { error -> logFlowErrorAndRethrow(error, userId, OperationType.LIST, "users/$userId/settlements") }

    private fun logFlowErrorAndRethrow(
        error: Throwable,
        uid: String,
        operationType: OperationType,
        path: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth?.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    /**
     * Ensures the authenticated user has a clean, isolated profile document and their own
     * self member document (`mem_self`) with zero hardcoded demo data.
     */
    override suspend fun ensureInitialized(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val user = auth?.currentUser
            val resolvedName = user?.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                ?: "SplitFlow User"
            val snap = userDoc(uid).get().await()
            if (!snap.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = resolvedName,
                    email = user?.email,
                    upiVpa = null,
                    defaultCurrency = CurrencyCode.INR.name,
                    darkMode = true,
                    seededDemo = false
                )
                val selfMember = LedgerMember(
                    userId = uid,
                    name = resolvedName,
                    email = user?.email,
                    upiVpa = null,
                    avatarColorHex = "#0E6F4E",
                    selfProfile = true,
                    guestProfile = false,
                    dietPreference = DietPreference.NON_VEG.name,
                    drinksAlcohol = true
                )
                val batch = db.batch()
                batch.set(userDoc(uid), profile.toCreateMap(uid))
                batch.set(membersCol(uid).document(GuestLedgerRepository.SELF_MEMBER_ID), selfMember.toCreateMap(uid))
                batch.commit().await()
            } else {
                // Guarantee the user always has a self member document
                val selfDoc = membersCol(uid).document(GuestLedgerRepository.SELF_MEMBER_ID).get().await()
                if (!selfDoc.exists()) {
                    val existingProfile = snap.toObject(UserProfile::class.java)
                    val selfMember = LedgerMember(
                        userId = uid,
                        name = existingProfile?.displayName?.takeIf { it.isNotBlank() } ?: resolvedName,
                        email = existingProfile?.email ?: user?.email,
                        upiVpa = existingProfile?.upiVpa,
                        avatarColorHex = "#0E6F4E",
                        selfProfile = true,
                        guestProfile = false,
                        dietPreference = DietPreference.NON_VEG.name,
                        drinksAlcohol = true
                    )
                    membersCol(uid)
                        .document(GuestLedgerRepository.SELF_MEMBER_ID)
                        .set(selfMember.toCreateMap(uid))
                        .await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Migrates user-authored local Guest Mode splits, groups, friends, and settlements into
     * the authenticated user's isolated Cloud Firestore database in safe chunked batches.
     */
    suspend fun migrateGuestDataToCloud(
        userId: String,
        payload: GuestMigrationPayload
    ): Result<Int> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            ensureInitialized(uid).getOrThrow()

            val writeOps = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()

            payload.members
                .filter { !it.selfProfile }
                .forEach { member ->
                    val docId = sanitizeDocId(member.id, "mem")
                    writeOps += membersCol(uid).document(docId) to member.toCreateMap(uid)
                }

            payload.groups.forEach { group ->
                val docId = sanitizeDocId(group.id, "grp")
                writeOps += groupsCol(uid).document(docId) to group.toCreateMap(uid)
            }

            payload.expenses.forEach { expense ->
                val docId = sanitizeDocId(expense.id, "exp")
                writeOps += expensesCol(uid).document(docId) to expense.toCreateMap(uid)
            }

            payload.settlements.forEach { settlement ->
                val docId = sanitizeDocId(settlement.id, "set")
                writeOps += settlementsCol(uid).document(docId) to settlement.toCreateMap(uid)
            }

            writeOps.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (ref, data) -> batch.set(ref, data) }
                batch.commit().await()
            }

            // Preserve guest UPI VPA if the cloud profile doesn't have one yet
            val guestUpi = payload.upiVpa?.trim()?.takeIf { it.isNotEmpty() }
            if (guestUpi != null) {
                val profileSnap = userDoc(uid).get().await()
                val existingUpi = profileSnap.getString("upiVpa")
                if (existingUpi.isNullOrBlank()) {
                    userDoc(uid).update(
                        mapOf(
                            "upiVpa" to guestUpi.take(80),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(writeOps.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Permanently wipes all expenses, settlements, groups, and non-self friends from the user's
     * Firestore ledger using chunked batch deletes, leaving only the user's clean profile intact.
     */
    override suspend fun clearAllLedgerData(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val refsToDelete = mutableListOf<DocumentReference>()

            val collectionsToWipe = listOf(expensesCol(uid), settlementsCol(uid), groupsCol(uid))
            for (col in collectionsToWipe) {
                val docs = col.get().await().documents
                docs.forEach { refsToDelete += it.reference }
            }

            // Delete all non-self friends while keeping the user's own self profile document
            val memberDocs = membersCol(uid).get().await().documents
            memberDocs.forEach { doc ->
                val isSelf = doc.id == GuestLedgerRepository.SELF_MEMBER_ID ||
                    doc.getBoolean("selfProfile") == true
                if (!isSelf) {
                    refsToDelete += doc.reference
                }
            }

            refsToDelete.chunked(MAX_BATCH_OPS).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }

            // Ensure seededDemo flag is cleared on profile
            runCatching {
                userDoc(uid).update(
                    mapOf(
                        "seededDemo" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            ensureInitialized(uid).getOrThrow()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(
        userId: String,
        displayName: String,
        upiVpa: String?,
        defaultCurrency: CurrencyCode,
        darkMode: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = displayName.trim().ifBlank { "SplitFlow User" }.take(80)
            val cleanUpi = upiVpa?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
            val updates = mutableMapOf<String, Any>(
                "displayName" to cleanName,
                "defaultCurrency" to defaultCurrency.name,
                "darkMode" to darkMode,
                "seededDemo" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanUpi != null) {
                updates["upiVpa"] = cleanUpi
            } else {
                updates["upiVpa"] = FieldValue.delete()
            }
            userDoc(uid).update(updates).await()

            // Keep self member document synchronized with display name and UPI VPA
            val selfUpdates = mutableMapOf<String, Any>(
                "name" to cleanName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanUpi != null) {
                selfUpdates["upiVpa"] = cleanUpi
            } else {
                selfUpdates["upiVpa"] = FieldValue.delete()
            }
            runCatching {
                membersCol(uid).document(GuestLedgerRepository.SELF_MEMBER_ID).update(selfUpdates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun addMember(userId: String, member: LedgerMember): Result<String> {
        val path = "users/$userId/members"
        return try {
            val uid = requireUserId(userId)
            val docRef = membersCol(uid).document()
            docRef.set(member.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateMemberPreferences(
        userId: String,
        memberId: String,
        dietPreference: DietPreference,
        drinksAlcohol: Boolean
    ): Result<Unit> {
        val path = "users/$userId/members/$memberId"
        return try {
            val uid = requireUserId(userId)
            membersCol(uid).document(memberId).update(
                mapOf(
                    "dietPreference" to dietPreference.name,
                    "drinksAlcohol" to drinksAlcohol,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun claimGuestProfile(
        userId: String,
        memberId: String,
        verifiedName: String,
        verifiedEmail: String,
        upiVpa: String?,
        claimedDateLabel: String
    ): Result<Unit> {
        val path = "users/$userId/members/$memberId"
        return try {
            val uid = requireUserId(userId)
            require(memberId.isNotBlank()) { "Member ID must not be blank" }
            val updates = mutableMapOf<String, Any>(
                "name" to verifiedName.trim().ifBlank { "Verified Friend" }.take(80),
                "email" to verifiedEmail.trim().take(120),
                "guestProfile" to false,
                "claimedAtLabel" to claimedDateLabel.take(60),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!upiVpa.isNullOrBlank()) {
                updates["upiVpa"] = upiVpa.trim().take(80)
            }
            membersCol(uid).document(memberId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun addGroup(userId: String, group: ExpenseGroup): Result<String> {
        val path = "users/$userId/groups"
        return try {
            val uid = requireUserId(userId)
            require(group.memberIds.isNotEmpty()) { "Group must contain at least one member" }
            val docRef = groupsCol(uid).document()
            docRef.set(group.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateGroupMembers(userId: String, groupId: String, memberIds: List<String>): Result<Unit> {
        val path = "users/$userId/groups/$groupId"
        return try {
            val uid = requireUserId(userId)
            require(memberIds.isNotEmpty()) { "Group must have at least one member" }
            groupsCol(uid).document(groupId).update(
                mapOf(
                    "memberIds" to memberIds.distinct().take(50),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateGroupBudget(
        userId: String,
        groupId: String,
        budgetMinorUnits: Long?
    ): Result<Unit> {
        val path = "users/$userId/groups/$groupId"
        return try {
            val uid = requireUserId(userId)
            val updates = mapOf(
                "budgetMinorUnits" to (budgetMinorUnits?.coerceAtLeast(0L) ?: 0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            groupsCol(uid).document(groupId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateGroupMemory(
        userId: String,
        groupId: String,
        memoryNotes: String?
    ): Result<Unit> {
        val path = "users/$userId/groups/$groupId"
        return try {
            val uid = requireUserId(userId)
            val clean = memoryNotes?.trim()?.take(400).orEmpty()
            val updates = mapOf(
                "splitMemoryNotes" to clean,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            groupsCol(uid).document(groupId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun addExpense(userId: String, expense: ExpenseRecord, preferredId: String?): Result<String> {
        val path = "users/$userId/expenses"
        return try {
            val uid = requireUserId(userId)
            require(expense.totalMinorUnits > 0L) { "Total amount must be greater than zero" }
            require(expense.payers.isNotEmpty()) { "Expense must have at least one payer" }
            require(expense.allocations.isNotEmpty()) { "Expense must have at least one participant" }
            val docRef = if (!preferredId.isNullOrBlank()) {
                expensesCol(uid).document(preferredId)
            } else {
                expensesCol(uid).document()
            }
            docRef.set(expense.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateExpense(userId: String, expenseId: String, expense: ExpenseRecord): Result<Unit> {
        val path = "users/$userId/expenses/$expenseId"
        return try {
            val uid = requireUserId(userId)
            require(expenseId.isNotBlank()) { "Expense ID must not be blank" }
            expensesCol(uid).document(expenseId).update(expense.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteExpense(userId: String, expenseId: String): Result<Unit> {
        val path = "users/$userId/expenses/$expenseId"
        return try {
            val uid = requireUserId(userId)
            require(expenseId.isNotBlank()) { "Expense ID must not be blank" }
            expensesCol(uid).document(expenseId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun addSettlement(
        userId: String,
        settlement: SettlementRecord,
        preferredId: String?
    ): Result<String> {
        val path = "users/$userId/settlements"
        return try {
            val uid = requireUserId(userId)
            require(settlement.amountMinorUnits > 0L) { "Settlement amount must be positive" }
            require(settlement.fromMemberId != settlement.toMemberId) { "Payer and payee must differ" }
            val docRef = if (!preferredId.isNullOrBlank()) {
                settlementsCol(uid).document(sanitizeDocId(preferredId, "set"))
            } else {
                settlementsCol(uid).document()
            }
            docRef.set(settlement.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateSettlement(
        userId: String,
        settlementId: String,
        settlement: SettlementRecord
    ): Result<Unit> {
        val path = "users/$userId/settlements/$settlementId"
        return try {
            val uid = requireUserId(userId)
            require(settlementId.isNotBlank()) { "Settlement ID must not be blank" }
            require(settlement.amountMinorUnits > 0L) { "Settlement amount must be positive" }
            settlementsCol(uid).document(settlementId).update(settlement.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteSettlement(userId: String, settlementId: String): Result<Unit> {
        val path = "users/$userId/settlements/$settlementId"
        return try {
            val uid = requireUserId(userId)
            require(settlementId.isNotBlank()) { "Settlement ID must not be blank" }
            settlementsCol(uid).document(settlementId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun sanitizeDocId(raw: String, prefix: String): String {
        val clean = raw.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(120)
        return clean.ifBlank { "${prefix}_${UUID.randomUUID().toString().take(8)}" }
    }

    companion object {
        private const val MAX_BATCH_OPS = 400
    }
}
