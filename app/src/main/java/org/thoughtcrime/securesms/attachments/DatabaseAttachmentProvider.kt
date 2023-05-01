package org.thoughtcrime.securesms.attachments

import android.content.Context
import android.text.TextUtils
import com.google.protobuf.ByteString
import org.greenrobot.eventbus.EventBus
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.sending_receiving.attachments.*
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.UploadResult
import org.session.libsession.utilities.Util
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.messages.SignalServiceAttachment
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceAttachmentStream
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.IOException
import java.io.InputStream

class DatabaseAttachmentProvider(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), MessageDataProvider {

    private val databaseComponent = DatabaseComponent.get(context)
    private val smsDatabase = databaseComponent.smsDatabase()
    private val mmsDatabase = databaseComponent.mmsDatabase()
    private val mmsSmsDatabase = databaseComponent.mmsSmsDatabase()
    private val lokiMessageDatabase = databaseComponent.lokiMessageDatabase()
    private val attachmentDatabase = databaseComponent.attachmentDatabase()
    private fun getDatabase(isMms: Boolean) = if (isMms) mmsDatabase else smsDatabase

    override fun getAttachmentStream(attachmentId: Long): SessionServiceAttachmentStream? =
        attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0))
            ?.toAttachmentStream(context)

    override fun getAttachmentPointer(attachmentId: Long): SessionServiceAttachmentPointer? =
        attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0))
            ?.toAttachmentPointer()

    override fun getSignalAttachmentStream(attachmentId: Long): SignalServiceAttachmentStream? =
        attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0))
            ?.toSignalAttachmentStream(context)

    override fun getScaledSignalAttachmentStream(attachmentId: Long): SignalServiceAttachmentStream? =
        attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0))
            ?.let { scaleAndStripExif(attachmentDatabase, MediaConstraints.getPushMediaConstraints(), it) }
            ?.let(::getAttachmentFor)

    override fun getSignalAttachmentPointer(attachmentId: Long): SignalServiceAttachmentPointer? =
        attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0))
            ?.toSignalAttachmentPointer()

    override fun setAttachmentState(attachmentState: AttachmentState, attachmentId: AttachmentId, messageID: Long) =
        attachmentDatabase.setTransferState(messageID, attachmentId, attachmentState.value)

    override fun getMessageForQuote(timestamp: Long, author: Address): Triple<Long, Boolean, String>? =
        mmsSmsDatabase.getMessageFor(timestamp, author)
            ?.run { Triple(id, isMms, body) }

    override fun getAttachmentsAndLinkPreviewFor(mmsId: Long): List<Attachment> =
        attachmentDatabase.getAttachmentsForMessage(mmsId)

    override fun getMessageBodyFor(timestamp: Long, author: String): String =
        mmsSmsDatabase.getMessageFor(timestamp, author)!!.body

    override fun getAttachmentIDsFor(messageID: Long): List<Long> =
        attachmentDatabase
            .getAttachmentsForMessage(messageID)
            .filterNot { it.isQuote }
            .mapNotNull { it.attachmentId.rowId }

    override fun getLinkPreviewAttachmentIDFor(messageID: Long): Long? =
        mmsDatabase.getOutgoingMessage(messageID)
            .linkPreviews
            .firstOrNull()
            ?.attachmentId
            ?.rowId

    override fun getIndividualRecipientForMms(mmsId: Long): Recipient? =
        mmsDatabase.getMessage(mmsId)
            .use { mmsDatabase.readerFor(it).next }
            ?.individualRecipient

    override fun insertAttachment(messageId: Long, attachmentId: AttachmentId, stream: InputStream) =
        attachmentDatabase.insertAttachmentsForPlaceholder(messageId, attachmentId, stream)

    override fun updateAudioAttachmentDuration(
        attachmentId: AttachmentId,
        durationMs: Long,
        threadId: Long
    ) {
        attachmentDatabase.setAttachmentAudioExtras(DatabaseAttachmentAudioExtras(
            attachmentId = attachmentId,
            visualSamples = byteArrayOf(),
            durationMs = durationMs
        ), threadId)
    }

    override fun isMmsOutgoing(mmsMessageId: Long): Boolean =
        mmsDatabase.getMessage(mmsMessageId)
            .use { cursor -> mmsDatabase.readerFor(cursor).next }
            ?.isOutgoing ?: false

    override fun isOutgoingMessage(timestamp: Long): Boolean =
        smsDatabase.isOutgoingMessage(timestamp) || mmsDatabase.isOutgoingMessage(timestamp)

    override fun handleSuccessfulAttachmentUpload(attachmentId: Long, attachmentStream: SignalServiceAttachmentStream, attachmentKey: ByteArray, uploadResult: UploadResult) {
        val databaseAttachment = getDatabaseAttachment(attachmentId) ?: return
        val attachmentPointer = SignalServiceAttachmentPointer(uploadResult.id,
            attachmentStream.contentType,
            attachmentKey,
            Optional.of(Util.toIntExact(attachmentStream.length)),
            attachmentStream.preview,
            attachmentStream.width, attachmentStream.height,
            Optional.fromNullable(uploadResult.digest),
            attachmentStream.fileName,
            attachmentStream.voiceNote,
            attachmentStream.caption,
            uploadResult.url);
        val attachment = PointerAttachment.forPointer(Optional.of(attachmentPointer), databaseAttachment.fastPreflightId).get()
        attachmentDatabase.updateAttachmentAfterUploadSucceeded(databaseAttachment.attachmentId, attachment)
    }

    override fun handleFailedAttachmentUpload(attachmentId: Long) {
        getDatabaseAttachment(attachmentId)
            ?.attachmentId
            ?.let(attachmentDatabase::handleFailedAttachmentUpload)
    }

    override fun getMessageID(serverID: Long): Long? = lokiMessageDatabase.getMessageID(serverID)

    override fun getMessageID(serverId: Long, threadId: Long): Pair<Long, Boolean>? =
        lokiMessageDatabase.getMessageID(serverId, threadId)

    override fun getMessageIDs(serverIds: List<Long>, threadId: Long): Pair<List<Long>, List<Long>> =
        lokiMessageDatabase.getMessageIDs(serverIds, threadId)

    override fun deleteMessage(messageID: Long, isSms: Boolean) {
        getDatabase(isMms = !isSms).deleteMessage(messageID)
        lokiMessageDatabase.deleteMessage(messageID, isSms)
        lokiMessageDatabase.deleteMessageServerHash(messageID)
    }

    override fun deleteMessages(messageIDs: List<Long>, threadId: Long, isSms: Boolean) {
        getDatabase(isMms = !isSms).deleteMessages(messageIDs.toLongArray(), threadId)
        lokiMessageDatabase.deleteMessages(messageIDs)
        lokiMessageDatabase.deleteMessageServerHashes(messageIDs)
    }

    override fun updateMessageAsDeleted(timestamp: Long, author: String): Long? =
        mmsSmsDatabase.getMessageFor(timestamp, Address.fromSerialized(author))?.apply {
            getDatabase(isMms).apply {
                markAsDeleted(id, isRead, hasMention)
                if (isOutgoing) deleteMessage(id)
            }
        }?.id

    override fun getServerHashForMessage(messageID: Long): String? =
        lokiMessageDatabase.getMessageServerHash(messageID)

    override fun getDatabaseAttachment(attachmentId: Long): DatabaseAttachment? =
        attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0))

    private fun scaleAndStripExif(attachmentDatabase: AttachmentDatabase, constraints: MediaConstraints, attachment: Attachment): Attachment? =
        try {
            if (constraints.isSatisfied(context, attachment)) {
                if (MediaUtil.isJpeg(attachment)) {
                    val stripped = constraints.getResizedMedia(context, attachment)
                    attachmentDatabase.updateAttachmentData(attachment, stripped)
                } else attachment
            } else if (constraints.canResize(attachment)) {
                val resized = constraints.getResizedMedia(context, attachment)
                attachmentDatabase.updateAttachmentData(attachment, resized)
            } else null
        } catch (e: Exception) {
            null
        }

    private fun getAttachmentFor(attachment: Attachment): SignalServiceAttachmentStream? {
        try {
            if (attachment.dataUri == null || attachment.size == 0L) throw IOException("Assertion failed, outgoing attachment has no data!")
            val `is` = PartAuthority.getAttachmentStream(context, attachment.dataUri!!)
            return SignalServiceAttachment.newStreamBuilder()
                    .withStream(`is`)
                    .withContentType(attachment.contentType)
                    .withLength(attachment.size)
                    .withFileName(attachment.fileName)
                    .withVoiceNote(attachment.isVoiceNote)
                    .withWidth(attachment.width)
                    .withHeight(attachment.height)
                    .withCaption(attachment.caption)
                    .withListener { total: Long, progress: Long -> EventBus.getDefault().postSticky(PartProgressEvent(attachment, total, progress)) }
                    .build()
        } catch (ioe: IOException) {
            Log.w("Loki", "Couldn't open attachment", ioe)
        }
        return null
    }

}

