package org.zotero.android.screens.sortpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.zotero.android.uicomponents.CustomScaffold
import org.zotero.android.uicomponents.Drawables
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.foundation.safeClickable
import org.zotero.android.uicomponents.misc.NewDivider
import org.zotero.android.uicomponents.selector.MultiSelector
import org.zotero.android.uicomponents.selector.MultiSelectorOption
import org.zotero.android.uicomponents.theme.CustomTheme
import org.zotero.android.uicomponents.theme.CustomThemeWithStatusAndNavBars

@Composable
internal fun SortPickerScreen(
    onBack: () -> Unit,
    navigateToSinglePickerScreen: () -> Unit,
    viewModel: SortPickerViewModel = hiltViewModel(),
) {
    CustomThemeWithStatusAndNavBars(statusBarBackgroundColor = CustomTheme.colors.topBarBackgroundColor) {
        val viewState by viewModel.viewStates.observeAsState(SortPickerViewState())
        val viewEffect by viewModel.viewEffects.observeAsState()
        LaunchedEffect(key1 = viewModel) {
            viewModel.init()
        }

        LaunchedEffect(key1 = viewEffect) {
            when (viewEffect?.consume()) {
                null -> Unit
                is SortPickerViewEffect.OnBack -> {
                    onBack()
                }

                is SortPickerViewEffect.NavigateToSinglePickerScreen -> {
                    navigateToSinglePickerScreen()
                }
            }
        }
        CustomScaffold(
            topBar = {
                SortPickerTopBar(
                    onDone = viewModel::onDone,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .background(color = CustomTheme.colors.surface)
            ) {
                SortPickerDisplayFields(
                    sortByTitle = viewState.sortByTitle,
                    isAscending = viewState.isAscending,
                    onSortFieldClicked = viewModel::onSortFieldClicked,
                    onSortDirectionChanged = viewModel::onSortDirectionChanged,
                )
            }
        }
    }
}