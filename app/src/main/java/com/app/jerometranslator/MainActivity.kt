package com.app.jerometranslator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.jerometranslator.ui.AppPhase
import com.app.jerometranslator.ui.DownloadScreen
import com.app.jerometranslator.ui.HistoryScreen
import com.app.jerometranslator.ui.OnboardingScreen
import com.app.jerometranslator.ui.ScreenRoute
import com.app.jerometranslator.ui.StatisticsScreen
import com.app.jerometranslator.ui.TranslationScreen
import com.app.jerometranslator.ui.TranslationViewModel
import com.app.jerometranslator.ui.theme.JeromeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JeromeTheme {
                val viewModel: TranslationViewModel = viewModel()
                val state by viewModel.state.collectAsState()

                when (state.appPhase) {
                    AppPhase.ONBOARDING -> JeromeTheme(darkTheme = true) {
                        OnboardingScreen(
                            onContinue = { preset -> viewModel.completeOnboarding(preset) },
                        )
                    }
                    AppPhase.READY -> when (state.currentScreen) {
                        ScreenRoute.TRANSLATION -> TranslationScreen(viewModel)
                        ScreenRoute.HISTORY -> HistoryScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.TRANSLATION) },
                        )
                        ScreenRoute.STATISTICS -> StatisticsScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.navigateTo(ScreenRoute.TRANSLATION) },
                        )
                    }
                    else -> DownloadScreen(
                        phase = state.appPhase,
                        progress = state.downloadProgress,
                        error = state.error,
                        modelLabel = state.activePreset.userFriendlyLabel,
                        modelDescription = state.activePreset.onboardingDescription,
                        onRetry = { viewModel.retryDownload() },
                    )
                }
            }
        }
    }
}
