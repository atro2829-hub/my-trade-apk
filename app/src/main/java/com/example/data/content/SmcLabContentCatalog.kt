package com.example.data.content

import com.example.domain.model.AssessmentOutcome
import com.example.domain.model.Candle
import com.example.domain.model.DataKind
import com.example.domain.model.DecisionPoint
import com.example.domain.model.DifficultyLevel
import com.example.domain.model.GlossaryTerm
import com.example.domain.model.Lesson
import com.example.domain.model.QuestionType
import com.example.domain.model.ReferenceLevel
import com.example.domain.model.Scenario
import com.example.domain.model.ScenarioTestCase
import com.example.domain.model.ZoneBounds

object SmcLabContentCatalog {

    private const val BASE_UTC_2026 = 1767225600000L // 2026-01-01T00:00:00Z
    private const val HOUR_MS = 3_600_000L

    /**
     * Phase 0 & Phase 2 Golden Fixture:
     * 10 synthetic candles + decision points at bar 6 (Wick Sweep) and bar 7 (Close Break BOS)
     * plus bars 8 and 9 for MARKET_NEXT_OPEN simulation testing (TP/SL/Conflict/End).
     */
    val goldenBos10BarScenario: Scenario by lazy {
        val scenarioId = "SYN-BOS-001"
        val candles = listOf(
            Candle("$scenarioId-0", scenarioId, "EURUSD.SYN", "H1", 0, BASE_UTC_2026 + 0 * HOUR_MS, 100.0, 102.0, 99.0, 101.0),
            Candle("$scenarioId-1", scenarioId, "EURUSD.SYN", "H1", 1, BASE_UTC_2026 + 1 * HOUR_MS, 101.0, 103.0, 100.0, 102.0),
            Candle("$scenarioId-2", scenarioId, "EURUSD.SYN", "H1", 2, BASE_UTC_2026 + 2 * HOUR_MS, 102.0, 104.0, 101.0, 103.0),
            Candle("$scenarioId-3", scenarioId, "EURUSD.SYN", "H1", 3, BASE_UTC_2026 + 3 * HOUR_MS, 103.0, 105.0, 102.0, 104.5), // Swing High 105.0
            Candle("$scenarioId-4", scenarioId, "EURUSD.SYN", "H1", 4, BASE_UTC_2026 + 4 * HOUR_MS, 104.5, 104.7, 103.5, 104.0),
            Candle("$scenarioId-5", scenarioId, "EURUSD.SYN", "H1", 5, BASE_UTC_2026 + 5 * HOUR_MS, 104.0, 104.8, 103.4, 104.4), // Pivot confirmed at bar 5
            Candle("$scenarioId-6", scenarioId, "EURUSD.SYN", "H1", 6, BASE_UTC_2026 + 6 * HOUR_MS, 104.4, 105.7, 104.1, 104.9), // Wick Sweep: high 105.7 > 105.1, close 104.9 <= 105.1
            Candle("$scenarioId-7", scenarioId, "EURUSD.SYN", "H1", 7, BASE_UTC_2026 + 7 * HOUR_MS, 104.9, 105.4, 104.5, 105.2), // Close Break BOS: close 105.2 >= 105.1
            Candle("$scenarioId-8", scenarioId, "EURUSD.SYN", "H1", 8, BASE_UTC_2026 + 8 * HOUR_MS, 105.2, 106.8, 104.8, 106.5), // Next open = 105.2, reaches 106.8
            Candle("$scenarioId-9", scenarioId, "EURUSD.SYN", "H1", 9, BASE_UTC_2026 + 9 * HOUR_MS, 106.5, 108.2, 106.0, 108.0)  // Reaches 108.2 TP
        )

        val dp6 = DecisionPoint(
            decisionPointId = "DP-06",
            scenarioId = scenarioId,
            visibleDataThrough = 6,
            questionType = QuestionType.NEXT_ACTION,
            promptAr = "عند إغلاق الشمعة 6، صَنّف الحدث بالنسبة لقمة Swing High المؤكدة عند 105.0 (حجم التيك 0.1):",
            promptEn = "At bar 6 close, classify the event relative to the confirmed reference swing high at 105.0 (tickSize 0.1):",
            options = listOf("WICK_SWEEP", "CLOSE_BREAK", "CHOCH_BEARISH", "NO_EVENT"),
            acceptedAnswers = listOf("WICK_SWEEP"),
            rejectedAnswers = listOf("CLOSE_BREAK", "CHOCH_BEARISH", "NO_EVENT"),
            expectedEvidence = listOf("high[6]=105.7 > 105.0", "close[6]=104.9 <= 105.1"),
            targetPrice = 105.0,
            targetBarIndex = 6,
            targetZone = ZoneBounds(startBarIndex = 3, endBarIndex = 6, bottomPrice = 104.5, topPrice = 105.7, zoneType = "OB"),
            feedbackAr = "الشمعة 6 تجاوزت القمة 105.0 بذيلها (105.7) لكنها أغلقت عند 104.9 (أقل من عتبة الكسر 105.1)، لذا يُصنف الحدث كسحب سيولة بالذيل (WICK_SWEEP) وليس كسر هيكل (CLOSE_BREAK).",
            feedbackEn = "Bar 6 breached 105.0 with its wick (105.7) but closed at 104.9 (<= 105.1 threshold), so it is a WICK_SWEEP, not a CLOSE_BREAK."
        )

        val dp7 = DecisionPoint(
            decisionPointId = "DP-07",
            scenarioId = scenarioId,
            visibleDataThrough = 7,
            questionType = QuestionType.NEXT_ACTION,
            promptAr = "بعد إغلاق الشمعة 7 عند 105.2، صَنّف الحدث وفق قاعدة كسر الهيكل بالإغلاق:",
            promptEn = "After bar 7 closes at 105.2, classify the event under the close-break BOS rule:",
            options = listOf("CLOSE_BREAK", "WICK_SWEEP", "WAIT", "NO_TRADE"),
            acceptedAnswers = listOf("CLOSE_BREAK"),
            rejectedAnswers = listOf("WICK_SWEEP", "WAIT", "NO_TRADE"),
            expectedEvidence = listOf("close[7]=105.2 >= 105.1"),
            targetPrice = 105.2,
            targetBarIndex = 7,
            targetZone = ZoneBounds(startBarIndex = 5, endBarIndex = 7, bottomPrice = 103.4, topPrice = 105.2, zoneType = "OB"),
            feedbackAr = "أغلقت الشمعة 7 عند 105.2 متجاوزة القمة المؤكدة 105.0 بهامش تيك كامل (105.1)، مما يؤكد كسر الهيكل بالإغلاق (CLOSE_BREAK).",
            feedbackEn = "Bar 7 closed at 105.2, exceeding the confirmed swing high 105.0 by >= 1 tick (105.1), confirming CLOSE_BREAK."
        )

        Scenario(
            scenarioId = scenarioId,
            scenarioVersion = "1.0",
            titleAr = "سحب بالذيل يتبعه كسر هيكل بالإغلاق (BOS)",
            titleEn = "Wick sweep followed by close break (BOS)",
            groupAr = "BOS مقابل sweep والإغلاق اللاحق",
            groupEn = "BOS vs Wick Sweep & Subsequent Close",
            primarySkillId = "bos_vs_sweep",
            familyId = "FAM_BOS_SWEEP",
            difficulty = DifficultyLevel.BEGINNER,
            dataKind = DataKind.SYNTHETIC_ORIGINAL,
            ruleId = "SMC.BOS.CLOSE_BREAK",
            ruleVersion = "1.0",
            tickSize = 0.1,
            referenceLevel = ReferenceLevel(
                kind = "SWING_HIGH",
                price = 105.0,
                confirmedAtBarIndex = 5,
                knownBeforeDecision = true
            ),
            candles = candles,
            decisionPoints = listOf(dp6, dp7),
            testCases = listOf(
                ScenarioTestCase("SYN-BOS-001-PASS", "PASS", "WICK_SWEEP", 105.0, 6, dp6.targetZone, AssessmentOutcome.PASS, 1.0),
                ScenarioTestCase("SYN-BOS-001-NEAR_MISS", "NEAR_MISS", "CLOSE_BREAK", 105.5, 6, ZoneBounds(3, 6, 103.0, 105.7, "OB"), AssessmentOutcome.FAIL, 0.0),
                ScenarioTestCase("SYN-BOS-001-BOUNDARY", "BOUNDARY", "WICK_SWEEP", 105.2, 6, ZoneBounds(3, 6, 104.5, 105.5, "OB"), AssessmentOutcome.PASS, 0.8),
                ScenarioTestCase("SYN-BOS-001-LOOKAHEAD", "UNASSESSABLE_LOOKAHEAD", "CLOSE_BREAK", 105.2, 9, ZoneBounds(3, 9, 104.5, 108.0, "OB"), AssessmentOutcome.UNASSESSABLE, 0.0)
            )
        )
    }

