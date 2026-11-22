package com.example.firebase

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    val currentUser = auth?.currentUser

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        if (BuildConfig.DEBUG) {
            val providerInfoList = currentUser?.providerData?.map { provider ->
                JSONObject().apply {
                    put("providerId", provider.providerId)
                    put("email", provider.email)
                }
            } ?: emptyList()

            val authInfoJson = JSONObject().apply {
                put("userId", currentUser?.uid)
                put("email", currentUser?.email)
                put("emailVerified", currentUser?.isEmailVerified)
                put("tenantId", currentUser?.tenantId)
                put("providerInfo", JSONArray(providerInfoList))
            }
            put("authInfo", authInfoJson)
        } else {
            put("authenticated", currentUser != null)
        }
    }

    val jsonString = errorInfoJson.toString()
    if (BuildConfig.DEBUG) {
        Log.e("FirestoreError", "Firestore Error: $jsonString")
    } else {
        Log.e("FirestoreError", "Firestore ${operationType.value} failed: ${rootError.javaClass.simpleName}")
    }
    return jsonString
}
