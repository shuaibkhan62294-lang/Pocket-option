package com.example.engine.movement

import com.example.model.PriceDirection
import kotlin.math.abs

/**
 * Single visible price movement update between candle changes (Requirement 7).
 */
data class PriceMovementTick(
  val timestamp: Long,
  val price: Double,
  val direction: PriceDirection,
  val movementSize: Double,
  val movementDurationMs: Long
)

/**
 * Internal micro-structure summary used by the Pro Trader Brain to detect:
 * - momentum
 * - acceleration
 * - deceleration
 * - rapid reversal
 * - price rejection
 * - short consolidation
 * - breakout attempt
 */
data class PriceMicroStructureSnapshot(
  val recentTicksCount: Int = 0,
  val lastPrice: Double? = null,
  val lastDirection: PriceDirection = PriceDirection.FLAT,
  val momentum: Double = 0.0,
  val isAccelerating: Boolean = false,
  val isDecelerating: Boolean = false,
  val hasRapidReversal: Boolean = false,
  val hasPriceRejection: Boolean = false,
  val isShortConsolidation: Boolean = false,
  val isBreakoutAttempt: Boolean = false,
  val lastUpdateTimestamp: Long = 0L
)

/**
 * 7. Price Movement Detector (Micro-Structure Engine)
 *
 * Tracks visible intra-candle price updates and computes micro-structure traits
 * without cluttering the beginner-friendly main screen.
 */
interface PriceMovementDetector {
  fun recordPriceUpdate(price: Double, timestamp: Long = System.currentTimeMillis()): PriceMicroStructureSnapshot
  fun getCurrentSnapshot(): PriceMicroStructureSnapshot
  fun getRecentTicks(limit: Int = 30): List<PriceMovementTick>
  fun isPriceUpdatingRecently(nowMillis: Long = System.currentTimeMillis(), maxStaleMs: Long = 10_000L): Boolean
  fun clear()
}

class DefaultPriceMovementDetector(
  private val maxTickHistory: Int = 120
) : PriceMovementDetector {

  private val ticks = ArrayDeque<PriceMovementTick>(maxTickHistory)
  private var latestSnapshot = PriceMicroStructureSnapshot()

  @Synchronized
  override fun recordPriceUpdate(price: Double, timestamp: Long): PriceMicroStructureSnapshot {
    val previous = ticks.lastOrNull()
    val delta = if (previous != null) price - previous.price else 0.0
    val absDelta = abs(delta)
    val duration = if (previous != null) (timestamp - previous.timestamp).coerceAtLeast(1L) else 0L
    val direction = when {
      delta > 1e-7 -> PriceDirection.UP
      delta < -1e-7 -> PriceDirection.DOWN
      else -> PriceDirection.FLAT
    }

    val tick = PriceMovementTick(
      timestamp = timestamp,
      price = price,
      direction = direction,
      movementSize = absDelta,
      movementDurationMs = duration
    )

    if (ticks.size >= maxTickHistory) {
      ticks.removeFirst()
    }
    ticks.addLast(tick)

    latestSnapshot = evaluateMicroStructure(timestamp)
    return latestSnapshot
  }

  @Synchronized
  override fun getCurrentSnapshot(): PriceMicroStructureSnapshot = latestSnapshot

  @Synchronized
  override fun getRecentTicks(limit: Int): List<PriceMovementTick> {
    return ticks.takeLast(limit.coerceAtLeast(1))
  }

  @Synchronized
  override fun isPriceUpdatingRecently(nowMillis: Long, maxStaleMs: Long): Boolean {
    val last = ticks.lastOrNull() ?: return false
    return (nowMillis - last.timestamp) in 0..maxStaleMs
  }

  @Synchronized
  override fun clear() {
    ticks.clear()
    latestSnapshot = PriceMicroStructureSnapshot()
  }

  private fun evaluateMicroStructure(nowMillis: Long): PriceMicroStructureSnapshot {
    val recent = ticks.takeLast(10)
    if (recent.isEmpty()) return PriceMicroStructureSnapshot()

    val lastTick = recent.last()
    if (recent.size < 3) {
      return PriceMicroStructureSnapshot(
        recentTicksCount = recent.size,
        lastPrice = lastTick.price,
        lastDirection = lastTick.direction,
        lastUpdateTimestamp = nowMillis
      )
    }

    val signedMoves = recent.map {
      when (it.direction) {
        PriceDirection.UP -> it.movementSize
        PriceDirection.DOWN -> -it.movementSize
        PriceDirection.FLAT -> 0.0
      }
    }
    val momentum = signedMoves.sum()

    val recentThreeAvg = recent.takeLast(3).map { it.movementSize }.average()
    val priorAvg = recent.dropLast(3).map { it.movementSize }.average().coerceAtLeast(1e-7)

    val isAccelerating = recentThreeAvg > priorAvg * 1.35
    val isDecelerating = recentThreeAvg < priorAvg * 0.65

    val prevTick = recent[recent.size - 2]
    val hasRapidReversal = (prevTick.direction != PriceDirection.FLAT &&
      lastTick.direction != PriceDirection.FLAT &&
      prevTick.direction != lastTick.direction &&
      lastTick.movementSize > priorAvg * 1.4)

    val prices = recent.map { it.price }
    val high = prices.maxOrNull() ?: lastTick.price
    val low = prices.minOrNull() ?: lastTick.price
    val range = (high - low).coerceAtLeast(1e-7)

    val hasPriceRejection = hasRapidReversal && (lastTick.movementSize >= range * 0.45)
    val isShortConsolidation = recent.size >= 5 && (range < priorAvg * 1.8)
    val isBreakoutAttempt = recent.size >= 5 && (lastTick.movementSize > priorAvg * 2.0) && isAccelerating

    return PriceMicroStructureSnapshot(
      recentTicksCount = ticks.size,
      lastPrice = lastTick.price,
      lastDirection = lastTick.direction,
      momentum = momentum,
      isAccelerating = isAccelerating,
      isDecelerating = isDecelerating,
      hasRapidReversal = hasRapidReversal,
      hasPriceRejection = hasPriceRejection,
      isShortConsolidation = isShortConsolidation,
      isBreakoutAttempt = isBreakoutAttempt,
      lastUpdateTimestamp = nowMillis
    )
  }
}
