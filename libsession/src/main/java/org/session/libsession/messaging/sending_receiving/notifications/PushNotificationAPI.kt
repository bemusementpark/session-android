package org.session.libsession.messaging.sending_receiving.notifications

import android.annotation.SuppressLint
import nl.komponents.kovenant.functional.map
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsession.snode.Version
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.retryIfNeeded

@SuppressLint("StaticFieldLeak")
object PushNotificationAPI {
    val context = MessagingModuleConfiguration.shared.context
    val server = "https://push.getsession.org"
    val serverPublicKey: String = "d7557fe563e2610de876c0ac7341b62f3c82d5eea4b62c702392ea4368f51b3b"
    private val legacyServer = "https://live.apns.getsession.org"
    private val legacyServerPublicKey = "642a6585919742e5a2d4dc51244964fbcd8bcab2b75612407de58b810740d049"
    private val maxRetryCount = 4
    private val tokenExpirationInterval = 12 * 60 * 60 * 1000

    enum class ClosedGroupOperation {
        Subscribe, Unsubscribe;

        val rawValue: String
            get() {
                return when (this) {
                    Subscribe -> "subscribe_closed_group"
                    Unsubscribe -> "unsubscribe_closed_group"
                }
            }
    }

    fun unregister(token: String) {
        val parameters = mapOf( "token" to token )
        val url = "$server/unregister"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request.build(), server, serverPublicKey, Version.V2).map { response ->
                val code = response.info["code"] as? Int
                if (code != null && code != 0) {
                    TextSecurePreferences.setIsUsingFCM(context, false)
                } else {
                    Log.d("Loki", "Couldn't disable FCM due to error: ${response.info["message"] as? String ?: "null"}.")
                }
            }.fail { exception ->
                Log.d("Loki", "Couldn't disable FCM due to error: ${exception}.")
            }
        }
        // Unsubscribe from all closed groups
        val allClosedGroupPublicKeys = MessagingModuleConfiguration.shared.storage.getAllClosedGroupPublicKeys()
        val userPublicKey = MessagingModuleConfiguration.shared.storage.getUserPublicKey()!!
        allClosedGroupPublicKeys.iterator().forEach { closedGroup ->
            performOperation(ClosedGroupOperation.Unsubscribe, closedGroup, userPublicKey)
        }
    }

    fun register(token: String, publicKey: String, force: Boolean) {
        // Subscribe to all closed groups
        val allClosedGroupPublicKeys = MessagingModuleConfiguration.shared.storage.getAllClosedGroupPublicKeys()
        allClosedGroupPublicKeys.iterator().forEach { closedGroup ->
            performOperation(ClosedGroupOperation.Subscribe, closedGroup, publicKey)
        }
    }

    fun performOperation(operation: ClosedGroupOperation, closedGroupPublicKey: String, publicKey: String) {
        if (!TextSecurePreferences.isUsingFCM(context)) { return }
        val parameters = mapOf( "closedGroupPublicKey" to closedGroupPublicKey, "pubKey" to publicKey )
        val url = "$legacyServer/${operation.rawValue}"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request.build(), legacyServer, legacyServerPublicKey, Version.V2).map { response ->
                val code = response.info["code"] as? Int
                if (code == null || code == 0) {
                    Log.d("Loki", "Couldn't subscribe/unsubscribe closed group: $closedGroupPublicKey due to error: ${response.info["message"] as? String ?: "null"}.")
                }
            }.fail { exception ->
                Log.d("Loki", "Couldn't subscribe/unsubscribe closed group: $closedGroupPublicKey due to error: ${exception}.")
            }
        }
    }
}
