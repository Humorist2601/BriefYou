package me.ash.reader.brief.update

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ash.reader.R

/**
 * Whether the app looks for a new version when it starts (on by default). Tapping the version in
 * Tips & support still checks whenever you ask.
 */
object UpdateCheck {
    private const val PREFS = "update_check"
    private const val KEY = "on_start"

    fun onStart(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setOnStart(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}

/** The switch, under the version in Tips & support. */
@Composable
fun UpdateCheckSetting() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(UpdateCheck.onStart(context)) }
    val toggle = {
        on = !on
        UpdateCheck.setOnStart(context, on)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { toggle() }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.update_check_on_start),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.update_check_on_start_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        Switch(checked = on, onCheckedChange = { toggle() })
    }
}
