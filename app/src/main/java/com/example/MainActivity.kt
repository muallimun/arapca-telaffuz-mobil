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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
            val themeMode by appViewModel.appThemeMode.collectAsState()
            MyApplicationTheme(darkTheme = isDark, themeMode = themeMode) {
                MuallimunArabicAppRoot(viewModel = appViewModel)
            }
        }
    }
}

@Composable
fun MuallimunArabicAppRoot(viewModel: MainAppViewModel) {
    val palette = LocalMuallimunPalette.current
    val context = LocalContext.current
    val currentView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentMode by viewModel.currentMode.collectAsState()
    val activeModal by viewModel.activeModal.collectAsState()
    val sarfInitQuery by viewModel.sarfExplorerInitialQuery.collectAsState()
    val toastMsg by viewModel.toastMessage.collectAsState()
    val isDark by viewModel.isDarkTheme.collectAsState()
    val isPlayingAllDialogue by viewModel.isPlayingAllDialogue.collectAsState()
    val roleplayActive by viewModel.roleplayActive.collectAsState()
    val handsFreeAutoStart by viewModel.handsFreeAutoStart.collectAsState()
    val isRadioModeActive by viewModel.isRadioModeActive.collectAsState()
    val isRecording by viewModel.audioManager.isRecording.collectAsState()

    // Diyalog Tümünü Oynat, Rol Yapma, Oto-Dinle (Radyo) veya Eller Serbest modundayken ekranın kapanıp sesi kesmesini önle
    val keepScreenAwake = isPlayingAllDialogue || roleplayActive || handsFreeAutoStart || isRadioModeActive || isRecording
    DisposableEffect(currentView, keepScreenAwake) {
        currentView.keepScreenOn = keepScreenAwake
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    // Uygulama arka plana atıldığında veya ekran kapandığında (ON_PAUSE / ON_STOP) sesi ve mikrofonu otomatik durdur
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.stopRadioMode()
                viewModel.audioManager.stopSpeaking()
                viewModel.audioManager.stopListening()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // BackHandler: Modal kapalıysa ve HOME dışındaki bir sekmedeyse HOME ekranına dön
    BackHandler(enabled = activeModal == ActiveModal.NONE && currentMode != AppMode.HOME) {
        viewModel.stopRadioMode()
        viewModel.audioManager.stopSpeaking()
        viewModel.audioManager.stopListening()
        viewModel.switchMode(AppMode.HOME)
    }

    // BackHandler: Açık bir modal varsa uygulamadan çıkmak yerine modalı kapat
    BackHandler(enabled = activeModal != ActiveModal.NONE) {
        viewModel.stopRadioMode()
        viewModel.audioManager.stopSpeaking()
        viewModel.audioManager.stopListening()
        viewModel.closeModal()
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
            viewModel.onMicrophonePermissionDenied()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = palette.bgColor,
            topBar = {
                MuallimunTopAppBar(
                    isDark = isDark,
                    currentMode = currentMode,
                    onOpenSarf = { viewModel.openModal(ActiveModal.SARF_EXPLORER) },
                    onOpenListeningExam = { viewModel.openModal(ActiveModal.LISTENING_EXAM) },
                    onOpenReports = { viewModel.openModal(ActiveModal.ANALYTICS_REPORTS) },
                    onOpenGuideAndAbout = { viewModel.openModal(ActiveModal.ABOUT_INFO) },
                    onOpenSettings = { viewModel.openModal(ActiveModal.SETTINGS) },
                    onToggleTheme = { viewModel.toggleTheme() }
                )
            },
            bottomBar = {
                MuallimunCompactBottomBar(
                    currentMode = currentMode,
                    onSelectMode = { viewModel.switchMode(it) }
                )
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
            }
        }

        // Tam Ekran Modallar: Dialog yerine doğrudan ana Activity penceresinde WindowInsets.safeDrawing ile render edilir
        if (activeModal != ActiveModal.NONE) {
            when (activeModal) {
                ActiveModal.NONE -> {}
                ActiveModal.STUDENT_NAME -> StudentNameModal(
                    viewModel = viewModel,
                    onDismiss = { viewModel.closeModal() }
                )
                ActiveModal.ABOUT_INFO -> AboutInfoModal(
                    viewModel = viewModel,
                    onDismiss = { viewModel.closeModal() }
                )
                else -> {
                    Surface(
                        color = palette.bgColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .imePadding()
                    ) {
                        when (activeModal) {
                            ActiveModal.ONBOARDING_TOUR -> OnboardingTourModal(
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
                            ActiveModal.ROLEPLAY_REPORT_CARD -> RoleplayReportCardModal(
                                viewModel = viewModel,
                                onDismiss = { viewModel.closeModal() }
                            )
                            else -> {}
                        }
                    }
                }
            }
        }

        // Floating Toast Banner (Her zaman en üstte görünür)
        AnimatedVisibility(
            visible = !toastMsg.isNullOrBlank(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WarmGold,
                border = BorderStroke(1.dp, Color(0xFF0F172A)),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = toastMsg ?: "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MuallimunTopAppBar(
    isDark: Boolean,
    currentMode: AppMode,
    onOpenSarf: () -> Unit,
    onOpenListeningExam: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenGuideAndAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val palette = LocalMuallimunPalette.current
    var showToolsInSubMode by remember(currentMode) { mutableStateOf(false) }
    val showActionStrip = currentMode != AppMode.HOME && showToolsInSubMode

    Surface(
        color = palette.panelColor,
        border = BorderStroke(0.5.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            // Üst Satır (Maksimum 40.dp yükseklik, tek satır başlık + sadeleştirilmiş 32.dp kompakt ikonlar)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "Uygulama Simgesi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Text(
                        text = "Arapça Asistanı",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentHead,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Çalışma sekmelerinde araç şeridini açıp kapatan kompakt buton (🔧)
                    if (currentMode != AppMode.HOME) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (showToolsInSubMode) RoyalBlue else palette.btnSec,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { showToolsInSubMode = !showToolsInSubMode }
                                .testTag("btn_toggle_top_tools")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "Araçlar",
                                    tint = if (showToolsInSubMode) Color.White else palette.accentHead,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Tema Değiştirici (☀️/🌙)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.btnSec,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onToggleTheme() }
                            .testTag("btn_toggle_theme")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = if (isDark) "☀️" else "🌙", fontSize = 13.sp)
                        }
                    }

                    // Birleştirilmiş Rehber & Hakkında Butonu (ℹ️)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.btnSec,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onOpenGuideAndAbout() }
                            .testTag("btn_open_guide")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Rehber ve Uygulama Hakkında",
                                tint = palette.accentHead,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Ayarlar Butonu (⚙️)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.btnSec,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onOpenSettings() }
                            .testTag("btn_open_settings")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ayarlar",
                                tint = palette.textMain,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // Eşit Genişlikli 3 Kompakt Aksiyon Butonu (Kaydırmasız, %100 Ekran Uyumu)
            if (showActionStrip) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Button(
                        onClick = onOpenSarf,
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .testTag("top_btn_sarf")
                    ) {
                        Text(
                            text = "📖 Sözlük",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onOpenListeningExam,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCoral, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .testTag("top_btn_listening_exam")
                    ) {
                        Text(
                            text = "🎧 Sınav",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onOpenReports,
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .testTag("top_btn_reports")
                    ) {
                        Text(
                            text = "📊 Karne",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private data class CompactNavItem(
    val mode: AppMode,
    val label: String,
    val icon: ImageVector,
    val activeColor: Color,
    val activeIconTint: Color,
    val testTag: String
)

@Composable
private fun MuallimunCompactBottomBar(
    currentMode: AppMode,
    onSelectMode: (AppMode) -> Unit
) {
    val palette = LocalMuallimunPalette.current
    val navItems = listOf(
        CompactNavItem(AppMode.HOME, "Ana Sayfa", Icons.Default.Home, WarmGold, Color(0xFF0F172A), "nav_home"),
        CompactNavItem(AppMode.WORDS, "Kelime", Icons.Default.MenuBook, RoyalBlue, Color.White, "nav_words"),
        CompactNavItem(AppMode.DIALOGUE, "Diyalog", Icons.AutoMirrored.Filled.Chat, AccentCoral, Color.White, "nav_dialogue"),
        CompactNavItem(AppMode.SENTENCES, "Cümle", Icons.AutoMirrored.Filled.ListAlt, EmeraldGreen, Color(0xFF0F172A), "nav_sentences")
    )

    Surface(
        color = palette.panelColor,
        border = BorderStroke(0.5.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp)
        ) {
            navItems.forEach { item ->
                val isSelected = currentMode == item.mode
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectMode(item.mode) }
                        .testTag(item.testTag)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) item.activeColor else Color.Transparent,
                        modifier = Modifier.size(width = 44.dp, height = 24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (isSelected) item.activeIconTint else palette.textDim,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) palette.accentHead else palette.textDim,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
