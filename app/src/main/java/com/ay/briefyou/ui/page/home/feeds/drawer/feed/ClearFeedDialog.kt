package com.ay.briefyou.ui.page.home.feeds.drawer.feed

import com.ay.briefyou.brief.ui.DialogConfirmButton

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ay.briefyou.R
import com.ay.briefyou.ui.component.base.BYDialog
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.ext.showToast

@Composable
fun ClearFeedDialog(
    feedName: String,
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    onConfirm: () -> Unit = {}
) {
    val context = LocalContext.current
    val feedOptionUiState = feedOptionViewModel.feedOptionUiState.collectAsStateValue()
    val scope = rememberCoroutineScope()
    val toastString = stringResource(R.string.clear_articles_in_feed_toast, feedName)

    BYDialog(
        danger = true, // Brief You: red icon and button
        visible = feedOptionUiState.clearDialogVisible,
        onDismissRequest = {
            feedOptionViewModel.hideClearDialog()
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(R.string.clear_articles),
            )
        },
        title = {
            Text(text = stringResource(R.string.clear_articles))
        },
        text = {
            Text(text = stringResource(R.string.clear_articles_feed_tips, feedName))
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = {
                    feedOptionViewModel.clearFeed {
                        feedOptionViewModel.hideClearDialog()
                        onConfirm()
                        context.showToast(toastString)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.clear),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    feedOptionViewModel.hideClearDialog()
                }
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                )
            }
        },
    )
}
