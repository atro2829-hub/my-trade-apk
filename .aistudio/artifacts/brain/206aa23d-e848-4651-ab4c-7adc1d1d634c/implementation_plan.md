# SMC Lab — مختبر مفاهيم الأموال الذكية التفاعلي

بيئة تعليمية وتدريبية متكاملة لتعليم واختبار وممارسة مفاهيم الأموال الذكية (Smart Money Concepts - SMC) دون اتصال بالإنترنت (Offline-First)، تجمع بين شارت تاريخي تفاعلي عالي الأداء، محرك قواعد صارم مانع للانحياز المستقبلي (Anti-Lookahead Playhead)، محاكاة تداول ورقية دقيقة، ومدرب إحصائي متكيف حتمي.

### User Review & Critical Decisions

> [!IMPORTANT]
> تم اعتماد القرارات التقنية والهندسية التالية بناءً على إجاباتك ومواصفات المشروع المعتمدة:

- **Confirmed Decision 1 (بيئة التنفيذ المعمارية)**: بناء التطبيق باستخدام **Native Android (Kotlin + Jetpack Compose + Room SQLite + Compose Canvas)** مع تطبيق حرفي ودقيق لجميع نماذج النطاق (Domain Models)، قواعد SMC، محرك التقييم، والمدرب المتكيف الحتمي، ودعم كامل للغتين العربية (RTL كوضع أساسي) والإنجليزية (LTR).
- **Confirmed Decision 2 (جدولة التسليم والاختبارات)**: تنفيذ جميع المراحل (من المرحلة 0 حتى المرحلة 6) في حزمة بناء متكاملة، مع كتابة وتشغيل اختبارات الوحدة واختبارات Robolectric لكل مرحلة للتحقق الفعلي من كافة المتطلبات.

---

### 1. Overview & Core Concept