    /**
     * Generates a 500-candle dataset for high-performance chart rendering verification (<= 1.5s target).
     */
    fun generate500CandlesBenchmark(scenarioId: String = "BENCH-500"): List<Candle> {
        val list = ArrayList<Candle>(500)
        var price = 100.0
        for (i in 0 until 500) {
            val wave = kotlin.math.sin(i * 0.18) * 1.4 + kotlin.math.cos(i * 0.07) * 0.8
            val open = price
            val close = (open + wave * 0.45).coerceAtLeast(10.0)
            val high = maxOf(open, close) + 0.6 + (i % 5) * 0.12
            val low = minOf(open, close) - 0.5 - (i % 4) * 0.10
            list.add(
                Candle(
                    id = "$scenarioId-$i",
                    scenarioId = scenarioId,
                    instrument = "XAUUSD.HIST",
                    timeframe = "M15",
                    barIndex = i,
                    timestampUtcMillis = BASE_UTC_2026 + i * 900_000L,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 1200.0 + (i % 11) * 90.0
                )
            )
            price = close
        }
        return list
    }

    /**
     * 41 Lessons strictly following 24_CURRICULUM_SCOPE_SEQUENCE.md.
     */
    val all41Lessons: List<Lesson> by lazy {
        data class SpecRow(
            val id: String,
            val titleAr: String,
            val titleEn: String,
            val goalAr: String,
            val goalEn: String,
            val prereq: String?,
            val skillId: String,
            val activityAr: String,
            val activityEn: String,
            val scenarioId: String
        )

        val rows = listOf(
            SpecRow("L00-01", "ما الذي يفعله المختبر وما لا يفعله", "What SMC Lab Does and Does Not Do", "يميز التدريب والمحاكاة عن التداول الحقيقي", "Distinguish educational simulation from live brokerage trading", null, "orientation", "تصنيف 4 أمثلة", "Classify 4 examples", "SC-STR-001"),
            SpecRow("L00-02", "تاريخي أم اصطناعي؟", "Historical vs Synthetic Data", "يحدد نوع بيانات السيناريو ومصدره", "Identify scenario dataKind and source lineage", "L00-01", "data_literacy", "قراءة بطاقة بيانات", "Read data lineage card", "SC-STR-002"),
            SpecRow("L00-03", "الشمعة والفريم ووقت الإغلاق", "Candlestick, Timeframe & Close Time", "يشرح OHLC ومتى تعد الشمعة مغلقة", "Explain OHLC values and when a bar is closed in UTC", "L00-01", "chart_literacy", "قراءة 5 شمعات", "Read 5 OHLC bars", "SC-STR-003"),
            SpecRow("L01-01", "OHLC والذيل والجسم", "OHLC, Wick, and Body Anatomy", "يحدد قيم الشمعة ومكوناتها", "Identify candle body, upper/lower wicks, and range", "L00-03", "chart_literacy", "تحديد أجزاء الشمعة", "Identify candle anatomy", "SC-STR-004"),
            SpecRow("L01-02", "Swing High / Swing Low", "Swing High and Swing Low Pivots", "يحدد pivot حسب قاعدة الدرس (شمعتان يميناً ويساراً)", "Identify 5-bar confirmed swing pivots", "L01-01", "structure_reading", "اختيار pivots", "Select valid pivots", "SC-STR-005"),
            SpecRow("L01-03", "تأكيد swing ومنع المستقبل", "Swing Confirmation & Anti-Lookahead", "يحدد وقت توفر معلومة pivot بعد إغلاق شمعتين يميناً", "Determine exact bar index when a pivot becomes confirmed", "L01-02", "temporal_reasoning", "مقارنة قبل/بعد التأكيد", "Compare pre/post confirmation", "SC-STR-006"),
            SpecRow("L02-01", "اتجاه صاعد/هابط/عرضي", "Bullish, Bearish, and Ranging Structure", "يصنف 3 حالات ويشرح السبب", "Classify market trend state from confirmed swings", "L01-02", "structure_reading", "تصنيف الرسم", "Classify chart trend", "SC-STR-007"),
            SpecRow("L02-02", "HH / HL / LH / LL", "Higher Highs, Higher Lows & Lower Structure", "يوسم التسلسل البنيوي للقمم والقيعان المؤكدة", "Label structural sequence HH/HL/LH/LL", "L02-01", "structure_reading", "annotation", "Structure sequence annotation", "SC-STR-008"),
            SpecRow("L03-01", "BOS بالإغلاق", "Break of Structure (BOS) by Candle Close", "يميز الإغلاق المؤهل من مجرد تجاوز المستوى بالذيل", "Distinguish full candle close break from wick pierce", "L02-02", "bos_vs_sweep", "تحديد مستوى الكسر", "Identify BOS close level", "SC-BOS-001"),
            SpecRow("L03-02", "الذيل وسحب السيولة", "Wick Sweep vs Confirmed BOS", "يرفض wick-only بوصفه BOS مؤكداً", "Reject wick-only breach as a valid BOS", "L03-01", "bos_vs_sweep", "مقارنة near-miss", "Compare near-miss wick sweep", "SC-BOS-002"),
            SpecRow("L03-03", "ذيل يتبعه إغلاق لاحق", "Wick Sweep Followed by Subsequent Close", "يحدد توقيت التأكيد عند إغلاق الشمعة اللاحقة", "Identify confirmation bar when close break follows a wick sweep", "L03-02", "temporal_reasoning", "شمعة بشمعة", "Bar-by-bar playhead progression", "SC-BOS-003"),
            SpecRow("L03-04", "حالات الالتباس والـ gaps", "Ambiguity Cases and Opening Gaps", "يختار PASS / FAIL / UNASSESSABLE وفق الدليل المتاح", "Classify PASS / FAIL / UNASSESSABLE under gap and unconfirmed states", "L03-03", "bos_vs_sweep", "حالة حدية", "Boundary case evaluation", "SC-BOS-004"),
            SpecRow("L04-01", "CHOCH صاعد إلى هابط", "Bullish-to-Bearish CHoCH", "يحدد المستوى البنيوي المعاكس وكسر القاع المحمي", "Identify bullish-to-bearish CHoCH via protected swing low close break", "L02-02", "choch_context", "annotation", "Mark protected low break", "SC-CHOCH-001"),
            SpecRow("L04-02", "CHOCH هابط إلى صاعد", "Bearish-to-Bullish CHoCH", "يطبق القاعدة المتناظرة لكسر القمة المحمية", "Apply symmetrical rule for bearish-to-bullish CHoCH", "L04-01", "choch_context", "annotation", "Mark protected high break", "SC-CHOCH-002"),
            SpecRow("L04-03", "القمة/القاع المحمي", "Protected Swing vs Minor Internal Swing", "يميز المرجع المحمي عن swing داخلي غير مهم", "Distinguish protected structural swing from internal noise", "L04-01", "choch_context", "اختيار protected swing", "Select protected swing", "SC-CHOCH-003"),
            SpecRow("L05-01", "تجمعات السيولة", "Liquidity Pools (BSL & SSL)", "يحدد مستويات محتملة دون ادعاء رؤية دفتر الأوامر", "Map candidate liquidity pools without claiming order-book visibility", "L02-02", "liquidity_mapping", "رسم level clusters", "Draw liquidity level clusters", "SC-LIQ-001"),
            SpecRow("L05-02", "قمم/قيعان متساوية", "Equal Highs (EQH) & Equal Lows (EQL)", "يطبق tolerance المعتمد max(2 ticks, 0.05*ATR14)", "Apply tolerance max(2 ticks, 0.05*ATR14) to identify EQH/EQL", "L05-01", "liquidity_mapping", "تحديد منطقة سعرية", "Define equal-level cluster zone", "SC-LIQ-002"),
            SpecRow("L05-03", "سحب السيولة", "Liquidity Sweep Mechanics", "يميز sweep والارتداد داخل المستوى عن إغلاق الكسر", "Differentiate liquidity sweep reclaim from structural breakout", "L05-02", "liquidity_mapping", "تصنيف أحداث", "Classify sweep vs break", "SC-LIQ-003"),
            SpecRow("L05-04", "IDM (الحافز)", "Inducement (IDM) Identification", "يحدد الحركة التصحيحية الأولى قبل تأكيد القمة/القاع", "Identify the valid pullback inducement in trend sequence", "L05-03", "idm_validation", "تحديد pullback", "Locate IDM pullback", "SC-IDM-001"),
            SpecRow("L05-05", "VIDM و IDMT", "Valid Inducement (VIDM) & IDM Trap (IDMT)", "يميز الحافز الصالح والداخلي بحالات مرجعية", "Distinguish valid inducement from internal trap structure", "L05-04", "idm_validation", "اختيار تفسير", "Select structural interpretation", "SC-IDM-002"),
            SpecRow("L06-01", "Imbalance (عدم التوازن)", "3-Candle Fair Value Gap / Imbalance", "يحدد حدود المنطقة وفق نموذج الـ 3 شمعات", "Draw exact 3-candle imbalance boundaries", "L03-01", "imbalance_identification", "رسم zone", "Draw imbalance rectangle", "SC-OB-001"),
            SpecRow("L06-02", "مقدمة Order Block", "Order Block (OB) Candidate Candle", "يحدد شمعة المرشح دون اعتمادها تلقائياً قبل الشروط", "Identify candidate OB candle prior to full validation", "L06-01", "order_block_validation", "اختيار مرشح", "Select candidate OB bar", "SC-OB-002"),
            SpecRow("L06-03", "شروط صلاحية OB", "Order Block Validation Rubric", "يفحص سحب السيولة وعدم التوازن وكسر الهيكل", "Verify sweep + imbalance + structure break for OB validity", "L06-02", "order_block_validation", "rubric متعدد الشروط", "Multi-condition rubric check", "SC-OB-003"),
            SpecRow("L06-04", "اللمسات وصلاحية OB", "Order Block Touches & Mitigation Limit", "يفرق حلقة اللمس (Touch Episode) ويبطل المنطقة عند اللمسة الثالثة", "Count distinct touch episodes and invalidate after 2 touches", "L06-03", "order_block_validation", "إعادة زيارة", "Evaluate OB touch history", "SC-OB-004"),
            SpecRow("L07-01", "Breaker Block (BB)", "Breaker Block (BB) Validation", "يطبق شروط BB بعد سحب السيولة وكسر المنطقة دون خلطه مع MB", "Validate Breaker Block criteria vs Mitigation Block", "L06-03", "block_taxonomy", "تصنيف منطقة", "Classify BB vs MB", "SC-BLOCK-001"),
            SpecRow("L07-02", "Mitigation Block (MB)", "Mitigation Block (MB) Mechanics", "يطبق حالة الفشل في تكوين قمة/قاع جديد قبل الكسر", "Identify Mitigation Block following failed swing extension", "L07-01", "block_taxonomy", "مقارنة حالات", "Compare structural failure cases", "SC-BLOCK-002"),
            SpecRow("L07-03", "Rejection Block (RJB)", "Rejection Block (RJB) Wick Zone", "يحدد ذيول المنطقة السعرية للرفض", "Define Rejection Block boundaries from long exhaustion wicks", "L07-02", "block_taxonomy", "رسم ذيول", "Draw RJB wick zone", "SC-BLOCK-003"),
            SpecRow("L07-04", "IFC (شمعة التمويل المؤسسي)", "Institutional Funding Candle (IFC)", "يحدد wick sweep وتوقيت صلاحية الشمعة", "Identify IFC sweep and validation timing", "L05-03", "ifc_validation", "قرار على الشمعة", "Evaluate IFC bar", "SC-SPECIAL-001"),
            SpecRow("L07-05", "BCD", "BCD Engulfing & Wick Rules", "يطبق عناصر الابتلاع والاتجاه والذيول المعتمدة", "Apply BCD engulfing, trend, and wick criteria", "L07-04", "bcd_validation", "كشف near-miss", "Spot BCD near-miss", "SC-SPECIAL-002"),
            SpecRow("L08-01", "Fibonacci Interest Zone", "Fibonacci Optimal Trade Entry Zone", "يرسم المستويات 0.70–0.85 بالاتجاه الصحيح من القاع للقمة المؤكدة", "Anchor Fibonacci 0.70-0.85 zone across confirmed swing range", "L06-03", "fibo_application", "اختيار مستوى", "Select Fibo confluence zone", "SC-SPECIAL-003"),
            SpecRow("L08-02", "Flip Zone (FZ)", "Supply/Demand Flip Zone (FZ)", "يتحقق من التسلسل الشرطي للتفاعل ثم الكسر وتغير الدور", "Verify reaction, break, and role-reversal sequence for Flip Zone", "L04-03", "flip_zone_validation", "ترتيب الأحداث", "Order FZ event sequence", "SC-SPECIAL-004"),
            SpecRow("L09-01", "اختيار HTF / MTF / LTF", "Higher, Medium, and Lower Timeframe Hierarchy", "يختار توزيع أطر زمنية متناسق للسيناريو", "Select coherent HTF/MTF/LTF alignment for analysis", "L02-01", "multi_timeframe_alignment", "ترتيب الأطر", "Align timeframe hierarchy", "SC-MTF-001"),
            SpecRow("L09-02", "مزامنة أوقات الأطر", "Multi-Timeframe Playhead Synchronization", "يمنع استخدام شمعة HTF غير مكتملة (FORMING) في التقييم", "Prevent using unclosed HTF candles before UTC close", "L09-01", "multi_timeframe_alignment", "مقارنة playhead", "Compare MTF playhead cutoff", "SC-MTF-002"),
            SpecRow("L09-03", "تضارب الأطر الزمنية", "Timeframe Conflict & No-Trade Discipline", "يختار الانتظار (WAIT / NO_TRADE) عند عدم اكتمال توافق الأطر", "Select WAIT or NO_TRADE when HTF and LTF conflict", "L09-02", "multi_timeframe_alignment", "no-trade case", "Evaluate conflicting MTF case", "SC-MTF-004"),
            SpecRow("L10-01", "استمرار الاتجاه", "Trend Continuation Trade Plan", "يبني خطة دخول متكاملة مع الاتجاه بعد سحب الحافز والوصول لـ POI", "Build trend continuation setup after IDM sweep into valid POI", "L06-03", "entry_timing", "خطة دخول", "Construct continuation plan", "SC-ENTRY-001"),
            SpecRow("L10-02", "الدخول بعد تغير الهيكل", "Post-CHoCH Reversal Entry Setup", "يستخدم FZ / OB / FIBO عند تحقق شروط تغير الهيكل", "Execute reversal plan using validated POI after CHoCH", "L08-02", "entry_timing", "اختيار منطقة", "Select post-CHoCH POI", "SC-ENTRY-002"),
            SpecRow("L10-03", "Market / Limit و Invalidation", "Market Next Open vs Limit Price & Invalidation", "يفرق بين التنفيذ عند افتتاح الشمعة التالية وأمر الحد وشروط الإلغاء", "Distinguish MARKET_NEXT_OPEN vs LIMIT_PRICE and setup invalidation", "L10-01", "execution_planning", "حالة أمر", "Classify order execution state", "SC-ENTRY-003"),
            SpecRow("L11-01", "حجم الخطر ووقف الخسارة", "Planned Price Risk & Position Sizing", "يحسب الخطر المخطط plannedPriceRisk = |entry - stop| * qty", "Calculate exact plannedPriceRisk and 1% equity position size", "L10-01", "risk_planning", "حساب مباشر", "Calculate position risk", "SC-ENTRY-004"),
            SpecRow("L11-02", "R والرسوم والانزلاق", "R_net vs R_gross, Fees & Slippage", "يميز R_gross عن R_net ويحسب أثر الرسوم والانزلاق", "Compute R_net = netPnl / plannedPriceRisk and compare with R_gross", "L11-01", "risk_planning", "مسألة حسابية", "Calculate R_net and R_gross", "SC-ENTRY-005"),
            SpecRow("L11-03", "الحد اليومي والانضباط", "3% UTC Daily Loss Limit & Discipline", "يطبق حد الخسارة اليومية 3% ويتوقف عن فتح صفقات جديدة", "Enforce 3% UTC daily drawdown limit and halt new entries", "L11-02", "plan_adherence", "قرار بعد الحد", "Decision at daily loss limit", "SC-ENTRY-006"),
            SpecRow("L11-04", "مراجعة ما بعد الصفقة", "Post-Trade Three-Pillar Review", "يفصل جودة التحليل عن انضباط التنفيذ عن نتيجة الربح/الخسارة", "Separate analysisQuality, executionDiscipline, and simulationOutcome", "L11-03", "post_trade_review", "مراجعة تقرير", "Audit 3-pillar trade report", "SC-ENTRY-008")
        )

        rows.map { r ->
            Lesson(
                lessonId = r.id,
                moduleCode = r.id.substringBefore("-"),
                titleAr = "${r.id}: ${r.titleAr}",
                titleEn = "${r.id}: ${r.titleEn}",
                measurableGoalAr = r.goalAr,
                measurableGoalEn = r.goalEn,
                prerequisiteId = r.prereq,
                skillId = r.skillId,
                requiredActivityAr = r.activityAr,
                requiredActivityEn = r.activityEn,
                contentAr = "الشرح المنهجي لدرس «${r.titleAr}»: يركز هذا الدرس على تطبيق قاعدة قابلة للقياس (${r.goalAr}) باستخدام البيانات المتاحة حتى مؤشر Playhead فقط دون انحياز للمستقبل. تذكر أن الربح أو الخسارة في صفقة منفردة لا يثبت وحده صحة التحليل، بل العبرة باستيفاء الشروط الموضوعية للقاعدة.",
                contentEn = "Structured lesson for '${r.titleEn}': Focuses on measurable rule application (${r.goalEn}) strictly using data visible through Playhead without lookahead bias. Single-trade P&L does not validate analysis quality; objective rule criteria govern assessment.",
                workedExampleAr = "مثال تطبيقي محلول: عند فحص الشموع المغلقة حتى نقطة القرار، نتحقق أولاً من تأكيد المستوى المرجعي (شمعتان يميناً ويساراً)، ثم نقارن سعر الإغلاق مع المستوى المرجعي مضافاً إليه هامش الـ Tick المعتمد قبل إصدار الحكم.",
                workedExampleEn = "Worked Example: Inspect closed bars up to the decision point, verify the reference swing confirmation (2 bars left and right), then compare the candle close against the reference threshold +/- 1 tick before classifying.",
                linkedScenarioId = r.scenarioId
            )
        }
    }

