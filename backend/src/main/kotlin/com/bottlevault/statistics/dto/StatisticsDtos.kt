package com.bottlevault.statistics.dto

import java.math.BigDecimal

data class StatisticsResponse(
    val totalBottles: Long,
    val totalValue: BigDecimal,
    val averageRating: Double?,
    val percentageOpened: Double,
    val statusBreakdown: List<StatusCount>,
    val typeDistribution: List<TypeCount>,
    val spendingOverTime: List<MonthlySpending>,
    val topRatedBottles: List<BottleSummary>,
    val recentAdditions: List<BottleSummary>
)

data class StatusCount(
    val status: String,
    val count: Long
)

data class TypeCount(
    val type: String,
    val count: Long
)

data class MonthlySpending(
    val year: Int,
    val month: Int,
    val total: BigDecimal
)

data class BottleSummary(
    val id: String,
    val productName: String,
    val brandName: String,
    val type: String,
    val rating: Int?,
    val purchaseCost: BigDecimal?,
    val createdAt: String
)
