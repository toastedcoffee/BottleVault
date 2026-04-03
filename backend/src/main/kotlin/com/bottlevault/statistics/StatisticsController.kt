package com.bottlevault.statistics

import com.bottlevault.statistics.dto.StatisticsResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/statistics")
class StatisticsController(private val statisticsService: StatisticsService) {

    @GetMapping
    fun getStatistics(authentication: Authentication): StatisticsResponse =
        statisticsService.getStatistics(UUID.fromString(authentication.principal as String))
}
