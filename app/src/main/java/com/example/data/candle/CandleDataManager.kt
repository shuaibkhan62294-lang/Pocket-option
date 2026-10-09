package com.example.data.candle

import com.example.model.Candle
import com.example.model.CandleSource
import com.example.model.CurrentCandleTrackerSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 4. Candle Data Engine & 5. Current Candle Tracking
 *
 * Maintains a rolling window of at least 100 candles internally (default capacity 120),
 * tracks the currently forming candle continuously, freezes it upon close, and appends
 * it to historical rolling storage.
 */
interface CandleDataManager {
  val candlesFlow: StateFlow<List<Candle>>
  val currentCandleFlow: StateFlow<Candle?>
  val currentCandleTrackerFlow: StateFlow<CurrentCandleTrackerSnapshot?>
  val maxCapacity: Int

  suspend fun updateCurrentCandle(
    price: Double,
    timestamp: Long = System.currentTimeMillis(),
    volumeDelta: Double? = null,
    source: CandleSource = CandleSource.LIVE_CAPTURE,
    qualityScore: Float = 1.0f
  )

  suspend fun closeCurrentCandleAndOpenNext(
    nextOpenPrice: Double? = null,
    nextTimestamp: Long = System.currentTimeMillis(),
    source: CandleSource = CandleSource.LIVE_CAPTURE,
    qualityScore: Float = 1.0f
  )

  suspend fun closeCurrentCandle()

  suspend fun appendOrUpdateCandle(candle: Candle)

  suspend fun ingestVisualExtractedCandles(
    extractedCandles: List<Candle>,
    nowMillis: Long = System.currentTimeMillis()
  )

  suspend fun replaceAllCandles(candles: List<Candle>)

  suspend fun clear()

  fun getRecentCandles(count: Int = MIN_BRAIN_ROLLING_CANDLES): List<Candle>

  fun getCandleTrackingStatusWord(): String

  companion object {
    const val MIN_REQUIRED_CANDLES = 40
    const val MIN_BRAIN_ROLLING_CANDLES = 100
    const val DEFAULT_ROLLING_CAPACITY = 120
  }
}

/**
 * Rolling candle repository implementation that maintains at least 100 recent candles
 * internally (default capacity 120) while supporting 40+ candle queries from Part 1.
 */