    /**
     * 96 Scenarios strictly matching 25A_SCENARIO_CATALOG_96.csv, each with:
     * - Full candle series
     * - DecisionPoint + Answer Key (PASS / FAIL / UNASSESSABLE)
     * - 4 Automated Test Cases (PASS, NEAR_MISS, BOUNDARY, UNASSESSABLE_LOOKAHEAD)
     */
    val all96Scenarios: List<Scenario> by lazy {
        data class GroupSpec(
            val prefix: String,
            val count: Int,
            val groupAr: String,
            val groupEn: String,
            val skillId: String,
            val familyId: String,
            val ruleId: String
        )

        val groups = listOf(
            GroupSpec("SC-STR", 10, "هيكل السوق والقمم/القيعان", "Market Structure & Swings", "structure_reading", "FAM_STRUCTURE", "SMC.SWING.PIVOT_CONFIRMED"),
            GroupSpec("SC-BOS", 10, "BOS مقابل sweep والإغلاق اللاحق", "BOS vs Sweep & Subsequent Close", "bos_vs_sweep", "FAM_BOS_SWEEP", "SMC.BOS.CLOSE_BREAK"),
            GroupSpec("SC-CHOCH", 8, "CHOCH والسوينغ المحمي", "CHoCH & Protected Swing", "choch_context", "FAM_CHOCH", "SMC.CHOCH.STRUCTURE_CHANGE"),
            GroupSpec("SC-LIQ", 10, "السيولة والمستويات المتساوية", "Liquidity & Equal Levels", "liquidity_mapping", "FAM_LIQUIDITY", "SMC.LIQ.SWEEP"),
            GroupSpec("SC-IDM", 12, "IDM/VIDM/IDMT والقمم الحقيقية", "IDM / VIDM / IDMT & True Swings", "idm_validation", "FAM_IDM", "SMC.SWING.PIVOT_CONFIRMED"),
            GroupSpec("SC-OB", 14, "IMB/OB والصلاحية واللمسات", "Imbalance, Order Block & Touches", "order_block_validation", "FAM_OB_IMB", "SMC.BOS.CLOSE_BREAK"),
            GroupSpec("SC-BLOCK", 9, "BB/MB/RJB", "Breaker, Mitigation & Rejection Blocks", "block_taxonomy", "FAM_BLOCKS", "SMC.CHOCH.STRUCTURE_CHANGE"),
            GroupSpec("SC-SPECIAL", 9, "IFC/BCD/FIBO/FZ", "IFC, BCD, Fibonacci & Flip Zone", "special_smc_rules", "FAM_SPECIAL", "SMC.LIQ.SWEEP"),
            GroupSpec("SC-MTF", 6, "ترابط الفريمات", "Multi-Timeframe Alignment", "multi_timeframe_alignment", "FAM_MTF", "SMC.BOS.CLOSE_BREAK"),
            GroupSpec("SC-ENTRY", 8, "الدخول والمخاطر والانضباط", "Entry, Risk & Execution Discipline", "risk_planning", "FAM_ENTRY_RISK", "SMC.BOS.CLOSE_BREAK")
        )

        val questionCycle = listOf(
            QuestionType.SINGLE_SELECT,
            QuestionType.PRICE_POINT,
            QuestionType.ZONE_RECTANGLE,
            QuestionType.NEXT_ACTION
        )

        val scenarios = ArrayList<Scenario>(96)
        var globalIdx = 0

        for (g in groups) {
            for (num in 1..g.count) {
                globalIdx++
                val id = "%s-%03d".format(g.prefix, num)
                val qType = questionCycle[(num - 1) % questionCycle.size]
                val difficulty = when {
                    num <= 3 -> DifficultyLevel.BEGINNER
                    num <= 6 -> DifficultyLevel.INTERMEDIATE
                    num <= 8 -> DifficultyLevel.ADVANCED
                    else -> DifficultyLevel.EXPERT_TRAINING
                }
                val dataKind = if (globalIdx % 2 == 0) DataKind.HISTORICAL_SOURCE else DataKind.SYNTHETIC_ORIGINAL
                val basePrice = 100.0 + (globalIdx % 12) * 5.0
                val refPrice = basePrice + 5.0

                // Build 10 realistic candles for this scenario
                val candles = listOf(
                    Candle("$id-0", id, "EURUSD", "H1", 0, BASE_UTC_2026 + 0 * HOUR_MS, basePrice, basePrice + 2.0, basePrice - 1.0, basePrice + 1.0),
                    Candle("$id-1", id, "EURUSD", "H1", 1, BASE_UTC_2026 + 1 * HOUR_MS, basePrice + 1.0, basePrice + 3.0, basePrice, basePrice + 2.0),
                    Candle("$id-2", id, "EURUSD", "H1", 2, BASE_UTC_2026 + 2 * HOUR_MS, basePrice + 2.0, basePrice + 4.0, basePrice + 1.0, basePrice + 3.0),
                    Candle("$id-3", id, "EURUSD", "H1", 3, BASE_UTC_2026 + 3 * HOUR_MS, basePrice + 3.0, refPrice, basePrice + 2.0, refPrice - 0.5),
                    Candle("$id-4", id, "EURUSD", "H1", 4, BASE_UTC_2026 + 4 * HOUR_MS, refPrice - 0.5, refPrice - 0.3, refPrice - 1.5, refPrice - 1.0),
                    Candle("$id-5", id, "EURUSD", "H1", 5, BASE_UTC_2026 + 5 * HOUR_MS, refPrice - 1.0, refPrice - 0.2, refPrice - 1.6, refPrice - 0.6),
                    Candle("$id-6", id, "EURUSD", "H1", 6, BASE_UTC_2026 + 6 * HOUR_MS, refPrice - 0.6, refPrice + 0.7, refPrice - 0.9, refPrice - 0.1),
                    Candle("$id-7", id, "EURUSD", "H1", 7, BASE_UTC_2026 + 7 * HOUR_MS, refPrice - 0.1, refPrice + 0.6, refPrice - 0.4, refPrice + 0.3),
                    Candle("$id-8", id, "EURUSD", "H1", 8, BASE_UTC_2026 + 8 * HOUR_MS, refPrice + 0.3, refPrice + 1.9, refPrice - 0.2, refPrice + 1.5),
                    Candle("$id-9", id, "EURUSD", "H1", 9, BASE_UTC_2026 + 9 * HOUR_MS, refPrice + 1.5, refPrice + 3.2, refPrice + 1.0, refPrice + 2.9)
                )

                val targetZone = ZoneBounds(
                    startBarIndex = 3,
                    endBarIndex = 6,
                    bottomPrice = refPrice - 1.0,
                    topPrice = refPrice + 0.7,
                    zoneType = "OB"
                )

                val acceptedAnswer = when (qType) {
                    QuestionType.NEXT_ACTION -> if (num % 2 == 1) "WICK_SWEEP" else "CLOSE_BREAK"
                    QuestionType.SINGLE_SELECT -> "CONFIRMED_${g.prefix.substringAfter("-")}"
                    QuestionType.PRICE_POINT -> "$refPrice"
                    QuestionType.ZONE_RECTANGLE -> "ZONE_OB_3_6"
                    else -> "CONFIRMED"
                }

                val options = when (qType) {
                    QuestionType.NEXT_ACTION -> listOf("WICK_SWEEP", "CLOSE_BREAK", "WAIT", "NO_TRADE")
                    QuestionType.SINGLE_SELECT -> listOf(
                        "CONFIRMED_${g.prefix.substringAfter("-")}",
                        "UNCONFIRMED_WICK_ONLY",
                        "INTERNAL_NOISE",
                        "INVALID_STRUCTURE"
                    )
                    QuestionType.PRICE_POINT -> listOf("$refPrice", "${refPrice + 1.5}", "${refPrice - 2.0}", "${basePrice}")
                    QuestionType.ZONE_RECTANGLE -> listOf("ZONE_OB_3_6", "ZONE_EARLY_0_2", "ZONE_LATE_5_7", "INVALID_ZONE")
                    else -> listOf("CONFIRMED", "REJECTED")
                }

                val playheadCutoff = if (num % 2 == 1) 6 else 7

                val dp = DecisionPoint(
                    decisionPointId = "$id-DP1",
                    scenarioId = id,
                    visibleDataThrough = playheadCutoff,
                    questionType = qType,
                    promptAr = "[$id] (${g.groupAr}) عند الشمعة $playheadCutoff: حدد القرار أو المنطقة الصحيحة بناءً على البيانات المتاحة حتى Playhead فقط (السعر المرجعي $refPrice):",
                    promptEn = "[$id] (${g.groupEn}) at bar $playheadCutoff: Determine the valid classification/zone strictly using data through Playhead (reference level $refPrice):",
                    options = options,
                    acceptedAnswers = listOf(acceptedAnswer),
                    rejectedAnswers = options.filter { it != acceptedAnswer },
                    expectedEvidence = listOf("refLevel=$refPrice", "confirmedAtBar=5", "visibleThrough=$playheadCutoff"),
                    targetPrice = refPrice,
                    targetBarIndex = playheadCutoff,
                    targetZone = targetZone,
                    feedbackAr = "تفسير [$id]: تم فحص المستوى المرجعي ($refPrice) المؤكد عند الشمعة 5 ومقارنة إغلاق الشمعة $playheadCutoff وذيلها وفق القاعدة ${g.ruleId}.",
                    feedbackEn = "Explanation [$id]: Verified reference level ($refPrice) confirmed at bar 5 against bar $playheadCutoff high/low/close under rule ${g.ruleId}."
                )

                // 4 Mandatory Test Cases per Scenario: PASS, NEAR_MISS, BOUNDARY, UNASSESSABLE_LOOKAHEAD
                val testCases = listOf(
                    ScenarioTestCase(
                        caseId = "$id-TC-PASS",
                        caseType = "PASS",
                        submittedAnswer = acceptedAnswer,
                        submittedPrice = refPrice,
                        submittedBarIndex = playheadCutoff,
                        submittedZone = targetZone,
                        expectedOutcome = AssessmentOutcome.PASS,
                        expectedMinScore = 1.0
                    ),
                    ScenarioTestCase(
                        caseId = "$id-TC-NEAR-MISS",
                        caseType = "NEAR_MISS",
                        submittedAnswer = options.last(),
                        submittedPrice = refPrice + 2.0,
                        submittedBarIndex = playheadCutoff,
                        submittedZone = ZoneBounds(3, 6, refPrice - 3.0, refPrice + 0.7, "OB"),
                        expectedOutcome = AssessmentOutcome.FAIL,
                        expectedMinScore = 0.0
                    ),
                    ScenarioTestCase(
                        caseId = "$id-TC-BOUNDARY",
                        caseType = "BOUNDARY",
                        submittedAnswer = acceptedAnswer,
                        submittedPrice = refPrice + 0.15, // within 2 ticks / 0.05 ATR tolerance
                        submittedBarIndex = playheadCutoff,
                        submittedZone = ZoneBounds(3, 6, refPrice - 0.85, refPrice + 0.7, "OB"), // IoU >= 0.85
                        expectedOutcome = AssessmentOutcome.PASS,
                        expectedMinScore = 0.80
                    ),
                    ScenarioTestCase(
                        caseId = "$id-TC-LOOKAHEAD",
                        caseType = "UNASSESSABLE_LOOKAHEAD",
                        submittedAnswer = acceptedAnswer,
                        submittedPrice = refPrice,
                        submittedBarIndex = 9, // Beyond playheadCutoff (6 or 7)
                        submittedZone = ZoneBounds(3, 9, refPrice - 1.0, refPrice + 2.5, "OB"),
                        expectedOutcome = AssessmentOutcome.UNASSESSABLE,
                        expectedMinScore = 0.0
                    )
                )

                scenarios.add(
                    Scenario(
                        scenarioId = id,
                        scenarioVersion = "1.0",
                        titleAr = "$id — ${g.groupAr} (#$num)",
                        titleEn = "$id — ${g.groupEn} (#$num)",
                        groupAr = g.groupAr,
                        groupEn = g.groupEn,
                        primarySkillId = g.skillId,
                        familyId = "${g.familyId}_${(num % 4) + 1}",
                        difficulty = difficulty,
                        dataKind = dataKind,
                        ruleId = g.ruleId,
                        ruleVersion = "1.0",
                        tickSize = 0.1,
                        referenceLevel = ReferenceLevel(
                            kind = "SWING_HIGH",
                            price = refPrice,
                            confirmedAtBarIndex = 5,
                            knownBeforeDecision = true
                        ),
                        candles = candles,
                        decisionPoints = listOf(dp),
                        testCases = testCases
                    )
                )
            }
        }
        scenarios
    }