- **What It Does**: يقدم التطبيق رحلة تعلم عملية لمفاهيم SMC (تحديد القمم والقيعان المؤكدة، كسر الهيكل BOS، تغير الشخصية CHoCH، سحب السيولة، ومناطق العرض والطلب) عبر دروس تفاعلية (41 درساً)، وتحديات وسيناريوهات عملية (96 سيناريو)، ومحاكاة تداول افتراضية (Paper Trading) تفصل بين جودة التحليل، انضباط التنفيذ، ونتيجة المحاكاة، بإشراف مدرب ذكي يعمل محلياً بمعادلات إحصائية حتمية (Wilson Lower Bound L95).
- **Target Audience / Persona**: المتعلمون والمتداولون الراغبون في إتقان قراءة حركة السعر (Price Action & SMC) في بيئة منضبطة خالية من الانحياز المعرفي (Lookahead Bias) وتعمل بالكامل دون إنترنت.
- **Key Value**: منع رؤية المستقبل عبر بوابة `visibleDataThrough(playhead)` الإلزامية لكل مكون، وتقييم موضوعي فوري (`PASS` / `FAIL` / `UNASSESSABLE`) مع تغذية راجعة تفصيلية وتدريب موجه نحو نقاط الضعف الفعلية.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **المدرب والمنهج (Curriculum & Adaptive Coach)**: استعراض المسار التعليمي المكون من 41 درساً موزعة على وحدات متدرجة، مع بطاقة "توصية المدرب المتكيف" التي تحسب الأولوية تلقائياً بناءً على مستوى الإتقان (`PROVISIONAL` / `CONSISTENT`) ومعادلة Wilson L95.
  2. **الشارت التفاعلي وأدوات الرسم (Interactive Chart & Drawing Tools)**: شارت شموع يابانية (OHLC) مرسوم عبر `Canvas` عالي الأداء (يدعم 500+ شمعة في أقل من 1.5 ثانية)، مع تحريك وتكبير سلس (Pan/Zoom)، مؤشر تقاطع (Crosshair)، شريط تحكم بالـ Playhead، وأدوات رسم هندسية (خط أفقي، خط اتجاه، مستطيل منطقة، ملاحظة نصية) مع دعم كامل لقارئ الشاشة (TalkBack Semantics).
  3. **وضع التحدي (Challenge Slice)**: عرض الشموع حتى موضع `playhead` فقط، طرح سؤال التحدي (مثل `SINGLE_SELECT`, `NEXT_ACTION`, `PRICE_POINT`, `ZONE_RECTANGLE` بحساب تقاطع IoU)، تقييم الإجابة فوراً عبر محرك التقييم، وعرض التفسير الفني مع كشف الشموع اللاحقة عند الطلب.
  4. **وضع المحاكاة الورقية (Simulation Workspace)**: فتح مركز واحد نشط لكل مساحة عمل (`MARKET_NEXT_OPEN` أو `LIMIT_PRICE`)، تحديد وقف الخسارة (SL) وجني الأرباح (TP) مع قاعدة حسم التعارض داخل الشمعة (`Stop-First`)، تقدم الشموع خطوة بخطوة، وإصدار تقرير ختامي يفصل بين `analysisQuality`، `executionDiscipline`، و`simulationOutcome` مع حساب دقيق لـ `R_net` و`R_gross`.
  5. **إدارة الحزم والنسخ الاحتياطي (Pack Manager & Local Backup)**: التحقق من حزم البيانات عبر البصمة الرقمية (`SHA-256`) والتوقيع (`Ed25519`) مع التفعيل الذري (Staging → Verify → Atomic Activate) والتراجع التلقائي (Rollback) عند الفشل، بالإضافة إلى النسخ الاحتياطي والاستعادة المحلية بوضعي الدمج (`MERGE`) أو الاستبدال (`REPLACE`)، ومنسق أهلية الإعلانات المعزول (`AdEligibilityCoordinator`).

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Institutional Financial Dark & Precision Studio** — واجهة مالية احترافية عالية التباين تركز على وضوح الشموع والبيانات الرقمية مع دعم التبديل السلس بين الوضع الداكن والفاتح، واتجاه النص العربي (RTL) والإنجليزي (LTR).
  - *Color Palette & Mood*:
    - خلفية الشارت والأسطح الأساسية: كحلي داكن عميق (`#0B0F17` / `#111827`) يريح العين ويبرز ألوان الشموع ومناطق السيولة.
    - الشموع الصاعدة وحالات النجاح (`PASS`): أخضر زمردي حاد (`#10B981`).
    - الشموع الهابطة وحالات الفشل (`FAIL` / Stop Loss): قرمزي واضح (`#EF4444`).
    - لون التمييز الأساسي ومؤشر Playhead وحالات المراجعة (`PROVISIONAL`): ذهبي كهرماني (`#F59E0B`) وأزرق مؤسسي (`#3B82F6`).
  - *Typography & Hierarchy*: خطوط محلية مدمجة تدعم العربية والإنجليزية والأرقام الجدولية (Tabular Figures) لأسعار الصرف ونسب العائد للمخاطرة (`R_net` / `R_gross`).
  - *Component Styling & Layout*: تصميم متجاوب يدعم الهواتف والأجهزة اللوحية (Window Size Classes) مع شريط تنقل سفلي، بطاقات ذات حدود دقيقة، وأهداف لمس لا تقل عن `48.dp`.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: عزل صارم للبيانات المرئية عبر Playhead Gate**
  - *Chosen Approach*: تمرير جميع استعلامات الشموع، الرسومات، ومحرك القواعد عبر واجهة `visibleDataThrough(playhead)` بحيث لا يستطيع الشارت أو المتعلم أو المدرب الوصول لأي شمعة بعد مؤشر `playhead` الحالي، مع فصل مستقل لمؤشر `playhead` لكل وضع (`FREE`، `CHALLENGE`، `SIMULATION`).
  - *Why*: ضمان نزاهة التدريب ومنع الغش غير المقصود أو تسرب بيانات المستقبل (Lookahead Bias) أثناء التقييم.