class RollingCandleRepository(
  override val maxCapacity: Int = CandleDataManager.DEFAULT_ROLLING_CAPACITY
) : CandleDataManager {

  init {
    require(maxCapacity >= CandleDataManager.MIN_REQUIRED_CANDLES) {
      "RollingCandleRepository capacity must be at least ${CandleDataManager.MIN_REQUIRED_CANDLES} candles."
    }
  }

  private val mutex = Mutex()
  private val buffer = ArrayDeque<Candle>(maxCapacity)

  private val _candlesFlow = MutableStateFlow<List<Candle>>(emptyList())
  override val candlesFlow: StateFlow<List<Candle>> = _candlesFlow.asStateFlow()

  private val _currentCandleFlow = MutableStateFlow<Candle?>(null)
  override val currentCandleFlow: StateFlow<Candle?> = _currentCandleFlow.asStateFlow()

  private val _currentCandleTrackerFlow = MutableStateFlow<CurrentCandleTrackerSnapshot?>(null)
  override val currentCandleTrackerFlow: StateFlow<CurrentCandleTrackerSnapshot?> =
    _currentCandleTrackerFlow.asStateFlow()

  override suspend fun updateCurrentCandle(
    price: Double,
    timestamp: Long,
    volumeDelta: Double?,
    source: CandleSource,
    qualityScore: Float
  ) {
    mutex.withLock {
      val last = buffer.lastOrNull()
      if (last == null || last.isComplete) {
        val freshCandle = Candle(
          timestamp = timestamp,
          open = price,
          high = price,
          low = price,
          close = price,
          volume = volumeDelta,
          isComplete = false,
          source = source,
          qualityScore = qualityScore,
          openedAtMillis = timestamp
        )
        appendInternal(freshCandle)
      } else {
        val updated = last.withLiveTick(
          newPrice = price,
          tickVolumeDelta = volumeDelta,
          updatedQualityScore = qualityScore
        )
        buffer[buffer.lastIndex] = updated
      }
      publishStateLocked(timestamp)
    }
  }

  override suspend fun closeCurrentCandle() {
    mutex.withLock {
      val last = buffer.lastOrNull() ?: return@withLock
      if (!last.isComplete) {
        buffer[buffer.lastIndex] = last.markComplete()
        publishStateLocked()
      }
    }
  }

  override suspend fun closeCurrentCandleAndOpenNext(
    nextOpenPrice: Double?,
    nextTimestamp: Long,
    source: CandleSource,
    qualityScore: Float
  ) {
    mutex.withLock {
      val last = buffer.lastOrNull()
      val openPrice = nextOpenPrice ?: last?.close ?: 1.08500
      if (last != null && !last.isComplete) {
        buffer[buffer.lastIndex] = last.markComplete()
      }
      val newActiveCandle = Candle(
        timestamp = nextTimestamp,
        open = openPrice,
        high = openPrice,
        low = openPrice,
        close = openPrice,
        volume = 1.0,
        isComplete = false,
        source = source,
        qualityScore = qualityScore,
        openedAtMillis = nextTimestamp
      )
      appendInternal(newActiveCandle)
      publishStateLocked(nextTimestamp)
    }
  }

  override suspend fun appendOrUpdateCandle(candle: Candle) {
    mutex.withLock {
      val existingIndex = buffer.indexOfLast { it.timestamp == candle.timestamp }
      if (existingIndex >= 0) {
        buffer[existingIndex] = candle
      } else {
        val tail = buffer.lastOrNull()
        if (tail != null && !tail.isComplete && candle.timestamp > tail.timestamp) {
          buffer[buffer.lastIndex] = tail.markComplete()
        }
        appendInternal(candle)
      }
      publishStateLocked(candle.timestamp)
    }
  }

  override suspend fun ingestVisualExtractedCandles(
    extractedCandles: List<Candle>,
    nowMillis: Long
  ) {
    if (extractedCandles.isEmpty()) return
    mutex.withLock {
      if (buffer.isEmpty()) {
        // Seed rolling buffer from detected visual candles
        val trimmed = extractedCandles.takeLast(maxCapacity)
        buffer.addAll(trimmed)
      } else {
        // Update the currently forming candle from the rightmost detected visual candle,
        // or roll over if a new completed candle appeared
        val newestExtracted = extractedCandles.last()
        val currentTail = buffer.last()
        val priceShift = abs(newestExtracted.open - currentTail.open)
        if (priceShift > 0.00035 && currentTail.elapsedTimeMs(nowMillis) >= 3_500L) {
          // Previous forming candle closed; freeze it and start tracking the new candle
          buffer[buffer.lastIndex] = currentTail.markComplete()
          appendInternal(
            newestExtracted.copy(
              timestamp = nowMillis,
              openedAtMillis = nowMillis,
              isComplete = false,
              source = CandleSource.LIVE_CAPTURE
            )
          )
        } else {
          // Continuously update current forming candle OHLC
          buffer[buffer.lastIndex] = currentTail.copy(
            high = maxOf(currentTail.high, newestExtracted.high),
            low = minOf(currentTail.low, newestExtracted.low),
            close = newestExtracted.close,
            qualityScore = newestExtracted.qualityScore,
            source = CandleSource.LIVE_CAPTURE,
            isComplete = false
          )
        }
      }
      publishStateLocked(nowMillis)
    }
  }

  override suspend fun replaceAllCandles(candles: List<Candle>) {
    mutex.withLock {
      buffer.clear()
      val trimmed = if (candles.size > maxCapacity) {
        candles.takeLast(maxCapacity)
      } else {
        candles
      }
      buffer.addAll(trimmed)
      publishStateLocked()
    }
  }

  override suspend fun clear() {
    mutex.withLock {
      buffer.clear()
      publishStateLocked()
    }
  }

  override fun getRecentCandles(count: Int): List<Candle> {
    val currentList = _candlesFlow.value
    return currentList.takeLast(count.coerceAtLeast(1))
  }

  override fun getCandleTrackingStatusWord(): String {
    val current = _currentCandleFlow.value ?: return "Waiting"
    return if (current.isComplete) {
      "Candle closed"
    } else {
      "Tracking live candle"
    }
  }

  private fun appendInternal(candle: Candle) {
    if (buffer.size >= maxCapacity) {
      buffer.removeFirst()
    }
    buffer.addLast(candle)
  }

  private fun publishStateLocked(nowMillis: Long = System.currentTimeMillis()) {
    val snapshot = buffer.toList()
    _candlesFlow.value = snapshot
    val latest = snapshot.lastOrNull()
    _currentCandleFlow.value = latest
    _currentCandleTrackerFlow.value = latest?.let {
      CurrentCandleTrackerSnapshot.fromCandle(it, nowMillis)
    }
  }
}

/**
 * Generates clearly-labeled DEMO / PLACEHOLDER candles solely to demonstrate
 * the candlestick chart UI when requested by the user.
 *
 * This generator is NEVER presented as live Pocket Option data.
 */
object DemoPlaceholderCandleSeeder {
  private const val CANDLE_INTERVAL_MS = 5_000L

  fun generateInitialDemoCandles(
    count: Int = 105,
    nowMillis: Long = System.currentTimeMillis()
  ): List<Candle> {
    val random = Random(42109L)
    val result = ArrayList<Candle>(count)
    var currentPrice = 1.08420
    val startTimestamp = nowMillis - (count - 1) * CANDLE_INTERVAL_MS

    for (i in 0 until count) {
      val isLast = (i == count - 1)
      val open = currentPrice
      val waveBias = kotlin.math.sin(i * 0.45) * 0.00018
      val delta = (random.nextDouble(-0.00042, 0.00042)) + waveBias
      val close = roundPrice(open + delta)
      val bodyHigh = maxOf(open, close)
      val bodyLow = minOf(open, close)
      val upperWickExtra = random.nextDouble(0.00006, 0.00034)
      val lowerWickExtra = random.nextDouble(0.00006, 0.00034)
      val high = roundPrice(bodyHigh + upperWickExtra)
      val low = roundPrice(bodyLow - lowerWickExtra)
      val volume = (random.nextDouble(12.0, 95.0) * 10.0).roundToInt() / 10.0
      val ts = startTimestamp + i * CANDLE_INTERVAL_MS

      result.add(
        Candle(
          timestamp = ts,
          open = open,
          high = high,
          low = low,
          close = close,
          volume = volume,
          isComplete = !isLast,
          source = CandleSource.DEMO_PLACEHOLDER,
          qualityScore = 0.95f,
          openedAtMillis = ts
        )
      )
      currentPrice = close
    }
    return result
  }

  fun roundPrice(value: Double): Double {
    return (value * 100000.0).roundToInt() / 100000.0
  }
}
