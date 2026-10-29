package com.example.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Candle
import com.example.domain.model.ChartDrawing
import com.example.domain.model.DrawingToolType
import com.example.domain.model.PaperTrade
import com.example.domain.model.Playhead
import com.example.domain.model.ReferenceLevel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure viewport & coordinate math for OHLC chart rendering.
 * Used both by Compose Canvas and by unit/performance benchmark tests (500 candles <= 1.5s).
 */
data class OhlcRenderPrimitive(
    val barIndex: Int,
    val centerX: Float,
    val wickTopY: Float,
    val wickBottomY: Float,
    val bodyTopY: Float,
    val bodyHeight: Float,
    val bodyWidth: Float,
    val isBullish: Boolean
)

object OhlcChartGeometry {

    fun priceToY(price: Double, minPrice: Double, maxPrice: Double, canvasHeight: Float): Float {
        val range = (maxPrice - minPrice).coerceAtLeast(1e-6)
        val normalized = ((price - minPrice) / range).toFloat().coerceIn(0f, 1f)
        val pad = canvasHeight * 0.08f
        val usable = (canvasHeight - 2f * pad).coerceAtLeast(1f)
        return pad + (1f - normalized) * usable
    }

    fun yToPrice(y: Float, minPrice: Double, maxPrice: Double, canvasHeight: Float): Double {
        val pad = canvasHeight * 0.08f
        val usable = (canvasHeight - 2f * pad).coerceAtLeast(1f)
        val normalized = (1f - ((y - pad) / usable)).coerceIn(0f, 1f)
        return minPrice + normalized * (maxPrice - minPrice)
    }

    fun computeVisiblePrimitives(
        playhead: Playhead,
        allCandles: List<Candle>,
        canvasWidth: Float,
        canvasHeight: Float,
        zoomFactor: Float = 1f,
        panOffsetBars: Float = 0f
    ): List<OhlcRenderPrimitive> {
        // Mandatory Playhead Gate: never inspect or render bars beyond playhead
        val visibleCandles = playhead.visibleDataThrough(allCandles)
        if (visibleCandles.isEmpty() || canvasWidth <= 0f || canvasHeight <= 0f) return emptyList()

        val totalVisible = visibleCandles.size
        val windowSize = (totalVisible / zoomFactor.coerceIn(1f, 10f)).roundToInt().coerceIn(5, totalVisible)
        val startIdx = (totalVisible - windowSize - panOffsetBars.roundToInt()).coerceIn(0, (totalVisible - windowSize).coerceAtLeast(0))
        val endIdx = (startIdx + windowSize).coerceAtMost(totalVisible)
        val slice = visibleCandles.subList(startIdx, endIdx)
        if (slice.isEmpty()) return emptyList()

        val minPrice = slice.minOf { it.low }
        val maxPrice = slice.maxOf { it.high }

        val slotWidth = canvasWidth / slice.size.toFloat()
        val bodyWidth = (slotWidth * 0.64f).coerceIn(2f, 36f)

        val primitives = ArrayList<OhlcRenderPrimitive>(slice.size)
        for (i in slice.indices) {
            val c = slice[i]
            val cx = (i + 0.5f) * slotWidth
            val highY = priceToY(c.high, minPrice, maxPrice, canvasHeight)
            val lowY = priceToY(c.low, minPrice, maxPrice, canvasHeight)
            val openY = priceToY(c.open, minPrice, maxPrice, canvasHeight)
            val closeY = priceToY(c.close, minPrice, maxPrice, canvasHeight)
            val bodyTop = min(openY, closeY)
            val bodyBottom = max(openY, closeY)
            val bodyH = (bodyBottom - bodyTop).coerceAtLeast(2f)

            primitives.add(
                OhlcRenderPrimitive(
                    barIndex = c.barIndex,
                    centerX = cx,
                    wickTopY = highY,
                    wickBottomY = lowY,
                    bodyTopY = bodyTop,
                    bodyHeight = bodyH,
                    bodyWidth = bodyWidth,
                    isBullish = c.isBullish
                )
            )
        }
        return primitives
    }
}

