package org.session.libsession.messaging.messages.control

import com.google.protobuf.ByteString
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.copyExpiration
import org.session.libsession.utilities.Address
import org.session.libsignal.crypto.ecc.DjbECPrivateKey
import org.session.libsignal.crypto.ecc.DjbECPublicKey
import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.protos.SignalServiceProtos
import org.session.libsignal.protos.SignalServiceProtos.DataMessage
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.removingIdPrefixIfNeeded
import org.session.libsignal.utilities.toHexString

class ClosedGroupControlMessage() : ControlMessage() {
    var kind: Kind? = null
    var groupID: String? = null

    override val defaultTtl: Long get() {
        return when (kind) {
            is Kind.EncryptionKeyPair -> 14 * 24 * 60 * 60 * 1000
            else -> 14 * 24 * 60 * 60 * 1000
        }
    }

    override val isSelfSendValid: Boolean = true

    override fun isValid(): Boolean {
        val kind = kind
        if (!super.isValid() || kind == null) return false
        return when (kind) {
            is Kind.New -> {
                !kind.publicKey.isEmpty && kind.name.isNotEmpty() && kind.encryptionKeyPair?.publicKey != null
                    && kind.encryptionKeyPair?.privateKey != null && kind.members.isNotEmpty() && kind.admins.isNotEmpty()
                    && kind.expirationTimer >= 0
            }
            is Kind.EncryptionKeyPair -> true
            is Kind.NameChange -> kind.name.isNotEmpty()
            is Kind.MembersAdded -> kind.members.isNotEmpty()
            is Kind.MembersRemoved -> kind.members.isNotEmpty()
            is Kind.MemberLeft -> true
        }
    }

    sealed class Kind {
        class New(var publicKey: ByteString, var name: String, var encryptionKeyPair: ECKeyPair?, var members: List<ByteString>, var admins: List<ByteString>, var expirationTimer: Int) : Kind() {
            internal constructor() : this(ByteString.EMPTY, "", null, listOf(), listOf(), 0)
        }
        /** An encryption key pair encrypted for each member individually.
         *
         * **Note:** `publicKey` is only set when an encryption key pair is sent in a one-to-one context (i.e. not in a group).
         */
        class EncryptionKeyPair(var publicKey: ByteString?, var wrappers: Collection<KeyPairWrapper>) : Kind() {
            internal constructor() : this(null, listOf())
        }
        class NameChange(var name: String) : Kind() {
            internal constructor() : this("")
        }
        class MembersAdded(var members: List<ByteString>) : Kind() {
            internal constructor() : this(listOf())
        }
        class MembersRemoved(var members: List<ByteString>) : Kind() {
            internal constructor() : this(listOf())
        }
        class MemberLeft() : Kind()

        val description: String =
            when (this) {
                is New -> "new"
                is EncryptionKeyPair -> "encryptionKeyPair"
                is NameChange -> "nameChange"
                is MembersAdded -> "membersAdded"
                is MembersRemoved -> "membersRemoved"
                is MemberLeft -> "memberLeft"
            }
    }

    companion object {
        const val TAG = "ClosedGroupControlMessage"

        fun fromProto(proto: SignalServiceProtos.Content): ClosedGroupControlMessage? {
            if (!proto.hasDataMessage() || !proto.dataMessage.hasClosedGroupControlMessage()) return null
            val closedGroupControlMessageProto = proto.dataMessage!!.closedGroupControlMessage!!
            val kind: Kind
            when (closedGroupControlMessageProto.type!!) {
                DataMessage.ClosedGroupControlMessage.Type.NEW -> {
                    val publicKey = closedGroupControlMessageProto.publicKey ?: return null
                    val name = closedGroupControlMessageProto.name ?: return null
                    val encryptionKeyPairAsProto = closedGroupControlMessageProto.encryptionKeyPair ?: return null
                    val expirationTimer = closedGroupControlMessageProto.expirationTimer
                    try {
                        val encryptionKeyPair = ECKeyPair(DjbECPublicKey(encryptionKeyPairAsProto.publicKey.toByteArray()),
                            DjbECPrivateKey(encryptionKeyPairAsProto.privateKey.toByteArray()))
                        kind = Kind.New(publicKey, name, encryptionKeyPair, closedGroupControlMessageProto.membersList, closedGroupControlMessageProto.adminsList, expirationTimer)
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't parse key pair from proto: $encryptionKeyPairAsProto.")
                        return null
                    }
                }
                DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR -> {
                    val publicKey = closedGroupControlMessageProto.publicKey
                    val wrappers = closedGroupControlMessageProto.wrappersList.mapNotNull { KeyPairWrapper.fromProto(it) }
                    kind = Kind.EncryptionKeyPair(publicKey, wrappers)
                }
                DataMessage.ClosedGroupControlMessage.Type.NAME_CHANGE -> {
                    val name = closedGroupControlMessageProto.name ?: return null
                    kind = Kind.NameChange(name)
                }
                DataMessage.ClosedGroupControlMessage.Type.MEMBERS_ADDED -> {
                    kind = Kind.MembersAdded(closedGroupControlMessageProto.membersList)
                }
                DataMessage.ClosedGroupControlMessage.Type.MEMBERS_REMOVED -> {
                    kind = Kind.MembersRemoved(closedGroupControlMessageProto.membersList)
                }
                DataMessage.ClosedGroupControlMessage.Type.MEMBER_LEFT -> {
                    kind = Kind.MemberLeft()
                }
            }
            return ClosedGroupControlMessage(kind)
                    .copyExpiration(proto)
        }
    }

