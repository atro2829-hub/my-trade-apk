package com.example.domain.engine

import com.example.domain.model.AssessmentOutcome
import com.example.domain.model.Candle
import com.example.domain.model.ReferenceLevel
import kotlin.math.abs
import kotlin.math.max

data class PredicateEvaluationResult(
    val ruleId: String,
    val outcome: AssessmentOutcome,
    val reasonAr: String,
    val reasonEn: String,
    val evidence: List<String> = emptyList()
)

object SmcRulePredicates {

    /**
     * Computes ATR14 strictly from closed candles available at or before [visibleDataThrough].
     * Returns null if fewer than 14 candles are available.
     */
    fun computeAtr14(
        visibleCandles: List<Candle>,
        visibleDataThrough: Int
    ): Double? {
        val restricted = visibleCandles
            .filter { it.barIndex <= visibleDataThrough }
            .sortedBy { it.barIndex }
        if (restricted.size < 14) return null
        val recent14 = restricted.takeLast(14)
        var trSum = 0.0
        for (i in recent14.indices) {
            val current = recent14[i]
            val prevClose = if (i > 0) recent14[i - 1].close else current.open
            val tr = maxOf(
                current.high - current.low,
                abs(current.high - prevClose),
                abs(current.low - prevClose)
            )
            trSum += tr
        }
        return trSum / 14.0
    }

