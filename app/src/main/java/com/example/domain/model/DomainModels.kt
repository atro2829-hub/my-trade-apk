package com.example.domain.model

enum class PlayheadMode {
    FREE_CHART,
    CHALLENGE,
    SIMULATION
}

enum class DataKind {
    SYNTHETIC_ORIGINAL,
    HISTORICAL_SOURCE,
    HISTORICAL_RESAMPLED,
    MIXED_DERIVED
}

enum class DifficultyLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT_TRAINING
}

enum class QuestionType {
    SINGLE_SELECT,
    MULTI_SELECT,
    PRICE_POINT,
    LEVEL_LINE,
    ZONE_RECTANGLE,
    SEQUENCE_ORDER,
    NEXT_ACTION,
    TRADE_PLAN
}

enum class AssessmentOutcome {
    PASS,
    FAIL,
    UNASSESSABLE
}

enum class TradeDirection {
    LONG,
    SHORT
}

enum class EntryMode {
    MARKET_NEXT_OPEN,
    LIMIT_PRICE
}

enum class TradeStatus {
    DRAFT,
    VALIDATED,
    OPEN,
    CLOSED_TP,
    CLOSED_SL,
    CLOSED_MANUAL,
    CLOSED_SCENARIO_END,
    CANCELLED,
    CANCELLED_SCENARIO_END,
    DATA_EXHAUSTED,
    INVALIDATED,
    ERROR_RECOVERABLE
}

enum class MasteryStatus {
    UNSEEN,
    INTRODUCED,
    PRACTICING,
    PROVISIONAL,
    CONSISTENT
}

enum class ErrorTag {
    CONCEPT_MISUNDERSTOOD,
    REFERENCE_LEVEL_WRONG,
    CONDITION_MISSING,
    ZONE_BOUNDARY_WRONG,
    TIMEFRAME_MISMATCH,
    ENTRY_TOO_EARLY,
    INVALID_RISK,
    PLAN_DEVIATION,
    UNCLASSIFIED,
    NONE
}

enum class DrawingToolType {
    NONE,
    HORIZONTAL_LINE,
    TREND_LINE,
    RECTANGLE_ZONE,
    TEXT_NOTE
}

data class Candle(
    val id: String,
    val scenarioId: String,
    val instrument: String,
    val timeframe: String,
    val barIndex: Int,
    val timestampUtcMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 1000.0
) {
    val isBullish: Boolean get() = close >= open
    val bodySize: Double get() = kotlin.math.abs(close - open)
    val rangeSize: Double get() = high - low
}

data class ReferenceLevel(
    val kind: String, // e.g., "SWING_HIGH", "SWING_LOW", "EQUAL_HIGH", "PROTECTED_SWING_LOW"
    val price: Double,
    val confirmedAtBarIndex: Int,
    val knownBeforeDecision: Boolean
)

data class ZoneBounds(
    val startBarIndex: Int,
    val endBarIndex: Int,
    val bottomPrice: Double,
    val topPrice: Double,
    val zoneType: String = "OB"
) {
    val widthBars: Double get() = (kotlin.math.max(endBarIndex, startBarIndex) - kotlin.math.min(endBarIndex, startBarIndex) + 1).toDouble()
    val heightPrice: Double get() = kotlin.math.abs(topPrice - bottomPrice)
    val area: Double get() = widthBars * heightPrice
}

data class DecisionPoint(
    val decisionPointId: String,
    val scenarioId: String,
    val visibleDataThrough: Int,
    val questionType: QuestionType,
    val promptAr: String,
    val promptEn: String,
    val options: List<String>,
    val acceptedAnswers: List<String>,
    val rejectedAnswers: List<String>,
    val expectedEvidence: List<String>,
    val targetPrice: Double? = null,
    val targetBarIndex: Int? = null,
    val targetZone: ZoneBounds? = null,
    val criticalConditions: List<String> = emptyList(),
    val feedbackAr: String,
    val feedbackEn: String,
    val answerKeyStatus: String = "APPROVED",
    val rubricVersion: String = "1.0"
)

data class ScenarioTestCase(
    val caseId: String,
    val caseType: String, // "PASS", "NEAR_MISS", "BOUNDARY", "UNASSESSABLE_LOOKAHEAD"
    val submittedAnswer: String,
    val submittedPrice: Double? = null,
    val submittedBarIndex: Int? = null,
    val submittedZone: ZoneBounds? = null,
    val expectedOutcome: AssessmentOutcome,
    val expectedMinScore: Double
)

data class Scenario(
    val scenarioId: String,
    val scenarioVersion: String = "1.0",
    val titleAr: String,
    val titleEn: String,
    val groupAr: String,
    val groupEn: String,
    val primarySkillId: String,
    val familyId: String,
    val difficulty: DifficultyLevel,
    val dataKind: DataKind,
    val ruleId: String,
    val ruleVersion: String = "1.0",
    val tickSize: Double = 0.1,
    val referenceLevel: ReferenceLevel?,
    val candles: List<Candle>,
    val decisionPoints: List<DecisionPoint>,
    val testCases: List<ScenarioTestCase> = emptyList(),
    val packId: String = "CORE_PACK_V1"
)