@Composable
fun InteractiveOhlcChart(
    playhead: Playhead,
    allCandles: List<Candle>,
    referenceLevel: ReferenceLevel?,
    drawings: List<ChartDrawing>,
    activeTool: DrawingToolType,
    activeTrade: PaperTrade?,
    isArabic: Boolean,
    showAccessibilityTable: Boolean,
    onAddDrawing: (ChartDrawing) -> Unit,
    onCandleSelected: (Candle) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCandles = remember(playhead, allCandles) {
        playhead.visibleDataThrough(allCandles)
    }
    val visibleDrawings = remember(playhead, drawings) {
        drawings.filter { it.createdAtPlayheadBar <= playhead.currentBarIndex }
    }

    var zoomFactor by remember { mutableFloatStateOf(1f) }
    var panOffsetBars by remember { mutableFloatStateOf(0f) }
    var crosshairOffset by remember { mutableStateOf<Offset?>(null) }
    var selectedCandle by remember(visibleCandles) { mutableStateOf(visibleCandles.lastOrNull()) }

    val minPrice = remember(visibleCandles) { visibleCandles.minOfOrNull { it.low } ?: 99.0 }
    val maxPrice = remember(visibleCandles) { visibleCandles.maxOfOrNull { it.high } ?: 106.0 }

    val a11ySummary = remember(visibleCandles, playhead, referenceLevel, isArabic) {
        val last = visibleCandles.lastOrNull()
        if (isArabic) {
            "الشارت التفاعلي: معروض ${visibleCandles.size} شمعة حتى Playhead ${playhead.currentBarIndex}. آخر إغلاق ${last?.close ?: 0.0}. المستوى المرجعي ${referenceLevel?.price ?: 0.0}."
        } else {
            "Interactive OHLC Chart: showing ${visibleCandles.size} candles through Playhead ${playhead.currentBarIndex}. Last close ${last?.close ?: 0.0}. Reference level ${referenceLevel?.price ?: 0.0}."
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Crosshair & Selected Bar Telemetry Strip
        val inspected = selectedCandle ?: visibleCandles.lastOrNull()
        if (inspected != null) {
            Surface(
                color = Color(0xFF111827),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chart_telemetry_strip")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bar #${inspected.barIndex} (${inspected.timeframe})",
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "O:${"%.2f".format(inspected.open)}  H:${"%.2f".format(inspected.high)}  L:${"%.2f".format(inspected.low)}  C:${"%.2f".format(inspected.close)}",
                        color = if (inspected.isBullish) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showAccessibilityTable) {
            // DR-019: TalkBack Accessible OHLC Data Table Mode
            Surface(
                color = Color(0xFF0B0F17),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .testTag("chart_accessibility_table")
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(visibleCandles, key = { it.id }) { c ->
                        val dirLabel = if (c.isBullish) {
                            if (isArabic) "صاعدة (Bullish)" else "Bullish"
                        } else {
                            if (isArabic) "هابطة (Bearish)" else "Bearish"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Bar ${c.barIndex}: Open ${c.open}, High ${c.high}, Low ${c.low}, Close ${c.close}, $dirLabel"
                                },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "#${c.barIndex} [$dirLabel]",
                                color = if (c.isBullish) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "O:${c.open} H:${c.high} L:${c.low} C:${c.close}",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            // High-Performance CustomPainter / Canvas OHLC Renderer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color(0xFF0B0F17), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .semantics { contentDescription = a11ySummary }
                    .testTag("ohlc_chart_canvas")
                    .pointerInput(visibleCandles, activeTool) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomFactor = (zoomFactor * zoom).coerceIn(1f, 8f)
                            panOffsetBars = (panOffsetBars - pan.x / 18f).coerceIn(0f, visibleCandles.size.toFloat())
                        }
                    }
                    .pointerInput(visibleCandles, activeTool, minPrice, maxPrice) {
                        detectTapGestures { tapOffset ->
                            crosshairOffset = tapOffset
                            if (visibleCandles.isNotEmpty() && size.width > 0) {
                                val idx = ((tapOffset.x / size.width.toFloat()) * visibleCandles.size)
                                    .toInt()
                                    .coerceIn(0, visibleCandles.lastIndex)
                                val tappedBar = visibleCandles[idx]
                                selectedCandle = tappedBar
                                onCandleSelected(tappedBar)

                                if (activeTool != DrawingToolType.NONE) {
                                    val tappedPrice = OhlcChartGeometry.yToPrice(
                                        tapOffset.y,
                                        minPrice,
                                        maxPrice,
                                        size.height.toFloat()
                                    )
                                    val startBar = (tappedBar.barIndex - 2).coerceAtLeast(0)
                                    val endBar = tappedBar.barIndex
                                    onAddDrawing(
                                        ChartDrawing(
                                            drawingId = "DRW-${System.currentTimeMillis()}",
                                            scenarioId = playhead.scenarioId,
                                            toolType = activeTool,
                                            startBarIndex = startBar,
                                            endBarIndex = endBar,
                                            startPrice = tappedPrice - 0.4,
                                            endPrice = tappedPrice + 0.4,
                                            noteText = if (activeTool == DrawingToolType.TEXT_NOTE) "POI @ ${"%.2f".format(tappedPrice)}" else "",
                                            createdAtPlayheadBar = playhead.currentBarIndex
                                        )
                                    )
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. Subtle horizontal price grid lines
                    val gridSteps = 4
                    for (g in 1..gridSteps) {
                        val gy = h * (g.toFloat() / (gridSteps + 1))
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1f
                        )
                    }

                    // 2. Reference Swing Level Line (only if confirmed at or before current playhead)
                    if (referenceLevel != null && referenceLevel.confirmedAtBarIndex <= playhead.currentBarIndex) {
                        val refY = OhlcChartGeometry.priceToY(referenceLevel.price, minPrice, maxPrice, h)
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = Offset(0f, refY),
                            end = Offset(w, refY),
                            strokeWidth = 2.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                        )
                    }

                    // 3. Active Paper Trade SL / TP / Entry lines
                    if (activeTrade != null) {
                        val entryY = OhlcChartGeometry.priceToY(
                            activeTrade.executedEntryPrice ?: activeTrade.plannedEntryPrice,
                            minPrice,
                            maxPrice,
                            h
                        )
                        val slY = OhlcChartGeometry.priceToY(activeTrade.stopPrice, minPrice, maxPrice, h)
                        val tpY = OhlcChartGeometry.priceToY(activeTrade.targetPrice, minPrice, maxPrice, h)

                        drawLine(Color(0xFF3B82F6), Offset(0f, entryY), Offset(w, entryY), strokeWidth = 2f)
                        drawLine(Color(0xFFEF4444), Offset(0f, slY), Offset(w, slY), strokeWidth = 2f)
                        drawLine(Color(0xFF10B981), Offset(0f, tpY), Offset(w, tpY), strokeWidth = 2f)
                    }

                    // 4. User Drawings (Horizontal Line, Trend Line, Rectangle Zone, Text Note marker)
                    val totalBars = visibleCandles.size.coerceAtLeast(1)
                    val slotW = w / totalBars.toFloat()
                    for (drw in visibleDrawings) {
                        val x1 = (drw.startBarIndex.coerceIn(0, totalBars - 1) + 0.5f) * slotW
                        val x2 = (drw.endBarIndex.coerceIn(0, totalBars - 1) + 0.5f) * slotW
                        val y1 = OhlcChartGeometry.priceToY(drw.startPrice, minPrice, maxPrice, h)
                        val y2 = OhlcChartGeometry.priceToY(drw.endPrice, minPrice, maxPrice, h)

                        when (drw.toolType) {
                            DrawingToolType.HORIZONTAL_LINE -> {
                                drawLine(
                                    color = Color(0xFF38BDF8),
                                    start = Offset(0f, y2),
                                    end = Offset(w, y2),
                                    strokeWidth = 2.5f
                                )
                            }
                            DrawingToolType.TREND_LINE -> {
                                drawLine(
                                    color = Color(0xFFA855F7),
                                    start = Offset(x1, y1),
                                    end = Offset(max(x2, x1 + 24f), y2),
                                    strokeWidth = 3f
                                )
                            }
                            DrawingToolType.RECTANGLE_ZONE -> {
                                val left = min(x1, x2)
                                val right = max(x1, x2).coerceAtLeast(left + slotW)
                                val top = min(y1, y2)
                                val bottom = max(y1, y2).coerceAtLeast(top + 8f)
                                drawRect(
                                    color = Color(0x333B82F6),
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top)
                                )
                                drawRect(
                                    color = Color(0xFF3B82F6),
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f)
                                )
                            }
                            DrawingToolType.TEXT_NOTE -> {
                                drawCircle(
                                    color = Color(0xFFFBBF24),
                                    radius = 7f,
                                    center = Offset(x2, y2)
                                )
                            }
                            DrawingToolType.NONE -> Unit
                        }
                    }

                    // 5. Render OHLC Candlesticks strictly through Playhead
                    val primitives = OhlcChartGeometry.computeVisiblePrimitives(
                        playhead = playhead,
                        allCandles = allCandles,
                        canvasWidth = w,
                        canvasHeight = h,
                        zoomFactor = zoomFactor,
                        panOffsetBars = panOffsetBars
                    )

                    for (p in primitives) {
                        val candleColor = if (p.isBullish) Color(0xFF10B981) else Color(0xFFEF4444)
                        // Wick
                        drawLine(
                            color = candleColor,
                            start = Offset(p.centerX, p.wickTopY),
                            end = Offset(p.centerX, p.wickBottomY),
                            strokeWidth = 2f
                        )
                        // Body
                        drawRect(
                            color = candleColor,
                            topLeft = Offset(p.centerX - p.bodyWidth / 2f, p.bodyTopY),
                            size = Size(p.bodyWidth, p.bodyHeight)
                        )
                    }

                    // 6. Playhead cutoff vertical marker at right edge of visible bars
                    val lastPrimitive = primitives.lastOrNull()
                    if (lastPrimitive != null) {
                        val cutoffX = (lastPrimitive.centerX + lastPrimitive.bodyWidth).coerceAtMost(w - 2f)
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = Offset(cutoffX, 0f),
                            end = Offset(cutoffX, h),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    }

                    // 7. Interactive Crosshair
                    val ch = crosshairOffset
                    if (ch != null) {
                        drawLine(
                            color = Color(0x8894A3B8),
                            start = Offset(ch.x.coerceIn(0f, w), 0f),
                            end = Offset(ch.x.coerceIn(0f, w), h),
                            strokeWidth = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        drawLine(
                            color = Color(0x8894A3B8),
                            start = Offset(0f, ch.y.coerceIn(0f, h)),
                            end = Offset(w, ch.y.coerceIn(0f, h)),
                            strokeWidth = 1.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }
                }

                // Overlay badges for Reference Level & Playhead Cutoff
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    if (referenceLevel != null && referenceLevel.confirmedAtBarIndex <= playhead.currentBarIndex) {
                        Surface(
                            color = Color(0xCC1E293B),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${referenceLevel.kind}: ${"%.2f".format(referenceLevel.price)} (Confirmed @ #${referenceLevel.confirmedAtBarIndex})",
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xCC0F172A),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Playhead [${playhead.mode.name}]: Bar ${playhead.currentBarIndex}/${playhead.maxBarIndex} | Zoom ${"%.1f".format(zoomFactor)}x",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
