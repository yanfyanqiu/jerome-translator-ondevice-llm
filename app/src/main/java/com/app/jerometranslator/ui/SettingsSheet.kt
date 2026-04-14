package com.app.jerometranslator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.jerometranslator.config.ModelConfig
import com.app.jerometranslator.config.ModelPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: TranslationUiState,
    onGrammarToggle: (Boolean) -> Unit,
    onNoThinkToggle: (Boolean) -> Unit,
    onPresetSelected: (ModelPreset) -> Unit,
    onDeleteModelAndSwitch: (oldPreset: ModelPreset, newPreset: ModelPreset) -> Unit,
    onDeleteModel: (ModelPreset) -> Unit,
    onRefreshModels: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingPreset by remember { mutableStateOf<ModelPreset?>(null) }

    LaunchedEffect(Unit) { onRefreshModels() }

    // Delete-old-model confirmation dialog
    pendingPreset?.let { newPreset ->
        AlertDialog(
            onDismissRequest = { pendingPreset = null },
            title = { Text("Remove current model?") },
            text = {
                Text("Do you want to remove the current model (${state.activePreset.label}) to free disk space?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val old = state.activePreset
                    pendingPreset = null
                    onDeleteModelAndSwitch(old, newPreset)
                }) { Text("Yes, remove") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val preset = newPreset
                    pendingPreset = null
                    onPresetSelected(preset)
                }) { Text("No, keep") }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // --- Advanced section ---
            Text(
                "Advanced",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(8.dp))

            SettingSwitch(
                title = "Structured output (Grammar)",
                subtitle = "Constrains model output to JSON format. Prevents hallucination and improves speed.",
                checked = state.grammarEnabled,
                onCheckedChange = onGrammarToggle,
            )

            SettingSwitch(
                title = "Disable reasoning (/no_think)",
                subtitle = "Prevents the model from generating internal reasoning. Faster output.",
                checked = state.noThinkEnabled,
                onCheckedChange = onNoThinkToggle,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --- Model selection section ---
            Text(
                "Model",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Changing the model will download it if not already present.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            for (preset in ModelConfig.PRESETS) {
                ModelPresetRow(
                    preset = preset,
                    isSelected = preset.id == state.activePreset.id,
                    onClick = {
                        if (preset.id != state.activePreset.id) {
                            pendingPreset = preset
                        }
                    },
                )
            }

            // --- Downloaded models management ---
            if (state.downloadedModels.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    "Downloaded Models",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))

                for (model in state.downloadedModels) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                model.preset.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                formatFileSize(model.fileSizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (model.isActive) {
                            Text(
                                "Active",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        } else {
                            IconButton(onClick = { onDeleteModel(model.preset) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete ${model.preset.label}",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModelPresetRow(
    preset: ModelPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(preset.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0)
    else String.format("%.0f MB", mb)
}