    /**
     * 1. SWING.PIVOT_CONFIRMED
     * Bar i is a confirmed Swing High iff high[i] > high[i-2], high[i-1], high[i+1], high[i+2]
     * AND visibleDataThrough >= i + rightBars (default 2).
     * If visibleDataThrough < i + rightBars, returns UNASSESSABLE (Anti-Lookahead protection).
     */
    fun evaluateSwingPivotConfirmed(
        allCandles: List<Candle>,
        candidateBarIndex: Int,
        visibleDataThrough: Int,
        isSwingHigh: Boolean,
        leftBars: Int = 2,
        rightBars: Int = 2
    ): PredicateEvaluationResult {
        val ruleId = "SMC.SWING.PIVOT_CONFIRMED"
        // Strict anti-lookahead check: only inspect candles <= visibleDataThrough
        val visible = allCandles.filter { it.barIndex <= visibleDataThrough }.associateBy { it.barIndex }

        if (visibleDataThrough < candidateBarIndex + rightBars) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "لا يمكن تأكيد القمة/القاع عند الشمعة $candidateBarIndex قبل إغلاق $rightBars شموع يميناً (المتاح حتى $visibleDataThrough فقط — منع انحياز المستقبل).",
                reasonEn = "Cannot confirm pivot at bar $candidateBarIndex before $rightBars right-hand bars close (visible through $visibleDataThrough only — anti-lookahead).",
                evidence = listOf("visibleDataThrough=$visibleDataThrough < required=${candidateBarIndex + rightBars}")
            )
        }

        val center = visible[candidateBarIndex]
            ?: return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "شمعة المرشح غير موجودة ضمن البيانات المرئية.",
                reasonEn = "Candidate bar missing in visible data."
            )

        val neighborIndices = ((candidateBarIndex - leftBars) until candidateBarIndex) +
                ((candidateBarIndex + 1)..(candidateBarIndex + rightBars))

        val neighbors = neighborIndices.mapNotNull { visible[it] }
        if (neighbors.size < leftBars + rightBars) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "بيانات الشموع المجاورة غير مكتملة لحساب Pivot المؤكد.",
                reasonEn = "Insufficient neighbor bars to confirm pivot."
            )
        }

        val isConfirmed = if (isSwingHigh) {
            neighbors.all { center.high > it.high }
        } else {
            neighbors.all { center.low < it.low }
        }

        return if (isConfirmed) {
            PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.PASS,
                reasonAr = if (isSwingHigh) "قمة Swing High مؤكدة بعد إغلاق شمعتين يميناً." else "قاع Swing Low مؤكد بعد إغلاق شمعتين يميناً.",
                reasonEn = if (isSwingHigh) "Confirmed Swing High with 2 closed bars on both sides." else "Confirmed Swing Low with 2 closed bars on both sides.",
                evidence = listOf("bar=$candidateBarIndex", "visibleThrough=$visibleDataThrough")
            )
        } else {
            PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.FAIL,
                reasonAr = "الشمعة $candidateBarIndex لا تتجاوز جميع الشموع المجاورة المطلوبة.",
                reasonEn = "Bar $candidateBarIndex does not strictly dominate required neighbor bars.",
                evidence = listOf("bar=$candidateBarIndex")
            )
        }
    }

    /**
     * 2. BOS.CLOSE_BREAK
     * Bullish: close[decisionBar] > referenceLevel.price + minBreakDistanceTicks * tickSize
     * Bearish: close[decisionBar] < referenceLevel.price - minBreakDistanceTicks * tickSize
     * Requires referenceLevel to be confirmed before decisionBar and decisionBar <= visibleDataThrough.
     */
    fun evaluateBosCloseBreak(
        allCandles: List<Candle>,
        decisionBarIndex: Int,
        visibleDataThrough: Int,
        referenceLevel: ReferenceLevel?,
        isBullish: Boolean,
        tickSize: Double = 0.1,
        minBreakDistanceTicks: Int = 1
    ): PredicateEvaluationResult {
        val ruleId = "SMC.BOS.CLOSE_BREAK"
        if (tickSize <= 0.0 || referenceLevel == null || !referenceLevel.knownBeforeDecision) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "المستوى المرجعي غير مؤكد قبل نقطة القرار أو بيانات الدقة ناقصة.",
                reasonEn = "Reference swing is unconfirmed prior to decision point or tickSize invalid."
            )
        }
        if (decisionBarIndex > visibleDataThrough) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "شمعة القرار $decisionBarIndex تقع بعد مؤشر Playhead الحالي ($visibleDataThrough).",
                reasonEn = "Decision bar $decisionBarIndex is beyond visibleDataThrough ($visibleDataThrough)."
            )
        }
        if (referenceLevel.confirmedAtBarIndex >= decisionBarIndex) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "المستوى المرجعي لم يكن مؤكداً قبل شمعة القرار.",
                reasonEn = "Reference level was not confirmed before decision bar."
            )
        }

        val visible = allCandles.filter { it.barIndex <= visibleDataThrough }
        val bar = visible.find { it.barIndex == decisionBarIndex }
            ?: return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "شمعة القرار غير متاحة.",
                reasonEn = "Decision bar not found in visible data."
            )

        val margin = minBreakDistanceTicks * tickSize
        val epsilon = 1e-9
        return if (isBullish) {
            val threshold = referenceLevel.price + margin
            if (bar.close >= threshold - epsilon) {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.PASS,
                    reasonAr = "كسر هيكل صاعد مؤكد بالإغلاق (${bar.close} >= $threshold).",
                    reasonEn = "Confirmed bullish BOS close break (${bar.close} >= $threshold).",
                    evidence = listOf("close[${bar.barIndex}]=${bar.close}", "threshold=$threshold")
                )
            } else {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.FAIL,
                    reasonAr = "لم تغلق الشمعة فوق مستوى الكسر المطلوب ($threshold)؛ الذيل وحده لا يكفي لتأكيد BOS.",
                    reasonEn = "Bar close (${bar.close}) did not exceed break threshold ($threshold); wick alone is not BOS.",
                    evidence = listOf("high[${bar.barIndex}]=${bar.high}", "close[${bar.barIndex}]=${bar.close}", "threshold=$threshold")
                )
            }
        } else {
            val threshold = referenceLevel.price - margin
            if (bar.close <= threshold + epsilon) {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.PASS,
                    reasonAr = "كسر هيكل هابط مؤكد بالإغلاق (${bar.close} <= $threshold).",
                    reasonEn = "Confirmed bearish BOS close break (${bar.close} <= $threshold).",
                    evidence = listOf("close[${bar.barIndex}]=${bar.close}", "threshold=$threshold")
                )
            } else {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.FAIL,
                    reasonAr = "لم تغلق الشمعة تحت مستوى الكسر المطلوب ($threshold).",
                    reasonEn = "Bar close (${bar.close}) did not close below threshold ($threshold).",
                    evidence = listOf("low[${bar.barIndex}]=${bar.low}", "close[${bar.barIndex}]=${bar.close}", "threshold=$threshold")
                )
            }
        }
    }

    /**
     * 3. BOS.WICK_SWEEP
     * Bullish Wick Sweep: high > level + tolerance AND close <= level + tolerance
     * Bearish Wick Sweep: low < level - tolerance AND close >= level - tolerance
     */
    fun evaluateBosWickSweep(
        allCandles: List<Candle>,
        decisionBarIndex: Int,
        visibleDataThrough: Int,
        referenceLevel: ReferenceLevel?,
        isBullishSweep: Boolean,
        tickSize: Double = 0.1,
        minBreakDistanceTicks: Int = 1
    ): PredicateEvaluationResult {
        val ruleId = "SMC.BOS.WICK_SWEEP"
        if (tickSize <= 0.0 || referenceLevel == null || !referenceLevel.knownBeforeDecision) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "المستوى المرجعي غير مؤكد قبل القرار.",
                reasonEn = "Reference level is unconfirmed prior to decision."
            )
        }
        if (decisionBarIndex > visibleDataThrough || referenceLevel.confirmedAtBarIndex >= decisionBarIndex) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "محاولة استخدام بيانات مستقبلية بعد Playhead أو قبل تأكيد المرجع.",
                reasonEn = "Attempted lookahead beyond Playhead or before reference confirmation."
            )
        }

        val bar = allCandles.filter { it.barIndex <= visibleDataThrough }.find { it.barIndex == decisionBarIndex }
            ?: return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "الشمعة غير متاحة.",
                reasonEn = "Bar missing."
            )

        val tolerance = minBreakDistanceTicks * tickSize
        val epsilon = 1e-9
        return if (isBullishSweep) {
            val threshold = referenceLevel.price + tolerance
            val wickExceeded = bar.high >= threshold - epsilon
            val closedBackInside = bar.close < threshold - epsilon
            if (wickExceeded && closedBackInside) {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.PASS,
                    reasonAr = "تجاوز بالذيل (${bar.high}) مع إغلاق داخل النطاق (${bar.close}) — سحب سيولة بالذيل (Wick Sweep) وليس BOS.",
                    reasonEn = "Wick exceeded level (${bar.high}) while close remained at/below threshold (${bar.close}) — valid Wick Sweep.",
                    evidence = listOf("high[${bar.barIndex}]=${bar.high}", "close[${bar.barIndex}]=${bar.close}")
                )
            } else {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.FAIL,
                    reasonAr = "لا ينطبق شرط سحب الذيل (إما لم يتجاوز الذيل المستوى أو أغلقت الشمعة خارجه ككسر كامل).",
                    reasonEn = "Wick sweep criteria not met (either wick did not breach or candle closed beyond level).",
                    evidence = listOf("high[${bar.barIndex}]=${bar.high}", "close[${bar.barIndex}]=${bar.close}")
                )
            }
        } else {
            val threshold = referenceLevel.price - tolerance
            val wickExceeded = bar.low <= threshold + epsilon
            val closedBackInside = bar.close > threshold + epsilon
            if (wickExceeded && closedBackInside) {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.PASS,
                    reasonAr = "تجاوز سفلي بالذيل (${bar.low}) مع إغلاق فوق العتبة (${bar.close}) — سحب سيولة بالذيل.",
                    reasonEn = "Lower wick swept level (${bar.low}) and closed back above threshold (${bar.close}).",
                    evidence = listOf("low[${bar.barIndex}]=${bar.low}", "close[${bar.barIndex}]=${bar.close}")
                )
            } else {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.FAIL,
                    reasonAr = "لم يتحقق شرط السحب السفلي بالذيل.",
                    reasonEn = "Bearish wick sweep condition not met.",
                    evidence = listOf("low[${bar.barIndex}]=${bar.low}", "close[${bar.barIndex}]=${bar.close}")
                )
            }
        }
    }

    /**
     * 4. CHOCH.STRUCTURE_CHANGE
     * Requires prior trend context, confirmed protectedSwing, and IDM/structure sequence confirmation.
     * Bullish-to-Bearish CHoCH: qualified close below protectedSwingLow.
     * Bearish-to-Bullish CHoCH: qualified close above protectedSwingHigh.
     */
    fun evaluateChochStructureChange(
        allCandles: List<Candle>,
        decisionBarIndex: Int,
        visibleDataThrough: Int,
        protectedSwing: ReferenceLevel?,
        priorTrendIsBullish: Boolean?,
        idmConfirmedBeforeBreak: Boolean,
        tickSize: Double = 0.1,
        minBreakDistanceTicks: Int = 1
    ): PredicateEvaluationResult {
        val ruleId = "SMC.CHOCH.STRUCTURE_CHANGE"
        if (protectedSwing == null || !protectedSwing.knownBeforeDecision || priorTrendIsBullish == null || tickSize <= 0.0) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "سياق الاتجاه السابق أو القمة/القاع المحمي غير محدد قبل القرار.",
                reasonEn = "Prior trend context or protected swing not defined before decision."
            )
        }
        if (decisionBarIndex > visibleDataThrough || protectedSwing.confirmedAtBarIndex >= decisionBarIndex) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "البيانات المطلوبة لتأكيد CHoCH تقع بعد Playhead الحالي.",
                reasonEn = "CHoCH evaluation bar or protected swing confirmation is beyond visibleDataThrough."
            )
        }

        val bar = allCandles.filter { it.barIndex <= visibleDataThrough }.find { it.barIndex == decisionBarIndex }
            ?: return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "شمعة القرار غير متوفرة.",
                reasonEn = "Decision bar missing."
            )

        if (!idmConfirmedBeforeBreak) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.FAIL,
                reasonAr = "كسر هيكل داخلي دون تحقق تسلسل IDM والقمة/القاع المحمي لا يُعد CHoCH معتمداً.",
                reasonEn = "Internal break without confirmed IDM / protected swing sequence fails CHoCH validation."
            )
        }

        val margin = minBreakDistanceTicks * tickSize
        val epsilon = 1e-9
        return if (priorTrendIsBullish) {
            // Bullish to Bearish CHoCH requires close below protected swing low
            val threshold = protectedSwing.price - margin
            if (bar.close <= threshold + epsilon) {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.PASS,
                    reasonAr = "تغير هيكل CHoCH من صاعد إلى هابط بإغلاق مؤكد تحت القاع المحمي ($threshold).",
                    reasonEn = "Confirmed Bullish-to-Bearish CHoCH with close below protected swing low ($threshold).",
                    evidence = listOf("close[${bar.barIndex}]=${bar.close}", "protectedLow=${protectedSwing.price}")
                )
            } else {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.FAIL,
                    reasonAr = "لم تغلق الشمعة تحت القاع المحمي ($threshold)؛ الذيل وحده لا يؤكد CHoCH.",
                    reasonEn = "Candle did not close below protected swing low ($threshold); wick alone is not CHoCH.",
                    evidence = listOf("low[${bar.barIndex}]=${bar.low}", "close[${bar.barIndex}]=${bar.close}")
                )
            }
        } else {
            // Bearish to Bullish CHoCH requires close above protected swing high
            val threshold = protectedSwing.price + margin
            if (bar.close >= threshold - epsilon) {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.PASS,
                    reasonAr = "تغير هيكل CHoCH من هابط إلى صاعد بإغلاق مؤكد فوق القمة المحمية ($threshold).",
                    reasonEn = "Confirmed Bearish-to-Bullish CHoCH with close above protected swing high ($threshold).",
                    evidence = listOf("close[${bar.barIndex}]=${bar.close}", "protectedHigh=${protectedSwing.price}")
                )
            } else {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.FAIL,
                    reasonAr = "لم تغلق الشمعة فوق القمة المحمية ($threshold).",
                    reasonEn = "Candle did not close above protected swing high ($threshold).",
                    evidence = listOf("high[${bar.barIndex}]=${bar.high}", "close[${bar.barIndex}]=${bar.close}")
                )
            }
        }
    }

    /**
     * 5. LIQ.SWEEP
     * Evaluates liquidity pool / equal-level sweep with tolerance max(2*tickSize, 0.05*ATR14)
     * and records sweep extremity & reclaim close without claiming order-book visibility.
     */
    fun evaluateLiquiditySweep(
        allCandles: List<Candle>,
        decisionBarIndex: Int,
        visibleDataThrough: Int,
        liquidityLevel: ReferenceLevel?,
        isBuySideLiquidity: Boolean,
        tickSize: Double = 0.1
    ): PredicateEvaluationResult {
        val ruleId = "SMC.LIQ.SWEEP"
        if (liquidityLevel == null || !liquidityLevel.knownBeforeDecision || tickSize <= 0.0) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "مستوى تجمع السيولة المرجعي غير محدد قبل القرار.",
                reasonEn = "Reference liquidity level not established before decision."
            )
        }
        if (decisionBarIndex > visibleDataThrough || liquidityLevel.confirmedAtBarIndex >= decisionBarIndex) {
            return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "بيانات سحب السيولة تقع بعد Playhead الحالي.",
                reasonEn = "Liquidity sweep bar is beyond visibleDataThrough."
            )
        }

        val visible = allCandles.filter { it.barIndex <= visibleDataThrough }
        val bar = visible.find { it.barIndex == decisionBarIndex }
            ?: return PredicateEvaluationResult(
                ruleId = ruleId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                reasonAr = "شمعة القرار غير متوفرة.",
                reasonEn = "Decision bar missing."
            )

        val minSweepMargin = tickSize
        val epsilon = 1e-9

        return if (isBuySideLiquidity) {
            val breached = bar.high >= liquidityLevel.price + minSweepMargin - epsilon
            val reclaimed = bar.close <= liquidityLevel.price + epsilon
            if (breached && reclaimed) {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.PASS,
                    reasonAr = "سحب سيولة شرائية (BSL Sweep) بتجاوز القمة (${bar.high}) والارتداد بالإغلاق (${bar.close}) تحت المستوى.",
                    reasonEn = "Buy-side liquidity sweep confirmed: high (${bar.high}) breached level (${liquidityLevel.price}) and closed back at (${bar.close}).",
                    evidence = listOf("high=${bar.high}", "close=${bar.close}", "level=${liquidityLevel.price}")
                )
            } else {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.FAIL,
                    reasonAr = "لم يتحقق سحب السيولة الشرائية والارتداد داخل المستوى.",
                    reasonEn = "Buy-side liquidity sweep and close reclaim did not occur.",
                    evidence = listOf("high=${bar.high}", "close=${bar.close}", "level=${liquidityLevel.price}")
                )
            }
        } else {
            val breached = bar.low <= liquidityLevel.price - minSweepMargin + epsilon
            val reclaimed = bar.close >= liquidityLevel.price - epsilon
            if (breached && reclaimed) {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.PASS,
                    reasonAr = "سحب سيولة بيعية (SSL Sweep) بتجاوز القاع (${bar.low}) والارتداد بالإغلاق (${bar.close}) فوق المستوى.",
                    reasonEn = "Sell-side liquidity sweep confirmed: low (${bar.low}) breached level (${liquidityLevel.price}) and closed back at (${bar.close}).",
                    evidence = listOf("low=${bar.low}", "close=${bar.close}", "level=${liquidityLevel.price}")
                )
            } else {
                PredicateEvaluationResult(
                    ruleId = ruleId,
                    outcome = AssessmentOutcome.FAIL,
                    reasonAr = "لم يتحقق سحب السيولة البيعية والارتداد داخل المستوى.",
                    reasonEn = "Sell-side liquidity sweep and close reclaim did not occur.",
                    evidence = listOf("low=${bar.low}", "close=${bar.close}", "level=${liquidityLevel.price}")
                )
            }
        }
    }
}
