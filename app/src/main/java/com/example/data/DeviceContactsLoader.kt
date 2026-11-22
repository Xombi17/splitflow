package com.example.data

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight, privacy-scoped representation of a single device contact selected by the user.
 */
data class DeviceContact(
    val id: String,
    val displayName: String,
    val phoneNumber: String? = null,
    val email: String? = null
) {
    val initials: String
        get() {
            val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "${parts.first().first()}${parts.last().first()}".uppercase()
            }
        }

    val subtitle: String
        get() = listOfNotNull(
            phoneNumber?.takeIf { it.isNotBlank() },
            email?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
}

/**
 * Reads device contacts locally on-device using Android's [ContactsContract] APIs.
 * Only contacts explicitly selected by the user are ever saved to SplitFlow's ledger.
 */
object DeviceContactsLoader {

    suspend fun loadContacts(context: Context): List<DeviceContact> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val namesById = linkedMapOf<String, String>()
        val phonesById = mutableMapOf<String, String>()
        val emailsById = mutableMapOf<String, String>()

        // 1. Load contact IDs and display names
        runCatching {
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                ),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL AND ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} != ''",
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                if (idIdx >= 0 && nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIdx) ?: continue
                        val name = cursor.getString(nameIdx)?.trim().orEmpty()
                        if (name.isNotEmpty()) {
                            namesById[id] = name
                        }
                    }
                }
            }
        }

        if (namesById.isEmpty()) return@withContext emptyList()

        // 2. Attach first phone number per contact (if available)
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (idIdx >= 0 && numIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIdx) ?: continue
                        if (!phonesById.containsKey(id)) {
                            val num = cursor.getString(numIdx)?.trim()
                            if (!num.isNullOrBlank()) {
                                phonesById[id] = num
                            }
                        }
                    }
                }
            }
        }

        // 3. Attach first email per contact (if available)
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                if (idIdx >= 0 && emailIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIdx) ?: continue
                        if (!emailsById.containsKey(id)) {
                            val addr = cursor.getString(emailIdx)?.trim()
                            if (!addr.isNullOrBlank()) {
                                emailsById[id] = addr
                            }
                        }
                    }
                }
            }
        }

        // Deduplicate by lowercase display name + phone/email while preserving alphabetical order
        val seenKeys = mutableSetOf<String>()
        namesById.entries.mapNotNull { (id, name) ->
            val phone = phonesById[id]
            val email = emailsById[id]
            val dedupeKey = "${name.lowercase()}|${phone?.filter { it.isDigit() }.orEmpty()}|${email?.lowercase().orEmpty()}"
            if (seenKeys.add(dedupeKey)) {
                DeviceContact(
                    id = id,
                    displayName = name,
                    phoneNumber = phone,
                    email = email
                )
            } else {
                null
            }
        }
    }
}
