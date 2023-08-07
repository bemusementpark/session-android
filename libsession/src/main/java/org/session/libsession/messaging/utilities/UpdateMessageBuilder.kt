package org.session.libsession.messaging.utilities

import android.content.Context
import org.session.libsession.R
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.calls.CallMessageType
import org.session.libsession.messaging.calls.CallMessageType.CALL_FIRST_MISSED
import org.session.libsession.messaging.calls.CallMessageType.CALL_INCOMING
import org.session.libsession.messaging.calls.CallMessageType.CALL_MISSED
import org.session.libsession.messaging.calls.CallMessageType.CALL_OUTGOING
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.contacts.Contact.ContactContext.REGULAR
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage
import org.session.libsession.utilities.ExpirationUtil
import org.session.libsession.utilities.truncateIdForDisplay

object UpdateMessageBuilder {
    val storage = MessagingModuleConfiguration.shared.storage

    private fun getSenderName(senderId: String) =
        storage.getContactWithSessionID(senderId)?.displayName(REGULAR)
            ?: truncateIdForDisplay(senderId)

    fun buildGroupUpdateMessage(
        context: Context,
        updateMessageData: UpdateMessageData,
        senderId: String? = null,
        isOutgoing: Boolean = false
    ): String {
        val senderName: String = when {
            isOutgoing -> context.getString(R.string.MessageRecord_you)
            else -> getSenderName(senderId ?: return "")
        }

        return when (val updateData = updateMessageData.kind) {
            is UpdateMessageData.Kind.GroupCreation -> when {
                isOutgoing -> context.getString(R.string.MessageRecord_you_created_a_new_group)
                else -> context.getString(R.string.MessageRecord_s_added_you_to_the_group, senderName)
            }
            is UpdateMessageData.Kind.GroupNameChange -> when {
                isOutgoing -> context.getString(R.string.MessageRecord_you_renamed_the_group_to_s, updateData.name)
                else -> context.getString(R.string.MessageRecord_s_renamed_the_group_to_s, senderName, updateData.name)
            }
            is UpdateMessageData.Kind.GroupMemberAdded -> {
                val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)
                when {
                    isOutgoing -> context.getString(R.string.MessageRecord_you_added_s_to_the_group, members)
                    else -> context.getString(R.string.MessageRecord_s_added_s_to_the_group, senderName, members)
                }
            }
            is UpdateMessageData.Kind.GroupMemberRemoved ->
                if (storage.getUserPublicKey()!! in updateData.updatedMembers) {
                    // 1st case: you are part of the removed members
                    when {
                        isOutgoing -> context.getString(R.string.MessageRecord_left_group)
                        else -> context.getString(R.string.MessageRecord_you_were_removed_from_the_group)
                    }
                } else {
                    // 2nd case: you are not part of the removed members
                    val members = updateData.updatedMembers.joinToString(", ", transform = ::getSenderName)
                    when {
                        isOutgoing -> context.getString(R.string.MessageRecord_you_removed_s_from_the_group, members)
                        else -> context.getString(R.string.MessageRecord_s_removed_s_from_the_group, senderName, members)
                    }
                }
            is UpdateMessageData.Kind.GroupMemberLeft -> when {
                isOutgoing -> context.getString(R.string.MessageRecord_left_group)
                else -> context.getString(R.string.ConversationItem_group_action_left, senderName)
            }
            else -> return ""
        }
    }

    fun buildExpirationTimerMessage(context: Context, duration: Long, senderId: String? = null, isOutgoing: Boolean = false): String {
        fun time() = ExpirationUtil.getExpirationDisplayValue(context, duration.toInt())

        return if (isOutgoing) when {
            duration <= 0 -> context.getString(R.string.MessageRecord_you_disabled_disappearing_messages)
            else -> context.getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time())
        } else {
            val senderName = getSenderName(senderId ?: return "")
            if (duration <= 0) context.getString(R.string.MessageRecord_s_disabled_disappearing_messages, senderName)
            else context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, senderName, time())
        }
    }

    fun buildDataExtractionMessage(
        context: Context,
        kind: DataExtractionNotificationInfoMessage.Kind,
        senderId: String? = null
    ): String = when (kind) {
        DataExtractionNotificationInfoMessage.Kind.SCREENSHOT -> R.string.MessageRecord_s_took_a_screenshot
        DataExtractionNotificationInfoMessage.Kind.MEDIA_SAVED -> R.string.MessageRecord_media_saved_by_s
    }.let { context.getString(it, getSenderName(senderId!!)) }

    fun buildCallMessage(
        context: Context,
        type: CallMessageType,
        sender: String
    ): String = when (type) {
        CALL_INCOMING -> R.string.MessageRecord_s_called_you
        CALL_OUTGOING -> R.string.MessageRecord_called_s
        CALL_MISSED, CALL_FIRST_MISSED -> R.string.MessageRecord_missed_call_from
    }.let {
        context.getString(it, storage.getContactWithSessionID(sender)?.displayName(REGULAR) ?: sender)
    }
}
