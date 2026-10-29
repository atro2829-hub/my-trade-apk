package com.example.domain.engine

import com.example.domain.model.AssessmentOutcome
import com.example.domain.model.ErrorTag
import com.example.domain.model.MasteryStatus
import com.example.domain.model.Scenario
import kotlin.math.sqrt

data class AttemptHistoryItem(
    val attemptId: String,
    val scenarioId: String,
    val scenarioVersion: String = "1.0",
    val skillId: String,
    val familyId: String,
    val sessionId: String,
    val rubricScore: Double,
    val hintUsedBeforeAnswer: Boolean,
    val criticalError: Boolean,
    val outcome: AssessmentOutcome,
    val errorTag: ErrorTag,
    val timestampUtcMillis: Long,
    val isComplexTransferFamily: Boolean = false
) {
    /**
     * Spec 26.2:
     * firstTryPass = (rubricScore >= 0.80) AND (hintUsedBeforeAnswer == false) AND (criticalError == false)
     */
    val isFirstTryPass: Boolean
        get() = outcome == AssessmentOutcome.PASS &&
                rubricScore >= 0.80 - 1e-9 &&
                !hintUsedBeforeAnswer &&
                !criticalError
}

data class SkillMasterySnapshot(
    val skillId: String,
    val status: MasteryStatus,
    val independentScenarioCount: Int,
    val firstTryPassCount: Int,
    val sessionCount: Int,
    val distinctFamilyCount: Int,
    val pObserved: Double?,
    val wilsonL95: Double?,
    val insufficientEvidence: Boolean,
    val nextReviewDueUtcMillis: Long
)

data class CoachRecommendation(
    val scenario: Scenario,
    val priorityScore: Double,
    val errorRecency: Double,
    val skillDeficit: Double,
    val spacingDue: Double,
    val transferNeed: Double,
    val diversityBoost: Double,
    val reasonAr: String,
    val reasonEn: String
)

object AdaptiveCoachEngine {

    const val COACH_POLICY_VERSION = "1.0"
    private const val Z_95 = 1.96
    private const val DAY_MS = 86_400_000L

    /**
     * Computes Wilson Lower Bound 95% (z = 1.96) for k successes out of n independent trials.
     * Returns null when n == 0.
     */
    fun computeWilsonL95(k: Int, n: Int): Double? {
        if (n <= 0) return null
        val pHat = k.toDouble() / n.toDouble()
        val z2 = Z_95 * Z_95
        val denominator = 1.0 + (z2 / n.toDouble())
        val center = pHat + (z2 / (2.0 * n.toDouble()))
        val spread = Z_95 * sqrt((pHat * (1.0 - pHat) + (z2 / (4.0 * n.toDouble()))) / n.toDouble())
        return ((center - spread) / denominator).coerceIn(0.0, 1.0)
    }

