// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.barcode

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.barcode.providers.BarcodeLookupProvider
import com.bottlevault.barcode.providers.OpenFoodFactsProvider
import com.bottlevault.barcode.providers.UpcItemDbProvider
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals

@SpringBootTest(properties = ["app.rate-limit.user.barcode-lookups-per-day=2"])
@AutoConfigureMockMvc
class BarcodeLimitsIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var missRepository: BarcodeLookupMissRepository

    @MockitoBean lateinit var upcItemDb: UpcItemDbProvider
    @MockitoBean lateinit var barcodeLookup: BarcodeLookupProvider
    @MockitoBean lateinit var openFoodFacts: OpenFoodFactsProvider

    @BeforeEach
    fun setup() {
        stub(upcItemDb, "upcitemdb", 10, enabled = true)
        stub(barcodeLookup, "barcodelookup", 20, enabled = false)
        stub(openFoodFacts, "openfoodfacts", 30, enabled = true)
        missRepository.deleteAll()
    }

    private fun stub(mock: BarcodeProvider, source: String, priority: Int, enabled: Boolean) {
        Mockito.`when`(mock.source).thenReturn(source)
        Mockito.`when`(mock.priority).thenReturn(priority)
        Mockito.`when`(mock.isEnabled).thenReturn(enabled)
        Mockito.`when`(mock.lookup(anyString())).thenReturn(null)
    }

    private fun token(): String {
        val r = mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"lookups-${System.nanoTime()}@example.com","password":"password123"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readValue(r.response.contentAsString, AuthResponse::class.java).accessToken
    }

    private fun lookup(token: String, barcode: String) =
        mockMvc.perform(get("/api/barcode/{barcode}", barcode).header("Authorization", "Bearer $token"))

    @Test
    fun `local and cached answers are free, external lookups are counted per user`() {
        val t = token()
        lookup(t, "082184000427").andExpect(status().isOk).andExpect(jsonPath("$.source").value("local"))
        lookup(t, "20000001").andExpect(status().isOk)                           // external miss #1
        lookup(t, "20000001").andExpect(jsonPath("$.source").value("cached-miss")) // free
        lookup(t, "20000002").andExpect(status().isOk)                           // external miss #2
        lookup(t, "20000003").andExpect(status().isTooManyRequests).andExpect(header().exists("Retry-After"))
        // Still free once the budget is spent: the seed and the miss cache.
        lookup(t, "082184000427").andExpect(status().isOk)
        lookup(t, "20000001").andExpect(status().isOk)

        // Another user has their own budget.
        lookup(token(), "20000003").andExpect(status().isOk)
    }

    @Test
    fun `two lookups of the same unknown barcode at once both answer not found, with one miss row`() {
        val t1 = token()
        val t2 = token()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(t1, t2).map { t ->
                pool.submit(Callable {
                    start.await()
                    lookup(t, "30000001").andReturn().response
                })
            }
            start.countDown()
            results.map { it.get() }.forEach { response ->
                assertEquals(200, response.status, response.contentAsString)
                assertEquals(false, objectMapper.readTree(response.contentAsString)["found"].asBoolean())
            }
        } finally {
            pool.shutdown()
        }
        assertEquals(1, missRepository.findAll().count { it.barcode == "30000001" })
    }
}