data class Playhead(
    val workspaceId: String,
    val scenarioId: String,
    val mode: PlayheadMode,
    val currentBarIndex: Int,
    val maxBarIndex: Int,
    val isDecisionLocked: Boolean = false,
    val hasActivePosition: Boolean = false,
    val isScenarioFinished: Boolean = false
) {
    /**
     * Strict Playhead Gate: Returns ONLY candles at or before currentBarIndex.
     * No component may read future candles beyond currentBarIndex.
     */
    fun visibleDataThrough(allCandles: List<Candle>): List<Candle> {
        return allCandles
            .filter { it.barIndex <= currentBarIndex }
            .sortedBy { it.barIndex }
    }

    fun canStepForward(): Boolean = currentBarIndex < maxBarIndex

    fun canRewind(): Boolean {
        return when (mode) {
            PlayheadMode.FREE_CHART -> currentBarIndex > 0
            PlayheadMode.CHALLENGE -> isDecisionLocked && currentBarIndex > 0 // Review read-only after decision locked
            PlayheadMode.SIMULATION -> !hasActivePosition && isScenarioFinished && currentBarIndex > 0
        }
    }

    fun stepForward(): Playhead {
        if (!canStepForward()) return this
        val next = currentBarIndex + 1
        return copy(
            currentBarIndex = next,
            isScenarioFinished = next >= maxBarIndex
        )
    }

    fun rewind(): Playhead {
        if (!canRewind()) return this
        return copy(currentBarIndex = (currentBarIndex - 1).coerceAtLeast(0))
    }
}

data class PaperTrade(
    val tradeId: String,
    val workspaceId: String,
    val scenarioId: String,
    val scenarioVersion: String = "1.0",
    val direction: TradeDirection,
    val entryMode: EntryMode,
    val quantity: Double,
    val plannedEntryPrice: Double,
    val executedEntryPrice: Double? = null,
    val stopPrice: Double,
    val targetPrice: Double,
    val exitPrice: Double? = null,
    val status: TradeStatus = TradeStatus.DRAFT,
    val createdAtBarIndex: Int,
    val openedAtBarIndex: Int? = null,
    val closedAtBarIndex: Int? = null,
    val feeAmount: Double = 0.0,
    val slippageTicks: Double = 0.0,
    val stopFirstConflictTriggered: Boolean = false,
    val engineVersion: String = "1.0",
    val executionPolicyId: String = "OHLC_STOP_FIRST_V1"
) {
    val plannedPriceRisk: Double
        get() = kotlin.math.abs(plannedEntryPrice - stopPrice) * quantity

    val grossPnl: Double
        get() {
            val entry = executedEntryPrice ?: return 0.0
            val exit = exitPrice ?: return 0.0
            return when (direction) {
                TradeDirection.LONG -> (exit - entry) * quantity
                TradeDirection.SHORT -> (entry - exit) * quantity
            }
        }

    val netPnl: Double
        get() {
            if (executedEntryPrice == null || exitPrice == null) return 0.0
            return grossPnl - feeAmount
        }

    /**
     * R = R_net = netPnl / plannedPriceRisk.
     */
    val rNet: Double
        get() = if (plannedPriceRisk > 0.0) netPnl / plannedPriceRisk else 0.0

    /**
     * R_gross displayed separately.
     */
    val rGross: Double
        get() = if (plannedPriceRisk > 0.0) grossPnl / plannedPriceRisk else 0.0
}

data class TradeEvent(
    val eventId: String,
    val tradeId: String,
    val workspaceId: String,
    val eventSequence: Int,
    val barIndex: Int,
    val timestampUtcMillis: Long,
    val fromStatus: TradeStatus?,
    val toStatus: TradeStatus,
    val reason: String,
    val idempotencyKey: String
)

data class SimulationReport(
    val reportId: String,
    val workspaceId: String,
    val scenarioId: String,
    val tradeId: String,
    val analysisQualityScore: Double, // [0.0, 1.0] - separated from outcome
    val analysisOutcome: AssessmentOutcome,
    val executionDisciplineScore: Double, // [0.0, 1.0] - plan adherence & risk limits
    val executionViolations: List<String>,
    val grossPnl: Double,
    val netPnl: Double,
    val plannedPriceRisk: Double,
    val rGross: Double,
    val rNet: Double,
    val exitStatus: TradeStatus,
    val stopFirstConflictNote: String?,
    val summaryAr: String,
    val summaryEn: String
)

data class ChartDrawing(
    val drawingId: String,
    val scenarioId: String,
    val toolType: DrawingToolType,
    val startBarIndex: Int,
    val endBarIndex: Int,
    val startPrice: Double,
    val endPrice: Double,
    val noteText: String = "",
    val createdAtPlayheadBar: Int
)

data class Lesson(
    val lessonId: String,
    val moduleCode: String,
    val titleAr: String,
    val titleEn: String,
    val measurableGoalAr: String,
    val measurableGoalEn: String,
    val prerequisiteId: String?,
    val skillId: String,
    val requiredActivityAr: String,
    val requiredActivityEn: String,
    val contentAr: String,
    val contentEn: String,
    val workedExampleAr: String,
    val workedExampleEn: String,
    val linkedScenarioId: String,
    val isCompleted: Boolean = false
)

data class GlossaryTerm(
    val termId: String,
    val code: String,
    val titleAr: String,
    val titleEn: String,
    val definitionAr: String,
    val definitionEn: String,
    val operationalNoteAr: String,
    val operationalNoteEn: String
)
