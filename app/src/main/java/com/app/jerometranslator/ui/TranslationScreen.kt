package com.app.jerometranslator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(viewModel: TranslationViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    // Runtime TTS language support check
    val ttsSupported = remember(state.targetLanguage) {
        viewModel.speechOutput.isLanguageSupported(state.targetLanguage.code)
    }
    val sttAvailable = remember(state.sourceLanguage) {
        viewModel.speechInput.isAvailable
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening()
    }

    // Show errors as snackbar
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Jerome",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("Statistics") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(ScreenRoute.STATISTICS)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("History") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(ScreenRoute.HISTORY)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text("Conference") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        // Navigate to conference mode - show ConferenceScreen
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )

                Spacer(Modifier.weight(1f))

                HorizontalDivider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSettings = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    label = { Text("Exit") },
                    selected = false,
                    onClick = { activity?.finish() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
        },
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jerome Translator") },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleStats() }) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Statistics",
                            tint = if (state.showStats) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Mode indicator
            Text(
                text = "${state.activePreset.label} mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center,
            )

            // Inline stats card
            AnimatedVisibility(visible = state.showStats) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Last", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = state.lastTranslationTimeMs?.let { "${it}ms" } ?: "--",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Average", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = state.averageTranslationTimeMs?.let { "${it}ms" } ?: "--",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }

            // Language selector row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { showSourcePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(state.sourceLanguage.displayName, maxLines = 1)
                }

                IconButton(onClick = { viewModel.swapLanguages() }) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap languages")
                }

                FilledTonalButton(
                    onClick = { showTargetPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(state.targetLanguage.displayName, maxLines = 1)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Input field
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                label = { Text(state.sourceLanguage.displayName) },
                placeholder = { Text("Enter text to translate") },
                trailingIcon = {
                    if (state.sourceLanguage.voiceInputSupported && sttAvailable) {
                        IconButton(
                            onClick = {
                                if (state.isListening) {
                                    viewModel.stopListening()
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) viewModel.startListening()
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        ) {
                            Icon(
                                if (state.isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (state.isListening) "Stop" else "Speak",
                                tint = if (state.isListening) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            // Translate button
            Button(
                onClick = { viewModel.translate() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.inputText.isNotBlank() && !state.isTranslating,
            ) {
                if (state.isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Translating...")
                } else {
                    Icon(Icons.Default.Translate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Translate")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Output card
            AnimatedVisibility(visible = state.outputText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.targetLanguage.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = state.outputText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )

                        Spacer(Modifier.height(8.dp))

                        // Action row
                        Row {
                            if (state.targetLanguage.voiceOutputSupported) {
                                IconButton(
                                    onClick = {
                                        if (state.isSpeaking) {
                                            viewModel.stopSpeaking()
                                        } else if (ttsSupported) {
                                            viewModel.speakOutput()
                                        } else {
                                            viewModel.showTtsUnavailableError(state.targetLanguage.displayName)
                                        }
                                    },
                                ) {
                                    Icon(
                                        if (state.isSpeaking) Icons.Default.Stop
                                        else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Read aloud",
                                        tint = if (ttsSupported) MaterialTheme.colorScheme.onPrimaryContainer
                                               else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.outputText))
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        // Warning
                        state.warning?.let { warning ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        // Debug: raw LLM output
                        val rawOutput = state.rawLlmOutput
                        if (state.showDebugOutput && !rawOutput.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Raw LLM output:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = rawOutput,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
    } // ModalNavigationDrawer

    // Language picker sheets
    if (showSourcePicker) {
        LanguageSelectorSheet(
            selected = state.sourceLanguage,
            exclude = state.targetLanguage,
            onSelect = { viewModel.setSourceLanguage(it) },
            onDismiss = { showSourcePicker = false },
        )
    }

    if (showTargetPicker) {
        LanguageSelectorSheet(
            selected = state.targetLanguage,
            exclude = state.sourceLanguage,
            onSelect = { viewModel.setTargetLanguage(it) },
            onDismiss = { showTargetPicker = false },
        )
    }

    if (showSettings) {
        SettingsSheet(
            state = state,
            onGrammarToggle = { viewModel.setGrammarEnabled(it) },
            onNoThinkToggle = { viewModel.setNoThinkEnabled(it) },
            onDebugOutputToggle = { viewModel.setShowDebugOutput(it) },
            onPresetSelected = {
                viewModel.selectPreset(it)
                showSettings = false
            },
            onDeleteModelAndSwitch = { old, new ->
                viewModel.deleteModelAndSwitch(old, new)
                showSettings = false
            },
            onDeleteModel = { viewModel.deleteModel(it) },
            onRefreshModels = { viewModel.refreshDownloadedModels() },
            onDismiss = { showSettings = false },
        )
    }
}