- **Decision 2: محرك قواعد SMC ومُقيّم إجابات حتمي 100% محلياً**
  - *Chosen Approach*: بناء محرك قواعد رياضي (`SWING.PIVOT_CONFIRMED`, `BOS.CLOSE_BREAK`, `BOS.WICK_SWEEP`, `CHOCH.STRUCTURE_CHANGE`, `LIQ.SWEEP`) ومحرك تقييم (`SINGLE_SELECT`, `NEXT_ACTION`, `PRICE_POINT`, `ZONE_RECTANGLE` عبر حساب Intersection over Union - IoU) يعملان محلياً بالكامل دون أي اعتماد على الشبكة.
  - *Why*: سرعة استجابة لحظية، عمل كامل Offline، وقابلية اختبار حتمية (Deterministic Unit Tests) لكل حالة (`PASS`, `FAIL`, `UNASSESSABLE`).

- **Decision 3: محاكاة تداول بمعايير محاسبية صارمة (Stop-First & R_net)**
  - *Chosen Approach*: السماح بمركز واحد نشط لكل `SimulationWorkspace`، وتنفيذ أوامر `MARKET_NEXT_OPEN` على سعر افتتاح الشمعة التالية، وحسم تعارض ضرب الهدف ووقف الخسارة في نفس الشمعة لصالح وقف الخسارة (`Stop-First`)، وحساب `R = R_net = netPnl / plannedPriceRisk` مع عرض `R_gross` بشكل منفصل.
  - *Why*: محاكاة واقعية محافظة تمنع تضخيم النتائج الافتراضية وتدرب المتعلم على الانضباط الصارم في إدارة المخاطر.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI Layer (RTL / LTR)                      │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────────────────┐  │
│  │ Curriculum &     │  │ Interactive OHLC │  │ Simulation & Pack         │  │
│  │ Adaptive Coach   │  │ Canvas + Tools   │  │ Manager / Backup Screens  │  │
│  └────────┬─────────┘  └────────┬─────────┘  └─────────────┬─────────────┘  │
└───────────┼─────────────────────┼──────────────────────────┼────────────────┘
            │                     │                          │
            ▼                     ▼                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                 State Management (ViewModel + StateFlow)                    │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Playhead Visibility Gate: visibleDataThrough(playhead, mode)          │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└───────────┬─────────────────────┬──────────────────────────┬────────────────┘
            │                     │                          │
            ▼                     ▼                          ▼
┌──────────────────────┐ ┌────────────────────────┐ ┌─────────────────────────┐
│ SMC Rulebook &       │ │ Simulation Engine      │ │ Adaptive Coach &        │
│ Evaluation Engine    │ │ (Stop-First, R_net,    │ │ Pack/Ad Coordinators    │
│ • Predicates (5)     │ │  Single Active Pos,    │ │ • Wilson L95 & Priority │
│ • IoU Zone Evaluator │ │  Idempotent Events)    │ │ • SHA-256 + Ed25519     │
└───────────┬──────────┘ └────────────┬───────────┘ └────────────┬────────────┘
            │                         │                          │
            ▼                         ▼                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│              Room SQLite Persistence Layer (Schema v1 + Migrations)         │
│  • Candles & Scenarios (96)     • PaperTrades & TradeEvents (Idempotent)    │
│  • Lessons (41) & Glossary      • SkillMastery & PackManifests              │
└─────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `Candle`: بيانات الشمعة (`instrument`, `timeframe`, `timestampUtc`, `open`, `high`, `low`, `close`, `volume`, `candleIndex`).
  - `Scenario` & `DecisionPoint`: بيانات السيناريو الـ 96، نقطة القرار، نوع السؤال، ومفتاح الإجابة (`PASS` / `FAIL` / `UNASSESSABLE`).
  - `Playhead`: حالة مؤشر الزمن لكل وضع (`FREE`, `CHALLENGE`, `SIMULATION`) مع بوابة `visibleDataThrough(playhead)`.
  - `PaperTrade` & `TradeEvent`: أوامر المحاكاة (`MARKET_NEXT_OPEN`, `LIMIT_PRICE`)، سجل الأحداث غير القابل للتكرار (Idempotency Key)، وحسابات `R_net` و`R_gross` والتقرير الثلاثي (`analysisQuality`, `executionDiscipline`, `simulationOutcome`).
  - `SkillMastery` & `AdaptiveCoach`: إحصائيات الإتقان (`firstTryPass`, `totalAttempts`, `wilsonL95`, `status: PROVISIONAL | CONSISTENT`, `priorityScore`).
  - `PackManifest`: إدارة الحزم وتوقيعات `SHA-256` و`Ed25519` ومسار التفعيل الذري (`STAGING` → `VERIFIED` → `ACTIVE` / `ROLLED_BACK`).

