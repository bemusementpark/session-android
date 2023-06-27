package org.thoughtcrime.securesms.conversation.v2

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityMessageDetailBinding
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.messaging.utilities.SessionId
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.IdPrefix
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.utilities.ResendMessageUtilities
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class MessageDetailActivity: PassphraseRequiredActionBarActivity() {
    private lateinit var binding: ActivityMessageDetailBinding

    @Inject
    lateinit var storage: Storage

    // region Settings
    companion object {
        // Extras
        const val MESSAGE_TIMESTAMP = "message_timestamp"
    }
    // endregion

    override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
        super.onCreate(savedInstanceState, ready)
        binding = ActivityMessageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = resources.getString(R.string.conversation_context__menu_message_details)
        val timestamp = intent.getLongExtra(MESSAGE_TIMESTAMP, -1L)
        // We only show this screen for messages fail to send,
        // so the author of the messages must be the current user.
        val author = Address.fromSerialized(TextSecurePreferences.getLocalNumber(this)!!)
        val messageRecord = DatabaseComponent.get(this).mmsSmsDatabase().getMessageFor(timestamp, author) ?: run {
            finish()
            return
        }
        val threadId = messageRecord.threadId
        val openGroup = storage.getOpenGroup(threadId)
        val blindedKey = openGroup?.let { group ->
            val userEdKeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair() ?: return@let null
            val blindingEnabled = storage.getServerCapabilities(group.server).contains(OpenGroupApi.Capability.BLIND.name.lowercase())
            if (blindingEnabled) {
                SodiumUtilities.blindedKeyPair(group.publicKey, userEdKeyPair)?.publicKey?.asBytes
                    ?.let { SessionId(IdPrefix.BLINDED, it) }?.hexString
            } else null
        }
        updateContent(messageRecord)
        binding.resendButton.setOnClickListener {
            ResendMessageUtilities.resend(this, messageRecord, blindedKey)
            finish()
        }
    }

    private fun updateContent(messageRecord: MessageRecord) {
        val dateLocale = Locale.getDefault()
        val dateFormatter: SimpleDateFormat = DateUtils.getDetailedDateFormatter(this, dateLocale)
        binding.sentTime.text = dateFormatter.format(Date(messageRecord.dateSent))

        val errorMessage = DatabaseComponent.get(this).lokiMessageDatabase().getErrorMessage(messageRecord.getId())
        binding.errorMessage.text = errorMessage

        val disappearing = messageRecord.expiresIn > 0 && messageRecord.expireStarted > 0

        binding.expiresIn.isVisible = disappearing

        if (disappearing) {
            val elapsed = SnodeAPI.nowWithOffset - messageRecord.expireStarted
            val remaining = messageRecord.expiresIn - elapsed

            val duration = ExpirationUtil.getExpirationDisplayValue(this, max((remaining / 1000).toInt(), 1))
            binding.expiresIn.text = duration
        }
    }
}
