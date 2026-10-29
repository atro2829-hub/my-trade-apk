package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "candles")
data class CandleEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val instrument: String,
    val timeframe: String,
    val barIndex: Int,
    val timestampUtcMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey val scenarioId: String,
    val scenarioVersion: String,
    val titleAr: String,
    val titleEn: String,
    val groupAr: String,
    val groupEn: String,
    val primarySkillId: String,
    val familyId: String,
    val difficulty: String,
    val dataKind: String,
    val ruleId: String,
    val ruleVersion: String,
    val tickSize: Double,
    val refKind: String?,
    val refPrice: Double?,
    val refConfirmedBarIndex: Int?,
    val refKnownBeforeDecision: Boolean?,
    val packId: String
)

@Entity(tableName = "decision_points")
data class DecisionPointEntity(
    @PrimaryKey val decisionPointId: String,
    val scenarioId: String,
    val visibleDataThrough: Int,
    val questionType: String,
    val promptAr: String,
    val promptEn: String,
    val optionsPipe: String,
    val acceptedAnswersPipe: String,
    val rejectedAnswersPipe: String,
    val expectedEvidencePipe: String,
    val targetPrice: Double?,
    val targetBarIndex: Int?,
    val zoneStartBar: Int?,
    val zoneEndBar: Int?,
    val zoneBottomPrice: Double?,
    val zoneTopPrice: Double?,
    val feedbackAr: String,
    val feedbackEn: String,
    val answerKeyStatus: String,
    val rubricVersion: String
)

@Entity(tableName = "playheads")
data class PlayheadEntity(
    @PrimaryKey val workspaceId: String,
    val scenarioId: String,
    val mode: String,
    val currentBarIndex: Int,
    val maxBarIndex: Int,
    val isDecisionLocked: Boolean,
    val hasActivePosition: Boolean,
    val isScenarioFinished: Boolean
)

@Entity(tableName = "paper_trades")
data class PaperTradeEntity(
    @PrimaryKey val tradeId: String,
    val workspaceId: String,
    val scenarioId: String,
    val scenarioVersion: String,
    val direction: String,
    val entryMode: String,
    val quantity: Double,
    val plannedEntryPrice: Double,
    val executedEntryPrice: Double?,
    val stopPrice: Double,
    val targetPrice: Double,
    val exitPrice: Double?,
    val status: String,
    val createdAtBarIndex: Int,
    val openedAtBarIndex: Int?,
    val closedAtBarIndex: Int?,
    val feeAmount: Double,
    val slippageTicks: Double,
    val stopFirstConflictTriggered: Boolean,
    val engineVersion: String,
    val executionPolicyId: String
)

@Entity(tableName = "trade_events")
data class TradeEventEntity(
    @PrimaryKey val idempotencyKey: String,
    val eventId: String,
    val tradeId: String,
    val workspaceId: String,
    val eventSequence: Int,
    val barIndex: Int,
    val timestampUtcMillis: Long,
    val fromStatus: String?,
    val toStatus: String,
    val reason: String
)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val isCompleted: Boolean,
    val completedAtUtcMillis: Long,
    val lastScore: Double
)

@Entity(tableName = "attempt_records")
data class AttemptRecordEntity(
    @PrimaryKey val attemptId: String,
    val scenarioId: String,
    val scenarioVersion: String,
    val skillId: String,
    val familyId: String,
    val sessionId: String,
    val isFirstTryForScenario: Boolean,
    val rubricScore: Double,
    val hintUsedBeforeAnswer: Boolean,
    val criticalError: Boolean,
    val outcome: String, // PASS, FAIL, UNASSESSABLE
    val errorTag: String,
    val timestampUtcMillis: Long
)

@Entity(tableName = "chart_drawings")
data class ChartDrawingEntity(
    @PrimaryKey val drawingId: String,
    val scenarioId: String,
    val toolType: String,
    val startBarIndex: Int,
    val endBarIndex: Int,
    val startPrice: Double,
    val endPrice: Double,
    val noteText: String,
    val createdAtPlayheadBar: Int
)

@Entity(tableName = "pack_manifests")
data class PackManifestEntity(
    @PrimaryKey val packId: String,
    val packVersion: String,
    val keyId: String,
    val schemaVersion: Int,
    val minAppVersion: Int,
    val sizeBytes: Long,
    val sha256Hex: String,
    val signatureHex: String,
    val dataKind: String,
    val sourceId: String,
    val licenseId: String,
    val status: String, // STAGING, VERIFIED, ACTIVE, PREVIOUS_VALID, ROLLED_BACK
    val activatedAtUtcMillis: Long
)