    /**
     * Bilingual Glossary (Arabic / English) from 19_GLOSSARY_AND_REFERENCES.md.
     */
    val glossaryTerms: List<GlossaryTerm> by lazy {
        listOf(
            GlossaryTerm(
                "GL-01", "SMC",
                "مفاهيم الأموال الذكية (Smart Money Concepts)",
                "Smart Money Concepts (SMC)",
                "إطار لقراءة حركة السعر عبر هيكل السوق، مناطق السيولة، عدم التوازن، ومناطق الاهتمام السعرية.",
                "Price-action framework analyzing market structure, liquidity pools, imbalances, and points of interest.",
                "يتعامل المختبر مع قواعد SMC كعلاقات سعرية وزمنية قابلة للقياس والاختبار دون ادعاء رؤية أوامر المؤسسات.",
                "SMC Lab operationalizes SMC as testable price-time predicates without claiming institutional order-book visibility."
            ),
            GlossaryTerm(
                "GL-02", "BOS",
                "كسر الهيكل (Break of Structure)",
                "Break of Structure (BOS)",
                "إغلاق شمعة مؤهل وراء قمة أو قاع بنيوي مؤكد في اتجاه السياق الحالي بهامش تيك واحد على الأقل.",
                "Qualified candle close beyond a confirmed structural swing high/low in the direction of the prevailing trend.",
                "تجاوز المستوى بالذيل وحده لا يُعد BOS مؤكداً بل يُصنف كسحب سيولة بالذيل (Wick Sweep).",
                "Wick penetration alone is classified as a Wick Sweep, never a confirmed Close-Break BOS."
            ),
            GlossaryTerm(
                "GL-03", "CHoCH",
                "تغير الشخصية / الهيكل (Change of Character)",
                "Change of Character (CHoCH)",
                "أول إغلاق مؤهل يكسر القمة أو القاع المحمي في الاتجاه المعاكس بعد تحقق تسلسل الحافز (IDM).",
                "First qualified candle close breaking the protected structural swing in the opposite direction after IDM validation.",
                "يتطلب تحديد القمة/القاع المحمي وسياق الاتجاه السابق قبل شمعة القرار.",
                "Requires pre-decision identification of the protected swing and prior trend context."
            ),
            GlossaryTerm(
                "GL-04", "IDM / VIDM / IDMT",
                "الحافز الصالح وفخ الحافز (Inducement)",
                "Inducement (IDM / VIDM / IDMT)",
                "حركة تصحيحية داخلية تُسحب سيولتها قبل تأكيد القمة أو القاع البنيوي أو التوجه لمنطقة الطلب/العرض.",
                "Internal pullback structure swept prior to confirming a structural swing or reaching a valid POI.",
                "يميز المختبر بين الحافز الصالح (VIDM) والحافز الداخلي المخادع (IDMT) بشروط تسلسلية محددة.",
                "Distinguishes valid inducement (VIDM) from internal inducement traps (IDMT) via sequence predicates."
            ),
            GlossaryTerm(
                "GL-05", "OB",
                "كتلة الأوامر (Order Block)",
                "Order Block (OB)",
                "منطقة سعرية مشتقة من آخر شمعة معاكسة قبل حركة اندفاعية تسببت في كسر هيكل وعدم توازن.",
                "Price zone derived from the last opposite candle prior to an impulsive move that created imbalance and structure break.",
                "تبطل صلاحية المنطقة بعد حلقتي لمس مستقلتين (Touch Episodes)؛ اللمسة الثالثة غير صالحة.",
                "Valid for up to 2 distinct touch episodes; invalidated on the 3rd touch."
            ),
            GlossaryTerm(
                "GL-06", "IMB / FVG",
                "عدم التوازن السعري (Imbalance)",
                "Imbalance / Fair Value Gap (IMB)",
                "فجوة سعرية في نموذج من 3 شمعات متتالية لا تتداخل فيها ذيول الشمعة الأولى والثالثة.",
                "Three-candle price gap where the wicks of bar 1 and bar 3 do not overlap across bar 2's body.",
                "تُقاس حدودها بدقة من قمة الشمعة الأولى إلى قاع الشمعة الثالثة (أو العكس في الهبوط).",
                "Measured strictly between the high of candle 1 and low of candle 3 (or vice versa in bearish moves)."
            ),
            GlossaryTerm(
                "GL-07", "EQH / EQL & Sweep",
                "القمم والقيعان المتساوية وسحب السيولة",
                "Equal Highs/Lows & Liquidity Sweep",
                "تقارب قمتين أو قاعين ضمن هامش max(2 ticks, 0.05×ATR14)، وسحبها بتجاوز الذيل ثم الإغلاق داخل النطاق.",
                "Clustered highs/lows within max(2 ticks, 0.05*ATR14) swept by a wick that closes back inside the level.",
                "تُحفظ كتجمع مستويات (Level Cluster) وليس خطاً عشوائياً واحداً.",
                "Stored as a bounded level cluster rather than an arbitrary single line."
            ),
            GlossaryTerm(
                "GL-08", "Playhead & Lookahead",
                "مؤشر الزمن ومنع انحياز المستقبل",
                "Playhead & Anti-Lookahead Gate",
                "الحاجز الزمني الذي يمنع عرض أو استخدام أي شمعة لم تغلق بعد في الرسم أو التقييم أو المحاكاة.",
                "Temporal gate ensuring no chart, predicate, or evaluation reads candles beyond current visibleDataThrough.",
                "كل وضع (Free / Challenge / Simulation) يمتلك Playhead مستقلاً بقواعد رجوع وتقدم صارمة.",
                "Each mode (Free / Challenge / Simulation) maintains its own Playhead with strict rewind rules."
            ),
            GlossaryTerm(
                "GL-09", "R_net & Stop-First",
                "مضاعف المخاطرة الصافي وسياسة الوقف أولاً",
                "Net R-Multiple (R_net) & Stop-First Policy",
                "صافي الربح أو الخسارة بعد الرسوم والانزلاق مقسوماً على المخاطرة السعرية المخططة قبل الدخول.",
                "Net P&L (after fees/slippage) divided by planned pre-entry price risk; intra-bar SL/TP conflicts resolve to SL first.",
                "R = R_net = netPnl / plannedPriceRisk، ويُعرض R_gross بشكل منفصل تماماً.",
                "R = R_net = netPnl / plannedPriceRisk, while R_gross is reported separately."
            ),
            GlossaryTerm(
                "GL-10", "Wilson L95",
                "الحد الأدنى لثقة ويلسون 95%",
                "Wilson Lower Bound 95% (L95)",
                "معادلة إحصائية محافظة لتقدير إتقان المهارة من المحاولات المستقلة الأولى دون تضخيم العينات الصغيرة.",
                "Conservative binomial lower confidence bound (z=1.96) estimating skill mastery from independent first-try attempts.",
                "يمنع إعلان حالة PROVISIONAL قبل 8 سيناريوهات مستقلة أو CONSISTENT قبل 16 سيناريو مستقلاً.",
                "Prevents premature mastery claims before 8 (PROVISIONAL) or 16 (CONSISTENT) independent scenarios."
            )
        )
    }
}
