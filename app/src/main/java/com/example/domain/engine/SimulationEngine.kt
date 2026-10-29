package com.example.domain.engine

import com.example.domain.model.AssessmentOutcome
import com.example.domain.model.Candle
import com.example.domain.model.EntryMode
import com.example.domain.model.PaperTrade
import com.example.domain.model.Playhead
import com.example.domain.model.PlayheadMode
import com.example.domain.model.SimulationReport
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeEvent
import com.example.domain.model.TradeStatus
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SimulationWorkspaceState(
    val workspaceId: String,
    val scenarioId: String,
    val startingEquity: Double = 10_000.0,
    val dayStartEquity: Double = 10_000.0,
    val currentDayUtcMidnightMillis: Long = 0L,
    val playhead: Playhead,
    val activeTrade: PaperTrade? = null,
    val completedTrades: List<PaperTrade> = emptyList(),
    val tradeEvents: List<TradeEvent> = emptyList(),
    val appliedIdempotencyKeys: Set<String> = emptySet(),
    val latestReport: SimulationReport? = null,
    val lastErrorAr: String? = null,
    val lastErrorEn: String? = null
) {
    /**
     * Realized net P&L across completed trades in this workspace.
     */
    val totalRealizedNetPnl: Double
        get() = completedTrades.sumOf { it.netPnl }

    /**
     * Calculates unrealized P&L at the close of the current visible playhead bar.
     */
    fun computeUnrealizedPnl(currentClosePrice: Double): Double {
        val active = activeTrade ?: return 0.0
        if (active.status != TradeStatus.OPEN) return 0.0
        val entry = active.executedEntryPrice ?: return 0.0
        return when (active.direction) {
            TradeDirection.LONG -> (currentClosePrice - entry) * active.quantity
            TradeDirection.SHORT -> (entry - currentClosePrice) * active.quantity
        }
    }

    /**
     * DR-003: dailyLossPct = max(0, -(currentMarkToMarketEquity - dayStartEquity) / dayStartEquity)
     */
    fun computeDailyLossPct(currentClosePrice: Double): Double {
        if (dayStartEquity <= 0.0) return 0.0
        val currentMtmEquity = startingEquity + totalRealizedNetPnl + computeUnrealizedPnl(currentClosePrice)
        return max(0.0, -(currentMtmEquity - dayStartEquity) / dayStartEquity)
    }
}

object SimulationEngine {

    const val ENGINE_VERSION = "1.0"
    const val EXECUTION_POLICY_ID = "OHLC_STOP_FIRST_V1"
    const val MAX_DAILY_LOSS_PCT = 0.03 // 3% daily loss limit in discipline challenges
    const val DEFAULT_MAX_TRADE_RISK_PCT = 0.01 // 1% per-trade educational risk guideline

