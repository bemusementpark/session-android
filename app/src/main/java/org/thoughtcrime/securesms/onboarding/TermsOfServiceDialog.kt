package org.thoughtcrime.securesms.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import network.loki.messenger.R
import org.thoughtcrime.securesms.showSessionDialog

fun Context.showTermsOfServiceDialog() = showSessionDialog {
    title(R.string.dialog_open_url_title)
    text(R.string.dialog_this_will_open_in_your_browser)
    button(R.string.dialog_terms_of_service) {
        openURL("https://getsession.org/terms-of-service/")
    }
    button(R.string.dialog_privacy_policy) {
        openURL("https://getsession.org/privacy-policy/")
    }
}

private fun Context.openURL(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
    }
}
