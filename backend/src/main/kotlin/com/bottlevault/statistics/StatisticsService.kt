package com.bottlevault.statistics

import com.bottlevault.bottle.Bottle
import com.bottlevault.bottle.BottleRepository
import com.bottlevault.statistics.dto.*
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class StatisticsService(private val bottleRepository: BottleRepository) {

    @Transactional(readOnly = true)
    fun getStatistics(userId: UUID): StatisticsResponse {
        val totalBottles = bottleRepository.countByUserId(userId)
        val totalValue = bottleRepository.sumPurchaseCostByUserId(userId)
        val bottlesWithCost = bottleRepository.countWithPurchaseCostByUserId(userId)
        val averageCost = if (bottlesWithCost > 0)
            totalValue.divide(BigDecimal(bottlesWithCost), 2, RoundingMode.HALF_UP)
        else null
        val averageRating = bottleRepository.avgRatingByUserId(userId)
            ?.let { BigDecimal(it).setScale(1, RoundingMode.HALF_UP).toDouble() }

        val statusBreakdown = bottleRepository.countByStatusForUser(userId).map { row ->
            StatusCount(status = row[0].toString(), count = row[1] as Long)
        }

        val openedCount = statusBreakdown
            .filter { it.status == "OPENED" || it.status == "EMPTY" }
            .sumOf { it.count }
        val percentageOpened = if (totalBottles > 0)
            BigDecimal(openedCount.toDouble() / totalBottles * 100)
                .setScale(1, RoundingMode.HALF_UP).toDouble()
        else 0.0

        val typeDistribution = bottleRepository.countByTypeForUser(userId).map { row ->
            TypeCount(type = row[0].toString(), count = row[1] as Long)
        }

        val spendingOverTime = bottleRepository.monthlySpendingByUser(userId).map { row ->
            MonthlySpending(
                year = (row[0] as Number).toInt(),
                month = (row[1] as Number).toInt(),
                total = row[2] as BigDecimal
            )
        }

        val topRated = bottleRepository.findTopRatedByUser(userId, PageRequest.of(0, 5))
            .map { toBottleSummary(it) }

        val recent = bottleRepository.findRecentByUser(userId, PageRequest.of(0, 5))
            .map { toBottleSummary(it) }

        return StatisticsResponse(
            totalBottles = totalBottles,
            totalValue = totalValue,
            averageCost = averageCost,
            averageRating = averageRating,
            percentageOpened = percentageOpened,
            statusBreakdown = statusBreakdown,
            typeDistribution = typeDistribution,
            spendingOverTime = spendingOverTime,
            topRatedBottles = topRated,
            recentAdditions = recent
        )
    }

    private fun toBottleSummary(b: Bottle) = BottleSummary(
        id = b.id.toString(),
        productName = b.product.name,
        brandName = b.product.brand.name,
        type = b.product.type.name,
        rating = b.rating,
        purchaseCost = b.purchaseCost,
        purchaseDate = b.purchaseDate?.toString(),
        createdAt = b.createdAt.toString()
    )
}
