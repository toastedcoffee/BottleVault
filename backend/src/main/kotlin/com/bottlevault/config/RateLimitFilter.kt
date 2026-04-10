package com.bottlevault.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitFilter(
    @Value("\${app.rate-limit.max-attempts:10}") private val maxAttempts: Int,
    @Value("\${app.rate-limit.window-seconds:60}") private val windowSeconds: Long
) : OncePerRequestFilter() {

    private val attempts = ConcurrentHashMap<String, MutableList<Instant>>()

    companion object {
        private const val CLEANUP_THRESHOLD = 1000
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        if (!isRateLimitedPath(path)) {
            filterChain.doFilter(request, response)
            return
        }

        val clientIp = getClientIp(request)
        val key = "$clientIp:$path"
        val now = Instant.now()
        val cutoff = now.minusSeconds(windowSeconds)

        val timestamps = attempts.compute(key) { _, existing ->
            val list = existing ?: mutableListOf()
            list.removeAll { it.isBefore(cutoff) }
            list
        }!!

        if (timestamps.size >= maxAttempts) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write("""{"status":429,"error":"Too Many Requests","message":"Rate limit exceeded. Try again later."}""")
            return
        }

        timestamps.add(now)

        if (attempts.size > CLEANUP_THRESHOLD) {
            cleanupStaleEntries(cutoff)
        }

        filterChain.doFilter(request, response)
    }

    private fun isRateLimitedPath(path: String): Boolean =
        path.startsWith("/api/auth/")

    private fun getClientIp(request: HttpServletRequest): String =
        request.getHeader("CF-Connecting-IP")
            ?: request.getHeader("X-Real-IP")
            ?: request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr

    private fun cleanupStaleEntries(cutoff: Instant) {
        attempts.entries.removeIf { (_, timestamps) ->
            timestamps.removeAll { it.isBefore(cutoff) }
            timestamps.isEmpty()
        }
    }
}
