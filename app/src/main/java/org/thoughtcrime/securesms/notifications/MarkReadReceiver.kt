package org.thoughtcrime.securesms.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import androidx.core.app.NotificationManagerCompat
import com.annimon.stream.Collectors
import com.annimon.stream.Stream
import nl.komponents.kovenant.task
import org.session.libsession.messaging.MessagingModuleConfiguration.Companion.shared
import org.session.libsession.messaging.messages.control.ReadReceipt
import org.session.libsession.messaging.sending_receiving.MessageSender.send
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeAPI.nowWithOffset
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.isReadReceiptsEnabled
import org.session.libsession.utilities.associateByNotNull
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.MessagingDatabase.ExpirationInfo
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.SessionMetaProtocol.shouldSendReadReceipt

class MarkReadReceiver : BroadcastReceiver() {
    @SuppressLint("StaticFieldLeak")
    override fun onReceive(context: Context, intent: Intent) {
        if (CLEAR_ACTION != intent.action) return
        val threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA)
        if (threadIds != null) {
            NotificationManagerCompat.from(context)
                .cancel(intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1))
            object : AsyncTask<Void?, Void?, Void?>() {
                override fun doInBackground(vararg params: Void?): Void? {
                    val currentTime = nowWithOffset
                    for (threadId in threadIds) {
                        Log.i(TAG, "Marking as read: $threadId")
                        val storage = shared.storage
                        storage.markConversationAsRead(threadId, currentTime, true)
                    }
                    return null
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    companion object {
        private val TAG = MarkReadReceiver::class.java.getSimpleName()
        const val CLEAR_ACTION = "network.loki.securesms.notifications.CLEAR"
        const val THREAD_IDS_EXTRA = "thread_ids"
        const val NOTIFICATION_ID_EXTRA = "notification_id"

        @JvmStatic
        fun process(context: Context, markedReadMessages: List<MarkedMessageInfo>) {
            if (markedReadMessages.isEmpty()) return

            val loki = DatabaseComponent.get(context).lokiMessageDatabase()

            task {
                val hashToInfo = markedReadMessages.associateByNotNull {
                    loki.getMessageServerHash(it.syncMessageId.timetamp)
                        ?: loki.getMessageServerHash(it.expirationInfo.id)
                }
                if (hashToInfo.isEmpty()) return@task

                @Suppress("UNCHECKED_CAST")
                val expiries = SnodeAPI.getExpiries(hashToInfo.keys.toList(), TextSecurePreferences.getLocalNumber(context)!!)
                    .get()["expiries"] as Map<String, Long>

                hashToInfo.forEach { (hash, info) -> expiries[hash]?.let { scheduleDeletion(context, info.expirationInfo, it - info.expirationInfo.expireStarted) } }
            } fail {
                Log.e(TAG, "process() disappear after read failed", it)
            }

            markedReadMessages.forEach { scheduleDeletion(context, it.expirationInfo) }

            if (!isReadReceiptsEnabled(context)) return

            markedReadMessages.map { it.syncMessageId }
                .filter { shouldSendReadReceipt(Recipient.from(context, it.address, false)) }
                .groupBy { it.address }
                .forEach { (address, messages) ->
                    messages.map { it.timetamp }
                        .let(::ReadReceipt)
                        .apply { sentTimestamp = nowWithOffset }
                        .let { send(it, address) }
                }
        }

        fun scheduleDeletion(
            context: Context?,
            expirationInfo: ExpirationInfo,
            expiresIn: Long = expirationInfo.expiresIn
        ) {
            android.util.Log.d(TAG, "scheduleDeletion() called with: expirationInfo = $expirationInfo, expiresIn = $expiresIn")

            if (expiresIn > 0 && expirationInfo.expireStarted <= 0) {
                val expirationManager =
                    ApplicationContext.getInstance(context).expiringMessageManager
                if (expirationInfo.isMms) DatabaseComponent.get(context!!).mmsDatabase()
                    .markExpireStarted(expirationInfo.id) else DatabaseComponent.get(
                    context!!
                ).smsDatabase().markExpireStarted(expirationInfo.id)
                expirationManager.scheduleDeletion(
                    expirationInfo.id,
                    expirationInfo.isMms,
                    expiresIn
                )
            }
        }
    }
}
