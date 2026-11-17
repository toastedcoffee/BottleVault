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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(properties = ["app.rate-limit.provider.upcitemdb-per-day=1"])
@AutoConfigureMockMvc
class ProviderCapIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var missRepository: BarcodeLookupMissRepository

    @MockitoBean lateinit var upcItemDb: UpcItemDbProvider
    @MockitoBean lateinit var barcodeLookup: BarcodeLookupProvider
    @MockitoBean lateinit var openFoodFacts: OpenFoodFactsProvider

    private lateinit var token: String

    @BeforeEach
    fun setup() {
        for ((mock, source, priority, enabled) in listOf(
            Quad(upcItemDb, "upcitemdb", 10, true),
            Quad(barcodeLookup, "barcodelookup", 20, false),
            Quad(openFoodFacts, "openfoodfacts", 30, true),
        )) {
            Mockito.`when`(mock.source).thenReturn(source)
            Mockito.`when`(mock.priority).thenReturn(priority)
            Mockito.`when`(mock.isEnabled).thenReturn(enabled)
            Mockito.`when`(mock.lookup(anyString())).thenReturn(null)
        }
        missRepository.deleteAll()
        val r = mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"cap-${System.nanoTime()}@example.com","password":"password123"}""")
        ).andExpect(status().isCreated).andReturn()
        token = objectMapper.readValue(r.response.contentAsString, AuthResponse::class.java).accessToken
    }

    private data class Quad(val mock: BarcodeProvider, val source: String, val priority: Int, val enabled: Boolean)

    private fun lookup(barcode: String) =
        mockMvc.perform(get("/api/barcode/{barcode}", barcode).header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)

    @Test
    fun `a capped provider is skipped, and a skip is not cached as a miss`() {
        lookup("10000001")
        lookup("10000002")
        Mockito.verify(upcItemDb, Mockito.times(1)).lookup(anyString())
        Mockito.verify(openFoodFacts, Mockito.times(2)).lookup(anyString())
        assertNotNull(missRepository.findById("10000001").orElse(null), "a full miss is cached")
        assertNull(missRepository.findById("10000002").orElse(null), "skipped lookups must be retried later, not cached as misses")
    }
}
