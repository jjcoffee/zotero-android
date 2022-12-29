@file:OptIn(ExperimentalPagerApi::class)

package org.zotero.android.login.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import org.zotero.android.architecture.ui.CustomLayoutSize
import org.zotero.android.login.LoginViewEffect.NavigateBack
import org.zotero.android.login.LoginViewEffect.NavigateToDashboard
import org.zotero.android.login.LoginViewModel
import org.zotero.android.login.LoginViewState
import org.zotero.android.uicomponents.CustomScaffold
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.button.PrimaryButton
import org.zotero.android.uicomponents.foundation.safeClickable
import org.zotero.android.uicomponents.misc.CustomDivider
import org.zotero.android.uicomponents.systemui.SolidStatusBar
import org.zotero.android.uicomponents.textinput.CustomTextField
import org.zotero.android.uicomponents.theme.CustomTheme
import org.zotero.android.uicomponents.topbar.HeadingTextButton
import org.zotero.android.uicomponents.topbar.NoIconTopBar

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun LoginScreen(
    onBack: () -> Unit,
    navigateToDashboard: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val layoutType = CustomLayoutSize.calculateLayoutType()
    val viewState by viewModel.viewStates.observeAsState(LoginViewState())
    val viewEffect by viewModel.viewEffects.observeAsState()
    LaunchedEffect(key1 = viewModel) {
        viewModel.init()
    }

    LaunchedEffect(key1 = viewEffect) {
        when (val consumedEffect = viewEffect?.consume()) {
            NavigateBack -> onBack()
            NavigateToDashboard -> navigateToDashboard()
            null -> Unit
        }
    }
    SolidStatusBar()

    CustomScaffold(
        topBar = {
            TopBar(
                onCancelClicked = onBack,
            )
        },
        snackbarMessage = viewState.snackbarMessage,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = CustomTheme.colors.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {

            Column(
                modifier = Modifier
                    .widthIn(max = 430.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                CustomTextField(
                    value = viewState.username,
                    hint = stringResource(id = Strings.login_username),
                    onValueChange = viewModel::onUsernameChanged
                )
                CustomDivider(modifier = Modifier.padding(vertical = 16.dp))
                CustomTextField(
                    value = viewState.password,
                    hint = stringResource(id = Strings.login_password),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    onValueChange = viewModel::onPasswordChanged
                )
                CustomDivider(modifier = Modifier.padding(vertical = 16.dp))
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(id = Strings.onboarding_sign_in),
                    onClick = viewModel::onSignInClicked,
                    isLoading = viewState.isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))
                val uriHandler = LocalUriHandler.current
                Text(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .safeClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                uriHandler.openUri("https://www.zotero.org/user/lostpassword?app=1")
                            }
                        ),
                    text = stringResource(id = Strings.login_forgot_password),
                    color = CustomTheme.colors.zoteroBlueWithDarkMode,
                    style = CustomTheme.typography.default,
                    fontSize = layoutType.calculateTextSize(),
                )
            }
        }
    }

}

@Composable
private fun TopBar(
    onCancelClicked: () -> Unit,
) {
    NoIconTopBar(
        title = "",
    ) {
        HeadingTextButton(
            onClick = onCancelClicked,
            text = stringResource(id = Strings.cancel),
            isEnabled = true,
            isLoading = false,
            modifier = Modifier
                .padding(end = 8.dp)
        )
    }
}