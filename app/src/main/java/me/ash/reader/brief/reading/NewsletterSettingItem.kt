package me.ash.reader.brief.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.ash.reader.R
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.page.settings.SettingItem

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
        RYSwitch(activated = on) { toggle() }
    }
}