@Dao
interface SmcLabDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandles(candles: List<CandleEntity>)

    @Query("SELECT * FROM candles WHERE scenarioId = :scenarioId AND barIndex <= :playheadBar ORDER BY barIndex ASC")
    suspend fun getVisibleCandlesThrough(scenarioId: String, playheadBar: Int): List<CandleEntity>

    @Query("SELECT * FROM candles WHERE scenarioId = :scenarioId ORDER BY barIndex ASC")
    suspend fun getAllCandlesForScenario(scenarioId: String): List<CandleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenarios(scenarios: List<ScenarioEntity>)

    @Query("SELECT * FROM scenarios ORDER BY scenarioId ASC")
    fun observeScenarios(): Flow<List<ScenarioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecisionPoints(points: List<DecisionPointEntity>)

    @Query("SELECT * FROM decision_points WHERE scenarioId = :scenarioId ORDER BY visibleDataThrough ASC")
    suspend fun getDecisionPointsForScenario(scenarioId: String): List<DecisionPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayhead(playhead: PlayheadEntity)

    @Query("SELECT * FROM playheads WHERE workspaceId = :workspaceId LIMIT 1")
    suspend fun getPlayhead(workspaceId: String): PlayheadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaperTrade(trade: PaperTradeEntity)

    @Query("SELECT * FROM paper_trades WHERE workspaceId = :workspaceId ORDER BY createdAtBarIndex DESC")
    fun observeTradesForWorkspace(workspaceId: String): Flow<List<PaperTradeEntity>>

    @Query("SELECT * FROM paper_trades WHERE workspaceId = :workspaceId AND status IN ('DRAFT', 'VALIDATED', 'OPEN') LIMIT 1")
    suspend fun getActiveTradeForWorkspace(workspaceId: String): PaperTradeEntity?

    @Query("SELECT * FROM paper_trades")
    suspend fun getAllPaperTrades(): List<PaperTradeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTradeEventIdempotent(event: TradeEventEntity): Long

    @Query("SELECT * FROM trade_events WHERE tradeId = :tradeId ORDER BY eventSequence ASC")
    suspend fun getEventsForTrade(tradeId: String): List<TradeEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLessonProgress(progress: LessonProgressEntity)

    @Query("SELECT * FROM lesson_progress")
    fun observeLessonProgress(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress")
    suspend fun getAllLessonProgress(): List<LessonProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttemptRecord(record: AttemptRecordEntity)

    @Query("SELECT * FROM attempt_records ORDER BY timestampUtcMillis ASC")
    fun observeAttemptRecords(): Flow<List<AttemptRecordEntity>>

    @Query("SELECT * FROM attempt_records ORDER BY timestampUtcMillis ASC")
    suspend fun getAllAttemptRecords(): List<AttemptRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChartDrawing(drawing: ChartDrawingEntity)

    @Query("DELETE FROM chart_drawings WHERE drawingId = :drawingId")
    suspend fun deleteChartDrawing(drawingId: String)

    @Query("SELECT * FROM chart_drawings WHERE scenarioId = :scenarioId AND createdAtPlayheadBar <= :playheadBar")
    suspend fun getDrawingsVisibleThrough(scenarioId: String, playheadBar: Int): List<ChartDrawingEntity>

    @Query("SELECT * FROM chart_drawings")
    suspend fun getAllDrawings(): List<ChartDrawingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackManifest(manifest: PackManifestEntity)

    @Query("SELECT * FROM pack_manifests ORDER BY activatedAtUtcMillis DESC")
    fun observePackManifests(): Flow<List<PackManifestEntity>>

    @Query("SELECT * FROM pack_manifests WHERE packId = :packId LIMIT 1")
    suspend fun getPackManifest(packId: String): PackManifestEntity?

    @Query("DELETE FROM lesson_progress")
    suspend fun clearLessonProgress()

    @Query("DELETE FROM attempt_records")
    suspend fun clearAttemptRecords()

    @Query("DELETE FROM paper_trades")
    suspend fun clearPaperTrades()

    @Query("DELETE FROM chart_drawings")
    suspend fun clearChartDrawings()
}

@Database(
    entities = [
        CandleEntity::class,
        ScenarioEntity::class,
        DecisionPointEntity::class,
        PlayheadEntity::class,
        PaperTradeEntity::class,
        TradeEventEntity::class,
        LessonProgressEntity::class,
        AttemptRecordEntity::class,
        ChartDrawingEntity::class,
        PackManifestEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SmcLabDatabase : RoomDatabase() {
    abstract fun dao(): SmcLabDao

    companion object {
        /**
         * Explicit SQLite schema v1 -> v2 migration verifying index creation and schema integrity.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_candles_scenarioId_barIndex ON candles(scenarioId, barIndex)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_attempt_records_skillId ON attempt_records(skillId)"
                )
            }
        }
    }
}
