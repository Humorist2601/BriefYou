package com.ay.briefyou.brief.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ay.briefyou.R
import com.ay.briefyou.ui.component.base.BYSwitch
import com.ay.briefyou.ui.page.settings.SettingItem

/** Reading page: whether newsletters get the extra tidying (see [EmailContent.tidyMore]). */
@Composable
fun NewsletterSettingItem() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(EmailContent.tidyMore) }
    val toggle = {
        on = !on
        EmailContent.setTidyMore(context, on)
    }
    SettingItem(
        title = stringResource(R.string.tidy_newsletters),
        desc = stringResource(R.string.tidy_newsletters_desc),
        onClick = toggle,
    ) {
        BYSwitch(activated = on) { toggle() }
    }
}
