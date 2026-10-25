package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ArabicSpeechAndAudioManager
import com.example.data.BadgeItem
import com.example.data.DialogueEntity
import com.example.data.DialogueLineEntity
import com.example.data.DifficultItemRow
import com.example.data.ImportReport
import com.example.data.ListeningQuestion
import com.example.data.MuallimunDatabase
import com.example.data.MuallimunRepository
import com.example.data.PackageAnalyticsRow
import com.example.data.PackageEntity
import com.example.data.SentenceEntity
import com.example.data.SozlukEntity
import com.example.data.WordEntity
import com.example.data.WordMorphologyInfo
import com.example.nlp.ArabicNlpEngine
import com.example.nlp.MakhrajTip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AppMode {
    HOME, WORDS, DIALOGUE, SENTENCES
}

enum class ActiveModal {
    NONE,
    ONBOARDING_TOUR,
    STUDENT_NAME,
    SARF_EXPLORER,
    LISTENING_EXAM,
    SHADOWING_TRAINER,
    ANALYTICS_REPORTS,
    WORKSHEET_GENERATOR,
    ADMIN_PANEL,
    SETTINGS,
    USER_GUIDE,
    ABOUT_INFO,
    ROLEPLAY_REPORT_CARD
}

data class EvaluationSummaryState(
    val expectedArabic: String = "",
    val heardArabic: String = "",
    val accuracyPct: Int = 0,
    val qualityPct: Int = 0,
    val isCorrect: Boolean? = null,
    val verdictTitle: String = "🎤 Telaffuz için 'BAŞLA VE KONUŞ' butonuna basın",
    val statusDetail: String = "",
    val wordStatuses: List<Pair<String, Boolean>> = emptyList(),
    val makhrajTips: List<MakhrajTip> = emptyList(),
    val harakaGuide: String = "",
    val showConfetti: Boolean = false
)

data class ShadowingTarget(
    val itemId: String,
    val arabic: String,
    val turkish: String
)

class MainAppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MuallimunDatabase.getInstance(application)
    val repository = MuallimunRepository(database.dao())
    val audioManager = ArabicSpeechAndAudioManager(application)

    val allWords: StateFlow<List<WordEntity>> = repository.allWordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSozluk: StateFlow<List<SozlukEntity>> = repository.allSozlukFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDialogues: StateFlow<List<DialogueEntity>> = repository.allDialoguesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDialogueLines: StateFlow<List<DialogueLineEntity>> = repository.allDialogueLinesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSentences: StateFlow<List<SentenceEntity>> = repository.allSentencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wordPackages: StateFlow<List<PackageEntity>> = repository.observePackages("words")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sentencePackages: StateFlow<List<PackageEntity>> = repository.observePackages("sentences")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentMode = MutableStateFlow(AppMode.HOME)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    private val _activeModal = MutableStateFlow(ActiveModal.NONE)
    val activeModal: StateFlow<ActiveModal> = _activeModal.asStateFlow()

    private val _sarfExplorerInitialQuery = MutableStateFlow("")
    val sarfExplorerInitialQuery: StateFlow<String> = _sarfExplorerInitialQuery.asStateFlow()

    // --- SETTINGS & PREFERENCES ---
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _studentName = MutableStateFlow("Öğrenci")
    val studentName: StateFlow<String> = _studentName.asStateFlow()

    private val _streakDays = MutableStateFlow(1)
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private val _dailyCount = MutableStateFlow(0)
    val dailyCount: StateFlow<Int> = _dailyCount.asStateFlow()

    private val _dailyGoal = MutableStateFlow(20)
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private val _totalPoints = MutableStateFlow(0)
    val totalPoints: StateFlow<Int> = _totalPoints.asStateFlow()

    private val _showTeacherBar = MutableStateFlow(true)
    val showTeacherBar: StateFlow<Boolean> = _showTeacherBar.asStateFlow()

    private val _showDemoSimulationButtons = MutableStateFlow(false)
    val showDemoSimulationButtons: StateFlow<Boolean> = _showDemoSimulationButtons.asStateFlow()

    private val _slowTts = MutableStateFlow(false)
    val slowTts: StateFlow<Boolean> = _slowTts.asStateFlow()

    private val _autoPlayOnCardChange = MutableStateFlow(true)
    val autoPlayOnCardChange: StateFlow<Boolean> = _autoPlayOnCardChange.asStateFlow()

    private val _autoAdvanceOnCorrect = MutableStateFlow(false)
    val autoAdvanceOnCorrect: StateFlow<Boolean> = _autoAdvanceOnCorrect.asStateFlow()

    private val _handsFreeAutoStart = MutableStateFlow(false)
    val handsFreeAutoStart: StateFlow<Boolean> = _handsFreeAutoStart.asStateFlow()

    private val _randomOrder = MutableStateFlow(false)
    val randomOrder: StateFlow<Boolean> = _randomOrder.asStateFlow()

    private val _testModeHideArabic = MutableStateFlow(false)
    val testModeHideArabic: StateFlow<Boolean> = _testModeHideArabic.asStateFlow()

    private val _editModeEnabled = MutableStateFlow(false)
    val editModeEnabled: StateFlow<Boolean> = _editModeEnabled.asStateFlow()

    private val _voicePreference = MutableStateFlow("Sırayla") // "Sırayla", "Kadın", "Erkek"
    val voicePreference: StateFlow<String> = _voicePreference.asStateFlow()

    private val _maxAttemptsSetting = MutableStateFlow("3 Kez") // "1 Kez", "2 Kez", "3 Kez", "5 Kez", "Sınırsız"
    val maxAttemptsSetting: StateFlow<String> = _maxAttemptsSetting.asStateFlow()

    private val _lastActiveModeName = MutableStateFlow("words")
    val lastActiveModeName: StateFlow<String> = _lastActiveModeName.asStateFlow()

    private val _lastListeningExamSummary = MutableStateFlow("Henüz Dinle & Bul sınavı tamamlanmadı.")
    val lastListeningExamSummary: StateFlow<String> = _lastListeningExamSummary.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // --- WORD MODE STATE ---
    private val _selectedWordCategory = MutableStateFlow("hayvanlar")
    val selectedWordCategory: StateFlow<String> = _selectedWordCategory.asStateFlow()

    private val _selectedWordLevel = MutableStateFlow("Tümü")
    val selectedWordLevel: StateFlow<String> = _selectedWordLevel.asStateFlow()

    private val _wordIndex = MutableStateFlow(0)
    val wordIndex: StateFlow<Int> = _wordIndex.asStateFlow()

    private val _wordShowTr = MutableStateFlow(true)
    val wordShowTr: StateFlow<Boolean> = _wordShowTr.asStateFlow()

    private val _wordShowTashkeel = MutableStateFlow(true)
    val wordShowTashkeel: StateFlow<Boolean> = _wordShowTashkeel.asStateFlow()

    private val _wordFontSizeSp = MutableStateFlow(38)
    val wordFontSizeSp: StateFlow<Int> = _wordFontSizeSp.asStateFlow()

    private val _activeMorphArabic = MutableStateFlow<String?>(null)
    val activeMorphArabic: StateFlow<String?> = _activeMorphArabic.asStateFlow()

    private val _activeMorphLabel = MutableStateFlow("")
    val activeMorphLabel: StateFlow<String> = _activeMorphLabel.asStateFlow()

    private val _wordAttemptCount = MutableStateFlow(0)
    val wordAttemptCount: StateFlow<Int> = _wordAttemptCount.asStateFlow()

    // --- SENTENCE MODE STATE ---
    private val _selectedSentenceFilter = MutableStateFlow("Tümü") // "Tümü", "📅 Günün 15 Tekrarı", "Favoriler", "Öğrendiklerim", "Zorlandıklarım"
    val selectedSentenceFilter: StateFlow<String> = _selectedSentenceFilter.asStateFlow()

    private val _selectedSentenceLevel = MutableStateFlow("Tümü")
    val selectedSentenceLevel: StateFlow<String> = _selectedSentenceLevel.asStateFlow()

    private val _sentenceIndex = MutableStateFlow(0)
    val sentenceIndex: StateFlow<Int> = _sentenceIndex.asStateFlow()

    private val _sentShowTr = MutableStateFlow(true)
    val sentShowTr: StateFlow<Boolean> = _sentShowTr.asStateFlow()

    private val _sentShowTashkeel = MutableStateFlow(true)
    val sentShowTashkeel: StateFlow<Boolean> = _sentShowTashkeel.asStateFlow()

    private val _sentHideArabic = MutableStateFlow(false)
    val sentHideArabic: StateFlow<Boolean> = _sentHideArabic.asStateFlow()

    private val _sentFontSizeSp = MutableStateFlow(28)
    val sentFontSizeSp: StateFlow<Int> = _sentFontSizeSp.asStateFlow()

    private val _sentAttemptCount = MutableStateFlow(0)
    val sentAttemptCount: StateFlow<Int> = _sentAttemptCount.asStateFlow()

    // --- DIALOGUE & ROLEPLAY MODE STATE ---
    private val _selectedDialogueId = MutableStateFlow("dlg_01")
    val selectedDialogueId: StateFlow<String> = _selectedDialogueId.asStateFlow()

    private val _selectedDialogueLevel = MutableStateFlow("Tümü")
    val selectedDialogueLevel: StateFlow<String> = _selectedDialogueLevel.asStateFlow()

    private val _dialogueSubmode = MutableStateFlow("learn") // "learn" or "roleplay"
    val dialogueSubmode: StateFlow<String> = _dialogueSubmode.asStateFlow()

    private val _selectedRoleName = MutableStateFlow("Ömer")
    val selectedRoleName: StateFlow<String> = _selectedRoleName.asStateFlow()

    private val _chatShowTr = MutableStateFlow(true)
    val chatShowTr: StateFlow<Boolean> = _chatShowTr.asStateFlow()

    private val _chatShowTashkeel = MutableStateFlow(true)
    val chatShowTashkeel: StateFlow<Boolean> = _chatShowTashkeel.asStateFlow()

    private val _chatFontSizeSp = MutableStateFlow(22)
    val chatFontSizeSp: StateFlow<Int> = _chatFontSizeSp.asStateFlow()

    private val _isPlayingAllDialogue = MutableStateFlow(false)
    val isPlayingAllDialogue: StateFlow<Boolean> = _isPlayingAllDialogue.asStateFlow()

    private val _highlightedDialogueLineIndex = MutableStateFlow<Int?>(null)
    val highlightedDialogueLineIndex: StateFlow<Int?> = _highlightedDialogueLineIndex.asStateFlow()

    private val _roleplayActive = MutableStateFlow(false)
    val roleplayActive: StateFlow<Boolean> = _roleplayActive.asStateFlow()

    private val _roleplayTurnIndex = MutableStateFlow(0)
    val roleplayTurnIndex: StateFlow<Int> = _roleplayTurnIndex.asStateFlow()

    private val _roleplayWaitingUser = MutableStateFlow(false)
    val roleplayWaitingUser: StateFlow<Boolean> = _roleplayWaitingUser.asStateFlow()

    private val _roleplayAttempt = MutableStateFlow(0)
    val roleplayAttempt: StateFlow<Int> = _roleplayAttempt.asStateFlow()

    private val _roleplayRevealedWords = MutableStateFlow<Map<Int, List<Boolean>>>(emptyMap())
    val roleplayRevealedWords: StateFlow<Map<Int, List<Boolean>>> = _roleplayRevealedWords.asStateFlow()

    private val _roleplayFinalResults = MutableStateFlow<Map<Int, List<Pair<String, Boolean>>>>(emptyMap())
    val roleplayFinalResults: StateFlow<Map<Int, List<Pair<String, Boolean>>>> = _roleplayFinalResults.asStateFlow()

    private val _roleplayHintShownLines = MutableStateFlow<Set<Int>>(emptySet())
    val roleplayHintShownLines: StateFlow<Set<Int>> = _roleplayHintShownLines.asStateFlow()

    private val _roleplayStatusText = MutableStateFlow("")
    val roleplayStatusText: StateFlow<String> = _roleplayStatusText.asStateFlow()

    // --- EVALUATION & MAKHRAJ COACH STATE ---
    private val _evalState = MutableStateFlow(EvaluationSummaryState())
    val evalState: StateFlow<EvaluationSummaryState> = _evalState.asStateFlow()

    private val _shadowingTarget = MutableStateFlow(ShadowingTarget("w_ari", "نَحْلَةٌ", "Arı"))
    val shadowingTarget: StateFlow<ShadowingTarget> = _shadowingTarget.asStateFlow()

    private val recentAttemptHistory = mutableListOf<Pair<String, Boolean>>()
    private var alternateVoiceToggle = "female"

    // Filtered Word List based on selected category & CEFR level
    val filteredWords: StateFlow<List<WordEntity>> = combine(
        allWords,
        _selectedWordCategory,
        _selectedWordLevel,
        _randomOrder
    ) { words, catId, level, rnd ->
        val baseList = when (catId) {
            "gunluk_tekrar" -> repository.buildDailySmartReviewWords(words, 15)
            "tekrar" -> words.filter { it.isFav == 1 }
            "zayif" -> words.filter { it.yanlis > 0 }.sortedByDescending { it.yanlis - it.dogru }
            "sozluk_tumu" -> words.filter { it.paketId.startsWith("szl_") }
            "gorselli_tumu" -> words.filter { !it.paketId.startsWith("szl_") }
            else -> words.filter { it.paketId == catId }
        }
        val levelFiltered = if (level == "Tümü") baseList else baseList.filter { it.seviye == level }
        if (rnd && catId != "zayif") levelFiltered.shuffled() else levelFiltered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Sentence List based on filter & CEFR level
    val filteredSentences: StateFlow<List<SentenceEntity>> = combine(
        allSentences,
        _selectedSentenceFilter,
        _selectedSentenceLevel,
        _randomOrder
    ) { sents, filter, level, rnd ->
        val baseList = when (filter) {
            "📅 Günün 15 Tekrarı" -> repository.buildDailySmartReviewSentences(sents, 15)
            "Favoriler" -> sents.filter { it.isFav == 1 }
            "Öğrendiklerim" -> sents.filter { it.isLearned == 1 }
            "Zorlandıklarım" -> sents.filter { it.yanlis > 0 }.sortedByDescending { it.yanlis - it.dogru }
            else -> sents
        }
        val levelFiltered = if (level == "Tümü") baseList else baseList.filter { it.seviye == level }
        if (rnd && filter != "Zorlandıklarım") levelFiltered.shuffled() else levelFiltered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Dialogues based on CEFR level
    val filteredDialogues: StateFlow<List<DialogueEntity>> = combine(
        allDialogues,
        _selectedDialogueLevel
    ) { dlgs, level ->
        if (level == "Tümü") dlgs else dlgs.filter { it.seviye == level }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentDialogueLines: StateFlow<List<DialogueLineEntity>> = combine(
        allDialogueLines,
        _selectedDialogueId
    ) { lines, dlgId ->
        lines.filter { it.diyalogId == dlgId }.sortedBy { it.sira }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureSeeded(getApplication())
            loadPersistedConfig()
        }
        viewModelScope.launch {
            repository.allConfigFlow.collect { cfgList ->
                val map = cfgList.associate { it.key to it.value }
                _studentName.value = map["student_name"]?.ifBlank { "Öğrenci" } ?: "Öğrenci"
                _streakDays.value = map["streak_days"]?.toIntOrNull() ?: 1
                _dailyCount.value = map["daily_count"]?.toIntOrNull() ?: 0
                _dailyGoal.value = map["daily_goal"]?.toIntOrNull() ?: 20
                _totalPoints.value = map["total_points"]?.toIntOrNull() ?: 0
                _isDarkTheme.value = (map["theme_mode"] ?: "dark") == "dark"
                _showTeacherBar.value = (map["show_teacher_bar"] ?: "1") != "0"
                _showDemoSimulationButtons.value = (map["show_demo_simulation"] ?: "0") == "1"
                _lastActiveModeName.value = map["last_active_mode"] ?: "words"
                _lastListeningExamSummary.value = map["last_listening_exam"] ?: "Henüz Dinle & Bul sınavı tamamlanmadı."
            }
        }
    }

    private suspend fun loadPersistedConfig() {
        val seenTour = repository.getConfig("onboarding_seen_v40", "0")
        if (seenTour != "1") {
            _activeModal.value = ActiveModal.ONBOARDING_TOUR
        }
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
        viewModelScope.launch {
            delay(3200)
            if (_toastMessage.value == msg) {
                _toastMessage.value = null
            }
        }
    }

    fun openModal(modal: ActiveModal, sarfQuery: String = "") {
        audioManager.stopAllAudio()
        if (modal == ActiveModal.SARF_EXPLORER) {
            _sarfExplorerInitialQuery.value = sarfQuery
        }
        _activeModal.value = modal
    }

    fun closeModal() {
        val prev = _activeModal.value
        _activeModal.value = ActiveModal.NONE
        if (prev == ActiveModal.ONBOARDING_TOUR) {
            viewModelScope.launch {
                val curName = repository.getConfig("student_name", "")
                if (curName.isBlank() || curName.equals("Öğrenci", ignoreCase = true)) {
                    _activeModal.value = ActiveModal.STUDENT_NAME
                }
            }
        }
    }

    fun finishOnboardingTour(dontShowAgain: Boolean) {
        viewModelScope.launch {
            if (dontShowAgain) {
                repository.setConfig("onboarding_seen_v40", "1")
            }
            closeModal()
        }
    }

    fun saveStudentName(name: String) {
        val clean = name.trim().ifBlank { "Öğrenci" }
        viewModelScope.launch {
            repository.setConfig("student_name", clean)
            _studentName.value = clean
            _activeModal.value = ActiveModal.NONE
            showToast("👋 Hoş geldin $clean!")
        }
    }

    fun toggleTheme() {
        val nextDark = !_isDarkTheme.value
        _isDarkTheme.value = nextDark
        viewModelScope.launch {
            repository.setConfig("theme_mode", if (nextDark) "dark" else "light")
        }
    }

    fun setShowTeacherBar(show: Boolean) {
        _showTeacherBar.value = show
        viewModelScope.launch {
            repository.setConfig("show_teacher_bar", if (show) "1" else "0")
        }
    }

    fun setShowDemoSimulationButtons(show: Boolean) {
        _showDemoSimulationButtons.value = show
        viewModelScope.launch {
            repository.setConfig("show_demo_simulation", if (show) "1" else "0")
        }
    }

    fun setSlowTts(slow: Boolean) { _slowTts.value = slow }
    fun setAutoPlayOnCardChange(auto: Boolean) { _autoPlayOnCardChange.value = auto }
    fun setAutoAdvanceOnCorrect(auto: Boolean) { _autoAdvanceOnCorrect.value = auto }
    fun setHandsFreeAutoStart(auto: Boolean) { _handsFreeAutoStart.value = auto }
    fun setRandomOrder(rnd: Boolean) { _randomOrder.value = rnd }
    fun setTestModeHideArabic(hide: Boolean) { _testModeHideArabic.value = hide }
    fun setEditModeEnabled(enabled: Boolean) { _editModeEnabled.value = enabled }
    fun setVoicePreference(pref: String) { _voicePreference.value = pref }
    fun setMaxAttemptsSetting(maxA: String) { _maxAttemptsSetting.value = maxA }

    fun switchMode(mode: AppMode) {
        audioManager.stopAllAudio()
        audioManager.stopSpeechRecognition()
        if (_roleplayActive.value) stopRoleplaySession()
        _currentMode.value = mode
        if (mode != AppMode.HOME) {
            val mStr = when (mode) {
                AppMode.WORDS -> "words"
                AppMode.DIALOGUE -> "dialogue"
                AppMode.SENTENCES -> "sentences"
                else -> "words"
            }
            viewModelScope.launch {
                repository.setConfig("last_active_mode", mStr)
            }
        }
        refreshCurrentCardCoachState()
    }

    fun resumeLastSession() {
        val mode = when (_lastActiveModeName.value) {
            "dialogue" -> AppMode.DIALOGUE
            "sentences" -> AppMode.SENTENCES
            else -> AppMode.WORDS
        }
        switchMode(mode)
    }

    // --- WORD NAVIGATION & SELECTION ---
    fun selectWordCategory(catId: String) {
        audioManager.stopAllAudio()
        _selectedWordCategory.value = catId
        _wordIndex.value = 0
        _wordAttemptCount.value = 0
        _activeMorphArabic.value = null
        _activeMorphLabel.value = ""
        viewModelScope.launch {
            repository.setConfig("last_word_cat_id", catId)
            delay(100)
            refreshCurrentCardCoachState()
        }
    }

    fun selectWordLevel(level: String) {
        _selectedWordLevel.value = level
        _wordIndex.value = 0
        _wordAttemptCount.value = 0
        _activeMorphArabic.value = null
        _activeMorphLabel.value = ""
        viewModelScope.launch {
            delay(100)
            refreshCurrentCardCoachState()
        }
    }

    fun currentWordItem(): WordEntity? {
        val list = filteredWords.value
        if (list.isEmpty()) return null
        val idx = _wordIndex.value.coerceIn(0, list.size - 1)
        return list[idx]
    }

    fun currentWordMorphology(): WordMorphologyInfo? {
        val item = currentWordItem() ?: return null
        if (!item.gorsel.isNullOrBlank()) return null
        return repository.resolveWordMorphology(item.id, allSozluk.value)
            ?: repository.resolveWordMorphology(item.arapca, allSozluk.value)
    }

    fun selectMorphFormOnCard(formArabic: String, formLabel: String) {
        _activeMorphArabic.value = formArabic
        _activeMorphLabel.value = formLabel
        val primary = ArabicNlpEngine.expandArabicVariants(formArabic).firstOrNull() ?: formArabic
        audioManager.speakArabic(primary, "male", _slowTts.value)
        _evalState.value = EvaluationSummaryState(
            expectedArabic = formArabic,
            verdictTitle = "🎯 Seçili Form: $formLabel — Şimdi telaffuz edebilirsiniz",
            harakaGuide = ArabicNlpEngine.buildHarakaGuide(formArabic)
        )
    }

    fun changeWordIndex(delta: Int) {
        val list = filteredWords.value
        if (list.isEmpty()) return
        audioManager.stopAllAudio()
        val nextIdx = (_wordIndex.value + delta + list.size) % list.size
        _wordIndex.value = nextIdx
        _wordAttemptCount.value = 0
        _activeMorphArabic.value = null
        _activeMorphLabel.value = ""
        refreshCurrentCardCoachState()

        val nextWord = list[nextIdx]
        if (_handsFreeAutoStart.value) {
            viewModelScope.launch {
                delay(350)
                startPronunciationRecording()
            }
        } else if (_autoPlayOnCardChange.value && !_testModeHideArabic.value) {
            speakCurrentItem(null)
        }
    }

    fun changeWordFontSize(delta: Int) {
        _wordFontSizeSp.value = (_wordFontSizeSp.value + delta).coerceIn(24, 56)
    }

    fun setWordShowTr(show: Boolean) { _wordShowTr.value = show }
    fun setWordShowTashkeel(show: Boolean) { _wordShowTashkeel.value = show }

    // --- SENTENCE NAVIGATION & SELECTION ---
    fun selectSentenceFilter(filter: String) {
        _selectedSentenceFilter.value = filter
        _sentenceIndex.value = 0
        _sentAttemptCount.value = 0
        viewModelScope.launch {
            delay(100)
            refreshCurrentCardCoachState()
        }
    }

    fun selectSentenceLevel(level: String) {
        _selectedSentenceLevel.value = level
        _sentenceIndex.value = 0
        _sentAttemptCount.value = 0
        viewModelScope.launch {
            delay(100)
            refreshCurrentCardCoachState()
        }
    }

    fun currentSentenceItem(): SentenceEntity? {
        val list = filteredSentences.value
        if (list.isEmpty()) return null
        val idx = _sentenceIndex.value.coerceIn(0, list.size - 1)
        return list[idx]
    }

    fun changeSentenceIndex(delta: Int) {
        val list = filteredSentences.value
        if (list.isEmpty()) return
        audioManager.stopAllAudio()
        val nextIdx = (_sentenceIndex.value + delta + list.size) % list.size
        _sentenceIndex.value = nextIdx
        _sentAttemptCount.value = 0
        refreshCurrentCardCoachState()
    }

    fun jumpToSentenceIndex(oneBasedIndex: Int) {
        val list = filteredSentences.value
        if (list.isEmpty()) return
        val target = (oneBasedIndex - 1).coerceIn(0, list.size - 1)
        _sentenceIndex.value = target
        _sentAttemptCount.value = 0
        refreshCurrentCardCoachState()
    }

    fun changeSentenceFontSize(delta: Int) {
        _sentFontSizeSp.value = (_sentFontSizeSp.value + delta).coerceIn(18, 46)
    }

    fun setSentShowTr(show: Boolean) { _sentShowTr.value = show }
    fun setSentShowTashkeel(show: Boolean) { _sentShowTashkeel.value = show }
    fun setSentHideArabic(hide: Boolean) { _sentHideArabic.value = hide }

    // --- DIALOGUE & ROLEPLAY METHODS ---
    fun selectDialogue(dialogueId: String) {
        audioManager.stopAllAudio()
        if (_roleplayActive.value) stopRoleplaySession()
        _selectedDialogueId.value = dialogueId
        viewModelScope.launch {
            delay(80)
            val speakers = currentDialogueLines.value.map { it.konusmaci }.distinct()
            if (speakers.isNotEmpty() && _selectedRoleName.value !in speakers) {
                _selectedRoleName.value = speakers.first()
            }
        }
    }

    fun selectDialogueLevel(level: String) {
        _selectedDialogueLevel.value = level
        viewModelScope.launch {
            delay(80)
            val dlgs = filteredDialogues.value
            if (dlgs.isNotEmpty() && dlgs.none { it.id == _selectedDialogueId.value }) {
                selectDialogue(dlgs.first().id)
            }
        }
    }

    fun switchDialogueSubmode(submode: String) {
        audioManager.stopAllAudio()
        if (_roleplayActive.value) stopRoleplaySession()
        _dialogueSubmode.value = submode
        if (submode == "roleplay") {
            _roleplayStatusText.value = "Rolünüzü seçip '🎬 Canlandırmayı Başlat' butonuna basın"
        } else {
            _roleplayStatusText.value = ""
        }
    }

    fun selectRoleName(role: String) {
        _selectedRoleName.value = role
    }

    fun setChatShowTr(show: Boolean) { _chatShowTr.value = show }
    fun setChatShowTashkeel(show: Boolean) { _chatShowTashkeel.value = show }
    fun changeChatFontSize(delta: Int) {
        _chatFontSizeSp.value = (_chatFontSizeSp.value + delta).coerceIn(16, 36)
    }

    fun revealRoleplayHintForLine(lineIdx: Int) {
        _roleplayHintShownLines.value = _roleplayHintShownLines.value + lineIdx
    }

    fun togglePlayAllDialogue() {
        if (_isPlayingAllDialogue.value) {
            _isPlayingAllDialogue.value = false
            _highlightedDialogueLineIndex.value = null
            audioManager.stopAllAudio()
            return
        }
        val lines = currentDialogueLines.value
        if (lines.isEmpty()) return
        _isPlayingAllDialogue.value = true
        playDialogueLineSequentially(0)
    }

    private fun playDialogueLineSequentially(idx: Int) {
        val lines = currentDialogueLines.value
        if (!_isPlayingAllDialogue.value || idx >= lines.size) {
            _isPlayingAllDialogue.value = false
            _highlightedDialogueLineIndex.value = null
            return
        }
        _highlightedDialogueLineIndex.value = idx
        val line = lines[idx]
        val vType = if (line.cinsiyet.equals("K", ignoreCase = true)) "female" else "male"
        audioManager.speakArabic(
            text = line.arapca,
            voiceType = vType,
            slow = _slowTts.value,
            audioPath = line.ses,
            itemId = "dlgline_${line.id}",
            paketId = line.diyalogId
        ) {
            if (_isPlayingAllDialogue.value) {
                viewModelScope.launch {
                    delay(280)
                    playDialogueLineSequentially(idx + 1)
                }
            }
        }
    }

    fun toggleRoleplaySession() {
        if (_roleplayActive.value) {
            stopRoleplaySession()
        } else {
            startRoleplaySession()
        }
    }

    fun startRoleplaySession() {
        val lines = currentDialogueLines.value
        if (lines.isEmpty()) return
        audioManager.stopAllAudio()
        _roleplayActive.value = true
        _roleplayTurnIndex.value = 0
        _roleplayAttempt.value = 0
        _roleplayRevealedWords.value = emptyMap()
        _roleplayFinalResults.value = emptyMap()
        _roleplayHintShownLines.value = emptySet()
        _roleplayWaitingUser.value = false
        processRoleplayTurn()
    }

    fun stopRoleplaySession() {
        _roleplayActive.value = false
        _roleplayWaitingUser.value = false
        _highlightedDialogueLineIndex.value = null
        audioManager.stopAllAudio()
        audioManager.stopSpeechRecognition()
        _roleplayStatusText.value = "Canlandırma durduruldu."
    }

    private fun processRoleplayTurn() {
        val lines = currentDialogueLines.value
        if (!_roleplayActive.value) return
        val idx = _roleplayTurnIndex.value
        if (idx >= lines.size) {
            _roleplayActive.value = false
            _roleplayWaitingUser.value = false
            _highlightedDialogueLineIndex.value = null
            _activeModal.value = ActiveModal.ROLEPLAY_REPORT_CARD
            return
        }

        val line = lines[idx]
        _highlightedDialogueLineIndex.value = idx
        val chosenRole = _selectedRoleName.value

        if (line.konusmaci == chosenRole) {
            _roleplayWaitingUser.value = true
            _roleplayAttempt.value = 0
            _roleplayStatusText.value = "🎙️ Sıra Sizde (${line.sira}. Cümle) — Mikrofona konuşun veya Örnek Simülasyon yapın"
            _evalState.value = EvaluationSummaryState(
                expectedArabic = line.arapca,
                verdictTitle = "🎙️ Sıra Sizde (${line.konusmaci})",
                harakaGuide = ArabicNlpEngine.buildHarakaGuide(line.arapca)
            )
        } else {
            _roleplayWaitingUser.value = false
            _roleplayStatusText.value = "🔊 ${line.konusmaci} konuşuyor (${line.sira}. Cümle)..."
            val vType = if (line.cinsiyet.equals("K", ignoreCase = true)) "female" else "male"
            audioManager.speakArabic(
                text = line.arapca,
                voiceType = vType,
                slow = _slowTts.value,
                audioPath = line.ses,
                itemId = "dlgline_${line.id}",
                paketId = line.diyalogId
            ) {
                if (_roleplayActive.value) {
                    viewModelScope.launch {
                        delay(300)
                        _roleplayTurnIndex.value += 1
                        processRoleplayTurn()
                    }
                }
            }
        }
    }

    fun triggerRoleplayUserMic() {
        if (!_roleplayActive.value || !_roleplayWaitingUser.value) return
        val lines = currentDialogueLines.value
        val idx = _roleplayTurnIndex.value
        if (idx >= lines.size) return
        val line = lines[idx]

        audioManager.startSpeechRecognition("rp_${line.id}") { heard, _, errMsg ->
            if (!errMsg.isNullOrBlank() && heard.isBlank()) {
                _roleplayStatusText.value = "🤔 $errMsg (Emülatör için '🎯 Örnek Okuma' butonunu da kullanabilirsiniz)"
            } else {
                handleRoleplayTranscription(heard)
            }
        }
    }

    fun handleRoleplayTranscription(heardText: String) {
        val lines = currentDialogueLines.value
        val idx = _roleplayTurnIndex.value
        if (!_roleplayActive.value || idx >= lines.size) return
        val line = lines[idx]

        _roleplayAttempt.value += 1
        val maxAttempts = 3
        val eval = ArabicNlpEngine.evaluateSentenceWords(line.arapca, heardText)
        val makhrajTips = ArabicNlpEngine.analyzePhonemeAndMakhrajErrors(line.arapca, heardText)

        val prevMask = _roleplayRevealedWords.value[idx] ?: List(eval.wordResults.size) { false }
        val newMask = eval.wordResults.mapIndexed { wIdx, pair ->
            pair.second || (prevMask.getOrElse(wIdx) { false })
        }
        val combinedResults = eval.wordResults.mapIndexed { wIdx, pair ->
            pair.first to newMask[wIdx]
        }

        _roleplayRevealedWords.value = _roleplayRevealedWords.value + (idx to newMask)
        val allPassed = newMask.all { it } || eval.ratio >= 0.78

        _evalState.value = EvaluationSummaryState(
            expectedArabic = line.arapca,
            heardArabic = heardText,
            accuracyPct = (eval.ratio * 100).roundToInt(),
            qualityPct = (eval.ratio * 100).roundToInt(),
            isCorrect = allPassed,
            verdictTitle = if (allPassed) "✅ ${line.sira}. Cümle Başarılı!" else "⚠️ Eksik veya Hatalı Kelimeler Var",
            statusDetail = if (makhrajTips.isNotEmpty()) makhrajTips.first().tip else "Algılanan: $heardText",
            wordStatuses = combinedResults,
            makhrajTips = makhrajTips,
            harakaGuide = ArabicNlpEngine.buildHarakaGuide(line.arapca)
        )

        if (allPassed || _roleplayAttempt.value >= maxAttempts) {
            _roleplayWaitingUser.value = false
            _roleplayFinalResults.value = _roleplayFinalResults.value + (idx to combinedResults)
            viewModelScope.launch {
                repository.recordPracticeOutcome("dlgline_${line.id}", "dialogue", allPassed)
            }
            if (allPassed) {
                audioManager.playPraiseTone()
                _roleplayStatusText.value = "✅ ${line.sira}. Cümle Başarılı! Sonraki satıra geçiliyor..."
            } else {
                _roleplayStatusText.value = "⏭️ ${line.sira}. Cümle tamamlandı, sonraki satıra geçiliyor..."
            }
            viewModelScope.launch {
                delay(1200)
                _roleplayTurnIndex.value += 1
                processRoleplayTurn()
            }
        } else {
            val rem = maxAttempts - _roleplayAttempt.value
            _roleplayStatusText.value = "⚠️ Eksik kelimeler var (Kalan Hak: $rem) — Tekrar söyleyin"
        }
    }

    // --- SPEAKING & PRONUNCIATION EVALUATION ENGINE ---
    fun getExpectedArabicForCurrentCard(): String {
        return when (_currentMode.value) {
            AppMode.WORDS -> _activeMorphArabic.value ?: currentWordItem()?.arapca ?: ""
            AppMode.SENTENCES -> currentSentenceItem()?.arapca ?: ""
            AppMode.DIALOGUE -> {
                val lines = currentDialogueLines.value
                val idx = _roleplayTurnIndex.value.coerceIn(0, max(0, lines.size - 1))
                lines.getOrNull(idx)?.arapca ?: ""
            }
            else -> ""
        }
    }

    fun refreshCurrentCardCoachState() {
        val expected = getExpectedArabicForCurrentCard()
        _evalState.value = EvaluationSummaryState(
            expectedArabic = expected,
            verdictTitle = "🎤 Telaffuz için 'BAŞLA VE KONUŞ' butonuna basın",
            statusDetail = "",
            harakaGuide = ArabicNlpEngine.buildHarakaGuide(expected)
        )
    }

    fun speakCurrentItem(explicitVoiceType: String? = null) {
        val expected = getExpectedArabicForCurrentCard()
        if (expected.isBlank()) return
        val vType = explicitVoiceType ?: when (_voicePreference.value) {
            "Kadın" -> "female"
            "Erkek" -> "male"
            else -> {
                val chosen = alternateVoiceToggle
                alternateVoiceToggle = if (alternateVoiceToggle == "female") "male" else "female"
                chosen
            }
        }
        when (_currentMode.value) {
            AppMode.WORDS -> {
                val w = currentWordItem()
                // Eğer aktif bir sarf çekimi seçili değilse kelimenin gerçek ses dosyasını (varsa) çal
                val audioPath = if (_activeMorphArabic.value == null) w?.ses else null
                val itemId = if (_activeMorphArabic.value == null) w?.id else null
                audioManager.speakArabic(
                    text = expected,
                    voiceType = vType,
                    slow = _slowTts.value,
                    audioPath = audioPath,
                    itemId = itemId,
                    paketId = w?.paketId
                )
            }
            AppMode.SENTENCES -> {
                val s = currentSentenceItem()
                audioManager.speakArabic(
                    text = expected,
                    voiceType = vType,
                    slow = _slowTts.value,
                    audioPath = s?.ses,
                    itemId = s?.id,
                    paketId = s?.paketId
                )
            }
            else -> {
                audioManager.speakArabic(expected, vType, _slowTts.value)
            }
        }
    }

    fun openShadowingForCurrentCard() {
        when (_currentMode.value) {
            AppMode.WORDS -> {
                val w = currentWordItem() ?: return
                val ar = _activeMorphArabic.value ?: w.arapca
                _shadowingTarget.value = ShadowingTarget(w.id, ar, w.turkce)
                openModal(ActiveModal.SHADOWING_TRAINER)
            }
            AppMode.SENTENCES -> {
                val s = currentSentenceItem() ?: return
                _shadowingTarget.value = ShadowingTarget(s.id, s.arapca, s.turkce)
                openModal(ActiveModal.SHADOWING_TRAINER)
            }
            else -> {}
        }
    }

    fun openShadowingForCustomTarget(itemId: String, arapca: String, turkce: String) {
        _shadowingTarget.value = ShadowingTarget(itemId, arapca, turkce)
        openModal(ActiveModal.SHADOWING_TRAINER)
    }

    fun startPronunciationRecording() {
        if (audioManager.isRecording.value) {
            audioManager.stopSpeechRecognition()
            return
        }
        val expected = getExpectedArabicForCurrentCard()
        if (expected.isBlank()) return

        val maxSetting = _maxAttemptsSetting.value
        val curAttempts = if (_currentMode.value == AppMode.WORDS) _wordAttemptCount.value else _sentAttemptCount.value
        if (maxSetting != "Sınırsız") {
            val maxAllowed = maxSetting.first().digitToIntOrNull() ?: 3
            if (curAttempts >= maxAllowed) {
                showToast("Deneme hakkınız doldu! Lütfen sonraki karta ilerleyin.")
                return
            }
        }

        if (_currentMode.value == AppMode.WORDS) {
            _wordAttemptCount.value += 1
        } else if (_currentMode.value == AppMode.SENTENCES) {
            _sentAttemptCount.value += 1
        }

        val itemId = when (_currentMode.value) {
            AppMode.WORDS -> currentWordItem()?.id ?: "word"
            AppMode.SENTENCES -> currentSentenceItem()?.id ?: "sent"
            else -> "dlg"
        }

        _evalState.value = _evalState.value.copy(
            verdictTitle = "🎙️ ŞİMDİ DİNLİYORUM...",
            statusDetail = "Yeşil çerçeve aktifken net bir sesle Arapça metni okuyun"
        )

        audioManager.startSpeechRecognition(itemId) { heardText, confidence, errorMsg ->
            if (!errorMsg.isNullOrBlank() && heardText.isBlank()) {
                _evalState.value = _evalState.value.copy(
                    isCorrect = false,
                    verdictTitle = "🤔 Sesiniz Net Algılanamadı",
                    statusDetail = errorMsg
                )
            } else {
                evaluatePronunciationResult(expected, heardText, confidence)
            }
        }
    }

    /**
     * Allows testing the full Makhraj Coach, Root Consonant Guard, Wazn/Med & SM-2 pipeline
     * even on cloud emulators without physical microphone input.
     */
    fun simulatePronunciationAttempt(simulateErrorType: String = "correct") {
        val expected = getExpectedArabicForCurrentCard()
        if (expected.isBlank()) return
        val primary = ArabicNlpEngine.expandArabicVariants(expected).firstOrNull() ?: expected

        val simulatedHeard = when (simulateErrorType) {
            "makhraj" -> {
                // Create a realistic makhraj or root shift to showcase the live coach
                when {
                    primary.contains("ن") -> primary.replaceFirst("ن", "ر") // نَحْلَة -> رحلة
                    primary.contains("ص") -> primary.replaceFirst("ص", "س")
                    primary.contains("ط") -> primary.replaceFirst("ط", "ت")
                    primary.contains("ق") -> primary.replaceFirst("ق", "ك")
                    primary.contains("ع") -> primary.replaceFirst("ع", "ا")
                    primary.contains("ح") -> primary.replaceFirst("ح", "ه")
                    primary.contains("ذ") -> primary.replaceFirst("ذ", "ز")
                    primary.contains("ك") -> primary.replaceFirst("ك", "ق")
                    else -> "رحلة"
                }
            }
            else -> primary
        }

        if (_currentMode.value == AppMode.DIALOGUE && _roleplayActive.value && _roleplayWaitingUser.value) {
            handleRoleplayTranscription(simulatedHeard)
        } else {
            evaluatePronunciationResult(expected, simulatedHeard, 0.92f)
        }
    }

    fun evaluatePronunciationResult(expectedRaw: String, heardRaw: String, confidence: Float = 0.88f) {
        val cmp = ArabicNlpEngine.compareArabic(expectedRaw, heardRaw)
        val wordEval = ArabicNlpEngine.evaluateSentenceWords(expectedRaw, heardRaw)
        val makhrajTips = ArabicNlpEngine.analyzePhonemeAndMakhrajErrors(expectedRaw, heardRaw)

        val isSentenceOrDlg = _currentMode.value == AppMode.SENTENCES || _currentMode.value == AppMode.DIALOGUE
        val isCorrect = if (isSentenceOrDlg) {
            wordEval.ratio >= 0.75 || cmp.score >= 0.65
        } else {
            cmp.score >= 0.65 && wordEval.ratio >= 0.75
        }

        val pct = (cmp.score * 100.0).roundToInt().coerceIn(0, 100)
        val vol = audioManager.volumeScore.value
        val confScore = (confidence * 100f).roundToInt().coerceIn(50, 100)
        val quality = ((pct * 0.5) + (vol * 0.2) + (confScore * 0.3)).roundToInt().coerceIn(0, 100)

        val itemId = when (_currentMode.value) {
            AppMode.WORDS -> currentWordItem()?.id ?: ""
            AppMode.SENTENCES -> currentSentenceItem()?.id ?: ""
            else -> ""
        }
        val modeStr = if (_currentMode.value == AppMode.SENTENCES) "sentences" else "words"

        if (itemId.isNotEmpty()) {
            viewModelScope.launch {
                repository.recordPracticeOutcome(itemId, modeStr, isCorrect)
            }
        }

        val coachShort = if (makhrajTips.isNotEmpty()) " • ${makhrajTips.first().title}" else ""
        val verdictTitle = if (isCorrect) {
            "✅ HARİKA! — Kalite: %$quality (Doğruluk: %$pct)"
        } else {
            if (_currentMode.value == AppMode.SENTENCES) {
                val okCnt = wordEval.wordResults.count { it.second }
                "❌ ${wordEval.wordResults.size} kelimeden ${okCnt}'i doğru$coachShort"
            } else {
                "❌ TEKRAR DENE — Doğruluk: %$pct$coachShort"
            }
        }

        val detail = if (isCorrect) {
            "Beklenen: $expectedRaw  |  Duyulan: $heardRaw"
        } else {
            makhrajTips.firstOrNull()?.tip ?: "Beklenen: $expectedRaw  |  Duyulan: $heardRaw"
        }

        _evalState.value = EvaluationSummaryState(
            expectedArabic = expectedRaw,
            heardArabic = heardRaw,
            accuracyPct = pct,
            qualityPct = quality,
            isCorrect = isCorrect,
            verdictTitle = verdictTitle,
            statusDetail = detail,
            wordStatuses = wordEval.wordResults,
            makhrajTips = makhrajTips,
            harakaGuide = ArabicNlpEngine.buildHarakaGuide(expectedRaw),
            showConfetti = isCorrect && quality >= 75
        )

        if (isCorrect && quality >= 75) {
            audioManager.playPraiseTone()
        }

        evaluateAdaptiveCoaching(itemId, isCorrect, makhrajTips)

        if (isCorrect && _autoAdvanceOnCorrect.value) {
            viewModelScope.launch {
                delay(if (makhrajTips.isNotEmpty()) 2800L else 1500L)
                if (_currentMode.value == AppMode.WORDS) changeWordIndex(1)
                else if (_currentMode.value == AppMode.SENTENCES) changeSentenceIndex(1)
            }
        }
    }

    private fun evaluateAdaptiveCoaching(itemId: String, isCorrect: Boolean, makhrajTips: List<MakhrajTip>) {
        if (itemId.isBlank()) return
        recentAttemptHistory.add(itemId to isCorrect)
        if (recentAttemptHistory.size > 15) recentAttemptHistory.removeAt(0)

        val sameItemAttempts = recentAttemptHistory.filter { it.first == itemId }.map { it.second }
        if (sameItemAttempts.size >= 3 && sameItemAttempts.takeLast(3).none { it }) {
            if (!_slowTts.value) {
                _slowTts.value = true
                showToast("💡 Koç Önerisi: Zorlandığınız için '🐢 Yavaş Seslendirme (%25)' otomatik açıldı!")
            } else if (makhrajTips.isNotEmpty()) {
                showToast("💡 ${makhrajTips.first().tip}")
            }
            recentAttemptHistory.removeAll { it.first == itemId }
            return
        }

        if (recentAttemptHistory.size >= 6) {
            val lastSix = recentAttemptHistory.takeLast(6)
            val distinctIds = lastSix.map { it.first }.toSet()
            if (lastSix.all { it.second } && distinctIds.size >= 4) {
                showToast("🌟 Muhteşem Seri (6/6)! Bir üst CEFR seviyesine veya Sınav Moduna geçebilirsiniz!")
                recentAttemptHistory.clear()
            }
        }
    }

    // --- ANALYTICS, BADGES & STATS HELPERS ---
    fun getLevelTitle(): String {
        val pts = _totalPoints.value
        return when {
            pts >= 2500 -> "💎 Elmas Üstad (Seviye 5)"
            pts >= 1200 -> "🥇 Altın Muallim (Seviye 4)"
            pts >= 500 -> "🥈 Gümüş Hatip (Seviye 3)"
            pts >= 150 -> "🥉 Bronz Konuşmacı (Seviye 2)"
            else -> "🌱 Başlangıç Talebesi (Seviye 1)"
        }
    }

    fun getBadgesList(): List<BadgeItem> {
        val wCorr = allWords.value.sumOf { it.dogru }
        val dCorr = allDialogueLines.value.sumOf { it.dogru }
        val sCorr = allSentences.value.sumOf { it.dogru }
        val streak = _streakDays.value
        val pts = _totalPoints.value

        return listOf(
            BadgeItem("🔥 İlk Kıvılcım", "İlk sesli telaffuz pratiğini tamamla", (wCorr + dCorr + sCorr) >= 1),
            BadgeItem("🎯 Kelime Avcısı", "En az 25 kelimeyi doğru telaffuz et", wCorr >= 25),
            BadgeItem("💬 Muhâdese Yıldızı", "Diyaloglarda 30 cümleyi doğru seslendir", dCorr >= 30),
            BadgeItem("📜 Cümle Üstadı", "Anahtar cümlelerde 50 doğru telaffuza ulaş", sCorr >= 50),
            BadgeItem("⚡ İstikrar Abidesi", "3 gün üst üste çalışma serisi (Streak) yap", streak >= 3),
            BadgeItem("👑 Arapça Hatibi", "Toplam 1000 başarı puanını geç", pts >= 1000)
        )
    }

    fun getPackageAnalyticsRows(): List<PackageAnalyticsRow> {
        val rows = mutableListOf<PackageAnalyticsRow>()
        val words = allWords.value
        for (pkg in wordPackages.value) {
            val pWords = if (pkg.id == "sozluk_tumu") {
                words.filter { it.paketId.startsWith("szl_") }
            } else {
                words.filter { it.paketId == pkg.id }
            }
            val tot = pWords.size
            val lrn = pWords.count { it.isLearned == 1 || it.dogru > 0 }
            val corr = pWords.sumOf { it.dogru }
            val wrg = pWords.sumOf { it.yanlis }
            val acc = if (corr + wrg > 0) ((corr.toDouble() / (corr + wrg)) * 100).roundToInt() else 0
            val comp = if (tot > 0) ((lrn.toDouble() / tot) * 100).roundToInt() else 0
            rows.add(PackageAnalyticsRow("Kelime Paketi", pkg.ad, tot, lrn, corr, wrg, acc, comp))
        }

        val sents = allSentences.value
        for (pkg in sentencePackages.value) {
            val pSents = sents.filter { it.paketId == pkg.id }
            val tot = pSents.size
            val lrn = pSents.count { it.isLearned == 1 || it.dogru > 0 }
            val corr = pSents.sumOf { it.dogru }
            val wrg = pSents.sumOf { it.yanlis }
            val acc = if (corr + wrg > 0) ((corr.toDouble() / (corr + wrg)) * 100).roundToInt() else 0
            val comp = if (tot > 0) ((lrn.toDouble() / tot) * 100).roundToInt() else 0
            rows.add(PackageAnalyticsRow("Cümle Paketi", pkg.ad, tot, lrn, corr, wrg, acc, comp))
        }

        val lines = allDialogueLines.value
        for (dlg in allDialogues.value) {
            val dLines = lines.filter { it.diyalogId == dlg.id }
            val tot = dLines.size
            val lrn = dLines.count { it.dogru > 0 }
            val corr = dLines.sumOf { it.dogru }
            val wrg = dLines.sumOf { it.yanlis }
            val acc = if (corr + wrg > 0) ((corr.toDouble() / (corr + wrg)) * 100).roundToInt() else 0
            val comp = if (tot > 0) ((lrn.toDouble() / tot) * 100).roundToInt() else 0
            rows.add(PackageAnalyticsRow("Diyalog", "[${dlg.seviye}] ${dlg.baslikTr}", tot, lrn, corr, wrg, acc, comp))
        }

        return rows
    }

    fun getMostDifficultRows(): List<DifficultItemRow> {
        val list = mutableListOf<DifficultItemRow>()
        allWords.value.filter { it.yanlis > 0 }.forEach {
            list.add(DifficultItemRow("Kelime", it.id, it.turkce, it.arapca, it.dogru, it.yanlis))
        }
        allSentences.value.filter { it.yanlis > 0 }.forEach {
            list.add(DifficultItemRow("Cümle", it.id, it.turkce, it.arapca, it.dogru, it.yanlis))
        }
        allDialogueLines.value.filter { it.yanlis > 0 }.forEach {
            list.add(DifficultItemRow("Diyalog (${it.konusmaci})", "dlgline_${it.id}", it.turkce, it.arapca, it.dogru, it.yanlis))
        }
        return list.sortedByDescending { it.yanlis }.take(30)
    }

    fun saveLastListeningExamSummary(summary: String) {
        _lastListeningExamSummary.value = summary
        viewModelScope.launch {
            repository.setConfig("last_listening_exam", summary)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.shutdown()
    }
}
