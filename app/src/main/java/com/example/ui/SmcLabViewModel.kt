package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.content.SmcLabContentCatalog
import com.example.data.local.AttemptRecordEntity
import com.example.data.local.ChartDrawingEntity
import com.example.data.local.LessonProgressEntity
import com.example.data.local.PackManifestEntity
import com.example.data.local.PaperTradeEntity
import com.example.data.local.SmcLabDao
import com.example.data.local.TradeEventEntity
import com.example.domain.engine.AdaptiveCoachEngine
import com.example.domain.engine.AnswerEvaluationEngine
import com.example.domain.engine.AppSessionActivityState
import com.example.domain.engine.AttemptHistoryItem
import com.example.domain.engine.BackupRestoreMode
import com.example.domain.engine.CoachRecommendation
import com.example.domain.engine.EvaluationResult
import com.example.domain.engine.LocalBackupBundle
import com.example.domain.engine.LocalBackupEngine
import com.example.domain.engine.PackActivationResult
import com.example.domain.engine.PackCandidate
import com.example.domain.engine.PackManagerEngine
import com.example.domain.engine.SimulationEngine
import com.example.domain.engine.SimulationWorkspaceState
import com.example.domain.engine.SkillMasterySnapshot
import com.example.domain.model.AssessmentOutcome
import com.example.domain.model.ChartDrawing
import com.example.domain.model.DrawingToolType
import com.example.domain.model.EntryMode
import com.example.domain.model.ErrorTag
import com.example.domain.model.GlossaryTerm
import com.example.domain.model.Lesson
import com.example.domain.model.Playhead
import com.example.domain.model.PlayheadMode
import com.example.domain.model.QuestionType
import com.example.domain.model.Scenario
import com.example.domain.model.TradeDirection
import com.example.domain.model.ZoneBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SmcLabTab {
    CURRICULUM_COACH,
    CHART_CHALLENGE,
    SIMULATION,
    GLOSSARY,
    PACKS_BACKUP
}

data class SmcLabUiState(
    val isArabic: Boolean = true, // Arabic RTL default per specification
    val currentTab: SmcLabTab = SmcLabTab.CURRICULUM_COACH,
    val sessionActivityState: AppSessionActivityState = AppSessionActivityState.CURRICULUM_OVERVIEW,
    val lessons: List<Lesson> = SmcLabContentCatalog.all41Lessons,
    val selectedLesson: Lesson = SmcLabContentCatalog.all41Lessons[8], // Default to L03-01 BOS
    val scenarios: List<Scenario> = listOf(SmcLabContentCatalog.goldenBos10BarScenario) + SmcLabContentCatalog.all96Scenarios,
    val activeScenario: Scenario = SmcLabContentCatalog.goldenBos10BarScenario,
    val activeDecisionPointIndex: Int = 0,
    val freePlayhead: Playhead = Playhead(
        workspaceId = "WS-FREE",
        scenarioId = "SYN-BOS-001",
        mode = PlayheadMode.FREE_CHART,
        currentBarIndex = 6,
        maxBarIndex = 9
    ),
    val challengePlayhead: Playhead = Playhead(
        workspaceId = "WS-CHALLENGE",
        scenarioId = "SYN-BOS-001",
        mode = PlayheadMode.CHALLENGE,
        currentBarIndex = 6,
        maxBarIndex = 9
    ),
    val activePlayheadMode: PlayheadMode = PlayheadMode.CHALLENGE,
    val selectedOption: String = "",
    val hintUsedBeforeAnswer: Boolean = false,
    val latestEvaluation: EvaluationResult? = null,
    val activeDrawingTool: DrawingToolType = DrawingToolType.NONE,
    val drawings: List<ChartDrawing> = emptyList(),
    val showAccessibilityTable: Boolean = false,
    val simulationWorkspace: SimulationWorkspaceState = SimulationWorkspaceState(
        workspaceId = "WS-SIM-MAIN",
        scenarioId = "SYN-BOS-001",
        playhead = Playhead(
            workspaceId = "WS-SIM-MAIN",
            scenarioId = "SYN-BOS-001",
            mode = PlayheadMode.SIMULATION,
            currentBarIndex = 7,
            maxBarIndex = 9
        )
    ),
    val attemptHistory: List<AttemptHistoryItem> = emptyList(),
    val skillMasteries: Map<String, SkillMasterySnapshot> = emptyMap(),
    val coachRecommendation: CoachRecommendation? = null,
    val glossaryTerms: List<GlossaryTerm> = SmcLabContentCatalog.glossaryTerms,
    val glossarySearchQuery: String = "",
    val activePackManifest: PackManifestEntity = PackManifestEntity(
        packId = "CORE_PACK_V1",
        packVersion = "1.0.0",
        keyId = "SMC_PACK_SIGNING_KEY_2026_V1",
        schemaVersion = 2,
        minAppVersion = 1,
        sizeBytes = 65536L,
        sha256Hex = PackManagerEngine.computeSha256Hex("CORE_PACK_V1_PAYLOAD".toByteArray()),
        signatureHex = "VERIFIED_INITIAL_CORE_PACK",
        dataKind = "SYNTHETIC_ORIGINAL",
        sourceId = "SMC_LAB_INTERNAL_V1",
        licenseId = "ORIGINAL_SYNTHETIC_APPROVED",
        status = "ACTIVE",
        activatedAtUtcMillis = 1767225600000L
    ),
    val previousValidPackManifest: PackManifestEntity? = null,
    val lastPackOperationResult: PackActivationResult? = null,
    val lastBackupSnapshot: LocalBackupBundle? = null,
    val backupStatusMessageAr: String? = null,
    val backupStatusMessageEn: String? = null
) {
    val currentPlayhead: Playhead
        get() = when (activePlayheadMode) {
            PlayheadMode.FREE_CHART -> freePlayhead
            PlayheadMode.CHALLENGE -> challengePlayhead
            PlayheadMode.SIMULATION -> simulationWorkspace.playhead
        }
}

