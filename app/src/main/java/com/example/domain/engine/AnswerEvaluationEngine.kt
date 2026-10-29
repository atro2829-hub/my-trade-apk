package com.example.domain.engine

import com.example.domain.model.AssessmentOutcome
import com.example.domain.model.DecisionPoint
import com.example.domain.model.ErrorTag
import com.example.domain.model.QuestionType
import com.example.domain.model.ZoneBounds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class EvaluationResult(
    val decisionPointId: String,
    val outcome: AssessmentOutcome,
    val rubricScore: Double, // [0.0, 1.0]
    val criticalError: Boolean,
    val errorTag: ErrorTag,
    val iouValue: Double? = null,
    val feedbackAr: String,
    val feedbackEn: String
)

object AnswerEvaluationEngine {

    /**
     * Evaluates SINGLE_SELECT and NEXT_ACTION questions.
     * 1.0 for accepted answer, 0.0 for rejected/other, UNASSESSABLE if answerKeyStatus != APPROVED or empty key.
     */
    fun evaluateChoice(
        decisionPoint: DecisionPoint,
        selectedOption: String,
        failedCriticalCondition: Boolean = false
    ): EvaluationResult {
        if (decisionPoint.answerKeyStatus != "APPROVED" || decisionPoint.acceptedAnswers.isEmpty()) {
            return EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                rubricScore = 0.0,
                criticalError = false,
                errorTag = ErrorTag.UNCLASSIFIED,
                feedbackAr = "هذا السؤال غير قابل للتقييم حالياً (UNASSESSABLE) ولا يدخل في حساب الإتقان.",
                feedbackEn = "This question is currently UNASSESSABLE and excluded from mastery calculation."
            )
        }

        val normalized = selectedOption.trim()
        val isMatch = decisionPoint.acceptedAnswers.any { it.equals(normalized, ignoreCase = true) }

