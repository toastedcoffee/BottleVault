package com.bottlevault.barcode

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.auth.dto.RegisterRequest
import com.bottlevault.barcode.dto.BarcodeLookupResponse
import com.bottlevault.barcode.dto.ExternalProductData
import com.bottlevault.barcode.providers.BarcodeLookupProvider
import com.bottlevault.barcode.providers.OpenFoodFactsProvider
import com.bottlevault.barcode.providers.UpcItemDbProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BarcodeIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var missRepository: BarcodeLookupMissRepository

    @MockitoBean lateinit var upcItemDb: UpcItemDbProvider
    @MockitoBean lateinit var barcodeLookup: BarcodeLookupProvider
    @MockitoBean lateinit var openFoodFacts: OpenFoodFactsProvider

    private var accessToken: String = ""

    @BeforeEach
    fun setup() {
        // Default each mocked provider to enabled-but-empty so external lookups never run real HTTP.
        // Tests that need a hit will override the lookup stub.
        stubProvider(upcItemDb, "upcitemdb", 10, enabled = true)
        stubProvider(barcodeLookup, "barcodelookup", 20, enabled = false)
        stubProvider(openFoodFacts, "openfoodfacts", 30, enabled = true)

        val registerRequest = RegisterRequest(
            email = "barcode-test-${System.nanoTime()}@example.com",
            password = "password123",
            displayName = "Barcode Tester"
        )
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        ).andExpect(status().isCreated).andReturn()
        accessToken = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).accessToken

        missRepository.deleteAll()
    }

    private fun stubProvider(mock: BarcodeProvider, source: String, priority: Int, enabled: Boolean) {
        Mockito.`when`(mock.source).thenReturn(source)
        Mockito.`when`(mock.priority).thenReturn(priority)
        Mockito.`when`(mock.isEnabled).thenReturn(enabled)
        Mockito.`when`(mock.lookup(anyString())).thenReturn(null)
    }

    private fun lookup(barcode: String): BarcodeLookupResponse {
        val result = mockMvc.perform(
            get("/api/barcode/{barcode}", barcode).header("Authorization", "Bearer $accessToken")
        ).andExpect(status().isOk).andReturn()
        return objectMapper.readValue(result.response.contentAsString, BarcodeLookupResponse::class.java)
    }

    @Test
    fun `local hit returns seeded product without calling any provider`() {
        // V3 seeds Jack Daniel's Old No. 7 with this barcode
        val response = lookup("082184000427")

        assertTrue(response.found, "should find seeded product")
        assertEquals("local", response.source)
        val product = assertNotNull(response.product)
        assertEquals("Old No. 7 Tennessee Whiskey", product.name)

        Mockito.verify(upcItemDb, Mockito.never()).lookup(anyString())
        Mockito.verify(openFoodFacts, Mockito.never()).lookup(anyString())
    }

    @Test
    fun `external hit returns provider data and clears any prior miss`() {
        val barcode = "9999999999991"
        val data = ExternalProductData(
            name = "Test Bourbon 45% ABV",
            brandName = "Test Distillery",
            barcode = barcode,
            size = "750ml",
            abv = 45.0,
            imageUrl = null,
            categories = "Spirits"
        )
        Mockito.`when`(upcItemDb.lookup(barcode)).thenReturn(data)

        val response = lookup(barcode)

        assertTrue(response.found)
        assertEquals("upcitemdb", response.source)
        assertNull(response.product)
        assertEquals("Test Bourbon 45% ABV", response.externalProduct?.name)
        assertEquals(45.0, response.externalProduct?.abv)
        assertTrue(missRepository.findById(barcode).isEmpty, "miss row should not be persisted on hit")
    }

    @Test
    fun `provider order respects priority - higher priority skipped after first hit`() {
        val barcode = "9999999999992"
        // upcItemDb (priority 10) hits first; openFoodFacts (priority 30) must not be called
        Mockito.`when`(upcItemDb.lookup(barcode)).thenReturn(
            ExternalProductData(
                name = "First Provider Wins",
                brandName = null,
                barcode = barcode,
                size = null,
                abv = null,
                imageUrl = null,
                categories = null
            )
        )

        val response = lookup(barcode)

        assertEquals("upcitemdb", response.source)
        Mockito.verify(openFoodFacts, Mockito.never()).lookup(barcode)
    }

    @Test
    fun `disabled provider is skipped`() {
        val barcode = "9999999999993"
        // barcodeLookup is stubbed disabled by default; even if its lookup is stubbed, it must not run.
        Mockito.`when`(barcodeLookup.lookup(barcode)).thenReturn(
            ExternalProductData("should not be returned", null, barcode, null, null, null, null)
        )

        val response = lookup(barcode)

        // No enabled provider hits → all-miss
        assertEquals(false, response.found)
        Mockito.verify(barcodeLookup, Mockito.never()).lookup(barcode)
    }

    @Test
    fun `negative cache short-circuits subsequent lookups within TTL`() {
        val barcode = "9999999999994"

        val first = lookup(barcode)
        assertEquals(false, first.found)
        assertNull(first.source, "first all-miss returns null source, not cached-miss")
        assertTrue(missRepository.findById(barcode).isPresent, "miss should be recorded")

        val second = lookup(barcode)
        assertEquals(false, second.found)
        assertEquals("cached-miss", second.source)

        // First call hit each enabled provider once; second call must NOT call any provider again.
        Mockito.verify(upcItemDb, Mockito.times(1)).lookup(barcode)
        Mockito.verify(openFoodFacts, Mockito.times(1)).lookup(barcode)
    }
}
