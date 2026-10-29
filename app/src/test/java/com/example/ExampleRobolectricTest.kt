package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.content.SmcLabContentCatalog
import com.example.data.local.CandleEntity
import com.example.data.local.SmcLabDatabase
import com.example.data.local.TradeEventEntity
import com.example.ui.SmcLabViewModel
import com.example.ui.screens.SmcLabMainScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: SmcLabDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SmcLabDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(SmcLabDatabase.MIGRATION_1_2)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun phase0_sqliteRoomDaoPlayheadQueryAndIdempotentTradeEvents() = runBlocking {
        val dao = db.dao()
        val scenario = SmcLabContentCatalog.goldenBos10BarScenario
        val entities = scenario.candles.map { c ->
            CandleEntity(
                id = c.id,
                scenarioId = c.scenarioId,
                instrument = c.instrument,
                timeframe = c.timeframe,
                barIndex = c.barIndex,
                timestampUtcMillis = c.timestampUtcMillis,
                open = c.open,
                high = c.high,
                low = c.low,
                close = c.close,
                volume = c.volume
            )
        }
        dao.insertCandles(entities)

        // Query strictly through Playhead bar 6
        val visibleThrough6 = dao.getVisibleCandlesThrough(scenario.scenarioId, 6)
        assertEquals(7, visibleThrough6.size)
        assertEquals(6, visibleThrough6.last().barIndex)

        // Idempotent TradeEvent insertion (simulating double-tap / process recovery)
        val ev1 = TradeEventEntity(
            idempotencyKey = "IDEMP-001",
            eventId = "EV-1",
            tradeId = "TRD-1",
            workspaceId = "WS-1",
            eventSequence = 1,
            barIndex = 7,
            timestampUtcMillis = 1000L,
            fromStatus = "DRAFT",
            toStatus = "VALIDATED",
            reason = "SUBMIT"
        )
        val firstRowId = dao.insertTradeEventIdempotent(ev1)
        val duplicateRowId = dao.insertTradeEventIdempotent(ev1.copy(eventId = "EV-2"))
        assertTrue(firstRowId > 0L)
        assertEquals(-1L, duplicateRowId) // Ignored due to idempotency key primary key constraint

        val events = dao.getEventsForTrade("TRD-1")
        assertEquals(1, events.size)
    }

    @Test
    fun phase2And5_endToEndComposeRtlLtrChartTalkBackAndDoubleTap() {
        val vm = SmcLabViewModel(db.dao())
        composeTestRule.setContent {
            MyApplicationTheme {
                SmcLabMainScreen(viewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Default Arabic RTL Curriculum & Adaptive Coach card displayed
        composeTestRule.onNodeWithTag("adaptive_coach_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_lesson_card").assertIsDisplayed()

        // 2. Toggle Language RTL -> LTR and back
        composeTestRule.onNodeWithTag("toggle_language_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, vm.uiState.value.isArabic)

        composeTestRule.onNodeWithTag("toggle_language_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, vm.uiState.value.isArabic)

        // 3. Navigate to Chart & Challenge tab, test TalkBack Accessibility Table & BOS Answer
        composeTestRule.onNodeWithTag("nav_tab_chart").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ohlc_chart_canvas").assertIsDisplayed()

        composeTestRule.onNodeWithTag("toggle_a11y_table_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_accessibility_table").assertIsDisplayed()

        composeTestRule.onNodeWithTag("toggle_a11y_table_button").performClick()
        composeTestRule.waitForIdle()

        // Select WICK_SWEEP at DP-06 and evaluate
        composeTestRule.onNodeWithTag("option_item_WICK_SWEEP").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("submit_challenge_answer_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("evaluation_feedback_box").performScrollTo().assertIsDisplayed()

        // 4. Navigate to Simulation tab and test double-tap order idempotency
        composeTestRule.onNodeWithTag("nav_tab_simulation").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sim_open_order_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, vm.uiState.value.simulationWorkspace.tradeEvents.size)
    }
}