        if (failedCriticalCondition) {
            return EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.FAIL,
                rubricScore = 0.0,
                criticalError = true,
                errorTag = ErrorTag.CONDITION_MISSING,
                feedbackAr = "فشل شرط حرج يمنع قبول الإجابة: ${decisionPoint.feedbackAr}",
                feedbackEn = "Failed critical condition prevents passing: ${decisionPoint.feedbackEn}"
            )
        }

        return if (isMatch) {
            EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.PASS,
                rubricScore = 1.0,
                criticalError = false,
                errorTag = ErrorTag.NONE,
                feedbackAr = decisionPoint.feedbackAr,
                feedbackEn = decisionPoint.feedbackEn
            )
        } else {
            val tag = if (decisionPoint.questionType == QuestionType.NEXT_ACTION) {
                ErrorTag.ENTRY_TOO_EARLY
            } else {
                ErrorTag.CONCEPT_MISUNDERSTOOD
            }
            EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.FAIL,
                rubricScore = 0.0,
                criticalError = false,
                errorTag = tag,
                feedbackAr = decisionPoint.feedbackAr,
                feedbackEn = decisionPoint.feedbackEn
            )
        }
    }

    /**
     * Evaluates PRICE_POINT selection:
     * epsilonPrice = max(2 * tickSize, 0.05 * atr14)
     * Score = 1.0 within epsilon, 0.0 beyond 3 * epsilon, linear decay between 1x and 3x epsilon.
     * Right price at wrong bar window (> barWindowTolerance) scores 0.0 (FAIL).
     * Selecting a bar beyond visibleDataThrough is a critical lookahead error.
     */
    fun evaluatePricePoint(
        decisionPoint: DecisionPoint,
        submittedPrice: Double,
        submittedBarIndex: Int,
        tickSize: Double = 0.1,
        atr14: Double = 1.0,
        barWindowTolerance: Int = 1
    ): EvaluationResult {
        val targetPrice = decisionPoint.targetPrice
        val targetBar = decisionPoint.targetBarIndex
        if (decisionPoint.answerKeyStatus != "APPROVED" || targetPrice == null || targetBar == null) {
            return EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                rubricScore = 0.0,
                criticalError = false,
                errorTag = ErrorTag.UNCLASSIFIED,
                feedbackAr = "بيانات النقطة المرجعية غير مكتملة (UNASSESSABLE).",
                feedbackEn = "Target price/bar reference missing (UNASSESSABLE)."
            )
        }

        // Lookahead guard
        if (submittedBarIndex > decisionPoint.visibleDataThrough) {
            return EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.FAIL,
                rubricScore = 0.0,
                criticalError = true,
                errorTag = ErrorTag.TIMEFRAME_MISMATCH,
                feedbackAr = "خطأ حرج: لا يمكن اختيار شمعة بعد مؤشر Playhead المتاح.",
                feedbackEn = "Critical error: Cannot select a bar beyond visibleDataThrough."
            )
        }

        // Timestamp/bar window check: right price at wrong time is rejected
        if (abs(submittedBarIndex - targetBar) > barWindowTolerance) {
            return EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.FAIL,
                rubricScore = 0.0,
                criticalError = false,
                errorTag = ErrorTag.TIMEFRAME_MISMATCH,
                feedbackAr = "السعر قريب لكن التوقيت/الشمعة المختارة ($submittedBarIndex) خارج النافذة الزمنية الصحيحة ($targetBar).",
                feedbackEn = "Selected bar ($submittedBarIndex) is outside the valid bar window around bar $targetBar."
            )
        }

        val epsilonPrice = max(2.0 * tickSize, 0.05 * atr14)
        val distance = abs(submittedPrice - targetPrice)
        val eps = 1e-9

        val score = when {
            distance <= epsilonPrice + eps -> 1.0
            distance >= 3.0 * epsilonPrice - eps -> 0.0
            else -> 1.0 - ((distance - epsilonPrice) / (2.0 * epsilonPrice))
        }.coerceIn(0.0, 1.0)

        val outcome = if (score >= 0.80 - eps) AssessmentOutcome.PASS else AssessmentOutcome.FAIL
        val errorTag = if (outcome == AssessmentOutcome.PASS) ErrorTag.NONE else ErrorTag.REFERENCE_LEVEL_WRONG

        return EvaluationResult(
            decisionPointId = decisionPoint.decisionPointId,
            outcome = outcome,
            rubricScore = score,
            criticalError = false,
            errorTag = errorTag,
            feedbackAr = decisionPoint.feedbackAr,
            feedbackEn = decisionPoint.feedbackEn
        )
    }

    /**
     * Computes 2D Intersection over Union (IoU) on Price x Bar-Time space.
     * Spec section 3 (ZONE_RECTANGLE):
     * - IoU >= 0.80 -> score = 1.0
     * - 0.50 <= IoU < 0.80 -> score = (IoU - 0.50) / 0.30
     * - 0.20 <= IoU < 0.50 -> score = ((IoU - 0.20) / 0.30) * 0.5
     * - IoU < 0.20 -> score = 0.0
     */
    fun computeZoneIoU(target: ZoneBounds, submitted: ZoneBounds): Double {
        val tMinBar = min(target.startBarIndex, target.endBarIndex).toDouble()
        val tMaxBar = max(target.startBarIndex, target.endBarIndex).toDouble() + 1.0
        val tMinPrice = min(target.bottomPrice, target.topPrice)
        val tMaxPrice = max(target.bottomPrice, target.topPrice)

        val sMinBar = min(submitted.startBarIndex, submitted.endBarIndex).toDouble()
        val sMaxBar = max(submitted.startBarIndex, submitted.endBarIndex).toDouble() + 1.0
        val sMinPrice = min(submitted.bottomPrice, submitted.topPrice)
        val sMaxPrice = max(submitted.bottomPrice, submitted.topPrice)

        val interMinBar = max(tMinBar, sMinBar)
        val interMaxBar = min(tMaxBar, sMaxBar)
        val interMinPrice = max(tMinPrice, sMinPrice)
        val interMaxPrice = min(tMaxPrice, sMaxPrice)

        val interWidth = (interMaxBar - interMinBar).coerceAtLeast(0.0)
        val interHeight = (interMaxPrice - interMinPrice).coerceAtLeast(0.0)
        val intersectionArea = interWidth * interHeight

        val targetArea = (tMaxBar - tMinBar) * (tMaxPrice - tMinPrice)
        val submittedArea = (sMaxBar - sMinBar) * (sMaxPrice - sMinPrice)
        val unionArea = targetArea + submittedArea - intersectionArea

        if (unionArea <= 1e-12) return 0.0
        return (intersectionArea / unionArea).coerceIn(0.0, 1.0)
    }

    fun scoreFromIoU(iou: Double): Double {
        val eps = 1e-9
        return when {
            iou >= 0.80 - eps -> 1.0
            iou >= 0.50 - eps -> ((iou - 0.50) / 0.30).coerceIn(0.0, 1.0)
            iou >= 0.20 - eps -> (((iou - 0.20) / 0.30) * 0.5).coerceIn(0.0, 0.5)
            else -> 0.0
        }
    }

    fun evaluateZoneRectangle(
        decisionPoint: DecisionPoint,
        submittedZone: ZoneBounds
    ): EvaluationResult {
        val targetZone = decisionPoint.targetZone
        if (decisionPoint.answerKeyStatus != "APPROVED" || targetZone == null) {
            return EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.UNASSESSABLE,
                rubricScore = 0.0,
                criticalError = false,
                errorTag = ErrorTag.UNCLASSIFIED,
                feedbackAr = "المنطقة المرجعية غير محددة في مفتاح الإجابة (UNASSESSABLE).",
                feedbackEn = "Target zone not defined in answer key (UNASSESSABLE)."
            )
        }

        // Lookahead guard
        if (max(submittedZone.startBarIndex, submittedZone.endBarIndex) > decisionPoint.visibleDataThrough) {
            return EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.FAIL,
                rubricScore = 0.0,
                criticalError = true,
                errorTag = ErrorTag.TIMEFRAME_MISMATCH,
                iouValue = 0.0,
                feedbackAr = "خطأ حرج: تمتد المنطقة المرسومة إلى شموع مستقبلية بعد Playhead.",
                feedbackEn = "Critical error: Drawn zone extends beyond visibleDataThrough."
            )
        }

        // Zone type check
        if (!submittedZone.zoneType.equals(targetZone.zoneType, ignoreCase = true)) {
            return EvaluationResult(
                decisionPointId = decisionPoint.decisionPointId,
                outcome = AssessmentOutcome.FAIL,
                rubricScore = 0.0,
                criticalError = false,
                errorTag = ErrorTag.CONCEPT_MISUNDERSTOOD,
                iouValue = 0.0,
                feedbackAr = "نوع المنطقة المختار (${submittedZone.zoneType}) لا يطابق النوع المطلوب (${targetZone.zoneType}).",
                feedbackEn = "Selected zone type (${submittedZone.zoneType}) does not match expected (${targetZone.zoneType})."
            )
        }

        val iou = computeZoneIoU(targetZone, submittedZone)
        val score = scoreFromIoU(iou)
        val outcome = if (score >= 0.80 - 1e-9) AssessmentOutcome.PASS else AssessmentOutcome.FAIL
        val errorTag = if (outcome == AssessmentOutcome.PASS) ErrorTag.NONE else ErrorTag.ZONE_BOUNDARY_WRONG

        return EvaluationResult(
            decisionPointId = decisionPoint.decisionPointId,
            outcome = outcome,
            rubricScore = score,
            criticalError = false,
            errorTag = errorTag,
            iouValue = iou,
            feedbackAr = "${decisionPoint.feedbackAr} (IoU=${"%.2f".format(iou)}, الدرجة=${"%.2f".format(score)})",
            feedbackEn = "${decisionPoint.feedbackEn} (IoU=${"%.2f".format(iou)}, Score=${"%.2f".format(score)})"
        )
    }
}