fun DatabaseAttachment.toAttachmentPointer(): SessionServiceAttachmentPointer =
    SessionServiceAttachmentPointer(attachmentId.rowId, contentType, key?.toByteArray(), Optional.fromNullable(size.toInt()), Optional.absent(), width, height, Optional.fromNullable(digest), Optional.fromNullable(fileName), isVoiceNote, Optional.fromNullable(caption), url)

fun SessionServiceAttachmentPointer.toSignalPointer(): SignalServiceAttachmentPointer =
    SignalServiceAttachmentPointer(id,contentType,key?.toByteArray() ?: byteArrayOf(), size, preview, width, height, digest, fileName, voiceNote, caption, url)

fun DatabaseAttachment.toAttachmentStream(context: Context): SessionServiceAttachmentStream {
    val stream = PartAuthority.getAttachmentStream(context, dataUri!!)
    val listener = SignalServiceAttachment.ProgressListener { total: Long, progress: Long -> EventBus.getDefault().postSticky(PartProgressEvent(this, total, progress))}

    return SessionServiceAttachmentStream(stream, contentType, size, Optional.fromNullable(fileName), isVoiceNote, Optional.absent(), width, height, Optional.fromNullable(caption), listener)
        .also { attachmentStream ->
            attachmentStream.attachmentId = attachmentId.rowId
            attachmentStream.isAudio = MediaUtil.isAudio(this)
            attachmentStream.isGif = MediaUtil.isGif(this)
            attachmentStream.isVideo = MediaUtil.isVideo(this)
            attachmentStream.isImage = MediaUtil.isImage(this)

            attachmentStream.key = ByteString.copyFrom(key?.toByteArray())
            attachmentStream.digest = Optional.fromNullable(digest)

            attachmentStream.url = url
        }
}

fun DatabaseAttachment.toSignalAttachmentPointer(): SignalServiceAttachmentPointer? {
    if (TextUtils.isEmpty(location)) { return null }
    // `key` can be empty in an open group context (no encryption means no encryption key)
    return try {
        val id = location!!.toLong()
        val key = Base64.decode(key!!)
        SignalServiceAttachmentPointer(
            id,
            contentType,
            key,
            Optional.of(Util.toIntExact(size)),
            Optional.absent(),
            width,
            height,
            Optional.fromNullable(digest),
            Optional.fromNullable(fileName),
            isVoiceNote,
            Optional.fromNullable(caption),
            url
        )
    } catch (e: Exception) {
        null
    }
}

fun DatabaseAttachment.toSignalAttachmentStream(context: Context): SignalServiceAttachmentStream {
    val stream = PartAuthority.getAttachmentStream(context, dataUri!!)
    val listener = SignalServiceAttachment.ProgressListener { total: Long, progress: Long -> EventBus.getDefault().postSticky(PartProgressEvent(this, total, progress))}

    return SignalServiceAttachmentStream(stream, contentType, size, Optional.fromNullable(fileName), isVoiceNote, Optional.absent(), width, height, Optional.fromNullable(caption), listener)
}
