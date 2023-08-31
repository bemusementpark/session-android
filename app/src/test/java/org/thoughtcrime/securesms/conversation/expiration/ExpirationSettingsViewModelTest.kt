package org.thoughtcrime.securesms.conversation.expiration

import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import network.loki.messenger.libsession_util.util.ExpiryMode
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.session.libsession.messaging.messages.ExpirationConfiguration
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupRecord
import org.session.libsession.utilities.GroupUtil.CLOSED_GROUP_PREFIX
import org.session.libsession.utilities.SSKEnvironment
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.MainCoroutineRule
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class ExpirationSettingsViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    val application: Application = mock(Application::class.java)
    val textSecurePreferences: TextSecurePreferences = mock(TextSecurePreferences::class.java)
    val messageExpirationManager: SSKEnvironment.MessageExpirationManagerProtocol = mock(SSKEnvironment.MessageExpirationManagerProtocol::class.java)
    val threadDb: ThreadDatabase = mock(ThreadDatabase::class.java)
    val groupDb: GroupDatabase = mock(GroupDatabase::class.java)
    val storage: Storage = mock(Storage::class.java)

    val recipient = mock(Recipient::class.java)

    val groupRecord = mock(GroupRecord::class.java)
    val optionalGroupRecord = Optional.of(groupRecord)

    @Test
    fun `UI should show a list of times and an Off option`() = runTest {
        val threadId = 1L

        val expirationConfig = ExpirationConfiguration(
            threadId = threadId,
            expiryMode = ExpiryMode.AfterSend(12.hours.inWholeSeconds),
            updatedTimestampMs = 0
        )
        whenever(threadDb.getRecipientForThreadId(Mockito.anyLong())).thenReturn(recipient)
        whenever(storage.getExpirationConfiguration(Mockito.anyLong())).thenReturn(expirationConfig)

        val address = Address.fromSerialized("${CLOSED_GROUP_PREFIX}94198734289")

        whenever(recipient.isClosedGroupRecipient).thenReturn(true)
        whenever(recipient.address).thenReturn(address)

        whenever(groupDb.getGroup(Mockito.anyString())).thenReturn(optionalGroupRecord)

        val viewModel = createViewModel()

        advanceUntilIdle()

        val state = viewModel.state.value

        MatcherAssert.assertThat(
            state.isGroup,
            CoreMatchers.equalTo(true)
        )

        MatcherAssert.assertThat(
            viewModel.uiState.value.cards.count(),
            CoreMatchers.equalTo(1)
        )

        MatcherAssert.assertThat(
            viewModel.uiState.value.cards[0].options.count(),
            CoreMatchers.equalTo(6)
        )
    }

    private fun createViewModel(isNewConfigEnabled: Boolean = true) = ExpirationSettingsViewModel(
        1L,
        application,
        textSecurePreferences,
        messageExpirationManager,
        threadDb,
        groupDb,
        storage,
        isNewConfigEnabled
    )
}