    /**
     * Evaluates skill mastery strictly from independent first-try attempts (excluding UNASSESSABLE).
     * Repeated attempts on the same (scenarioId + scenarioVersion) do NOT increase independentScenarioCount
     * and do NOT enter the last-5 / last-10 mastery windows.
     */
    fun computeSkillMastery(
        skillId: String,
        hasCompletedIntroLesson: Boolean,
        allAttemptsForSkill: List<AttemptHistoryItem>,
        currentTimeUtcMillis: Long
    ): SkillMasterySnapshot {
        // Filter out UNASSESSABLE attempts (Spec 26.1: UNASSESSABLE never enters numerator or denominator)
        val assessable = allAttemptsForSkill
            .filter { it.skillId == skillId && it.outcome != AssessmentOutcome.UNASSESSABLE }
            .sortedBy { it.timestampUtcMillis }

        // Deduplicate by (scenarioId + scenarioVersion) keeping only the FIRST attempt for independent evidence
        val seenKeys = mutableSetOf<String>()
        val independentAttempts = mutableListOf<AttemptHistoryItem>()
        for (att in assessable) {
            val key = "${att.scenarioId}:${att.scenarioVersion}"
            if (seenKeys.add(key)) {
                independentAttempts.add(att)
            }
        }

        val n = independentAttempts.size
        val k = independentAttempts.count { it.isFirstTryPass }
        val sessions = independentAttempts.map { it.sessionId }.distinct().size
        val families = independentAttempts.map { it.familyId }.distinct().size

        val pObs = if (n > 0) k.toDouble() / n.toDouble() else null
        val wilson = computeWilsonL95(k, n)

        val last5 = independentAttempts.takeLast(5)
        val last5Passes = last5.count { it.isFirstTryPass }
        val last5CriticalError = last5.any { it.criticalError }

        val last10 = independentAttempts.takeLast(10)
        val last10Passes = last10.count { it.isFirstTryPass }

        val last2 = independentAttempts.takeLast(2)
        val repeatedErrorTwice = last2.size == 2 &&
                !last2[0].isFirstTryPass &&
                !last2[1].isFirstTryPass &&
                last2[0].errorTag != ErrorTag.NONE &&
                last2[0].errorTag == last2[1].errorTag

        val meetsConsistent = n >= 16 &&
                sessions >= 3 &&
                (pObs ?: 0.0) >= 0.875 - 1e-9 &&
                (wilson ?: 0.0) >= 0.65 - 1e-9 &&
                last10.size >= 10 &&
                last10Passes >= 9 &&
                families >= 4 &&
                !last5CriticalError &&
                !repeatedErrorTwice

        val meetsProvisional = n >= 8 &&
                sessions >= 3 &&
                (pObs ?: 0.0) >= 0.80 - 1e-9 &&
                last5.size >= 5 &&
                last5Passes >= 4 &&
                (wilson ?: 0.0) >= 0.50 - 1e-9 &&
                !repeatedErrorTwice

        val status = when {
            meetsConsistent -> MasteryStatus.CONSISTENT
            meetsProvisional -> MasteryStatus.PROVISIONAL
            n > 0 -> MasteryStatus.PRACTICING
            hasCompletedIntroLesson -> MasteryStatus.INTRODUCED
            else -> MasteryStatus.UNSEEN
        }

        val lastAttempt = independentAttempts.lastOrNull()
        val lastWasPass = lastAttempt?.isFirstTryPass == true
        val baseTime = lastAttempt?.timestampUtcMillis ?: currentTimeUtcMillis
        val reviewIntervalDays = when (status) {
            MasteryStatus.CONSISTENT -> if (lastWasPass) 7L else 1L
            MasteryStatus.PROVISIONAL -> if (lastWasPass) 3L else 1L
            else -> if (lastWasPass) 2L else 1L
        }
        val nextDue = if (lastAttempt == null) currentTimeUtcMillis else baseTime + reviewIntervalDays * DAY_MS

        return SkillMasterySnapshot(
            skillId = skillId,
            status = status,
            independentScenarioCount = n,
            firstTryPassCount = k,
            sessionCount = sessions,
            distinctFamilyCount = families,
            pObserved = pObs,
            wilsonL95 = wilson,
            insufficientEvidence = n < 8,
            nextReviewDueUtcMillis = nextDue
        )
    }

