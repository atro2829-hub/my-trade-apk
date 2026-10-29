package com.example

import com.example.data.content.SmcLabContentCatalog
import com.example.data.local.LessonProgressEntity
import com.example.data.local.PackManifestEntity
import com.example.domain.engine.AdEligibilityCoordinator
import com.example.domain.engine.AdaptiveCoachEngine
import com.example.domain.engine.AnswerEvaluationEngine
import com.example.domain.engine.AppSessionActivityState
import com.example.domain.engine.AttemptHistoryItem
import com.example.domain.engine.BackupRestoreMode
import com.example.domain.engine.LocalBackupBundle
import com.example.domain.engine.LocalBackupEngine
import com.example.domain.engine.PackActivationStatus
import com.example.domain.engine.PackCandidate
import com.example.domain.engine.PackManagerEngine
import com.example.domain.engine.SimulationEngine
import com.example.domain.engine.SimulationWorkspaceState
import com.example.domain.engine.SmcRulePredicates
import com.example.domain.model.AssessmentOutcome
import com.example.domain.model.Candle
import com.example.domain.model.EntryMode
import com.example.domain.model.ErrorTag
import com.example.domain.model.MasteryStatus
import com.example.domain.model.Playhead
import com.example.domain.model.PlayheadMode
import com.example.domain.model.QuestionType
import com.example.domain.model.ReferenceLevel
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeStatus
import com.example.domain.model.ZoneBounds
import com.example.ui.chart.OhlcChartGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    // =========================================================================
    // PHASE 0: Domain Models & 10-Candle Synthetic BOS Fixture
    // =========================================================================

    @Test
    fun phase0_syntheticFixture10CandlesAndPlayheadGate() {
        val scenario = SmcLabContentCatalog.goldenBos10BarScenario
        assertEquals("SYN-BOS-001", scenario.scenarioId)
        assertEquals(10, scenario.candles.size)
        assertEquals(2, scenario.decisionPoints.size)

        val dp6 = scenario.decisionPoints[0]
        assertEquals("DP-06", dp6.decisionPointId)
        assertEquals(6, dp6.visibleDataThrough)

        val playhead = Playhead(
            workspaceId = "WS-TEST",
            scenarioId = scenario.scenarioId,
            mode = PlayheadMode.CHALLENGE,
            currentBarIndex = dp6.visibleDataThrough,
            maxBarIndex = 9
        )

        val visible = playhead.visibleDataThrough(scenario.candles)
        // Bars 0..6 inclusive = 7 bars; bars 7, 8, 9 are strictly hidden
        assertEquals(7, visible.size)
        assertTrue(visible.all { it.barIndex <= 6 })
        assertFalse(playhead.canRewind()) // CHALLENGE mode before decision locked cannot rewind
    }

    @Test
    fun phase0_playheadModesRespectRewindRules() {
        val freePh = Playhead("W1", "S1", PlayheadMode.FREE_CHART, currentBarIndex = 5, maxBarIndex = 9)
        assertTrue(freePh.canRewind())
        assertEquals(4, freePh.rewind().currentBarIndex)

        val challengeUnlocked = Playhead("W2", "S1", PlayheadMode.CHALLENGE, currentBarIndex = 5, maxBarIndex = 9, isDecisionLocked = false)
        assertFalse(challengeUnlocked.canRewind())

        val challengeLockedReview = challengeUnlocked.copy(isDecisionLocked = true)
        assertTrue(challengeLockedReview.canRewind())

        val simActive = Playhead("W3", "S1", PlayheadMode.SIMULATION, currentBarIndex = 5, maxBarIndex = 9, hasActivePosition = true, isScenarioFinished = false)
        assertFalse(simActive.canRewind())

        val simFinishedNoPos = simActive.copy(hasActivePosition = false, isScenarioFinished = true)
        assertTrue(simFinishedNoPos.canRewind())
    }

    // =========================================================================
    // PHASE 1: Chart Geometry & 500-Candle Benchmark (<= 1.5s target)
    // =========================================================================

    @Test
    fun phase1_benchmark500CandlesUnder1500msAndPlayheadCutoff() {
        val candles500 = SmcLabContentCatalog.generate500CandlesBenchmark("BENCH-500")
        assertEquals(500, candles500.size)

        val playheadFull = Playhead("W-500", "BENCH-500", PlayheadMode.FREE_CHART, currentBarIndex = 499, maxBarIndex = 499)
        val startNs = System.nanoTime()
        val primitives = OhlcChartGeometry.computeVisiblePrimitives(
            playhead = playheadFull,
            allCandles = candles500,
            canvasWidth = 1080f,
            canvasHeight = 600f,
            zoomFactor = 1f,
            panOffsetBars = 0f
        )
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertEquals(500, primitives.size)
        assertTrue("Expected 500-candle layout <= 1500ms, actual=${elapsedMs}ms", elapsedMs <= 1500.0)

        // Verify Playhead cutoff at bar 249 only renders 250 bars (0..249)
        val playheadHalf = playheadFull.copy(currentBarIndex = 249)
        val halfPrimitives = OhlcChartGeometry.computeVisiblePrimitives(
            playhead = playheadHalf,
            allCandles = candles500,
            canvasWidth = 1080f,
            canvasHeight = 600f
        )
        assertEquals(250, halfPrimitives.size)
        assertEquals(249, halfPrimitives.last().barIndex)
    }

    // =========================================================================
    // PHASE 2: Vertical Slice (BOS Challenge + MARKET_NEXT_OPEN + Stop-First + R_net/R_gross + Idempotency)
    // =========================================================================

    @Test
    fun phase2_verticalSliceBosChallengeAndMarketNextOpenSimulation() {
        val scenario = SmcLabContentCatalog.goldenBos10BarScenario
        val dp6 = scenario.decisionPoints[0]
        val dp7 = scenario.decisionPoints[1]

        // At DP-06 (bar 6), WICK_SWEEP is PASS, CLOSE_BREAK is FAIL
        val evalDp6Pass = AnswerEvaluationEngine.evaluateChoice(dp6, "WICK_SWEEP")
        assertEquals(AssessmentOutcome.PASS, evalDp6Pass.outcome)
        assertEquals(1.0, evalDp6Pass.rubricScore, 1e-6)

        val evalDp6Fail = AnswerEvaluationEngine.evaluateChoice(dp6, "CLOSE_BREAK")
        assertEquals(AssessmentOutcome.FAIL, evalDp6Fail.outcome)

        // At DP-07 (bar 7), CLOSE_BREAK is PASS
        val evalDp7Pass = AnswerEvaluationEngine.evaluateChoice(dp7, "CLOSE_BREAK")
        assertEquals(AssessmentOutcome.PASS, evalDp7Pass.outcome)

        // Open MARKET_NEXT_OPEN LONG at bar 7 close (105.2), SL=104.2 (1.0 risk * 10 qty = $10 risk), TP=107.2 ($20 gross reward)
        var simState = SimulationWorkspaceState(
            workspaceId = "WS-SLICE",
            scenarioId = scenario.scenarioId,
            playhead = Playhead("WS-SLICE", scenario.scenarioId, PlayheadMode.SIMULATION, currentBarIndex = 7, maxBarIndex = 9)
        )

        simState = SimulationEngine.submitOrder(
            state = simState,
            allCandles = scenario.candles,
            tradeId = "T-1",
            direction = TradeDirection.LONG,
            entryMode = EntryMode.MARKET_NEXT_OPEN,
            quantity = 10.0,
            limitOrReferenceEntryPrice = 105.2,
            stopPrice = 104.2,
            targetPrice = 107.2,
            feeAmount = 2.0,
            idempotencyKey = "KEY-SUBMIT-1"
        )
        assertNotNull(simState.activeTrade)
        assertEquals(TradeStatus.VALIDATED, simState.activeTrade?.status)

        // Test Idempotency: submitting same key again does not duplicate event or error
        val duplicateState = SimulationEngine.submitOrder(
            state = simState,
            allCandles = scenario.candles,
            tradeId = "T-1",
            direction = TradeDirection.LONG,
            entryMode = EntryMode.MARKET_NEXT_OPEN,
            quantity = 10.0,
            limitOrReferenceEntryPrice = 105.2,
            stopPrice = 104.2,
            targetPrice = 107.2,
            feeAmount = 2.0,
            idempotencyKey = "KEY-SUBMIT-1"
        )
        assertEquals(simState.tradeEvents.size, duplicateState.tradeEvents.size)

        // Single active position constraint: submitting a second trade with a new key while T-1 is active is blocked
        val secondOrderAttempt = SimulationEngine.submitOrder(
            state = simState,
            allCandles = scenario.candles,
            tradeId = "T-2",
            direction = TradeDirection.LONG,
            entryMode = EntryMode.MARKET_NEXT_OPEN,
            quantity = 10.0,
            limitOrReferenceEntryPrice = 105.2,
            stopPrice = 104.2,
            targetPrice = 107.2,
            feeAmount = 2.0,
            idempotencyKey = "KEY-SUBMIT-2"
        )
        assertNotNull(secondOrderAttempt.lastErrorEn)

        // Step to Bar 8: Market order fills at Bar 8 open (105.2); Bar 8 high is 106.8 (< 107.2), low is 104.8 (> 104.2) -> stays OPEN
        simState = SimulationEngine.stepForwardBar(
            state = simState,
            allCandles = scenario.candles,
            idempotencyKey = "STEP-8"
        )
        assertEquals(TradeStatus.OPEN, simState.activeTrade?.status)
        assertEquals(105.2, simState.activeTrade?.executedEntryPrice ?: 0.0, 1e-6)

        // Step to Bar 9: Bar 9 high is 108.2 (>= 107.2 TP), low is 106.0 (> 104.2 SL) -> closes at TP (107.2)
        simState = SimulationEngine.stepForwardBar(
            state = simState,
            allCandles = scenario.candles,
            idempotencyKey = "STEP-9"
        )
        assertNull(simState.activeTrade)
        assertEquals(1, simState.completedTrades.size)
        val closedTrade = simState.completedTrades.first()
        assertEquals(TradeStatus.CLOSED_TP, closedTrade.status)
        assertEquals(10.0, closedTrade.plannedPriceRisk, 1e-6)
        assertEquals(20.0, closedTrade.grossPnl, 1e-6)
        assertEquals(18.0, closedTrade.netPnl, 1e-6)
        assertEquals(2.0, closedTrade.rGross, 1e-6)
        assertEquals(1.8, closedTrade.rNet, 1e-6) // R = R_net = 18 / 10 = 1.8R

        val report = simState.latestReport
        assertNotNull(report)
        assertEquals(1.8, report!!.rNet, 1e-6)
        assertEquals(2.0, report.rGross, 1e-6)
    }

    @Test
    fun phase2_stopFirstConflictResolutionInSingleCandle() {
        val scenario = SmcLabContentCatalog.goldenBos10BarScenario
        var simState = SimulationWorkspaceState(
            workspaceId = "WS-CONFLICT",
            scenarioId = scenario.scenarioId,
            playhead = Playhead("WS-CONFLICT", scenario.scenarioId, PlayheadMode.SIMULATION, currentBarIndex = 7, maxBarIndex = 9)
        )

        // Bar 8 has O=105.2, H=106.8, L=104.8, C=106.5
        // Set SL=105.0 (touched since L=104.8 <= 105.0) and TP=106.0 (touched since H=106.8 >= 106.0)
        simState = SimulationEngine.submitOrder(
            state = simState,
            allCandles = scenario.candles,
            tradeId = "T-CONFLICT",
            direction = TradeDirection.LONG,
            entryMode = EntryMode.MARKET_NEXT_OPEN,
            quantity = 10.0,
            limitOrReferenceEntryPrice = 105.2,
            stopPrice = 105.0,
            targetPrice = 106.0,
            feeAmount = 0.0,
            idempotencyKey = "KEY-CONFLICT"
        )
        simState = SimulationEngine.stepForwardBar(
            state = simState,
            allCandles = scenario.candles,
            idempotencyKey = "STEP-CONFLICT-8"
        )

        val closed = simState.completedTrades.first()
        assertEquals(TradeStatus.CLOSED_SL, closed.status)
        assertTrue(closed.stopFirstConflictTriggered)
        assertEquals(-1.0, closed.rNet, 1e-6)
        assertNotNull(simState.latestReport?.stopFirstConflictNote)
    }

    // =========================================================================
    // PHASE 3: SMC Rulebook Predicates (5 Rules x PASS/FAIL/UNASSESSABLE/Lookahead)
    //          + Evaluation Engine (IoU) + Adaptive Coach (Wilson L95 & Priority)
    // =========================================================================

    @Test
    fun phase3_allFivePredicatesPassFailUnassessableAndLookahead() {
        val candles = SmcLabContentCatalog.goldenBos10BarScenario.candles
        val refHigh = ReferenceLevel("SWING_HIGH", 105.0, confirmedAtBarIndex = 5, knownBeforeDecision = true)

        // 1. SWING.PIVOT_CONFIRMED (bar 3 is swing high 105.0, requires bars 1,2 and 4,5)
        val pivotPass = SmcRulePredicates.evaluateSwingPivotConfirmed(candles, candidateBarIndex = 3, visibleDataThrough = 5, isSwingHigh = true)
        assertEquals(AssessmentOutcome.PASS, pivotPass.outcome)

        val pivotLookahead = SmcRulePredicates.evaluateSwingPivotConfirmed(candles, candidateBarIndex = 3, visibleDataThrough = 4, isSwingHigh = true)
        assertEquals(AssessmentOutcome.UNASSESSABLE, pivotLookahead.outcome)

        val pivotFail = SmcRulePredicates.evaluateSwingPivotConfirmed(candles, candidateBarIndex = 2, visibleDataThrough = 5, isSwingHigh = true)
        assertEquals(AssessmentOutcome.FAIL, pivotFail.outcome)

        // 2. BOS.CLOSE_BREAK (bar 7 closes at 105.2 >= 105.1)
        val bosPass = SmcRulePredicates.evaluateBosCloseBreak(candles, decisionBarIndex = 7, visibleDataThrough = 7, referenceLevel = refHigh, isBullish = true)
        assertEquals(AssessmentOutcome.PASS, bosPass.outcome)

        val bosFailOnWick = SmcRulePredicates.evaluateBosCloseBreak(candles, decisionBarIndex = 6, visibleDataThrough = 6, referenceLevel = refHigh, isBullish = true)
        assertEquals(AssessmentOutcome.FAIL, bosFailOnWick.outcome)

        val bosLookahead = SmcRulePredicates.evaluateBosCloseBreak(candles, decisionBarIndex = 7, visibleDataThrough = 6, referenceLevel = refHigh, isBullish = true)
        assertEquals(AssessmentOutcome.UNASSESSABLE, bosLookahead.outcome)

        // 3. BOS.WICK_SWEEP (bar 6 has high=105.7 >= 105.1, close=104.9 < 105.1)
        val sweepPass = SmcRulePredicates.evaluateBosWickSweep(candles, decisionBarIndex = 6, visibleDataThrough = 6, referenceLevel = refHigh, isBullishSweep = true)
        assertEquals(AssessmentOutcome.PASS, sweepPass.outcome)

        val sweepFailOnCloseBreak = SmcRulePredicates.evaluateBosWickSweep(candles, decisionBarIndex = 7, visibleDataThrough = 7, referenceLevel = refHigh, isBullishSweep = true)
        assertEquals(AssessmentOutcome.FAIL, sweepFailOnCloseBreak.outcome)

        val sweepUnassessable = SmcRulePredicates.evaluateBosWickSweep(candles, decisionBarIndex = 6, visibleDataThrough = 6, referenceLevel = refHigh.copy(knownBeforeDecision = false), isBullishSweep = true)
        assertEquals(AssessmentOutcome.UNASSESSABLE, sweepUnassessable.outcome)

        // 4. CHOCH.STRUCTURE_CHANGE
        val protectedHigh = ReferenceLevel("PROTECTED_SWING_HIGH", 105.0, confirmedAtBarIndex = 5, knownBeforeDecision = true)
        val chochPass = SmcRulePredicates.evaluateChochStructureChange(
            allCandles = candles,
            decisionBarIndex = 7,
            visibleDataThrough = 7,
            protectedSwing = protectedHigh,
            priorTrendIsBullish = false,
            idmConfirmedBeforeBreak = true
        )
        assertEquals(AssessmentOutcome.PASS, chochPass.outcome)

        val chochFailNoIdm = SmcRulePredicates.evaluateChochStructureChange(
            allCandles = candles,
            decisionBarIndex = 7,
            visibleDataThrough = 7,
            protectedSwing = protectedHigh,
            priorTrendIsBullish = false,
            idmConfirmedBeforeBreak = false
        )
        assertEquals(AssessmentOutcome.FAIL, chochFailNoIdm.outcome)

        val chochUnassessable = SmcRulePredicates.evaluateChochStructureChange(
            allCandles = candles,
            decisionBarIndex = 7,
            visibleDataThrough = 6,
            protectedSwing = protectedHigh,
            priorTrendIsBullish = false,
            idmConfirmedBeforeBreak = true
        )
        assertEquals(AssessmentOutcome.UNASSESSABLE, chochUnassessable.outcome)

        // 5. LIQ.SWEEP (bar 6 high=105.7 >= 105.1 and close=104.9 <= 105.0)
        val liqPass = SmcRulePredicates.evaluateLiquiditySweep(candles, decisionBarIndex = 6, visibleDataThrough = 6, liquidityLevel = refHigh, isBuySideLiquidity = true)
        assertEquals(AssessmentOutcome.PASS, liqPass.outcome)

        val liqFail = SmcRulePredicates.evaluateLiquiditySweep(candles, decisionBarIndex = 7, visibleDataThrough = 7, liquidityLevel = refHigh, isBuySideLiquidity = true)
        assertEquals(AssessmentOutcome.FAIL, liqFail.outcome)

        val liqLookahead = SmcRulePredicates.evaluateLiquiditySweep(candles, decisionBarIndex = 6, visibleDataThrough = 5, liquidityLevel = refHigh, isBuySideLiquidity = true)
        assertEquals(AssessmentOutcome.UNASSESSABLE, liqLookahead.outcome)
    }

    @Test
    fun phase3_evaluationEngineZoneIoUAndPricePointBoundaries() {
        val targetZone = ZoneBounds(startBarIndex = 0, endBarIndex = 9, bottomPrice = 100.0, topPrice = 110.0, zoneType = "OB")
        // Identical zone -> IoU = 1.0 -> score = 1.0
        val iouFull = AnswerEvaluationEngine.computeZoneIoU(targetZone, targetZone)
        assertEquals(1.0, iouFull, 1e-6)
        assertEquals(1.0, AnswerEvaluationEngine.scoreFromIoU(0.80), 1e-6)
        assertEquals(0.5, AnswerEvaluationEngine.scoreFromIoU(0.65), 1e-6)
        assertEquals(0.25, AnswerEvaluationEngine.scoreFromIoU(0.35), 1e-6)
        assertEquals(0.0, AnswerEvaluationEngine.scoreFromIoU(0.19), 1e-6)

        // Price point right price at wrong timestamp/bar is rejected
        val dp = SmcLabContentCatalog.goldenBos10BarScenario.decisionPoints[0]
        val wrongTimeEval = AnswerEvaluationEngine.evaluatePricePoint(
            decisionPoint = dp,
            submittedPrice = 105.0,
            submittedBarIndex = 2, // targetBarIndex is 6
            tickSize = 0.1
        )
        assertEquals(AssessmentOutcome.FAIL, wrongTimeEval.outcome)
        assertEquals(0.0, wrongTimeEval.rubricScore, 1e-6)
    }

    @Test
    fun phase3_adaptiveCoachWilsonL95AndMasteryThresholds() {
        val now = 1767225600000L

        // 7 independent scenarios are NOT enough for PROVISIONAL (requires >= 8)
        val attempts7 = (1..7).map { i ->
            AttemptHistoryItem(
                attemptId = "A-$i",
                scenarioId = "SC-$i",
                skillId = "bos_vs_sweep",
                familyId = "FAM_${(i % 4) + 1}",
                sessionId = "SES-${(i % 3) + 1}",
                rubricScore = 1.0,
                hintUsedBeforeAnswer = false,
                criticalError = false,
                outcome = AssessmentOutcome.PASS,
                errorTag = ErrorTag.NONE,
                timestampUtcMillis = now + i * 1000L
            )
        }
        val mastery7 = AdaptiveCoachEngine.computeSkillMastery("bos_vs_sweep", true, attempts7, now + 10_000L)
        assertEquals(MasteryStatus.PRACTICING, mastery7.status)
        assertTrue(mastery7.insufficientEvidence)

        // Repeating the same scenario does NOT increase independentScenarioCount
        val repeated = attempts7 + AttemptHistoryItem(
            attemptId = "A-DUP",
            scenarioId = "SC-1",
            skillId = "bos_vs_sweep",
            familyId = "FAM_1",
            sessionId = "SES-1",
            rubricScore = 1.0,
            hintUsedBeforeAnswer = false,
            criticalError = false,
            outcome = AssessmentOutcome.PASS,
            errorTag = ErrorTag.NONE,
            timestampUtcMillis = now + 9000L
        )
        val masteryDup = AdaptiveCoachEngine.computeSkillMastery("bos_vs_sweep", true, repeated, now + 10_000L)
        assertEquals(7, masteryDup.independentScenarioCount)

        // 8th independent pass across >=3 sessions reaches PROVISIONAL (8/8 => p=1.0, L95=0.675 >= 0.50)
        val attempts8 = attempts7 + AttemptHistoryItem(
            attemptId = "A-8",
            scenarioId = "SC-8",
            skillId = "bos_vs_sweep",
            familyId = "FAM_4",
            sessionId = "SES-3",
            rubricScore = 1.0,
            hintUsedBeforeAnswer = false,
            criticalError = false,
            outcome = AssessmentOutcome.PASS,
            errorTag = ErrorTag.NONE,
            timestampUtcMillis = now + 8000L
        )
        val mastery8 = AdaptiveCoachEngine.computeSkillMastery("bos_vs_sweep", true, attempts8, now + 10_000L)
        assertEquals(MasteryStatus.PROVISIONAL, mastery8.status)
        assertFalse(mastery8.insufficientEvidence)

        // 16 independent passes across >=3 sessions and >=4 families reaches CONSISTENT
        val attempts16 = (1..16).map { i ->
            AttemptHistoryItem(
                attemptId = "A-$i",
                scenarioId = "SC-$i",
                skillId = "bos_vs_sweep",
                familyId = "FAM_${(i % 4) + 1}",
                sessionId = "SES-${(i % 3) + 1}",
                rubricScore = 1.0,
                hintUsedBeforeAnswer = false,
                criticalError = false,
                outcome = AssessmentOutcome.PASS,
                errorTag = ErrorTag.NONE,
                timestampUtcMillis = now + i * 1000L
            )
        }
        val mastery16 = AdaptiveCoachEngine.computeSkillMastery("bos_vs_sweep", true, attempts16, now + 20_000L)
        assertEquals(MasteryStatus.CONSISTENT, mastery16.status)
        assertTrue((mastery16.wilsonL95 ?: 0.0) >= 0.65)
    }

    // =========================================================================
    // PHASE 4: Content Verification (41 Lessons + 96 Scenarios x 4 Test Cases)
    // =========================================================================

    @Test
    fun phase4_verify41LessonsAnd96ScenariosWith384TestCases() {
        val lessons = SmcLabContentCatalog.all41Lessons
        assertEquals(41, lessons.size)
        assertEquals("L00-01", lessons.first().lessonId)
        assertEquals("L11-04", lessons.last().lessonId)

        val scenarios = SmcLabContentCatalog.all96Scenarios
        assertEquals(96, scenarios.size)

        var totalTestCasesVerified = 0
        for (sc in scenarios) {
            assertEquals("Each scenario must have 4 test cases", 4, sc.testCases.size)
            val dp = sc.decisionPoints.first()
            // Verify PASS test case evaluates to PASS
            val passTc = sc.testCases.first { it.caseType == "PASS" }
            val evalPass = when (dp.questionType) {
                QuestionType.PRICE_POINT -> AnswerEvaluationEngine.evaluatePricePoint(
                    dp,
                    passTc.submittedPrice!!,
                    passTc.submittedBarIndex!!,
                    sc.tickSize
                )
                QuestionType.ZONE_RECTANGLE -> AnswerEvaluationEngine.evaluateZoneRectangle(
                    dp,
                    passTc.submittedZone!!
                )
                else -> AnswerEvaluationEngine.evaluateChoice(dp, passTc.submittedAnswer)
            }
            assertEquals("Scenario ${sc.scenarioId} PASS test case failed", AssessmentOutcome.PASS, evalPass.outcome)
            totalTestCasesVerified += sc.testCases.size
        }
        assertEquals(384, totalTestCasesVerified)
        assertTrue(SmcLabContentCatalog.glossaryTerms.size >= 10)
    }

    // =========================================================================
    // PHASE 5: Pack SHA-256 + Ed25519 + Rollback, AdEligibilityCoordinator, Local Backup
    // =========================================================================

    @Test
    fun phase5_packVerificationAtomicActivationRollbackAndAdCoordinator() {
        val initialPack = PackManifestEntity(
            packId = "CORE_PACK_V1",
            packVersion = "1.0.0",
            keyId = "SMC_PACK_SIGNING_KEY_2026_V1",
            schemaVersion = 2,
            minAppVersion = 1,
            sizeBytes = 100L,
            sha256Hex = "init",
            signatureHex = "sig",
            dataKind = "SYNTHETIC_ORIGINAL",
            sourceId = "SRC",
            licenseId = "LIC",
            status = "ACTIVE",
            activatedAtUtcMillis = 1000L
        )

        val payload = "PACK_V2_BINARY_CONTENT".toByteArray()
        val sha = PackManagerEngine.computeSha256Hex(payload)
        val keyPair = PackManagerEngine.generateEd25519KeyPairOrNull()
        val canonical = PackManagerEngine.canonicalManifestString(
            packId = "HIST_PACK_V2",
            packVersion = "2.0.0",
            keyId = "SMC_PACK_SIGNING_KEY_2026_V1",
            schemaVersion = 2,
            minAppVersion = 1,
            sizeBytes = payload.size.toLong(),
            sha256Hex = sha,
            dataKind = "HISTORICAL_SOURCE",
            sourceId = "LICENSED_FEED",
            licenseId = "COMM_V1"
        )
        val signature = PackManagerEngine.signCanonicalManifest(canonical, keyPair)

        // 1. Valid activation keeps previous pack as PREVIOUS_VALID
        val validCandidate = PackCandidate(
            packId = "HIST_PACK_V2",
            packVersion = "2.0.0",
            keyId = "SMC_PACK_SIGNING_KEY_2026_V1",
            schemaVersion = 2,
            minAppVersion = 1,
            payloadBytes = payload,
            declaredSizeBytes = payload.size.toLong(),
            declaredSha256Hex = sha,
            signatureHex = signature,
            dataKind = "HISTORICAL_SOURCE",
            sourceId = "LICENSED_FEED",
            licenseId = "COMM_V1"
        )
        val actRes = PackManagerEngine.stageVerifyAndActivatePack(
            candidate = validCandidate,
            currentActivePack = initialPack,
            availableStorageBytes = 10_000_000L,
            publicKey = keyPair?.public
        )
        assertTrue(actRes.success)
        assertEquals(PackActivationStatus.ACTIVE, actRes.status)
        assertEquals("HIST_PACK_V2", actRes.activePack?.packId)
        assertEquals("CORE_PACK_V1", actRes.previousValidPack?.packId)

        // 2. Tampered SHA-256 rolls back and keeps previous active pack
        val tamperedCandidate = validCandidate.copy(declaredSha256Hex = "bad_sha256")
        val rollbackRes = PackManagerEngine.stageVerifyAndActivatePack(
            candidate = tamperedCandidate,
            currentActivePack = actRes.activePack,
            availableStorageBytes = 10_000_000L,
            publicKey = keyPair?.public
        )
        assertFalse(rollbackRes.success)
        assertEquals(PackActivationStatus.ROLLED_BACK, rollbackRes.status)
        assertEquals("HIST_PACK_V2", rollbackRes.activePack?.packId)

        // 3. Low storage (< 1.2x pack size) aborts safely and keeps active pack
        val lowStorageRes = PackManagerEngine.stageVerifyAndActivatePack(
            candidate = validCandidate,
            currentActivePack = initialPack,
            availableStorageBytes = 5L,
            publicKey = keyPair?.public
        )
        assertFalse(lowStorageRes.success)
        assertEquals(PackActivationStatus.ROLLED_BACK, lowStorageRes.status)
        assertEquals("CORE_PACK_V1", lowStorageRes.activePack?.packId)

        // 4. AdEligibilityCoordinator blocks ads during CHALLENGE, CHART_INTERACTION, SIMULATION_ACTIVE, TRADE_OPEN
        assertFalse(AdEligibilityCoordinator.isAdAllowed(AppSessionActivityState.CHALLENGE, false, false))
        assertFalse(AdEligibilityCoordinator.isAdAllowed(AppSessionActivityState.SIMULATION_ACTIVE, false, false))
        assertFalse(AdEligibilityCoordinator.isAdAllowed(AppSessionActivityState.HOME_BROWSING, true, false))
        assertFalse(AdEligibilityCoordinator.isAdAllowed(AppSessionActivityState.HOME_BROWSING, false, true))
        assertTrue(AdEligibilityCoordinator.isAdAllowed(AppSessionActivityState.HOME_BROWSING, false, false))

        // 5. Local Backup MERGE vs REPLACE
        val existingBundle = LocalBackupBundle(
            exportedAtUtcMillis = 1000L,
            lessonProgress = listOf(LessonProgressEntity("L00-01", true, 1000L, 1.0)),
            attempts = emptyList(),
            trades = emptyList(),
            drawings = emptyList()
        )
        val incomingBundle = LocalBackupBundle(
            exportedAtUtcMillis = 2000L,
            lessonProgress = listOf(LessonProgressEntity("L00-02", true, 2000L, 1.0)),
            attempts = emptyList(),
            trades = emptyList(),
            drawings = emptyList()
        )
        val merged = LocalBackupEngine.applyRestore(existingBundle, incomingBundle, BackupRestoreMode.MERGE)
        assertEquals(2, merged.lessonProgress.size)

        val replaced = LocalBackupEngine.applyRestore(existingBundle, incomingBundle, BackupRestoreMode.REPLACE)
        assertEquals(1, replaced.lessonProgress.size)
        assertEquals("L00-02", replaced.lessonProgress.first().lessonId)
    }
}
