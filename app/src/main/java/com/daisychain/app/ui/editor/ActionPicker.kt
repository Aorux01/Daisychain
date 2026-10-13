package com.daisychain.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daisychain.app.R
import com.daisychain.app.ui.library.colorFor
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.ActionRegistry
import com.daisychain.app.ui.common.SetupOrange
import com.daisychain.app.ui.common.needsShizukuSetup
import com.daisychain.app.ui.common.rememberShizukuReady
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column

/** Searchable list of every available block, grouped by category. Tapping a block adds it. */
@Composable
fun ActionPicker(onAdd: (ActionDefinition) -> Unit, modifier: Modifier = Modifier) {
    val lang = currentLanguage()
    val shizukuReady = rememberShizukuReady()
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }

    val grouped = ActionRegistry.search(query).groupBy { it.category }
    val categories = ActionRegistry.byCategory().keys.sortedBy { it.title.resolve(lang) }
    val shown = grouped.filterKeys { categoryId == null || it.id == categoryId }
        .toList().sortedBy { (c, _) -> c.title.resolve(lang) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CircleShape,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text(stringResource(R.string.search_actions)) },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = categoryId == null,
                    onClick = { categoryId = null },
                    label = { Text(stringResource(R.string.filter_all)) },
                    shape = CircleShape,
                )
            }
            items(categories, key = { it.id }) { c ->
                FilterChip(
                    selected = categoryId == c.id,
                    onClick = { categoryId = if (categoryId == c.id) null else c.id },
                    label = { Text(c.title.resolve(lang)) },
                    shape = CircleShape,
                )
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                Text(stringResource(R.string.no_actions_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEach { (category, defs) ->
                    item(key = "header-${category.id}") {
                        Text(
                            category.title.resolve(lang),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(defs, key = { it.id }) { def -> PickerRow(def, lang, shizukuReady) { onAdd(def) } }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(def: ActionDefinition, lang: String, shizukuReady: Boolean, onClick: () -> Unit) {
    val setup = def.needsShizukuSetup(shizukuReady)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (setup) Modifier.border(1.5.dp, SetupOrange, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = if (setup) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colorFor(def.colorKey)),
            contentAlignment = Alignment.Center,
        ) { Icon(actionIconFor(def.iconKey), null, tint = Color.White) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(def.title.resolve(lang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (setup) Text(stringResource(R.string.needs_shizuku), style = MaterialTheme.typography.bodySmall, color = SetupOrange)
        }
        Icon(Icons.Filled.Add, stringResource(R.string.add_action), tint = MaterialTheme.colorScheme.primary)
    }
}
