package com.daisychain.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daisychain.app.R
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.app.ui.library.colorFor
import com.daisychain.app.ui.library.iconFor

/** The shortcuts as a simple list to choose from (favourites first). */
@Composable
fun ShortcutPickList(shortcuts: List<ShortcutEntity>, onPick: (ShortcutEntity) -> Unit) {
    if (shortcuts.isEmpty()) {
        Text(stringResource(R.string.empty_library_title), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn {
        items(shortcuts.sortedWith(compareByDescending<ShortcutEntity> { it.isFavorite }.thenBy { it.name.lowercase() }), key = { it.id }) { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(s) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colorFor(s.colorKey)), contentAlignment = Alignment.Center) {
                    Icon(iconFor(s.iconKey), null, tint = Color.White)
                }
                Text(
                    s.name,
                    Modifier.weight(1f).padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    pluralStringResource(R.plurals.actions_count, s.actionCount, s.actionCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
