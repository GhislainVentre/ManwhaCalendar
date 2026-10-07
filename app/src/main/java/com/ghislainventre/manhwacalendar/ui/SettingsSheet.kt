package com.ghislainventre.manhwacalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ghislainventre.manhwacalendar.data.ChapterLanguage

/** Réglages regroupés dans une feuille : langue des chapitres et sites de recherche. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val sites by vm.sites.collectAsState()
    val language by vm.language.collectAsState()
    var address by remember { mutableStateOf("") }
    val addSite = {
        if (address.isNotBlank()) {
            vm.addSite(address.trim())
            address = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "title") {
                Text(
                    "Réglages",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }

            item(key = "language-title") {
                SheetSection("Langue des chapitres", "Pour les séries suivies sur MangaDex")
            }
            items(ChapterLanguage.entries, key = { it.name }) { lang ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = lang == language, role = Role.RadioButton) { vm.setLanguage(lang) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = lang == language, onClick = null, modifier = Modifier.padding(12.dp))
                    Text(lang.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            item(key = "sites-title") {
                SheetSection("Sites de recherche", "Les sites activés sont interrogés en même temps")
            }
            items(sites, key = { "site-${it.name}" }) { site ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(site.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            site.baseUrl.removePrefix("https://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (site.name != "MangaDex") {
                        IconButton(onClick = { vm.removeSite(site.name) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Retirer ${site.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = site.enabled, onCheckedChange = { vm.setSiteEnabled(site.name, it) })
                }
            }
            item(key = "add-site") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Ajouter un site") },
                        placeholder = { Text("exemple.com") },
                        supportingText = { Text("Sites WordPress au thème Madara") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addSite() }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = addSite, enabled = address.isNotBlank()) { Text("Ajouter") }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String, subtitle: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
