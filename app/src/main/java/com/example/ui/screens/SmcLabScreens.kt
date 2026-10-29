package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.AdEligibilityCoordinator
import com.example.domain.engine.BackupRestoreMode
import com.example.domain.model.AssessmentOutcome
import com.example.domain.model.DrawingToolType
import com.example.domain.model.EntryMode
import com.example.domain.model.PlayheadMode
import com.example.domain.model.TradeDirection
import com.example.ui.SmcLabTab
import com.example.ui.SmcLabUiState
import com.example.ui.SmcLabViewModel
import com.example.ui.chart.InteractiveOhlcChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmcLabMainScreen(
    viewModel: SmcLabViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutDirection = if (state.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    if (state.currentTab != SmcLabTab.CURRICULUM_COACH) {
        BackHandler {
            viewModel.selectTab(SmcLabTab.CURRICULUM_COACH)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (state.isArabic) "مختبر الأموال الذكية — SMC Lab" else "SMC Lab — Smart Money Concepts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.testTag("top_bar_title")
                            )
                            Text(
                                text = if (state.isArabic) {
                                    "41 درساً • 96 سيناريو • Playhead صارم • مدرب Wilson L95"
                                } else {
                                    "41 Lessons • 96 Scenarios • Strict Playhead • Wilson L95 Coach"
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier.testTag("toggle_language_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = if (state.isArabic) "Switch to English LTR" else "التبديل إلى العربية RTL",
                                tint = Color(0xFFF59E0B)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF111827),
                        titleContentColor = Color(0xFFF8FAFC)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF111827),
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = state.currentTab == SmcLabTab.CURRICULUM_COACH,
                        onClick = { viewModel.selectTab(SmcLabTab.CURRICULUM_COACH) },
                        icon = { Icon(Icons.Default.School, contentDescription = "Curriculum") },
                        label = { Text(if (state.isArabic) "المنهج والمدرب" else "Curriculum", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_curriculum")
                    )
                    NavigationBarItem(
                        selected = state.currentTab == SmcLabTab.CHART_CHALLENGE,
                        onClick = { viewModel.selectTab(SmcLabTab.CHART_CHALLENGE) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Chart Challenge") },
                        label = { Text(if (state.isArabic) "الشارت والتحدي" else "Chart & BOS", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_chart")
                    )
                    NavigationBarItem(
                        selected = state.currentTab == SmcLabTab.SIMULATION,
                        onClick = { viewModel.selectTab(SmcLabTab.SIMULATION) },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Simulation") },
                        label = { Text(if (state.isArabic) "المحاكاة" else "Simulation", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_simulation")
                    )
                    NavigationBarItem(
                        selected = state.currentTab == SmcLabTab.GLOSSARY,
                        onClick = { viewModel.selectTab(SmcLabTab.GLOSSARY) },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "Glossary") },
                        label = { Text(if (state.isArabic) "القاموس" else "Glossary", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_glossary")
                    )
                    NavigationBarItem(
                        selected = state.currentTab == SmcLabTab.PACKS_BACKUP,
                        onClick = { viewModel.selectTab(SmcLabTab.PACKS_BACKUP) },
                        icon = { Icon(Icons.Default.Security, contentDescription = "Packs & Backup") },
                        label = { Text(if (state.isArabic) "الحزم والنسخ" else "Packs", fontSize = 11.sp) },
                        modifier = Modifier.testTag("nav_tab_packs")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0F17))
                    .padding(innerPadding)
            ) {
                when (state.currentTab) {
                    SmcLabTab.CURRICULUM_COACH -> CurriculumAndCoachScreen(state, viewModel)
                    SmcLabTab.CHART_CHALLENGE -> ChartAndChallengeScreen(state, viewModel)
                    SmcLabTab.SIMULATION -> SimulationWorkspaceScreen(state, viewModel)
                    SmcLabTab.GLOSSARY -> GlossaryScreen(state, viewModel)
                    SmcLabTab.PACKS_BACKUP -> PacksAndBackupScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun CurriculumAndCoachScreen(
    state: SmcLabUiState,
    viewModel: SmcLabViewModel
) {
    val rec = state.coachRecommendation
    val adAllowed = AdEligibilityCoordinator.isAdAllowed(
        sessionState = state.sessionActivityState,
        isDrawingAnnotationActive = state.activeDrawingTool != DrawingToolType.NONE,
        hasActiveOpenTrade = state.simulationWorkspace.activeTrade != null
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("curriculum_screen_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Adaptive Coach Deterministic Recommendation Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("adaptive_coach_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.isArabic) "المدرب المتكيف الحتمي (Wilson L95)" else "Deterministic Adaptive Coach (Wilson L95)",
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "v1.0 Offline",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (rec != null) {
                        Text(
                            text = if (state.isArabic) {
                                "التمرين الموصى به الآن: ${rec.scenario.titleAr}"
                            } else {
                                "Recommended Next Exercise: ${rec.scenario.titleEn}"
                            },
                            color = Color(0xFFF8FAFC),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.isArabic) rec.reasonAr else rec.reasonEn,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.selectScenario(rec.scenario) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            modifier = Modifier.testTag("start_recommended_scenario_button")
                        ) {
                            Text(
                                text = if (state.isArabic) "ابدأ التحدي الموصى به" else "Start Recommended Challenge",
                                color = Color(0xFF0B0F17),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Selected Lesson Reader & Worked Example (Phase 2 Vertical Slice Lesson View)
        item {
            val lesson = state.selectedLesson
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                    .testTag("active_lesson_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.isArabic) lesson.titleAr else lesson.titleEn,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.isArabic) {
                            "الهدف القابل للقياس: ${lesson.measurableGoalAr} | المهارة: ${lesson.skillId}"
                        } else {
                            "Measurable Goal: ${lesson.measurableGoalEn} | Skill: ${lesson.skillId}"
                        },
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (state.isArabic) lesson.contentAr else lesson.contentEn,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.isArabic) lesson.workedExampleAr else lesson.workedExampleEn,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                viewModel.markLessonCompleted(lesson.lessonId)
                                viewModel.selectScenario(state.activeScenario)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.testTag("complete_lesson_and_practice_button")
                        ) {
                            Text(
                                text = if (state.isArabic) "إكمال الدرس وبدء تمرين الشارت" else "Complete & Open Chart Challenge",
                                color = Color(0xFF0B0F17),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. AdEligibilityCoordinator Non-Intrusive Status Banner (only on non-interactive browsing)
        item {
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ad_eligibility_banner")
            ) {
                Text(
                    text = if (state.isArabic) {
                        "منسق الإعلانات المعزول (AdEligibilityCoordinator): مسموح بالعرض هنا = $adAllowed (يُحظر تماماً داخل الشارت والتحديات والمحاكاة)"
                    } else {
                        "AdEligibilityCoordinator: Ad Eligible Here = $adAllowed (Strictly blocked during Chart, Challenge, and Simulation)"
                    },
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 4. All 41 Curriculum Lessons List
        item {
            Text(
                text = if (state.isArabic) {
                    "المنهج الكامل (${state.lessons.size} درساً معتمداً) و 96 سيناريو تدريبي:"
                } else {
                    "Full Curriculum (${state.lessons.size} Validated Lessons) & 96 Scenarios:"
                },
                color = Color(0xFFF8FAFC),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(state.lessons, key = { it.lessonId }) { lesson ->
            val mastery = state.skillMasteries[lesson.skillId]
            Surface(
                color = if (lesson.lessonId == state.selectedLesson.lessonId) Color(0xFF1E293B) else Color(0xFF111827),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectLesson(lesson) }
                    .testTag("lesson_item_${lesson.lessonId}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isArabic) lesson.titleAr else lesson.titleEn,
                            color = Color(0xFFF8FAFC),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (state.isArabic) lesson.measurableGoalAr else lesson.measurableGoalEn,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        if (mastery != null) {
                            val l95Text = mastery.wilsonL95?.let { "%.2f".format(it) } ?: "N/A"
                            Text(
                                text = "Skill: ${lesson.skillId} • ${mastery.status.name} (n=${mastery.independentScenarioCount}, L95=$l95Text)",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (lesson.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartAndChallengeScreen(
    state: SmcLabUiState,
    viewModel: SmcLabViewModel
) {
    val scenario = state.activeScenario
    val playhead = state.currentPlayhead
    val dp = scenario.decisionPoints.getOrNull(state.activeDecisionPointIndex) ?: scenario.decisionPoints.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("chart_challenge_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Scenario Header & Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isArabic) scenario.titleAr else scenario.titleEn,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.testTag("active_scenario_title")
                )
                Text(
                    text = "${scenario.scenarioId} • ${scenario.dataKind.name} • Rule: ${scenario.ruleId}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
            OutlinedButton(
                onClick = { viewModel.toggleAccessibilityTable() },
                modifier = Modifier.testTag("toggle_a11y_table_button")
            ) {
                Text(
                    text = if (state.showAccessibilityTable) {
                        if (state.isArabic) "عرض الرسم" else "Canvas View"
                    } else {
                        if (state.isArabic) "جدول TalkBack" else "TalkBack Table"
                    },
                    fontSize = 11.sp
                )
            }
        }

        // Playhead Mode Switcher (FREE_CHART / CHALLENGE / SIMULATION)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayheadMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.activePlayheadMode == mode,
                    onClick = { viewModel.switchPlayheadMode(mode) },
                    label = { Text(mode.name, fontSize = 11.sp) },
                    modifier = Modifier.testTag("mode_chip_${mode.name}")
                )
            }
            scenario.decisionPoints.forEachIndexed { idx, point ->
                FilterChip(
                    selected = state.activeDecisionPointIndex == idx,
                    onClick = { viewModel.selectDecisionPointIndex(idx) },
                    label = { Text("${point.decisionPointId} (@#${point.visibleDataThrough})", fontSize = 11.sp) },
                    modifier = Modifier.testTag("dp_chip_${point.decisionPointId}")
                )
            }
        }

        // Interactive OHLC Chart with strict Playhead Gate
        InteractiveOhlcChart(
            playhead = playhead,
            allCandles = scenario.candles,
            referenceLevel = scenario.referenceLevel,
            drawings = state.drawings.filter { it.scenarioId == scenario.scenarioId },
            activeTool = state.activeDrawingTool,
            activeTrade = state.simulationWorkspace.activeTrade,
            isArabic = state.isArabic,
            showAccessibilityTable = state.showAccessibilityTable,
            onAddDrawing = { viewModel.addChartDrawing(it) },
            onCandleSelected = {}
        )

        // Playhead Stepper & Drawing Tools Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { viewModel.rewindPlayhead() },
                    enabled = playhead.canRewind(),
                    modifier = Modifier.testTag("playhead_rewind_button")
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Rewind")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (state.isArabic) "رجوع" else "Rewind", fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.stepPlayheadForward() },
                    enabled = playhead.canStepForward(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    modifier = Modifier.testTag("playhead_step_forward_button")
                ) {
                    Text(if (state.isArabic) "تقدم شمعة" else "Step Bar", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.FastForward, contentDescription = "Step Forward")
                }
            }

            Text(
                text = if (state.isArabic) {
                    "الشموع المرئية: ${playhead.visibleDataThrough(scenario.candles).size}/${scenario.candles.size}"
                } else {
                    "Visible: ${playhead.visibleDataThrough(scenario.candles).size}/${scenario.candles.size}"
                },
                color = Color(0xFFF59E0B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("visible_candles_count_label")
            )
        }

        // 4 Drawing Tools Bar (Horizontal Line, Trend Line, Rectangle Zone, Text Note)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tools = listOf(
                DrawingToolType.HORIZONTAL_LINE to (if (state.isArabic) "خط أفقي" else "H-Line"),
                DrawingToolType.TREND_LINE to (if (state.isArabic) "خط اتجاه" else "Trendline"),
                DrawingToolType.RECTANGLE_ZONE to (if (state.isArabic) "منطقة مستطيل" else "Zone Rect"),
                DrawingToolType.TEXT_NOTE to (if (state.isArabic) "ملاحظة" else "Note")
            )
            tools.forEach { (tool, label) ->
                FilterChip(
                    selected = state.activeDrawingTool == tool,
                    onClick = {
                        viewModel.selectDrawingTool(
                            if (state.activeDrawingTool == tool) DrawingToolType.NONE else tool
                        )
                    },
                    label = { Text(label, fontSize = 11.sp) },
                    modifier = Modifier.testTag("drawing_tool_${tool.name}")
                )
            }
            if (state.drawings.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.clearDrawingsForCurrentScenario() },
                    modifier = Modifier.testTag("clear_drawings_button")
                ) {
                    Text(if (state.isArabic) "مسح الرسوم" else "Clear", fontSize = 11.sp)
                }
            }
        }

        // Decision Point Question & Evaluation Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .testTag("challenge_decision_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (state.isArabic) dp.promptAr else dp.promptEn,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.testTag("decision_prompt_text")
                )
                Spacer(modifier = Modifier.height(8.dp))

                dp.options.forEach { option ->
                    val isSelected = state.selectedOption == option
                    Surface(
                        color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { viewModel.selectChallengeOption(option) }
                            .testTag("option_item_$option")
                    ) {
                        Text(
                            text = option,
                            color = Color(0xFFF8FAFC),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.submitChallengeAnswer() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.testTag("submit_challenge_answer_button")
                    ) {
                        Text(
                            text = if (state.isArabic) "تحقق من الإجابة" else "Evaluate Answer",
                            color = Color(0xFF0B0F17),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.useHint() },
                        modifier = Modifier.testTag("use_hint_button")
                    ) {
                        Text(
                            text = if (state.isArabic) "تلميح (يمنع نقاط الإتقان)" else "Hint (No Mastery Credit)",
                            fontSize = 12.sp
                        )
                    }
                }

                if (state.hintUsedBeforeAnswer) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (state.isArabic) {
                            "تلميح: راقب هل أغلقت الشمعة فوق العتبة (${(scenario.referenceLevel?.price ?: 105.0) + scenario.tickSize}) أم اكتفت بتجاوز الذيل."
                        } else {
                            "Hint: Check whether the candle body closed beyond ${(scenario.referenceLevel?.price ?: 105.0) + scenario.tickSize} or only swept with its wick."
                        },
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp
                    )
                }

                // Evaluation Feedback Box
                val eval = state.latestEvaluation
                if (eval != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val badgeColor = when (eval.outcome) {
                        AssessmentOutcome.PASS -> Color(0xFF10B981)
                        AssessmentOutcome.FAIL -> Color(0xFFEF4444)
                        AssessmentOutcome.UNASSESSABLE -> Color(0xFFF59E0B)
                    }
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                            .testTag("evaluation_feedback_box")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Outcome: ${eval.outcome.name} (Score: ${"%.2f".format(eval.rubricScore)})",
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.testTag("evaluation_outcome_label")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.isArabic) eval.feedbackAr else eval.feedbackEn,
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulationWorkspaceScreen(
    state: SmcLabUiState,
    viewModel: SmcLabViewModel
) {
    val sim = state.simulationWorkspace
    val scenario = state.activeScenario
    val visibleCandles = sim.playhead.visibleDataThrough(scenario.candles)
    val currentClose = visibleCandles.lastOrNull()?.close ?: 105.2

    var selectedDirection by remember { mutableStateOf(TradeDirection.LONG) }
    var selectedEntryMode by remember { mutableStateOf(EntryMode.MARKET_NEXT_OPEN) }
    var stopPriceText by remember(currentClose) { mutableStateOf("%.1f".format(currentClose - 1.0)) }
    var targetPriceText by remember(currentClose) { mutableStateOf("%.1f".format(currentClose + 2.5)) }
    var limitPriceText by remember(currentClose) { mutableStateOf("%.1f".format(currentClose - 0.2)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("simulation_workspace_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Workspace Telemetry Header
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (state.isArabic) {
                        "مساحة المحاكاة الورقية (${sim.workspaceId}) — مركز واحد نشط كحد أقصى"
                    } else {
                        "Paper Simulation Workspace (${sim.workspaceId}) — Max 1 Active Position"
                    },
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val dailyLossPct = sim.computeDailyLossPct(currentClose) * 100.0
                Text(
                    text = "Equity: $${"%.2f".format(sim.startingEquity + sim.totalRealizedNetPnl)} | Daily Loss (UTC 00:00): ${"%.2f".format(dailyLossPct)}% / 3.00% | Playhead: #${sim.playhead.currentBarIndex}/${sim.playhead.maxBarIndex}",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("simulation_equity_summary")
                )
            }
        }

        // Chart inside Simulation Workspace
        InteractiveOhlcChart(
            playhead = sim.playhead,
            allCandles = scenario.candles,
            referenceLevel = scenario.referenceLevel,
            drawings = state.drawings.filter { it.scenarioId == scenario.scenarioId },
            activeTool = DrawingToolType.NONE,
            activeTrade = sim.activeTrade,
            isArabic = state.isArabic,
            showAccessibilityTable = false,
            onAddDrawing = {},
            onCandleSelected = {}
        )

        // Order Controls & Step Bar Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedDirection == TradeDirection.LONG,
                        onClick = {
                            selectedDirection = TradeDirection.LONG
                            stopPriceText = "%.1f".format(currentClose - 1.0)
                            targetPriceText = "%.1f".format(currentClose + 2.5)
                        },
                        label = { Text("LONG") },
                        modifier = Modifier.testTag("sim_dir_long")
                    )
                    FilterChip(
                        selected = selectedDirection == TradeDirection.SHORT,
                        onClick = {
                            selectedDirection = TradeDirection.SHORT
                            stopPriceText = "%.1f".format(currentClose + 1.0)
                            targetPriceText = "%.1f".format(currentClose - 2.5)
                        },
                        label = { Text("SHORT") },
                        modifier = Modifier.testTag("sim_dir_short")
                    )
                    FilterChip(
                        selected = selectedEntryMode == EntryMode.MARKET_NEXT_OPEN,
                        onClick = { selectedEntryMode = EntryMode.MARKET_NEXT_OPEN },
                        label = { Text("MARKET_NEXT_OPEN") },
                        modifier = Modifier.testTag("sim_mode_market")
                    )
                    FilterChip(
                        selected = selectedEntryMode == EntryMode.LIMIT_PRICE,
                        onClick = { selectedEntryMode = EntryMode.LIMIT_PRICE },
                        label = { Text("LIMIT_PRICE") },
                        modifier = Modifier.testTag("sim_mode_limit")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stopPriceText,
                        onValueChange = { stopPriceText = it },
                        label = { Text("SL Price") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sim_stop_input")
                    )
                    OutlinedTextField(
                        value = targetPriceText,
                        onValueChange = { targetPriceText = it },
                        label = { Text("TP Price") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sim_target_input")
                    )
                    if (selectedEntryMode == EntryMode.LIMIT_PRICE) {
                        OutlinedTextField(
                            value = limitPriceText,
                            onValueChange = { limitPriceText = it },
                            label = { Text("Limit Px") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_limit_input")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val sl = stopPriceText.toDoubleOrNull() ?: (currentClose - 1.0)
                            val tp = targetPriceText.toDoubleOrNull() ?: (currentClose + 2.5)
                            val lim = limitPriceText.toDoubleOrNull() ?: currentClose
                            viewModel.submitSimulationOrder(
                                direction = selectedDirection,
                                entryMode = selectedEntryMode,
                                quantity = 10.0,
                                limitOrReferenceEntryPrice = lim,
                                stopPrice = sl,
                                targetPrice = tp,
                                idempotencyKey = "ORDER-${sim.playhead.currentBarIndex}-${selectedDirection.name}-${selectedEntryMode.name}"
                            )
                        },
                        enabled = sim.activeTrade == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.testTag("sim_open_order_button")
                    ) {
                        Text(
                            text = if (state.isArabic) "إرسال أمر الصفقة" else "Submit Order",
                            color = Color(0xFF0B0F17),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { viewModel.stepSimulationForward() },
                        enabled = sim.playhead.canStepForward(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        modifier = Modifier.testTag("sim_step_bar_button")
                    ) {
                        Text(if (state.isArabic) "تقدم شمعة" else "Step Bar")
                    }

                    if (sim.activeTrade != null) {
                        OutlinedButton(
                            onClick = { viewModel.closeSimulationManually() },
                            modifier = Modifier.testTag("sim_close_manual_button")
                        ) {
                            Text(if (state.isArabic) "إغلاق يدوي" else "Close Manually")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.resetSimulationWorkspace() },
                            modifier = Modifier.testTag("sim_reset_workspace_button")
                        ) {
                            Text(if (state.isArabic) "إعادة ضبط" else "Reset")
                        }
                    }
                }

                if (sim.lastErrorAr != null) {
                    Text(
                        text = if (state.isArabic) sim.lastErrorAr else sim.lastErrorEn.orEmpty(),
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("sim_error_banner")
                    )
                }

                // Active Trade Status
                val active = sim.activeTrade
                if (active != null) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sim_active_trade_box")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Active Trade: ${active.tradeId} [${active.status.name}] • ${active.direction.name} (${active.entryMode.name})",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Planned Entry: ${active.plannedEntryPrice} | Executed: ${active.executedEntryPrice ?: "Pending Next Open"} | SL: ${active.stopPrice} | TP: ${active.targetPrice} | Risk: $${"%.2f".format(active.plannedPriceRisk)}",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Three-Pillar Simulation Report (analysisQuality / executionDiscipline / simulationOutcome)
        val report = sim.latestReport
        if (report != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                    .testTag("simulation_report_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (state.isArabic) {
                            "تقرير المحاكاة ثلاثي المحاور (منفصل تماماً)"
                        } else {
                            "Three-Pillar Simulation Report (Strictly Separated)"
                        },
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "1) analysisQuality: ${(report.analysisQualityScore * 100).toInt()}% (${report.analysisOutcome.name})",
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "2) executionDiscipline: ${(report.executionDisciplineScore * 100).toInt()}% | Violations: ${if (report.executionViolations.isEmpty()) "None" else report.executionViolations.joinToString()}",
                        color = Color(0xFFA855F7),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "3) simulationOutcome [${report.exitStatus.name}]: R_net = ${"%.2f".format(report.rNet)}R | R_gross = ${"%.2f".format(report.rGross)}R | Net P&L = $${"%.2f".format(report.netPnl)} | Gross P&L = $${"%.2f".format(report.grossPnl)} | Planned Risk = $${"%.2f".format(report.plannedPriceRisk)}",
                        color = if (report.netPnl >= 0.0) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("simulation_report_r_values")
                    )
                    if (report.stopFirstConflictNote != null) {
                        Text(
                            text = report.stopFirstConflictNote,
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlossaryScreen(
    state: SmcLabUiState,
    viewModel: SmcLabViewModel
) {
    val filtered = remember(state.glossaryTerms, state.glossarySearchQuery) {
        if (state.glossarySearchQuery.isBlank()) {
            state.glossaryTerms
        } else {
            val q = state.glossarySearchQuery.trim()
            state.glossaryTerms.filter {
                it.code.contains(q, ignoreCase = true) ||
                        it.titleAr.contains(q, ignoreCase = true) ||
                        it.titleEn.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("glossary_screen")
    ) {
        OutlinedTextField(
            value = state.glossarySearchQuery,
            onValueChange = { viewModel.updateGlossarySearch(it) },
            label = {
                Text(if (state.isArabic) "ابحث في قاموس مصطلحات SMC..." else "Search SMC Glossary...")
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("glossary_search_input")
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.termId }) { term ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("glossary_item_${term.code}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "[${term.code}] ${if (state.isArabic) term.titleAr else term.titleEn}",
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.isArabic) term.definitionAr else term.definitionEn,
                            color = Color(0xFFF8FAFC),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.isArabic) term.operationalNoteAr else term.operationalNoteEn,
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PacksAndBackupScreen(
    state: SmcLabUiState,
    viewModel: SmcLabViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("packs_backup_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Signed Data Pack Manager Card (SHA-256 + Ed25519 + Atomic Activation + Rollback)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (state.isArabic) {
                        "إدارة الحزم الموقعة (SHA-256 + Ed25519 + Atomic Activate)"
                    } else {
                        "Signed Content Pack Manager (SHA-256 + Ed25519 + Atomic Activate)"
                    },
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Active Pack: ${state.activePackManifest.packId} (v${state.activePackManifest.packVersion}) • Status: ${state.activePackManifest.status}",
                    color = Color(0xFF10B981),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("active_pack_label")
                )
                Text(
                    text = "SHA-256: ${state.activePackManifest.sha256Hex.take(24)}... | Key: ${state.activePackManifest.keyId}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                if (state.previousValidPackManifest != null) {
                    Text(
                        text = "Retained Previous Valid Pack: ${state.previousValidPackManifest.packId} (v${state.previousValidPackManifest.packVersion})",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("previous_valid_pack_label")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.verifyAndActivatePack(simulateTamperedHash = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.testTag("activate_valid_pack_button")
                    ) {
                        Text(
                            text = if (state.isArabic) "تفعيل حزمة v2 موقعة" else "Verify & Activate v2 Pack",
                            color = Color(0xFF0B0F17),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.verifyAndActivatePack(simulateTamperedHash = true) },
                        modifier = Modifier.testTag("simulate_tampered_pack_button")
                    ) {
                        Text(
                            text = if (state.isArabic) "اختبار Rollback عند فشل Hash" else "Test Hash Fail Rollback",
                            fontSize = 12.sp
                        )
                    }
                }

                val packRes = state.lastPackOperationResult
                if (packRes != null) {
                    Text(
                        text = if (state.isArabic) packRes.reasonAr else packRes.reasonEn,
                        color = if (packRes.success) Color(0xFF10B981) else Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("pack_operation_result_text")
                    )
                }
            }
        }

        // 2. Local Backup & Restore Card (MERGE / REPLACE)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (state.isArabic) {
                        "النسخ الاحتياطي والاستعادة المحلية (MERGE / REPLACE)"
                    } else {
                        "Local Backup & Restore (MERGE / REPLACE)"
                    },
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.exportLocalBackup() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        modifier = Modifier.testTag("export_backup_button")
                    ) {
                        Text(if (state.isArabic) "تصدير نسخة محلية" else "Export Local Backup", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.restoreLocalBackup(BackupRestoreMode.MERGE) },
                        enabled = state.lastBackupSnapshot != null,
                        modifier = Modifier.testTag("restore_merge_button")
                    ) {
                        Text(if (state.isArabic) "استعادة (MERGE)" else "Restore (MERGE)", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.restoreLocalBackup(BackupRestoreMode.REPLACE) },
                        enabled = state.lastBackupSnapshot != null,
                        modifier = Modifier.testTag("restore_replace_button")
                    ) {
                        Text(if (state.isArabic) "استعادة (REPLACE)" else "Restore (REPLACE)", fontSize = 12.sp)
                    }
                }
                if (state.backupStatusMessageAr != null) {
                    Text(
                        text = if (state.isArabic) state.backupStatusMessageAr else state.backupStatusMessageEn.orEmpty(),
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("backup_status_message")
                    )
                }
            }
        }
    }
}
