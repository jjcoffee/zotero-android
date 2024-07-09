package org.zotero.android.screens.addnote

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zotero.android.androidx.content.getDrawableByItemType
import org.zotero.android.screens.addnote.data.AddOrEditNoteArgs
import org.zotero.android.uicomponents.Strings
import org.zotero.android.uicomponents.theme.CustomTheme
import org.zotero.android.uicomponents.topbar.NewCustomTopBarWithTitleContainer
import org.zotero.android.uicomponents.topbar.NewHeadingTextButton

@Composable
internal fun AddNoteTopBar(
    titleData: AddOrEditNoteArgs.TitleData?,
    onDoneClicked: () -> Unit,
) {
    NewCustomTopBarWithTitleContainer(
        titleContainerContent = { modifier ->
            Row(
                modifier = modifier.padding(start = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val type = titleData?.type
                if (type != null) {
                    Image(
                        painter = painterResource(id = LocalContext.current.getDrawableByItemType(type)),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                val title = titleData?.title
                if (title != null) {
                    Text(
                        text = title,
                        color = CustomTheme.colors.primaryContent,
                        style = CustomTheme.typography.h2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        rightContainerContent = listOf {
            NewHeadingTextButton(
                isEnabled = true,
                onClick = onDoneClicked,
                text = stringResource(Strings.done)
            )
        })
}