    /**
     * Spec 26.5 Deterministic Next Exercise Selection:
     * priority = 0.40 * errorRecency + 0.25 * skillDeficit + 0.15 * spacingDue + 0.10 * transferNeed + 0.10 * diversityBoost
     * Tie-breaking: highest priority -> oldest nextReviewDue -> oldest lastSeenAt -> alphabetical scenarioId.
     */
    fun selectNextRecommendation(
        availableScenarios: List<Scenario>,
        allAttempts: List<AttemptHistoryItem>,
        completedLessonIds: Set<String>,
        currentTimeUtcMillis: Long
    ): CoachRecommendation? {
        // Filter only approved scenarios compatible with ruleVersion 1.0
        val validCandidates = availableScenarios.filter {
            it.ruleVersion.startsWith("1.0") &&
                    it.decisionPoints.all { dp -> dp.answerKeyStatus == "APPROVED" }
        }
        if (validCandidates.isEmpty()) return null

        val assessable = allAttempts
            .filter { it.outcome != AssessmentOutcome.UNASSESSABLE }
            .sortedBy { it.timestampUtcMillis }

        val recent3Families = assessable.takeLast(3).map { it.familyId }.toSet()

        data class ScoredCandidate(
            val recommendation: CoachRecommendation,
            val dueTime: Long,
            val lastSeenAt: Long
        )

        val scored = validCandidates.map { scenario ->
            val skillId = scenario.primarySkillId
            val skillAttempts = assessable.filter { it.skillId == skillId }
            val mastery = computeSkillMastery(
                skillId = skillId,
                hasCompletedIntroLesson = completedLessonIds.isNotEmpty(),
                allAttemptsForSkill = skillAttempts,
                currentTimeUtcMillis = currentTimeUtcMillis
            )

            // Dedup independent attempts for errorRecency
            val seen = mutableSetOf<String>()
            val independentSkillAttempts = skillAttempts.filter {
                seen.add("${it.scenarioId}:${it.scenarioVersion}")
            }

            val last2 = independentSkillAttempts.takeLast(2)
            val last5 = independentSkillAttempts.takeLast(5)
            val errorRecency = when {
                last2.size == 2 &&
                        last2[0].errorTag != ErrorTag.NONE &&
                        last2[0].errorTag == last2[1].errorTag -> 1.0
                last5.any { !it.isFirstTryPass && it.errorTag != ErrorTag.NONE } -> 0.5
                else -> 0.0
            }

            val skillDeficit = if (mastery.pObserved == null) 1.0 else (1.0 - mastery.pObserved).coerceIn(0.0, 1.0)
            val spacingDue = if (currentTimeUtcMillis >= mastery.nextReviewDueUtcMillis) 1.0 else 0.0

            val passedBasic = independentSkillAttempts.any { !it.isComplexTransferFamily && it.isFirstTryPass }
            val failedComplex = independentSkillAttempts.any { it.isComplexTransferFamily && !it.isFirstTryPass }
            val transferNeed = when {
                passedBasic && failedComplex -> 1.0
                passedBasic && independentSkillAttempts.none { it.isComplexTransferFamily } -> 0.5
                else -> 0.0
            }

            val diversityBoost = if (scenario.familyId !in recent3Families) 1.0 else 0.0

            val priority = (0.40 * errorRecency) +
                    (0.25 * skillDeficit) +
                    (0.15 * spacingDue) +
                    (0.10 * transferNeed) +
                    (0.10 * diversityBoost)

            val lastSeenForScenario = assessable
                .filter { it.scenarioId == scenario.scenarioId }
                .maxOfOrNull { it.timestampUtcMillis } ?: 0L

            val reasonAr = buildString {
                append("الأولوية ${"%.2f".format(priority)}: ")
                if (errorRecency >= 1.0) append("تكرار خطأ مستقل في آخر محاولتين | ")
                if (mastery.insufficientEvidence) append("لا توجد أدلة كافية بعد (${mastery.independentScenarioCount}/8) | ")
                if (spacingDue >= 1.0) append("مستحق للمراجعة الموزعة")
                else append("تعزيز إتقان مهارة $skillId")
            }

            val reasonEn = buildString {
                append("Priority ${"%.2f".format(priority)}: ")
                if (errorRecency >= 1.0) append("Repeated independent error in last 2 tries | ")
                if (mastery.insufficientEvidence) append("Insufficient evidence (${mastery.independentScenarioCount}/8) | ")
                if (spacingDue >= 1.0) append("Spaced repetition review due")
                else append("Strengthening skill $skillId")
            }

            ScoredCandidate(
                recommendation = CoachRecommendation(
                    scenario = scenario,
                    priorityScore = priority,
                    errorRecency = errorRecency,
                    skillDeficit = skillDeficit,
                    spacingDue = spacingDue,
                    transferNeed = transferNeed,
                    diversityBoost = diversityBoost,
                    reasonAr = reasonAr,
                    reasonEn = reasonEn
                ),
                dueTime = mastery.nextReviewDueUtcMillis,
                lastSeenAt = lastSeenForScenario
            )
        }

        return scored.sortedWith(
            compareByDescending<ScoredCandidate> { (it.recommendation.priorityScore * 1_000_000).toLong() }
                .thenBy { it.dueTime }
                .thenBy { it.lastSeenAt }
                .thenBy { it.recommendation.scenario.scenarioId }
        ).firstOrNull()?.recommendation
    }
}