    /**
     * DR-002: Computes UTC 00:00 midnight timestamp (in millis) for any market candle timestamp.
     */
    fun utcMidnightMillis(timestampUtcMillis: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timestampUtcMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Submits a new order in the workspace.
     * Enforces:
     * - Single active position per workspace (DR-004)
     * - Idempotency via idempotencyKey (Spec 06.10)
     * - Valid stop and target relative to direction and positive quantity/risk (Spec 06.5)
     * - Daily loss 3% gate (DR-003)
     */
    fun submitOrder(
        state: SimulationWorkspaceState,
        allCandles: List<Candle>,
        tradeId: String,
        direction: TradeDirection,
        entryMode: EntryMode,
        quantity: Double,
        limitOrReferenceEntryPrice: Double,
        stopPrice: Double,
        targetPrice: Double,
        feeAmount: Double = 1.0,
        slippageTicks: Double = 0.0,
        idempotencyKey: String,
        enforceDailyLossGate: Boolean = true
    ): SimulationWorkspaceState {
        // Idempotency check: duplicate command is a no-op
        if (idempotencyKey in state.appliedIdempotencyKeys) {
            return state
        }

        // Single active position constraint (DR-004)
        if (state.activeTrade != null) {
            return state.copy(
                lastErrorAr = "يوجد مركز نشط بالفعل في مساحة العمل هذه (الحد الأقصى مركز واحد نشط).",
                lastErrorEn = "An active position/order already exists in this SimulationWorkspace."
            )
        }

        val visibleCandles = state.playhead.visibleDataThrough(allCandles)
        val currentCandle = visibleCandles.lastOrNull()
            ?: return state.copy(
                lastErrorAr = "لا توجد شمعة متاحة عند مؤشر Playhead الحالي.",
                lastErrorEn = "No visible candle available at current Playhead."
            )

        // Check 3% daily loss limit (DR-003)
        val dailyLossPct = state.computeDailyLossPct(currentCandle.close)
        if (enforceDailyLossGate && dailyLossPct >= MAX_DAILY_LOSS_PCT - 1e-9) {
            return state.copy(
                lastErrorAr = "تم تجاوز حد الخسارة اليومي (3%)؛ يُمنع فتح صفقات جديدة التزاماً بالانضباط.",
                lastErrorEn = "Daily loss limit (3%) reached; new orders are blocked."
            )
        }

        // Validate quantity & SL/TP geometry
        val plannedEntry = if (entryMode == EntryMode.MARKET_NEXT_OPEN) {
            currentCandle.close
        } else {
            limitOrReferenceEntryPrice
        }

        val validGeometry = when (direction) {
            TradeDirection.LONG -> stopPrice < plannedEntry && targetPrice > plannedEntry
            TradeDirection.SHORT -> stopPrice > plannedEntry && targetPrice < plannedEntry
        }

        if (quantity <= 0.0 || !validGeometry) {
            return state.copy(
                lastErrorAr = "إعدادات الصفقة غير صالحة: تحقق من الكمية وموضع وقف الخسارة والهدف بالنسبة للاتجاه.",
                lastErrorEn = "Invalid order parameters: check quantity and SL/TP placement for direction."
            )
        }

        val newTrade = PaperTrade(
            tradeId = tradeId,
            workspaceId = state.workspaceId,
            scenarioId = state.scenarioId,
            direction = direction,
            entryMode = entryMode,
            quantity = quantity,
            plannedEntryPrice = plannedEntry,
            executedEntryPrice = null,
            stopPrice = stopPrice,
            targetPrice = targetPrice,
            status = TradeStatus.VALIDATED,
            createdAtBarIndex = state.playhead.currentBarIndex,
            feeAmount = feeAmount,
            slippageTicks = slippageTicks,
            engineVersion = ENGINE_VERSION,
            executionPolicyId = EXECUTION_POLICY_ID
        )

        val nextSeq = state.tradeEvents.size + 1
        val event = TradeEvent(
            eventId = "EVT-$tradeId-$nextSeq",
            tradeId = tradeId,
            workspaceId = state.workspaceId,
            eventSequence = nextSeq,
            barIndex = state.playhead.currentBarIndex,
            timestampUtcMillis = currentCandle.timestampUtcMillis,
            fromStatus = TradeStatus.DRAFT,
            toStatus = TradeStatus.VALIDATED,
            reason = "ORDER_VALIDATED_${entryMode.name}",
            idempotencyKey = idempotencyKey
        )

        return state.copy(
            playhead = state.playhead.copy(
                mode = PlayheadMode.SIMULATION,
                hasActivePosition = true
            ),
            activeTrade = newTrade,
            tradeEvents = state.tradeEvents + event,
            appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey,
            lastErrorAr = null,
            lastErrorEn = null
        )
    }

    /**
     * Advances the simulation by 1 bar and processes order fills, SL/TP triggers (with Stop-First conflict resolution),
     * UTC 00:00 day boundary updates, and end-of-data handling.
     */
    fun stepForwardBar(
        state: SimulationWorkspaceState,
        allCandles: List<Candle>,
        tickSize: Double = 0.1,
        analysisQualityScore: Double = 1.0,
        analysisOutcome: AssessmentOutcome = AssessmentOutcome.PASS,
        idempotencyKey: String
    ): SimulationWorkspaceState {
        if (idempotencyKey in state.appliedIdempotencyKeys) {
            return state
        }
        if (!state.playhead.canStepForward()) {
            return state
        }

        val steppedPlayhead = state.playhead.stepForward()
        val visible = steppedPlayhead.visibleDataThrough(allCandles)
        val bar = visible.lastOrNull() ?: return state.copy(playhead = steppedPlayhead)

        // Check UTC 00:00 day rollover (DR-002)
        val barDayMidnight = utcMidnightMillis(bar.timestampUtcMillis)
        val updatedDayStartEquity = if (barDayMidnight > state.currentDayUtcMidnightMillis && state.currentDayUtcMidnightMillis > 0L) {
            state.startingEquity + state.totalRealizedNetPnl + state.computeUnrealizedPnl(bar.open)
        } else {
            state.dayStartEquity
        }
        val updatedMidnight = if (state.currentDayUtcMidnightMillis == 0L) barDayMidnight else max(state.currentDayUtcMidnightMillis, barDayMidnight)

        var trade = state.activeTrade ?: return state.copy(
            playhead = steppedPlayhead,
            currentDayUtcMidnightMillis = updatedMidnight,
            dayStartEquity = updatedDayStartEquity,
            appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey
        )

        val events = state.tradeEvents.toMutableList()

        // 1. Check pending entry fill (VALIDATED -> OPEN)
        if (trade.status == TradeStatus.VALIDATED && bar.barIndex > trade.createdAtBarIndex) {
            when (trade.entryMode) {
                EntryMode.MARKET_NEXT_OPEN -> {
                    val slip = trade.slippageTicks * tickSize
                    val fillPrice = when (trade.direction) {
                        TradeDirection.LONG -> bar.open + slip
                        TradeDirection.SHORT -> bar.open - slip
                    }
                    trade = trade.copy(
                        executedEntryPrice = fillPrice,
                        status = TradeStatus.OPEN,
                        openedAtBarIndex = bar.barIndex
                    )
                    events.add(
                        TradeEvent(
                            eventId = "EVT-${trade.tradeId}-${events.size + 1}",
                            tradeId = trade.tradeId,
                            workspaceId = state.workspaceId,
                            eventSequence = events.size + 1,
                            barIndex = bar.barIndex,
                            timestampUtcMillis = bar.timestampUtcMillis,
                            fromStatus = TradeStatus.VALIDATED,
                            toStatus = TradeStatus.OPEN,
                            reason = "MARKET_NEXT_OPEN_FILLED_AT_$fillPrice",
                            idempotencyKey = "$idempotencyKey:OPEN"
                        )
                    )
                }
                EntryMode.LIMIT_PRICE -> {
                    val limitPrice = trade.plannedEntryPrice
                    val touched = bar.low <= limitPrice + 1e-9 && bar.high >= limitPrice - 1e-9
                    val favorableGap = when (trade.direction) {
                        TradeDirection.LONG -> bar.open < limitPrice
                        TradeDirection.SHORT -> bar.open > limitPrice
                    }
                    if (touched || favorableGap) {
                        val fillPrice = when (trade.direction) {
                            TradeDirection.LONG -> if (bar.open < limitPrice) bar.open else limitPrice
                            TradeDirection.SHORT -> if (bar.open > limitPrice) bar.open else limitPrice
                        }
                        trade = trade.copy(
                            executedEntryPrice = fillPrice,
                            status = TradeStatus.OPEN,
                            openedAtBarIndex = bar.barIndex
                        )
                        events.add(
                            TradeEvent(
                                eventId = "EVT-${trade.tradeId}-${events.size + 1}",
                                tradeId = trade.tradeId,
                                workspaceId = state.workspaceId,
                                eventSequence = events.size + 1,
                                barIndex = bar.barIndex,
                                timestampUtcMillis = bar.timestampUtcMillis,
                                fromStatus = TradeStatus.VALIDATED,
                                toStatus = TradeStatus.OPEN,
                                reason = "LIMIT_FILLED_AT_$fillPrice",
                                idempotencyKey = "$idempotencyKey:LIMIT_OPEN"
                            )
                        )
                    } else if (steppedPlayhead.isScenarioFinished) {
                        // DR-005: Unfilled limit order at end of scenario -> CANCELLED_SCENARIO_END
                        val cancelled = trade.copy(
                            status = TradeStatus.CANCELLED_SCENARIO_END,
                            closedAtBarIndex = bar.barIndex
                        )
                        events.add(
                            TradeEvent(
                                eventId = "EVT-${trade.tradeId}-${events.size + 1}",
                                tradeId = trade.tradeId,
                                workspaceId = state.workspaceId,
                                eventSequence = events.size + 1,
                                barIndex = bar.barIndex,
                                timestampUtcMillis = bar.timestampUtcMillis,
                                fromStatus = TradeStatus.VALIDATED,
                                toStatus = TradeStatus.CANCELLED_SCENARIO_END,
                                reason = "LIMIT_UNFILLED_AT_SCENARIO_END",
                                idempotencyKey = "$idempotencyKey:CANCEL_END"
                            )
                        )
                        val report = buildSimulationReport(
                            workspaceId = state.workspaceId,
                            scenarioId = state.scenarioId,
                            trade = cancelled,
                            startingEquity = state.startingEquity,
                            analysisQualityScore = analysisQualityScore,
                            analysisOutcome = analysisOutcome
                        )
                        return state.copy(
                            playhead = steppedPlayhead.copy(hasActivePosition = false),
                            currentDayUtcMidnightMillis = updatedMidnight,
                            dayStartEquity = updatedDayStartEquity,
                            activeTrade = null,
                            completedTrades = state.completedTrades + cancelled,
                            tradeEvents = events,
                            appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey,
                            latestReport = report
                        )
                    }
                }
            }
        }

        // 2. If trade is OPEN, evaluate SL / TP / Scenario End on current bar
        if (trade.status == TradeStatus.OPEN) {
            val eps = 1e-9
            val hitSl = when (trade.direction) {
                TradeDirection.LONG -> bar.low <= trade.stopPrice + eps
                TradeDirection.SHORT -> bar.high >= trade.stopPrice - eps
            }
            val hitTp = when (trade.direction) {
                TradeDirection.LONG -> bar.high >= trade.targetPrice - eps
                TradeDirection.SHORT -> bar.low <= trade.targetPrice + eps
            }

            if (hitSl && hitTp) {
                // DR-006: Intra-candle SL/TP conflict -> STOP-FIRST deterministic resolution
                val closed = trade.copy(
                    exitPrice = trade.stopPrice,
                    status = TradeStatus.CLOSED_SL,
                    closedAtBarIndex = bar.barIndex,
                    stopFirstConflictTriggered = true
                )
                events.add(
                    TradeEvent(
                        eventId = "EVT-${trade.tradeId}-${events.size + 1}",
                        tradeId = trade.tradeId,
                        workspaceId = state.workspaceId,
                        eventSequence = events.size + 1,
                        barIndex = bar.barIndex,
                        timestampUtcMillis = bar.timestampUtcMillis,
                        fromStatus = TradeStatus.OPEN,
                        toStatus = TradeStatus.CLOSED_SL,
                        reason = "STOP_FIRST_CONFLICT_RESOLUTION_SL_AT_${trade.stopPrice}",
                        idempotencyKey = "$idempotencyKey:CLOSE_CONFLICT_SL"
                    )
                )
                val report = buildSimulationReport(
                    workspaceId = state.workspaceId,
                    scenarioId = state.scenarioId,
                    trade = closed,
                    startingEquity = state.startingEquity,
                    analysisQualityScore = analysisQualityScore,
                    analysisOutcome = analysisOutcome
                )
                return state.copy(
                    playhead = steppedPlayhead.copy(hasActivePosition = false),
                    currentDayUtcMidnightMillis = updatedMidnight,
                    dayStartEquity = updatedDayStartEquity,
                    activeTrade = null,
                    completedTrades = state.completedTrades + closed,
                    tradeEvents = events,
                    appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey,
                    latestReport = report
                )
            } else if (hitSl) {
                val closed = trade.copy(
                    exitPrice = trade.stopPrice,
                    status = TradeStatus.CLOSED_SL,
                    closedAtBarIndex = bar.barIndex
                )
                events.add(
                    TradeEvent(
                        eventId = "EVT-${trade.tradeId}-${events.size + 1}",
                        tradeId = trade.tradeId,
                        workspaceId = state.workspaceId,
                        eventSequence = events.size + 1,
                        barIndex = bar.barIndex,
                        timestampUtcMillis = bar.timestampUtcMillis,
                        fromStatus = TradeStatus.OPEN,
                        toStatus = TradeStatus.CLOSED_SL,
                        reason = "STOP_LOSS_HIT_AT_${trade.stopPrice}",
                        idempotencyKey = "$idempotencyKey:CLOSE_SL"
                    )
                )
                val report = buildSimulationReport(
                    workspaceId = state.workspaceId,
                    scenarioId = state.scenarioId,
                    trade = closed,
                    startingEquity = state.startingEquity,
                    analysisQualityScore = analysisQualityScore,
                    analysisOutcome = analysisOutcome
                )
                return state.copy(
                    playhead = steppedPlayhead.copy(hasActivePosition = false),
                    currentDayUtcMidnightMillis = updatedMidnight,
                    dayStartEquity = updatedDayStartEquity,
                    activeTrade = null,
                    completedTrades = state.completedTrades + closed,
                    tradeEvents = events,
                    appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey,
                    latestReport = report
                )
            } else if (hitTp) {
                val closed = trade.copy(
                    exitPrice = trade.targetPrice,
                    status = TradeStatus.CLOSED_TP,
                    closedAtBarIndex = bar.barIndex
                )
                events.add(
                    TradeEvent(
                        eventId = "EVT-${trade.tradeId}-${events.size + 1}",
                        tradeId = trade.tradeId,
                        workspaceId = state.workspaceId,
                        eventSequence = events.size + 1,
                        barIndex = bar.barIndex,
                        timestampUtcMillis = bar.timestampUtcMillis,
                        fromStatus = TradeStatus.OPEN,
                        toStatus = TradeStatus.CLOSED_TP,
                        reason = "TAKE_PROFIT_HIT_AT_${trade.targetPrice}",
                        idempotencyKey = "$idempotencyKey:CLOSE_TP"
                    )
                )
                val report = buildSimulationReport(
                    workspaceId = state.workspaceId,
                    scenarioId = state.scenarioId,
                    trade = closed,
                    startingEquity = state.startingEquity,
                    analysisQualityScore = analysisQualityScore,
                    analysisOutcome = analysisOutcome
                )
                return state.copy(
                    playhead = steppedPlayhead.copy(hasActivePosition = false),
                    currentDayUtcMidnightMillis = updatedMidnight,
                    dayStartEquity = updatedDayStartEquity,
                    activeTrade = null,
                    completedTrades = state.completedTrades + closed,
                    tradeEvents = events,
                    appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey,
                    latestReport = report
                )
            } else if (steppedPlayhead.isScenarioFinished) {
                // Spec 06.8: Close at last close with CLOSED_SCENARIO_END
                val closed = trade.copy(
                    exitPrice = bar.close,
                    status = TradeStatus.CLOSED_SCENARIO_END,
                    closedAtBarIndex = bar.barIndex
                )
                events.add(
                    TradeEvent(
                        eventId = "EVT-${trade.tradeId}-${events.size + 1}",
                        tradeId = trade.tradeId,
                        workspaceId = state.workspaceId,
                        eventSequence = events.size + 1,
                        barIndex = bar.barIndex,
                        timestampUtcMillis = bar.timestampUtcMillis,
                        fromStatus = TradeStatus.OPEN,
                        toStatus = TradeStatus.CLOSED_SCENARIO_END,
                        reason = "SCENARIO_END_CLOSE_AT_${bar.close}",
                        idempotencyKey = "$idempotencyKey:CLOSE_END"
                    )
                )
                val report = buildSimulationReport(
                    workspaceId = state.workspaceId,
                    scenarioId = state.scenarioId,
                    trade = closed,
                    startingEquity = state.startingEquity,
                    analysisQualityScore = analysisQualityScore,
                    analysisOutcome = analysisOutcome
                )
                return state.copy(
                    playhead = steppedPlayhead.copy(hasActivePosition = false),
                    currentDayUtcMidnightMillis = updatedMidnight,
                    dayStartEquity = updatedDayStartEquity,
                    activeTrade = null,
                    completedTrades = state.completedTrades + closed,
                    tradeEvents = events,
                    appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey,
                    latestReport = report
                )
            }
        }

        return state.copy(
            playhead = steppedPlayhead,
            currentDayUtcMidnightMillis = updatedMidnight,
            dayStartEquity = updatedDayStartEquity,
            activeTrade = trade,
            tradeEvents = events,
            appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey
        )
    }

    /**
     * Manually closes an open trade or cancels a validated order at the current playhead bar close.
     */
    fun closePositionManually(
        state: SimulationWorkspaceState,
        allCandles: List<Candle>,
        analysisQualityScore: Double = 1.0,
        analysisOutcome: AssessmentOutcome = AssessmentOutcome.PASS,
        idempotencyKey: String
    ): SimulationWorkspaceState {
        if (idempotencyKey in state.appliedIdempotencyKeys) {
            return state
        }
        val active = state.activeTrade ?: return state
        val visible = state.playhead.visibleDataThrough(allCandles)
        val bar = visible.lastOrNull() ?: return state

        val newStatus = if (active.status == TradeStatus.OPEN) TradeStatus.CLOSED_MANUAL else TradeStatus.CANCELLED
        val exitPx = if (active.status == TradeStatus.OPEN) bar.close else null

        val closed = active.copy(
            exitPrice = exitPx,
            status = newStatus,
            closedAtBarIndex = bar.barIndex
        )
        val nextSeq = state.tradeEvents.size + 1
        val event = TradeEvent(
            eventId = "EVT-${active.tradeId}-$nextSeq",
            tradeId = active.tradeId,
            workspaceId = state.workspaceId,
            eventSequence = nextSeq,
            barIndex = bar.barIndex,
            timestampUtcMillis = bar.timestampUtcMillis,
            fromStatus = active.status,
            toStatus = newStatus,
            reason = "MANUAL_USER_EXIT",
            idempotencyKey = idempotencyKey
        )

        val report = buildSimulationReport(
            workspaceId = state.workspaceId,
            scenarioId = state.scenarioId,
            trade = closed,
            startingEquity = state.startingEquity,
            analysisQualityScore = analysisQualityScore,
            analysisOutcome = analysisOutcome
        )

        return state.copy(
            playhead = state.playhead.copy(hasActivePosition = false),
            activeTrade = null,
            completedTrades = state.completedTrades + closed,
            tradeEvents = state.tradeEvents + event,
            appliedIdempotencyKeys = state.appliedIdempotencyKeys + idempotencyKey,
            latestReport = report
        )
    }

    /**
     * Spec 27.4 & 06.11: Builds a separate 3-pillar report:
     * 1. analysisQuality
     * 2. executionDiscipline
     * 3. simulationOutcome (R = R_net = netPnl / plannedPriceRisk, plus R_gross separately)
     */
    fun buildSimulationReport(
        workspaceId: String,
        scenarioId: String,
        trade: PaperTrade,
        startingEquity: Double,
        analysisQualityScore: Double,
        analysisOutcome: AssessmentOutcome
    ): SimulationReport {
        val violations = mutableListOf<String>()
        var disciplineScore = 1.0

        val riskPct = if (startingEquity > 0.0) trade.plannedPriceRisk / startingEquity else 0.0
        if (riskPct > DEFAULT_MAX_TRADE_RISK_PCT + 1e-6) {
            violations.add("RISK_EXCEEDED_1PCT (${"%.2f".format(riskPct * 100)}%)")
            disciplineScore -= 0.40
        }
        if (trade.status == TradeStatus.CLOSED_MANUAL) {
            violations.add("EARLY_MANUAL_EXIT_BEFORE_SL_OR_TP")
            disciplineScore -= 0.25
        }
        disciplineScore = disciplineScore.coerceIn(0.0, 1.0)

        val conflictNote = if (trade.stopFirstConflictTriggered) {
            "تعارض OHLC داخل نفس الشمعة: تم تطبيق السياسة المحافظة (Stop-First) باحتساب ضرب وقف الخسارة أولاً."
        } else null

        val summaryAr = buildString {
            append("حالة الإغلاق: ${trade.status.name} | ")
            append("R الصافي (R_net): ${"%.2f".format(trade.rNet)}R | ")
            append("R الإجمالي (R_gross): ${"%.2f".format(trade.rGross)}R | ")
            append("جودة التحليل: ${(analysisQualityScore * 100).toInt()}% (${analysisOutcome.name}) | ")
            append("انضباط التنفيذ: ${(disciplineScore * 100).toInt()}%")
            if (conflictNote != null) append(" | $conflictNote")
        }

        val summaryEn = buildString {
            append("Exit Status: ${trade.status.name} | ")
            append("R_net: ${"%.2f".format(trade.rNet)}R | ")
            append("R_gross: ${"%.2f".format(trade.rGross)}R | ")
            append("Analysis Quality: ${(analysisQualityScore * 100).toInt()}% (${analysisOutcome.name}) | ")
            append("Execution Discipline: ${(disciplineScore * 100).toInt()}%")
            if (trade.stopFirstConflictTriggered) {
                append(" | Intra-candle SL/TP conflict resolved via Stop-First policy.")
            }
        }

        return SimulationReport(
            reportId = "REP-${trade.tradeId}",
            workspaceId = workspaceId,
            scenarioId = scenarioId,
            tradeId = trade.tradeId,
            analysisQualityScore = analysisQualityScore,
            analysisOutcome = analysisOutcome,
            executionDisciplineScore = disciplineScore,
            executionViolations = violations,
            grossPnl = trade.grossPnl,
            netPnl = trade.netPnl,
            plannedPriceRisk = trade.plannedPriceRisk,
            rGross = trade.rGross,
            rNet = trade.rNet,
            exitStatus = trade.status,
            stopFirstConflictNote = conflictNote,
            summaryAr = summaryAr,
            summaryEn = summaryEn
        )
    }
}
