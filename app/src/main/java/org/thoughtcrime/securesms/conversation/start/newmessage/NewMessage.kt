package org.thoughtcrime.securesms.conversation.start.newmessage

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.LoadingArcOr
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.BorderlessButtonWithIcon
import org.thoughtcrime.securesms.ui.components.MaybeScanQrCode
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.contentDescription

private val TITLES = listOf(R.string.enter_account_id, R.string.qrScan)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NewMessage(
    state: State,
    errors: Flow<String> = emptyFlow(),
    callbacks: Callbacks = object: Callbacks {},
    onClose: () -> Unit = {},
    onBack: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    val pagerState = rememberPagerState { TITLES.size }

    Column(modifier = Modifier.background(LocalColors.current.backgroundSecondary)) {
        AppBar(stringResource(R.string.messageNew), onClose = onClose, onBack = onBack)
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(pagerState) {
            when (TITLES[it]) {
                R.string.enter_account_id -> EnterAccountId(state, callbacks, onHelp)
                R.string.qrScan -> MaybeScanQrCode(errors, onScan = callbacks::onScanQrCode)
            }
        }
    }
}

@Composable
private fun EnterAccountId(
    state: State,
    callbacks: Callbacks,
    onHelp: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(horizontal = LocalDimensions.current.marginExtraExtraSmall, vertical = LocalDimensions.current.marginExtraSmall)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.marginExtraSmall)
    ) {
        SessionOutlinedTextField(
            text = state.newMessageIdOrOns,
            modifier = Modifier
                .padding(horizontal = LocalDimensions.current.marginSmall)
                .contentDescription("Session id input box"),
            placeholder = stringResource(R.string.accountIdOrOnsEnter),
            onChange = callbacks::onChange,
            onContinue = callbacks::onContinue,
            error = state.error?.string(),
        )

        BorderlessButtonWithIcon(
            text = stringResource(R.string.messageNewDescription),
            iconRes = R.drawable.ic_circle_question_mark,
            contentColor = LocalColors.current.textSecondary,
            modifier = Modifier
                .animateContentSize()
                .contentDescription(R.string.AccessibilityId_help_desk_link)
                .padding(horizontal = LocalDimensions.current.marginMedium)
                .fillMaxWidth(),
        ) { onHelp() }

        OutlineButton(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = LocalDimensions.current.marginLarge)
                .fillMaxWidth()
                .contentDescription(R.string.next),
            color = LocalColors.current.primary,
            enabled = state.isNextButtonEnabled,
            onClick = { callbacks.onContinue() }
        ) {
            LoadingArcOr(state.loading) {
                Text(stringResource(R.string.next))
            }
        }
    }
}

@Preview
@Composable
private fun PreviewNewMessage(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        NewMessage(State())
    }
}