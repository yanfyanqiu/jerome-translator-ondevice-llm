package com.app.jerometranslator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.jerometranslator.config.Language
import com.app.jerometranslator.config.Languages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorSheet(
    selected: Language,
    exclude: Language? = null,
    onSelect: (Language) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, exclude) {
        Languages.ALL
            .filter { it != exclude }
            .filter {
                query.isBlank() ||
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.nativeName.contains(query, ignoreCase = true) ||
                    it.code.equals(query, ignoreCase = true)
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索语言") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(top = 8.dp),
            ) {
                items(filtered, key = { it.code }) { language ->
                    val isSelected = language == selected
                    ListItem(
                        headlineContent = { Text(language.displayName) },
                        supportingContent = { Text(language.nativeName) },
                        trailingContent = {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "已选")
                            }
                        },
                        modifier = Modifier.clickable {
                            onSelect(language)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}
