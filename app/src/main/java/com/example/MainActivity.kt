package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.modals.AboutInfoModal
import com.example.ui.modals.AnalyticsAndReportsModal
import com.example.ui.modals.ListeningExamModal
import com.example.ui.modals.OnboardingTourModal
import com.example.ui.modals.RoleplayReportCardModal
import com.example.ui.modals.SarfExplorerModal
import com.example.ui.modals.SettingsModal
import com.example.ui.modals.ShadowingTrainerModal
import com.example.ui.modals.StudentNameModal
import com.example.ui.modals.UnifiedAdminPanelModal
import com.example.ui.modals.UserGuideModal
import com.example.ui.modals.WorksheetAndHandoutGeneratorModal
import com.example.ui.screens.DialogueModeScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.SentencesModeScreen
import com.example.ui.screens.WordsModeScreen
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.LocalMuallimunPalette
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.TealAccent
import com.example.ui.theme.WarmGold
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.AppMode
import com.example.viewmodel.MainAppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: MainAppViewModel = viewModel()
            val isDark by appViewModel.isDarkTheme.collectAsState()
            MyApplicationTheme(darkTheme = isDark) {
                MuallimunArabicAppRoot(viewModel = appViewModel)
            }
        }
    }
}

@Composable
fun MuallimunArabicAppRoot(viewModel: MainAppViewModel) {
    val palette = LocalMuallimunPalette.current
    val context = LocalContext.current

    val currentMode by viewModel.currentMode.collectAsState()
    val activeModal by viewModel.activeModal.collectAsState()
    val sarfInitQuery by viewModel.sarfExplorerInitialQuery.collectAsState()
    val toastMsg by viewModel.toastMessage.collectAsState()
    val isDark by viewModel.isDarkTheme.collectAsState()

    // BackHandler for non-Home modes
    BackHandler(enabled = currentMode != AppMode.HOME) {
        viewModel.switchMode(AppMode.HOME)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (currentMode == AppMode.DIALOGUE && viewModel.roleplayActive.value) {
                viewModel.triggerRoleplayUserMic()
            } else {
                viewModel.startPronunciationRecording()
            }
        } else {
            viewModel.showToast("Mikrofon izni verilmedi. '🎯 Örnek Doğru Okuma' butonunu kullanabilirsiniz.")
        }
    }

    fun requestMicAndStart(isRoleplay: Boolean = false) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            if (isRoleplay) viewModel.triggerRoleplayUserMic()
            else viewModel.startPronunciationRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = palette.bgColor,
        topBar = {
            MuallimunTopAppBar(
                isDark = isDark,
                onOpenSarf = { viewModel.openModal(ActiveModal.SARF_EXPLORER) },
                onOpenListeningExam = { viewModel.openModal(ActiveModal.LISTENING_EXAM) },
                onOpenReports = { viewModel.openModal(ActiveModal.ANALYTICS_REPORTS) },
                onOpenGuide = { viewModel.openModal(ActiveModal.USER_GUIDE) },
                onOpenAbout = { viewModel.openModal(ActiveModal.ABOUT_INFO) },
                onOpenSettings = { viewModel.openModal(ActiveModal.SETTINGS) },
                onToggleTheme = { viewModel.toggleTheme() }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = palette.panelColor,
                contentColor = palette.textMain
            ) {
                NavigationBarItem(
                    selected = currentMode == AppMode.HOME,
                    onClick = { viewModel.switchMode(AppMode.HOME) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Ana Sayfa") },
                    label = { Text("Ana Sayfa", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F172A),
                        selectedTextColor = palette.accentHead,
                        indicatorColor = WarmGold
                    ),
                    modifier = Modifier.testTag("nav_home")
                )

                NavigationBarItem(
                    selected = currentMode == AppMode.WORDS,
                    onClick = { viewModel.switchMode(AppMode.WORDS) },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Kelime & Sarf") },
                    label = { Text("Kelime Modu", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = RoyalBlue,
                        indicatorColor = RoyalBlue
                    ),
                    modifier = Modifier.testTag("nav_words")
                )

                NavigationBarItem(
                    selected = currentMode == AppMode.DIALOGUE,
                    onClick = { viewModel.switchMode(AppMode.DIALOGUE) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Diyaloglar") },
                    label = { Text("Diyalog Modu", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = AccentCoral,
                        indicatorColor = AccentCoral
                    ),
                    modifier = Modifier.testTag("nav_dialogue")
                )

                NavigationBarItem(
                    selected = currentMode == AppMode.SENTENCES,
                    onClick = { viewModel.switchMode(AppMode.SENTENCES) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Cümleler") },
                    label = { Text("Cümle Modu", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F172A),
                        selectedTextColor = palette.okGreen,
                        indicatorColor = EmeraldGreen
                    ),
                    modifier = Modifier.testTag("nav_sentences")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentMode) {
                AppMode.HOME -> HomeDashboardScreen(viewModel = viewModel)
                AppMode.WORDS -> WordsModeScreen(
                    viewModel = viewModel,
                    onRequestMicPermissionAndRecord = { requestMicAndStart(false) }
                )
                AppMode.DIALOGUE -> DialogueModeScreen(
                    viewModel = viewModel,
                    onRequestMicForRoleplay = { requestMicAndStart(true) }
                )
                AppMode.SENTENCES -> SentencesModeScreen(
                    viewModel = viewModel,
                    onRequestMicPermissionAndRecord = { requestMicAndStart(false) }
                )
            }

            // Floating Toast Banner
            AnimatedVisibility(
                visible = !toastMsg.isNullOrBlank(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarmGold,
                    border = BorderStroke(1.dp, Color(0xFF0F172A)),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = toastMsg ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    // Active Full-Screen / Dialog Modals
    when (activeModal) {
        ActiveModal.NONE -> {}
        ActiveModal.ONBOARDING_TOUR -> OnboardingTourModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.STUDENT_NAME -> StudentNameModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.SARF_EXPLORER -> SarfExplorerModal(
            viewModel = viewModel,
            initialQuery = sarfInitQuery,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.LISTENING_EXAM -> ListeningExamModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.SHADOWING_TRAINER -> ShadowingTrainerModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.ANALYTICS_REPORTS -> AnalyticsAndReportsModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.WORKSHEET_GENERATOR -> WorksheetAndHandoutGeneratorModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.ADMIN_PANEL -> UnifiedAdminPanelModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.SETTINGS -> SettingsModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.USER_GUIDE -> UserGuideModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.ABOUT_INFO -> AboutInfoModal(
            onDismiss = { viewModel.closeModal() }
        )
        ActiveModal.ROLEPLAY_REPORT_CARD -> RoleplayReportCardModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModal() }
        )
    }
}

@Composable
private fun MuallimunTopAppBar(
    isDark: Boolean,
    onOpenSarf: () -> Unit,
    onOpenListeningExam: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    Surface(
        color = palette.panelColor,
        border = BorderStroke(0.5.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🕌 Arapça Asistanı",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldGreen
                    ) {
                        Text(
                            text = "v40.0",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.btnSec,
                        modifier = Modifier
                            .clickable { onToggleTheme() }
                            .testTag("btn_toggle_theme")
                    ) {
                        Text(
                            text = if (isDark) "☀️" else "🌙",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.btnSec,
                        modifier = Modifier
                            .clickable { onOpenGuide() }
                            .testTag("btn_open_guide")
                    ) {
                        Text(
                            text = "📖",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenAbout,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_open_about")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Uygulama Hakkında",
                            tint = palette.accentHead
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_open_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = palette.textMain
                        )
                    }
                }
            }

            // Hızlı Üst Araç Çubuğu: 📖 Sarf Sözlüğü | 🎧 Dinle & Bul | 📊 Raporlar
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TealAccent,
                    modifier = Modifier
                        .clickable { onOpenSarf() }
                        .testTag("top_btn_sarf")
                ) {
                    Text(
                        text = "📖 Sarf Sözlüğü",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCoral,
                    modifier = Modifier
                        .clickable { onOpenListeningExam() }
                        .testTag("top_btn_listening_exam")
                ) {
                    Text(
                        text = "🎧 Dinle & Bul",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = RoyalPurple,
                    modifier = Modifier
                        .clickable { onOpenReports() }
                        .testTag("top_btn_reports")
                ) {
                    Text(
                        text = "📊 Raporlar & Karne",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
