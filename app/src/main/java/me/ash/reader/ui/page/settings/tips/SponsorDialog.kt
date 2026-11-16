package me.ash.reader.ui.page.settings.tips

import androidx.compose.foundation.layout.Arrangement // Brief You
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface // Brief You
import androidx.compose.material3.rememberModalBottomSheetState // Brief You
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape // Brief You
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.brief.ui.Group // Brief You
import me.ash.reader.brief.ui.SheetTitle // Brief You
import me.ash.reader.brief.ui.groupShape // Brief You
import me.ash.reader.infrastructure.preference.OpenLinkPreference
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.ext.openURL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorDialog(modifier: Modifier = Modifier, onDismissRequest: () -> Unit) {
    // Brief You: the app's sheet design (title with a muted line, divider, a joined group), opening fully.
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SponsorDialogContent()
    }
}


private fun githubAvatar(login: String): String = "https://github.com/${login}.png"

@Composable
private fun SponsorDialogContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Brief You: 24 dp sides and the shared sheet head, the two people as one joined group.
    Column(modifier = modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        SheetTitle(stringResource(R.string.become_a_sponsor), stringResource(R.string.sponsor_desc))
        Column(verticalArrangement = Arrangement.spacedBy(Group.Gap)) {
            SponsorItem(
                model = githubAvatar("Ashinch"),
                name = "Ash",
                description = "Lead Developer",
                shape = groupShape(0, 2),
            ) {
                context.openURL("https://ash7.io/sponsor/", openLink = OpenLinkPreference.default)
            }
            SponsorItem(
                model = githubAvatar("JunkFood02"),
                name = "junkfood",
                description = "Maintainer",
                shape = groupShape(1, 2),
            ) {
                context.openURL(
                    "https://github.com/sponsors/JunkFood02",
                    openLink = OpenLinkPreference.default
                )
            }
        }
    }
}

@Composable
private fun SponsorItem(
    modifier: Modifier = Modifier,
    model: Any?,
    name: String,
    description: String,
    shape: Shape, // Brief You
    onClick: () -> Unit,
) {
    // Brief You: a row of the joined group; the whole row and its button open the page.
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RYAsyncImage(
                data = model,
                modifier = Modifier
                    .size(40.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            FilledTonalButton(onClick = onClick) {
                Text(stringResource(R.string.sponsor))
            }
        }
    }
}
