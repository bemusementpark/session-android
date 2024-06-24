package org.thoughtcrime.securesms.onboarding.loading

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.TextSecurePreferences.Companion.LAST_CONFIGURATION_SYNC_TIME
import org.session.libsession.utilities.set
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.BaseActionBarActivity
import org.thoughtcrime.securesms.dependencies.ConfigFactory
import org.thoughtcrime.securesms.home.startHomeActivity
import org.thoughtcrime.securesms.onboarding.pickname.startPickDisplayNameActivity
import org.thoughtcrime.securesms.ui.setComposeContent
import org.thoughtcrime.securesms.util.setUpActionBarSessionLogo
import javax.inject.Inject

@AndroidEntryPoint
class LoadingActivity: BaseActionBarActivity() {

    @Inject
    internal lateinit var configFactory: ConfigFactory

    @Inject
    internal lateinit var prefs: SharedPreferences

    private val viewModel: LoadingViewModel by viewModels()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        return
    }

    private fun register(loadFailed: Boolean) {
        prefs[LAST_CONFIGURATION_SYNC_TIME] = System.currentTimeMillis()

        when {
            loadFailed -> startPickDisplayNameActivity(
                loadFailed = true,
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            else -> startHomeActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ApplicationContext.getInstance(this).newAccount = false

        setComposeContent {
            val state by viewModel.states.collectAsState()
            LoadingScreen(state)
        }

        setUpActionBarSessionLogo(true)

        lifecycleScope.launch {
            viewModel.events.collect {
                when (it) {
                    Event.TIMEOUT -> register(loadFailed = true)
                    Event.SUCCESS -> register(loadFailed = false)
                }
            }
        }
    }
}
