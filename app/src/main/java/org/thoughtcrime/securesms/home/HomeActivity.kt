package org.thoughtcrime.securesms.home

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.R
import network.loki.messenger.databinding.ActivityHomeBinding
import network.loki.messenger.libsession_util.ConfigBase
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.GroupUtil
import org.session.libsession.utilities.PreferencesFlow
import org.session.libsession.utilities.ProfilePictureModifiedEvent
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.HAS_HIDDEN_MESSAGE_REQUESTS
import org.session.libsession.utilities.TextSecurePreferences.Companion.HAS_RECEIVED_LEGACY_CONFIG
import org.session.libsession.utilities.TextSecurePreferences.Companion.PROFILE_NAME_PREF
import org.session.libsession.utilities.get
import org.session.libsession.utilities.groupByNotNull
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.set
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.ThreadUtils
import org.session.libsignal.utilities.toHexString
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.start.NewConversationFragment
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.utilities.NotificationUtils
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.database.GroupDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.Storage
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter
import org.thoughtcrime.securesms.home.search.GlobalSearchInputLayout
import org.thoughtcrime.securesms.home.search.GlobalSearchViewModel
import org.thoughtcrime.securesms.messagerequests.MessageRequestsActivity
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.notifications.PushRegistry
import org.thoughtcrime.securesms.recoverypassword.RecoveryPasswordActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.preferences.SettingsActivity
import org.thoughtcrime.securesms.showMuteDialog
import org.thoughtcrime.securesms.showSessionDialog
import org.thoughtcrime.securesms.ui.Divider
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.SessionShieldIcon
import org.thoughtcrime.securesms.ui.base
import org.thoughtcrime.securesms.ui.components.SlimOutlineButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.h4
import org.thoughtcrime.securesms.ui.h8
import org.thoughtcrime.securesms.ui.setThemedContent
import org.thoughtcrime.securesms.ui.small
import org.thoughtcrime.securesms.util.ConfigurationMessageUtilities
import org.thoughtcrime.securesms.util.IP2Country
import org.thoughtcrime.securesms.util.disableClipping
import org.thoughtcrime.securesms.util.push
import org.thoughtcrime.securesms.util.show
import org.thoughtcrime.securesms.util.start
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : PassphraseRequiredActionBarActivity(),
    ConversationClickListener,
    GlobalSearchInputLayout.GlobalSearchInputLayoutListener {

    companion object {
        const val FROM_ONBOARDING = "HomeActivity_FROM_ONBOARDING"
    }

    private lateinit var binding: ActivityHomeBinding
    private lateinit var glide: GlideRequests

    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var mmsSmsDatabase: MmsSmsDatabase
    @Inject lateinit var recipientDatabase: RecipientDatabase
    @Inject lateinit var storage: Storage
    @Inject lateinit var groupDatabase: GroupDatabase
    @Inject lateinit var textSecurePreferences: TextSecurePreferences
    @Inject lateinit var prefs: SharedPreferences
    @Inject lateinit var prefsFlow: PreferencesFlow
    @Inject lateinit var configFactory: ConfigFactory
    @Inject lateinit var pushRegistry: PushRegistry

    private val globalSearchViewModel by viewModels<GlobalSearchViewModel>()
    private val homeViewModel by viewModels<HomeViewModel>()

    private val publicKey: String
        get() = textSecurePreferences.getLocalNumber()!!

    private val homeAdapter: HomeAdapter by lazy {
        HomeAdapter(context = this, configFactory = configFactory, listener = this, ::showMessageRequests, ::hideMessageRequests)
    }

    private val globalSearchAdapter = GlobalSearchAdapter { model ->
        when (model) {
            is GlobalSearchAdapter.Model.Message -> {
                val threadId = model.messageResult.threadId
                val timestamp = model.messageResult.sentTimestampMs
                val author = model.messageResult.messageRecipient.address

                val intent = Intent(this, ConversationActivityV2::class.java)
                intent.putExtra(ConversationActivityV2.THREAD_ID, threadId)
                intent.putExtra(ConversationActivityV2.SCROLL_MESSAGE_ID, timestamp)
                intent.putExtra(ConversationActivityV2.SCROLL_MESSAGE_AUTHOR, author)
                push(intent)
            }
            is GlobalSearchAdapter.Model.SavedMessages -> {
                val intent = Intent(this, ConversationActivityV2::class.java)
                intent.putExtra(ConversationActivityV2.ADDRESS, Address.fromSerialized(model.currentUserPublicKey))
                push(intent)
            }
            is GlobalSearchAdapter.Model.Contact -> {
                val address = model.contact.sessionID

                val intent = Intent(this, ConversationActivityV2::class.java)
                intent.putExtra(ConversationActivityV2.ADDRESS, Address.fromSerialized(address))
                push(intent)
            }
            is GlobalSearchAdapter.Model.GroupConversation -> {
                val groupAddress = Address.fromSerialized(model.groupRecord.encodedId)
                val threadId = threadDb.getThreadIdIfExistsFor(Recipient.from(this, groupAddress, false))
                if (threadId >= 0) {
                    val intent = Intent(this, ConversationActivityV2::class.java)
                    intent.putExtra(ConversationActivityV2.THREAD_ID, threadId)
                    push(intent)
                }
            }
            else -> {
                Log.d("Loki", "callback with model: $model")
            }
        }
    }

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        // Set content view
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Set custom toolbar
        setSupportActionBar(binding.toolbar)
        // Set up Glide
        glide = GlideApp.with(this)
        // Set up toolbar buttons
        binding.profileButton.setOnClickListener { openSettings() }
        binding.searchViewContainer.setOnClickListener {
            globalSearchViewModel.refresh()
            binding.globalSearchInputLayout.requestFocus()
        }
        binding.sessionToolbar.disableClipping()
        // Set up seed reminder view
        lifecycleScope.launchWhenStarted {
            binding.seedReminderView.setThemedContent {
                if (!textSecurePreferences.getHasViewedSeed()) SeedReminder { start<RecoveryPasswordActivity>() }
            }
        }
        // Set up recycler view
        binding.globalSearchInputLayout.listener = this
        homeAdapter.setHasStableIds(true)
        homeAdapter.glide = glide
        binding.recyclerView.adapter = homeAdapter
        binding.globalSearchRecycler.adapter = globalSearchAdapter

        binding.configOutdatedView.setOnClickListener {
            prefs[HAS_RECEIVED_LEGACY_CONFIG] = false
            updateLegacyConfigView()
        }

        // Set up empty state view
        binding.emptyStateContainer.setThemedContent {
            EmptyView(ApplicationContext.getInstance(this).newAccount)
        }

        IP2Country.configureIfNeeded(this@HomeActivity)

        // Set up new conversation button
        binding.newConversationButton.setOnClickListener { showNewConversation() }
        // Observe blocked contacts changed events

        // subscribe to outdated config updates, this should be removed after long enough time for device migration
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefsFlow[HAS_RECEIVED_LEGACY_CONFIG].filter { it }.collect {
                    updateLegacyConfigView()
                }
            }
        }

        // Subscribe to threads and update the UI
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.data
                    .filterNotNull() // We don't actually want the null value here as it indicates a loading state (maybe we need a loading state?)
                    .collectLatest { data ->
                        val manager = binding.recyclerView.layoutManager as LinearLayoutManager
                        val firstPos = manager.findFirstCompletelyVisibleItemPosition()
                        val offsetTop = if(firstPos >= 0) {
                            manager.findViewByPosition(firstPos)?.let { view ->
                                manager.getDecoratedTop(view) - manager.getTopDecorationHeight(view)
                            } ?: 0
                        } else 0
                        homeAdapter.data = data
                        if(firstPos >= 0) { manager.scrollToPositionWithOffset(firstPos, offsetTop) }
                        updateEmptyState()
                    }
            }
        }

        lifecycleScope.launchWhenStarted {
            launch(Dispatchers.IO) {
                // Double check that the long poller is up
                (applicationContext as ApplicationContext).startPollingIfNeeded()
                // update things based on TextSecurePrefs (profile info etc)
                // Set up remaining components if needed
                pushRegistry.refresh(false)
                if (textSecurePreferences.getLocalNumber() != null) {
                    OpenGroupManager.startPolling()
                    JobQueue.shared.resumePendingJobs()
                }

                withContext(Dispatchers.Main) {
                    updateProfileButton()
                    prefsFlow[PROFILE_NAME_PREF].collect {
                        updateProfileButton()
                    }
                }
            }

            // monitor the global search VM query
            launch {
                binding.globalSearchInputLayout.query
                    .collect(globalSearchViewModel::setQuery)
            }
            // Get group results and display them
            launch {
                globalSearchViewModel.result.collect { result ->
                    if (result.query.isEmpty()) {
                        val hasNames = result.contacts.filter { it.nickname != null || it.name != null }
                            .groupByNotNull { (it.nickname?.firstOrNull() ?: it.name?.firstOrNull())?.uppercase() }
                            .toSortedMap(compareBy { it })
                            .flatMap { (key, contacts) -> listOf(GlobalSearchAdapter.Model.SubHeader(key)) + contacts.sortedBy { it.nickname ?: it.name }.map(GlobalSearchAdapter.Model::Contact) }

                        val noNames = result.contacts.filter { it.nickname == null && it.name == null }
                            .sortedBy { it.sessionID }
                            .map { GlobalSearchAdapter.Model.Contact(it) }

                        buildList {
                            add(GlobalSearchAdapter.Model.Header(R.string.contacts))
                            add(GlobalSearchAdapter.Model.SavedMessages(publicKey))
                            addAll(hasNames)
                            noNames.takeIf { it.isNotEmpty() }?.let {
                                add(GlobalSearchAdapter.Model.Header(R.string.unknown))
                                addAll(it)
                            }
                        }
                    } else {
                        val currentUserPublicKey = publicKey
                        val contactAndGroupList = result.contacts.map(GlobalSearchAdapter.Model::Contact) +
                            result.threads.map(GlobalSearchAdapter.Model::GroupConversation)

                        val contactResults = contactAndGroupList.toMutableList()

                        if (contactResults.isEmpty()) {
                            contactResults.add(GlobalSearchAdapter.Model.SavedMessages(currentUserPublicKey))
                        }

                        val userIndex = contactResults.indexOfFirst { it is GlobalSearchAdapter.Model.Contact && it.contact.sessionID == currentUserPublicKey }
                        if (userIndex >= 0) {
                            contactResults[userIndex] = GlobalSearchAdapter.Model.SavedMessages(currentUserPublicKey)
                        }

                        if (contactResults.isNotEmpty()) {
                            contactResults.add(0, GlobalSearchAdapter.Model.Header(R.string.conversations))
                        }

                        val unreadThreadMap = result.messages
                            .map { it.threadId }.toSet()
                            .associateWith { mmsSmsDatabase.getUnreadCount(it) }

                        val messageResults: MutableList<GlobalSearchAdapter.Model> = result.messages
                            .map { GlobalSearchAdapter.Model.Message(it, unreadThreadMap[it.threadId] ?: 0) }
                            .toMutableList()

                        if (messageResults.isNotEmpty()) {
                            messageResults.add(0, GlobalSearchAdapter.Model.Header(R.string.global_search_messages))
                        }

                        contactResults + messageResults
                    }.let { globalSearchAdapter.setNewData(result.query, it) }
                }
            }
        }
        EventBus.getDefault().register(this@HomeActivity)
        if (intent.hasExtra(FROM_ONBOARDING)
            && intent.getBooleanExtra(FROM_ONBOARDING, false)) {
            if (Build.VERSION.SDK_INT >= 33 &&
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled().not()) {
                Permissions.with(this)
                    .request(Manifest.permission.POST_NOTIFICATIONS)
                    .execute()
            }
            configFactory.user
                ?.takeUnless { it.isBlockCommunityMessageRequestsSet() }
                ?.setCommunityMessageRequests(false)
        }
    }

    override fun onInputFocusChanged(hasFocus: Boolean) {
        setSearchShown(hasFocus || binding.globalSearchInputLayout.query.value.isNotEmpty())
    }

    private fun setSearchShown(isShown: Boolean) {
        binding.searchToolbar.isVisible = isShown
        binding.sessionToolbar.isVisible = !isShown
        binding.recyclerView.isVisible = !isShown
        binding.emptyStateContainer.isVisible = (binding.recyclerView.adapter as HomeAdapter).itemCount == 0 && binding.recyclerView.isVisible
        binding.seedReminderView.isVisible = !TextSecurePreferences.getHasViewedSeed(this) && !isShown
        binding.globalSearchRecycler.isInvisible = !isShown
        binding.newConversationButton.isVisible = !isShown
    }

    private fun updateLegacyConfigView() {
        binding.configOutdatedView.isVisible = ConfigBase.isNewConfigEnabled(textSecurePreferences.hasForcedNewConfig(), SnodeAPI.nowWithOffset)
                && prefs[HAS_RECEIVED_LEGACY_CONFIG]
    }

    override fun onResume() {
        super.onResume()
        ApplicationContext.getInstance(this).messageNotifier.setHomeScreenVisible(true)
        if (textSecurePreferences.getLocalNumber() == null) { return; } // This can be the case after a secondary device is auto-cleared
        IdentityKeyUtil.checkUpdate(this)
        binding.profileButton.recycle() // clear cached image before update tje profilePictureView
        binding.profileButton.update()
        if (textSecurePreferences.getHasViewedSeed()) {
            binding.seedReminderView.isVisible = false
        }

        updateLegacyConfigView()

        // TODO: remove this after enough updates that we can rely on ConfigBase.isNewConfigEnabled to always return true
        // This will only run if we aren't using new configs, as they are schedule to sync when there are changes applied
        if (textSecurePreferences.getConfigurationMessageSynced()) {
            lifecycleScope.launch(Dispatchers.IO) {
                ConfigurationMessageUtilities.syncConfigurationIfNeeded(this@HomeActivity)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        ApplicationContext.getInstance(this).messageNotifier.setHomeScreenVisible(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }
    // endregion

    // region Updating
    private fun updateEmptyState() {
        val threadCount = (binding.recyclerView.adapter)!!.itemCount
        binding.emptyStateContainer.isVisible = threadCount == 0 && binding.recyclerView.isVisible
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUpdateProfileEvent(event: ProfilePictureModifiedEvent) {
        if (event.recipient.isLocalNumber) {
            updateProfileButton()
        } else {
            homeViewModel.tryReload()
        }
    }

    private fun updateProfileButton() {
        binding.profileButton.publicKey = publicKey
        binding.profileButton.displayName = textSecurePreferences.getProfileName()
        binding.profileButton.recycle()
        binding.profileButton.update()
    }
    // endregion

    // region Interaction
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.globalSearchRecycler.isVisible) binding.globalSearchInputLayout.clearSearch(true)
        else super.onBackPressed()
    }

    override fun onConversationClick(thread: ThreadRecord) {
        val intent = Intent(this, ConversationActivityV2::class.java)
        intent.putExtra(ConversationActivityV2.THREAD_ID, thread.threadId)
        push(intent)
    }

    override fun onLongConversationClick(thread: ThreadRecord) {
        val bottomSheet = ConversationOptionsBottomSheet(this)
        bottomSheet.thread = thread
        bottomSheet.onViewDetailsTapped = {
            bottomSheet.dismiss()
            val userDetailsBottomSheet = UserDetailsBottomSheet()
            val bundle = bundleOf(
                    UserDetailsBottomSheet.ARGUMENT_PUBLIC_KEY to thread.recipient.address.toString(),
                    UserDetailsBottomSheet.ARGUMENT_THREAD_ID to thread.threadId
            )
            userDetailsBottomSheet.arguments = bundle
            userDetailsBottomSheet.show(supportFragmentManager, userDetailsBottomSheet.tag)
        }
        bottomSheet.onCopyConversationId = onCopyConversationId@{
            bottomSheet.dismiss()
            if (!thread.recipient.isGroupRecipient && !thread.recipient.isLocalNumber) {
                val clip = ClipData.newPlainText("Session ID", thread.recipient.address.toString())
                val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(clip)
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            else if (thread.recipient.isCommunityRecipient) {
                val threadId = threadDb.getThreadIdIfExistsFor(thread.recipient)
                val openGroup = DatabaseComponent.get(this@HomeActivity).lokiThreadDatabase().getOpenGroupChat(threadId) ?: return@onCopyConversationId Unit

                val clip = ClipData.newPlainText("Community URL", openGroup.joinURL)
                val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(clip)
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
        }
        bottomSheet.onBlockTapped = {
            bottomSheet.dismiss()
            if (!thread.recipient.isBlocked) {
                blockConversation(thread)
            }
        }
        bottomSheet.onUnblockTapped = {
            bottomSheet.dismiss()
            if (thread.recipient.isBlocked) {
                unblockConversation(thread)
            }
        }
        bottomSheet.onDeleteTapped = {
            bottomSheet.dismiss()
            deleteConversation(thread)
        }
        bottomSheet.onSetMuteTapped = { muted ->
            bottomSheet.dismiss()
            setConversationMuted(thread, muted)
        }
        bottomSheet.onNotificationTapped = {
            bottomSheet.dismiss()
            NotificationUtils.showNotifyDialog(this, thread.recipient) { notifyType ->
                setNotifyType(thread, notifyType)
            }
        }
        bottomSheet.onPinTapped = {
            bottomSheet.dismiss()
            setConversationPinned(thread.threadId, true)
        }
        bottomSheet.onUnpinTapped = {
            bottomSheet.dismiss()
            setConversationPinned(thread.threadId, false)
        }
        bottomSheet.onMarkAllAsReadTapped = {
            bottomSheet.dismiss()
            markAllAsRead(thread)
        }
        bottomSheet.show(supportFragmentManager, bottomSheet.tag)
    }

    private fun blockConversation(thread: ThreadRecord) {
        showSessionDialog {
            title(R.string.RecipientPreferenceActivity_block_this_contact_question)
            text(R.string.RecipientPreferenceActivity_you_will_no_longer_receive_messages_and_calls_from_this_contact)
            button(R.string.RecipientPreferenceActivity_block) {
                lifecycleScope.launch(Dispatchers.IO) {
                    storage.setBlocked(listOf(thread.recipient), true)

                    withContext(Dispatchers.Main) {
                        binding.recyclerView.adapter!!.notifyDataSetChanged()
                    }
                }
            }
            cancelButton()
        }
    }

    private fun unblockConversation(thread: ThreadRecord) {
        showSessionDialog {
            title(R.string.RecipientPreferenceActivity_unblock_this_contact_question)
            text(R.string.RecipientPreferenceActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact)
            button(R.string.RecipientPreferenceActivity_unblock) {
                lifecycleScope.launch(Dispatchers.IO) {
                    storage.setBlocked(listOf(thread.recipient), false)

                    withContext(Dispatchers.Main) {
                        binding.recyclerView.adapter!!.notifyDataSetChanged()
                    }
                }
            }
            cancelButton()
        }
    }

    private fun setConversationMuted(thread: ThreadRecord, isMuted: Boolean) {
        if (!isMuted) {
            lifecycleScope.launch(Dispatchers.IO) {
                recipientDatabase.setMuted(thread.recipient, 0)
                withContext(Dispatchers.Main) {
                    binding.recyclerView.adapter!!.notifyDataSetChanged()
                }
            }
        } else {
            showMuteDialog(this) { until ->
                lifecycleScope.launch(Dispatchers.IO) {
                    recipientDatabase.setMuted(thread.recipient, until)
                    withContext(Dispatchers.Main) {
                        binding.recyclerView.adapter!!.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun setNotifyType(thread: ThreadRecord, newNotifyType: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            recipientDatabase.setNotifyType(thread.recipient, newNotifyType)
            withContext(Dispatchers.Main) {
                binding.recyclerView.adapter!!.notifyDataSetChanged()
            }
        }
    }

    private fun setConversationPinned(threadId: Long, pinned: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            storage.setPinned(threadId, pinned)
            homeViewModel.tryReload()
        }
    }

    private fun markAllAsRead(thread: ThreadRecord) {
        ThreadUtils.queue {
            MessagingModuleConfiguration.shared.storage.markConversationAsRead(thread.threadId, SnodeAPI.nowWithOffset)
        }
    }

    private fun deleteConversation(thread: ThreadRecord) {
        val threadID = thread.threadId
        val recipient = thread.recipient
        val message = if (recipient.isGroupRecipient) {
            val group = groupDatabase.getGroup(recipient.address.toString()).orNull()
            if (group != null && group.admins.map { it.toString() }.contains(textSecurePreferences.getLocalNumber())) {
                getString(R.string.admin_group_leave_warning)
            } else {
                resources.getString(R.string.activity_home_leave_group_dialog_message)
            }
        } else {
            resources.getString(R.string.activity_home_delete_conversation_dialog_message)
        }

        showSessionDialog {
            text(message)
            button(R.string.yes) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val context = this@HomeActivity
                    // Cancel any outstanding jobs
                    DatabaseComponent.get(context).sessionJobDatabase().cancelPendingMessageSendJobs(threadID)
                    // Send a leave group message if this is an active closed group
                    if (recipient.address.isClosedGroup && DatabaseComponent.get(context).groupDatabase().isActive(recipient.address.toGroupString())) {
                        try {
                            GroupUtil.doubleDecodeGroupID(recipient.address.toString()).toHexString()
                                .takeIf(DatabaseComponent.get(context).lokiAPIDatabase()::isClosedGroup)
                                ?.let { MessageSender.explicitLeave(it, false) }
                        } catch (_: IOException) {
                        }
                    }
                    // Delete the conversation
                    val v2OpenGroup = DatabaseComponent.get(context).lokiThreadDatabase().getOpenGroupChat(threadID)
                    if (v2OpenGroup != null) {
                        v2OpenGroup.apply { OpenGroupManager.delete(server, room, context) }
                    } else {
                        lifecycleScope.launch(Dispatchers.IO) {
                            threadDb.deleteConversation(threadID)
                        }
                    }
                    // Update the badge count
                    ApplicationContext.getInstance(context).messageNotifier.updateNotification(context)
                    // Notify the user
                    val toastMessage = if (recipient.isGroupRecipient) R.string.MessageRecord_left_group else R.string.activity_home_conversation_deleted_message
                    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
                }
            }
            button(R.string.no)
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        show(intent, isForResult = true)
    }

    private fun showMessageRequests() {
        val intent = Intent(this, MessageRequestsActivity::class.java)
        push(intent)
    }

    private fun hideMessageRequests() {
        showSessionDialog {
            text(getString(R.string.hide_message_requests))
            button(R.string.yes) {
                prefs[HAS_HIDDEN_MESSAGE_REQUESTS] = true
                homeViewModel.tryReload()
            }
            button(R.string.no)
        }
    }

    private fun showNewConversation() {
        NewConversationFragment().show(supportFragmentManager, "NewConversationFragment")
    }
}

fun Context.startHomeActivity() {
    Intent(this, HomeActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(HomeActivity.FROM_ONBOARDING, true)
    }.also(::startActivity)
}