    internal constructor(kind: Kind?, groupID: String? = null) : this() {
        this.kind = kind
        this.groupID = groupID
    }

    override fun toProto(): SignalServiceProtos.Content? {
        val kind = kind
        if (kind == null) {
            Log.w(TAG, "Couldn't construct closed group control message proto from: $this.")
            return null
        }
        try {
            val closedGroupControlMessage: DataMessage.ClosedGroupControlMessage.Builder = DataMessage.ClosedGroupControlMessage.newBuilder()
            when (kind) {
                is Kind.New -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.NEW
                    closedGroupControlMessage.publicKey = kind.publicKey
                    closedGroupControlMessage.name = kind.name
                    closedGroupControlMessage.encryptionKeyPair = SignalServiceProtos.KeyPair.newBuilder().also {
                        it.publicKey = ByteString.copyFrom(kind.encryptionKeyPair!!.publicKey.serialize().removingIdPrefixIfNeeded())
                        it.privateKey = ByteString.copyFrom(kind.encryptionKeyPair!!.privateKey.serialize())
                    }.build()
                    closedGroupControlMessage.addAllMembers(kind.members)
                    closedGroupControlMessage.addAllAdmins(kind.admins)
                    closedGroupControlMessage.expirationTimer = kind.expirationTimer
                }
                is Kind.EncryptionKeyPair -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.ENCRYPTION_KEY_PAIR
                    closedGroupControlMessage.publicKey = kind.publicKey ?: ByteString.EMPTY
                    closedGroupControlMessage.addAllWrappers(kind.wrappers.map { it.toProto() })
                }
                is Kind.NameChange -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.NAME_CHANGE
                    closedGroupControlMessage.name = kind.name
                }
                is Kind.MembersAdded -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.MEMBERS_ADDED
                    closedGroupControlMessage.addAllMembers(kind.members)
                }
                is Kind.MembersRemoved -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.MEMBERS_REMOVED
                    closedGroupControlMessage.addAllMembers(kind.members)
                }
                is Kind.MemberLeft -> {
                    closedGroupControlMessage.type = DataMessage.ClosedGroupControlMessage.Type.MEMBER_LEFT
                }
            }
            return SignalServiceProtos.Content.newBuilder().apply {
                dataMessage = DataMessage.newBuilder().also {
                    it.closedGroupControlMessage = closedGroupControlMessage.build()
                    it.setGroupContext()
                }.build()
                // Expiration timer
                applyExpiryMode()
            }.build()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't construct closed group control message proto from: $this.")
            return null
        }
    }

    class KeyPairWrapper(val publicKey: String?, val encryptedKeyPair: ByteString?) {

        val isValid: Boolean = run {
            this.publicKey != null && this.encryptedKeyPair != null
        }

        companion object {

            fun fromProto(proto: DataMessage.ClosedGroupControlMessage.KeyPairWrapper): KeyPairWrapper {
                return KeyPairWrapper(proto.publicKey.toByteArray().toHexString(), proto.encryptedKeyPair)
            }
        }

        fun toProto(): DataMessage.ClosedGroupControlMessage.KeyPairWrapper? {
            val result = DataMessage.ClosedGroupControlMessage.KeyPairWrapper.newBuilder()
            result.publicKey = ByteString.copyFrom(Hex.fromStringCondensed(publicKey ?: return null))
            result.encryptedKeyPair = encryptedKeyPair ?: return null
            return try {
                result.build()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't construct key pair wrapper proto from: $this")
                return null
            }
        }
    }
}