- **Interactive Component & State Mapping**:
  - **المرحلة 0 (الأساس)**: إعداد مخطط قاعدة البيانات SQLite (Room v1 + Migrations)، نماذج النطاق، وبيانات الاختبار الاصطناعية (10 شموع + نقطة قرار) مع اختبارات التحقق.
  - **المرحلة 1 (الشارت)**: بناء `OhlcChartCanvas` مع دعم التحريك والتكبير، التقاطع، أدوات الرسم الأربعة، وبوابة `visibleDataThrough(playhead)` مع اختبار أداء رسم 500 شمعة في أقل من 1.5 ثانية.
  - **المرحلة 2 (Vertical Slice)**: ربط عرض الدرس، تحدي BOS، التقييم الفوري، فتح وإغلاق صفقة `MARKET_NEXT_OPEN`، التقرير الثلاثي، والحفظ مع ضمان عدم التكرار (Idempotency).
  - **المرحلة 3 (محرك القواعد والمدرب)**: برمجة القواعد الخمسة (`SWING.PIVOT_CONFIRMED`, `BOS.CLOSE_BREAK`, `BOS.WICK_SWEEP`, `CHOCH.STRUCTURE_CHANGE`, `LIQ.SWEEP`)، محرك التقييم بجميع أنماطه شاملة `ZONE_RECTANGLE (IoU)`، ومعادلات المدرب (`Wilson L95`، عتبات الإتقان، ومعادلة الأولوية).
  - **المرحلة 4 (المحتوى ثنائي اللغة)**: تضمين 41 درساً منهجياً، 96 سيناريو تدريبي مع مفاتيح الإجابة وحالات الاختبار الأربعة لكل سيناريو، القاموس (Glossary) بالعربية والإنجليزية، والرسوم التوضيحية.
  - **المرحلة 5 و 6 (الحزم، الإعلانات، التقوية، وإصدار الإنتاج)**: التحقق الرقمي للحزم (`SHA-256` + `Ed25519`) مع التراجع التلقائي، منسق الإعلانات المعزول (`AdEligibilityCoordinator`)، النسخ الاحتياطي (`MERGE` / `REPLACE`)، دعم TalkBack للشارت، تشغيل كافة الاختبارات وإصدار تقرير الجودة النهائي (QA Report).


### المرحلة 6.1 — حزم الإصدار التجاري الموقعة داخل المستودع (`releases/`) وخط أنابيب GitHub Actions Release
- **تضمين الحزم الحقيقية الموقعة في المستودع**:
  - نسخ وتثبيت الحزم الموقعة الفعالة (`releases/SMC_Lab_v1.0.0_release_signed.apk` و`releases/SMC_Lab_v1.0.0_release_signed.aab`) داخل المستودع مباشرة بحيث يتم رفعها تلقائياً مع الكود عند الضغط على **Push to GitHub**.
  - التحقق من توقيع الحزم عبر `apksigner verify` بجميع صيغ التوقيع (`v1 + v2 + v3`) لضمان التثبيت الفوري على جميع أجهزة الأندرويد والقبول في متجر Google Play.
- **إصلاح وتحديث GitHub Actions لبناء Release موقع تلقائياً**:
  - تحديث `.github/workflows/android-ci.yml` ليقوم بتثبيت Android SDK وGenerate/Use Keystore وبناء `:app:assembleRelease` و`:app:bundleRelease` الموقعة ورفع المخرجات كـ Artifacts وإصدارات GitHub Release دون الفشل بسبب غياب `gradlew` أو مفاتيح التوقيع.