class SmcLabViewModel(
    private val dao: SmcLabDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmcLabUiState())
    val uiState: StateFlow<SmcLabUiState> = _uiState.asStateFlow()

    init {
        recomputeCoachAndMastery()
        loadPersistedState()
    }

    private fun loadPersistedState() {
        val dbDao = dao ?: return
        viewModelScope.launch {
            val savedProgress = dbDao.getAllLessonProgress().associateBy { it.lessonId }
            val savedAttempts = dbDao.getAllAttemptRecords().map { rec ->
                AttemptHistoryItem(
                    attemptId = rec.attemptId,
                    scenarioId = rec.scenarioId,
                    scenarioVersion = rec.scenarioVersion,
                    skillId = rec.skillId,
                    familyId = rec.familyId,
                    sessionId = rec.sessionId,
                    rubricScore = rec.rubricScore,
                    hintUsedBeforeAnswer = rec.hintUsedBeforeAnswer,
                    criticalError = rec.criticalError,
                    outcome = AssessmentOutcome.valueOf(rec.outcome),
                    errorTag = ErrorTag.valueOf(rec.errorTag),
                    timestampUtcMillis = rec.timestampUtcMillis
                )
            }
            _uiState.update { state ->
                val updatedLessons = state.lessons.map { l ->
                    l.copy(isCompleted = savedProgress[l.lessonId]?.isCompleted == true)
                }
                state.copy(
                    lessons = updatedLessons,
                    attemptHistory = if (savedAttempts.isNotEmpty()) savedAttempts else state.attemptHistory
                )
            }
            recomputeCoachAndMastery()
        }
    }

    fun toggleLanguage() {
        _uiState.update { it.copy(isArabic = !it.isArabic) }
    }

    fun selectTab(tab: SmcLabTab) {
        val sessionState = when (tab) {
            SmcLabTab.CURRICULUM_COACH -> AppSessionActivityState.CURRICULUM_OVERVIEW
            SmcLabTab.CHART_CHALLENGE -> AppSessionActivityState.CHALLENGE
            SmcLabTab.SIMULATION -> AppSessionActivityState.SIMULATION_ACTIVE
            SmcLabTab.GLOSSARY -> AppSessionActivityState.HOME_BROWSING
            SmcLabTab.PACKS_BACKUP -> AppSessionActivityState.RESTORE_FLOW
        }
        _uiState.update {
            it.copy(
                currentTab = tab,
                sessionActivityState = sessionState
            )
        }
    }

    fun selectLesson(lesson: Lesson) {
        val linkedScenario = _uiState.value.scenarios.find { it.scenarioId == lesson.linkedScenarioId }
            ?: SmcLabContentCatalog.goldenBos10BarScenario
        _uiState.update {
            it.copy(
                selectedLesson = lesson,
                activeScenario = linkedScenario,
                activeDecisionPointIndex = 0,
                latestEvaluation = null,
                selectedOption = "",
                hintUsedBeforeAnswer = false
            )
        }
        resetPlayheadsForScenario(linkedScenario)
    }

    fun markLessonCompleted(lessonId: String) {
        _uiState.update { state ->
            val updated = state.lessons.map {
                if (it.lessonId == lessonId) it.copy(isCompleted = true) else it
            }
            val updatedSelected = if (state.selectedLesson.lessonId == lessonId) {
                state.selectedLesson.copy(isCompleted = true)
            } else state.selectedLesson
            state.copy(lessons = updated, selectedLesson = updatedSelected)
        }
        viewModelScope.launch {
            dao?.upsertLessonProgress(
                LessonProgressEntity(
                    lessonId = lessonId,
                    isCompleted = true,
                    completedAtUtcMillis = System.currentTimeMillis(),
                    lastScore = 1.0
                )
            )
        }
        recomputeCoachAndMastery()
    }

    fun selectScenario(scenario: Scenario, decisionPointIndex: Int = 0) {
        _uiState.update {
            it.copy(
                activeScenario = scenario,
                activeDecisionPointIndex = decisionPointIndex.coerceIn(0, (scenario.decisionPoints.size - 1).coerceAtLeast(0)),
                latestEvaluation = null,
                selectedOption = "",
                hintUsedBeforeAnswer = false,
                currentTab = SmcLabTab.CHART_CHALLENGE,
                sessionActivityState = AppSessionActivityState.CHALLENGE
            )
        }
        resetPlayheadsForScenario(scenario, decisionPointIndex)
    }

    private fun resetPlayheadsForScenario(scenario: Scenario, dpIndex: Int = 0) {
        val dp = scenario.decisionPoints.getOrNull(dpIndex) ?: scenario.decisionPoints.first()
        val maxBar = (scenario.candles.size - 1).coerceAtLeast(0)
        _uiState.update { state ->
            state.copy(
                freePlayhead = Playhead(
                    workspaceId = "WS-FREE-${scenario.scenarioId}",
                    scenarioId = scenario.scenarioId,
                    mode = PlayheadMode.FREE_CHART,
                    currentBarIndex = dp.visibleDataThrough,
                    maxBarIndex = maxBar
                ),
                challengePlayhead = Playhead(
                    workspaceId = "WS-CHAL-${scenario.scenarioId}",
                    scenarioId = scenario.scenarioId,
                    mode = PlayheadMode.CHALLENGE,
                    currentBarIndex = dp.visibleDataThrough,
                    maxBarIndex = maxBar,
                    isDecisionLocked = false
                ),
                simulationWorkspace = SimulationWorkspaceState(
                    workspaceId = "WS-SIM-${scenario.scenarioId}",
                    scenarioId = scenario.scenarioId,
                    playhead = Playhead(
                        workspaceId = "WS-SIM-${scenario.scenarioId}",
                        scenarioId = scenario.scenarioId,
                        mode = PlayheadMode.SIMULATION,
                        currentBarIndex = dp.visibleDataThrough,
                        maxBarIndex = maxBar
                    )
                )
            )
        }
    }

    fun switchPlayheadMode(mode: PlayheadMode) {
        _uiState.update { it.copy(activePlayheadMode = mode) }
    }

    fun stepPlayheadForward() {
        _uiState.update { state ->
            when (state.activePlayheadMode) {
                PlayheadMode.FREE_CHART -> state.copy(freePlayhead = state.freePlayhead.stepForward())
                PlayheadMode.CHALLENGE -> state.copy(challengePlayhead = state.challengePlayhead.stepForward())
                PlayheadMode.SIMULATION -> {
                    val key = "STEP-SIM-${state.simulationWorkspace.playhead.currentBarIndex + 1}-${System.currentTimeMillis()}"
                    val nextSim = SimulationEngine.stepForwardBar(
                        state = state.simulationWorkspace,
                        allCandles = state.activeScenario.candles,
                        tickSize = state.activeScenario.tickSize,
                        analysisQualityScore = state.latestEvaluation?.rubricScore ?: 1.0,
                        analysisOutcome = state.latestEvaluation?.outcome ?: AssessmentOutcome.PASS,
                        idempotencyKey = key
                    )
                    state.copy(simulationWorkspace = nextSim)
                }
            }
        }
    }

    fun rewindPlayhead() {
        _uiState.update { state ->
            when (state.activePlayheadMode) {
                PlayheadMode.FREE_CHART -> state.copy(freePlayhead = state.freePlayhead.rewind())
                PlayheadMode.CHALLENGE -> state.copy(challengePlayhead = state.challengePlayhead.rewind())
                PlayheadMode.SIMULATION -> state.copy(
                    simulationWorkspace = state.simulationWorkspace.copy(
                        playhead = state.simulationWorkspace.playhead.rewind()
                    )
                )
            }
        }
    }

    fun selectDecisionPointIndex(index: Int) {
        val scenario = _uiState.value.activeScenario
        val dp = scenario.decisionPoints.getOrNull(index) ?: return
        _uiState.update { state ->
            state.copy(
                activeDecisionPointIndex = index,
                challengePlayhead = state.challengePlayhead.copy(
                    currentBarIndex = dp.visibleDataThrough,
                    isDecisionLocked = false
                ),
                selectedOption = "",
                latestEvaluation = null
            )
        }
    }

    fun selectChallengeOption(option: String) {
        _uiState.update { it.copy(selectedOption = option) }
    }

    fun useHint() {
        _uiState.update { it.copy(hintUsedBeforeAnswer = true) }
    }

    fun submitChallengeAnswer() {
        val state = _uiState.value
        val scenario = state.activeScenario
        val dp = scenario.decisionPoints.getOrNull(state.activeDecisionPointIndex) ?: return
        val chosen = state.selectedOption.ifBlank { dp.options.firstOrNull().orEmpty() }

        val eval = when (dp.questionType) {
            QuestionType.PRICE_POINT -> {
                val submittedPrice = chosen.toDoubleOrNull() ?: (dp.targetPrice ?: 105.0)
                AnswerEvaluationEngine.evaluatePricePoint(
                    decisionPoint = dp,
                    submittedPrice = submittedPrice,
                    submittedBarIndex = dp.visibleDataThrough,
                    tickSize = scenario.tickSize
                )
            }
            QuestionType.ZONE_RECTANGLE -> {
                val targetZone = dp.targetZone ?: ZoneBounds(3, 6, 104.5, 105.7, "OB")
                val submittedZone = if (chosen == dp.acceptedAnswers.firstOrNull()) {
                    targetZone
                } else {
                    ZoneBounds(0, 2, 99.0, 101.0, "OB")
                }
                AnswerEvaluationEngine.evaluateZoneRectangle(dp, submittedZone)
            }
            else -> AnswerEvaluationEngine.evaluateChoice(dp, chosen)
        }

        val now = System.currentTimeMillis()
        val isFirstForScenario = state.attemptHistory.none {
            it.scenarioId == scenario.scenarioId && it.scenarioVersion == scenario.scenarioVersion
        }
        val attemptItem = AttemptHistoryItem(
            attemptId = "ATT-${scenario.scenarioId}-$now",
            scenarioId = scenario.scenarioId,
            scenarioVersion = scenario.scenarioVersion,
            skillId = scenario.primarySkillId,
            familyId = scenario.familyId,
            sessionId = "SESSION-${now / 3_600_000L}",
            rubricScore = eval.rubricScore,
            hintUsedBeforeAnswer = state.hintUsedBeforeAnswer,
            criticalError = eval.criticalError,
            outcome = eval.outcome,
            errorTag = eval.errorTag,
            timestampUtcMillis = now
        )

        _uiState.update { current ->
            current.copy(
                latestEvaluation = eval,
                challengePlayhead = current.challengePlayhead.copy(isDecisionLocked = true),
                attemptHistory = current.attemptHistory + attemptItem
            )
        }

        viewModelScope.launch {
            dao?.insertAttemptRecord(
                AttemptRecordEntity(
                    attemptId = attemptItem.attemptId,
                    scenarioId = attemptItem.scenarioId,
                    scenarioVersion = attemptItem.scenarioVersion,
                    skillId = attemptItem.skillId,
                    familyId = attemptItem.familyId,
                    sessionId = attemptItem.sessionId,
                    isFirstTryForScenario = isFirstForScenario,
                    rubricScore = attemptItem.rubricScore,
                    hintUsedBeforeAnswer = attemptItem.hintUsedBeforeAnswer,
                    criticalError = attemptItem.criticalError,
                    outcome = attemptItem.outcome.name,
                    errorTag = attemptItem.errorTag.name,
                    timestampUtcMillis = attemptItem.timestampUtcMillis
                )
            )
        }
        recomputeCoachAndMastery()
    }

    fun selectDrawingTool(tool: DrawingToolType) {
        _uiState.update { it.copy(activeDrawingTool = tool) }
    }

    fun addChartDrawing(drawing: ChartDrawing) {
        _uiState.update { it.copy(drawings = it.drawings + drawing) }
        viewModelScope.launch {
            dao?.upsertChartDrawing(
                ChartDrawingEntity(
                    drawingId = drawing.drawingId,
                    scenarioId = drawing.scenarioId,
                    toolType = drawing.toolType.name,
                    startBarIndex = drawing.startBarIndex,
                    endBarIndex = drawing.endBarIndex,
                    startPrice = drawing.startPrice,
                    endPrice = drawing.endPrice,
                    noteText = drawing.noteText,
                    createdAtPlayheadBar = drawing.createdAtPlayheadBar
                )
            )
        }
    }

    fun clearDrawingsForCurrentScenario() {
        val scenarioId = _uiState.value.activeScenario.scenarioId
        _uiState.update { state ->
            state.copy(drawings = state.drawings.filterNot { it.scenarioId == scenarioId })
        }
    }

    fun toggleAccessibilityTable() {
        _uiState.update { it.copy(showAccessibilityTable = !it.showAccessibilityTable) }
    }

    /**
     * Submits a paper trade order in the SimulationWorkspace with idempotency protection (double-tap safe).
     */
    fun submitSimulationOrder(
        direction: TradeDirection,
        entryMode: EntryMode,
        quantity: Double,
        limitOrReferenceEntryPrice: Double,
        stopPrice: Double,
        targetPrice: Double,
        idempotencyKey: String
    ) {
        _uiState.update { state ->
            val updatedSim = SimulationEngine.submitOrder(
                state = state.simulationWorkspace,
                allCandles = state.activeScenario.candles,
                tradeId = "TRD-${state.activeScenario.scenarioId}-${state.simulationWorkspace.completedTrades.size + 1}",
                direction = direction,
                entryMode = entryMode,
                quantity = quantity,
                limitOrReferenceEntryPrice = limitOrReferenceEntryPrice,
                stopPrice = stopPrice,
                targetPrice = targetPrice,
                feeAmount = 1.5,
                slippageTicks = 0.0,
                idempotencyKey = idempotencyKey
            )
            state.copy(
                simulationWorkspace = updatedSim,
                sessionActivityState = if (updatedSim.activeTrade != null) {
                    AppSessionActivityState.TRADE_OPEN
                } else {
                    state.sessionActivityState
                }
            )
        }
        persistLatestSimulationState()
    }

    fun stepSimulationForward(idempotencyKey: String = "STEP-${System.currentTimeMillis()}") {
        _uiState.update { state ->
            val updatedSim = SimulationEngine.stepForwardBar(
                state = state.simulationWorkspace,
                allCandles = state.activeScenario.candles,
                tickSize = state.activeScenario.tickSize,
                analysisQualityScore = state.latestEvaluation?.rubricScore ?: 1.0,
                analysisOutcome = state.latestEvaluation?.outcome ?: AssessmentOutcome.PASS,
                idempotencyKey = idempotencyKey
            )
            state.copy(simulationWorkspace = updatedSim)
        }
        persistLatestSimulationState()
    }

    fun closeSimulationManually(idempotencyKey: String = "CLOSE-MANUAL-${System.currentTimeMillis()}") {
        _uiState.update { state ->
            val updatedSim = SimulationEngine.closePositionManually(
                state = state.simulationWorkspace,
                allCandles = state.activeScenario.candles,
                analysisQualityScore = state.latestEvaluation?.rubricScore ?: 1.0,
                analysisOutcome = state.latestEvaluation?.outcome ?: AssessmentOutcome.PASS,
                idempotencyKey = idempotencyKey
            )
            state.copy(
                simulationWorkspace = updatedSim,
                sessionActivityState = AppSessionActivityState.REPORT_REVIEW
            )
        }
        persistLatestSimulationState()
    }

    fun resetSimulationWorkspace() {
        resetPlayheadsForScenario(_uiState.value.activeScenario, _uiState.value.activeDecisionPointIndex)
    }

    private fun persistLatestSimulationState() {
        val dbDao = dao ?: return
        val sim = _uiState.value.simulationWorkspace
        viewModelScope.launch {
            val allTrades = listOfNotNull(sim.activeTrade) + sim.completedTrades
            for (t in allTrades) {
                dbDao.upsertPaperTrade(
                    PaperTradeEntity(
                        tradeId = t.tradeId,
                        workspaceId = t.workspaceId,
                        scenarioId = t.scenarioId,
                        scenarioVersion = t.scenarioVersion,
                        direction = t.direction.name,
                        entryMode = t.entryMode.name,
                        quantity = t.quantity,
                        plannedEntryPrice = t.plannedEntryPrice,
                        executedEntryPrice = t.executedEntryPrice,
                        stopPrice = t.stopPrice,
                        targetPrice = t.targetPrice,
                        exitPrice = t.exitPrice,
                        status = t.status.name,
                        createdAtBarIndex = t.createdAtBarIndex,
                        openedAtBarIndex = t.openedAtBarIndex,
                        closedAtBarIndex = t.closedAtBarIndex,
                        feeAmount = t.feeAmount,
                        slippageTicks = t.slippageTicks,
                        stopFirstConflictTriggered = t.stopFirstConflictTriggered,
                        engineVersion = t.engineVersion,
                        executionPolicyId = t.executionPolicyId
                    )
                )
            }
            for (ev in sim.tradeEvents) {
                dbDao.insertTradeEventIdempotent(
                    TradeEventEntity(
                        idempotencyKey = ev.idempotencyKey,
                        eventId = ev.eventId,
                        tradeId = ev.tradeId,
                        workspaceId = ev.workspaceId,
                        eventSequence = ev.eventSequence,
                        barIndex = ev.barIndex,
                        timestampUtcMillis = ev.timestampUtcMillis,
                        fromStatus = ev.fromStatus?.name,
                        toStatus = ev.toStatus.name,
                        reason = ev.reason
                    )
                )
            }
        }
    }

    private fun recomputeCoachAndMastery() {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val completedLessonIds = state.lessons.filter { it.isCompleted }.map { it.lessonId }.toSet()
        val distinctSkills = state.scenarios.map { it.primarySkillId }.distinct()

        val masteries = distinctSkills.associateWith { skillId ->
            AdaptiveCoachEngine.computeSkillMastery(
                skillId = skillId,
                hasCompletedIntroLesson = state.lessons.any { it.skillId == skillId && it.isCompleted },
                allAttemptsForSkill = state.attemptHistory.filter { it.skillId == skillId },
                currentTimeUtcMillis = now
            )
        }

        val rec = AdaptiveCoachEngine.selectNextRecommendation(
            availableScenarios = state.scenarios,
            allAttempts = state.attemptHistory,
            completedLessonIds = completedLessonIds,
            currentTimeUtcMillis = now
        )

        _uiState.update {
            it.copy(
                skillMasteries = masteries,
                coachRecommendation = rec
            )
        }
    }

    fun updateGlossarySearch(query: String) {
        _uiState.update { it.copy(glossarySearchQuery = query) }
    }

    /**
     * Triggers Pack Verification & Atomic Activation (or Rollback test).
     */
    fun verifyAndActivatePack(simulateTamperedHash: Boolean = false, simulateLowStorage: Boolean = false) {
        val state = _uiState.value
        val payload = "HISTORICAL_PACK_EURUSD_XAUUSD_2026_V2_VERIFIED_DATA".toByteArray()
        val validSha = PackManagerEngine.computeSha256Hex(payload)
        val declaredSha = if (simulateTamperedHash) "000000000000000000000000000000000000000000000000000000000000dead" else validSha
        val canonical = PackManagerEngine.canonicalManifestString(
            packId = "HIST_PACK_2026_V2",
            packVersion = "2.0.0",
            keyId = "SMC_PACK_SIGNING_KEY_2026_V1",
            schemaVersion = 2,
            minAppVersion = 1,
            sizeBytes = payload.size.toLong(),
            sha256Hex = declaredSha,
            dataKind = "HISTORICAL_SOURCE",
            sourceId = "LICENSED_INSTITUTIONAL_FEED",
            licenseId = "COMMERCIAL_OFFLINE_APPROVED_V1"
        )
        val sig = PackManagerEngine.signCanonicalManifest(canonical)

        val candidate = PackCandidate(
            packId = "HIST_PACK_2026_V2",
            packVersion = "2.0.0",
            keyId = "SMC_PACK_SIGNING_KEY_2026_V1",
            schemaVersion = 2,
            minAppVersion = 1,
            payloadBytes = payload,
            declaredSizeBytes = payload.size.toLong(),
            declaredSha256Hex = declaredSha,
            signatureHex = sig
        )

        val storageBytes = if (simulateLowStorage) 10L else 50_000_000L
        val result = PackManagerEngine.stageVerifyAndActivatePack(
            candidate = candidate,
            currentActivePack = state.activePackManifest,
            availableStorageBytes = storageBytes
        )

        _uiState.update { current ->
            current.copy(
                activePackManifest = result.activePack ?: current.activePackManifest,
                previousValidPackManifest = result.previousValidPack ?: current.previousValidPackManifest,
                lastPackOperationResult = result
            )
        }
    }

    /**
     * Creates a local backup snapshot and supports restoring in MERGE or REPLACE mode.
     */
    fun exportLocalBackup() {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val bundle = LocalBackupBundle(
            backupVersion = 1,
            exportedAtUtcMillis = now,
            lessonProgress = state.lessons.filter { it.isCompleted }.map {
                LessonProgressEntity(it.lessonId, true, now, 1.0)
            },
            attempts = state.attemptHistory.map {
                AttemptRecordEntity(
                    attemptId = it.attemptId,
                    scenarioId = it.scenarioId,
                    scenarioVersion = it.scenarioVersion,
                    skillId = it.skillId,
                    familyId = it.familyId,
                    sessionId = it.sessionId,
                    isFirstTryForScenario = true,
                    rubricScore = it.rubricScore,
                    hintUsedBeforeAnswer = it.hintUsedBeforeAnswer,
                    criticalError = it.criticalError,
                    outcome = it.outcome.name,
                    errorTag = it.errorTag.name,
                    timestampUtcMillis = it.timestampUtcMillis
                )
            },
            trades = state.simulationWorkspace.completedTrades.map { t ->
                PaperTradeEntity(
                    tradeId = t.tradeId,
                    workspaceId = t.workspaceId,
                    scenarioId = t.scenarioId,
                    scenarioVersion = t.scenarioVersion,
                    direction = t.direction.name,
                    entryMode = t.entryMode.name,
                    quantity = t.quantity,
                    plannedEntryPrice = t.plannedEntryPrice,
                    executedEntryPrice = t.executedEntryPrice,
                    stopPrice = t.stopPrice,
                    targetPrice = t.targetPrice,
                    exitPrice = t.exitPrice,
                    status = t.status.name,
                    createdAtBarIndex = t.createdAtBarIndex,
                    openedAtBarIndex = t.openedAtBarIndex,
                    closedAtBarIndex = t.closedAtBarIndex,
                    feeAmount = t.feeAmount,
                    slippageTicks = t.slippageTicks,
                    stopFirstConflictTriggered = t.stopFirstConflictTriggered,
                    engineVersion = t.engineVersion,
                    executionPolicyId = t.executionPolicyId
                )
            },
            drawings = state.drawings.map { d ->
                ChartDrawingEntity(
                    drawingId = d.drawingId,
                    scenarioId = d.scenarioId,
                    toolType = d.toolType.name,
                    startBarIndex = d.startBarIndex,
                    endBarIndex = d.endBarIndex,
                    startPrice = d.startPrice,
                    endPrice = d.endPrice,
                    noteText = d.noteText,
                    createdAtPlayheadBar = d.createdAtPlayheadBar
                )
            }
        )
        _uiState.update {
            it.copy(
                lastBackupSnapshot = bundle,
                backupStatusMessageAr = "تم إنشاء نسخة احتياطية محلية (${bundle.lessonProgress.size} درس، ${bundle.attempts.size} محاولة، ${bundle.trades.size} صفقة).",
                backupStatusMessageEn = "Local backup created (${bundle.lessonProgress.size} lessons, ${bundle.attempts.size} attempts, ${bundle.trades.size} trades)."
            )
        }
    }

    fun restoreLocalBackup(mode: BackupRestoreMode) {
        val state = _uiState.value
        val incoming = state.lastBackupSnapshot ?: return
        val now = System.currentTimeMillis()
        val existing = LocalBackupBundle(
            backupVersion = 1,
            exportedAtUtcMillis = now,
            lessonProgress = state.lessons.filter { it.isCompleted }.map {
                LessonProgressEntity(it.lessonId, true, now, 1.0)
            },
            attempts = state.attemptHistory.map {
                AttemptRecordEntity(
                    attemptId = it.attemptId,
                    scenarioId = it.scenarioId,
                    scenarioVersion = it.scenarioVersion,
                    skillId = it.skillId,
                    familyId = it.familyId,
                    sessionId = it.sessionId,
                    isFirstTryForScenario = true,
                    rubricScore = it.rubricScore,
                    hintUsedBeforeAnswer = it.hintUsedBeforeAnswer,
                    criticalError = it.criticalError,
                    outcome = it.outcome.name,
                    errorTag = it.errorTag.name,
                    timestampUtcMillis = it.timestampUtcMillis
                )
            },
            trades = emptyList(),
            drawings = emptyList()
        )

        val restored = LocalBackupEngine.applyRestore(existing, incoming, mode)
        val completedIds = restored.lessonProgress.filter { it.isCompleted }.map { it.lessonId }.toSet()

        _uiState.update { current ->
            current.copy(
                lessons = current.lessons.map { l -> l.copy(isCompleted = l.lessonId in completedIds) },
                backupStatusMessageAr = "تمت استعادة النسخة الاحتياطية بنجاح بوضع ${mode.name} (${completedIds.size} درس مكتمل).",
                backupStatusMessageEn = "Backup restored successfully in ${mode.name} mode (${completedIds.size} completed lessons)."
            )
        }
        recomputeCoachAndMastery()
    }
}
