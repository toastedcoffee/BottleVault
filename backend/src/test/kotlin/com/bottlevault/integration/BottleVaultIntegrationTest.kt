package com.bottlevault.integration

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.auth.dto.RegisterRequest
import com.bottlevault.bottle.dto.BottleCreateRequest
import com.bottlevault.bottle.dto.BottleResponse
import com.bottlevault.brand.dto.BrandResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BottleVaultIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private var accessToken: String = ""

    @BeforeEach
    fun setup() {
        val registerRequest = RegisterRequest(
            email = "test-${System.nanoTime()}@example.com",
            password = "password123",
            displayName = "Test User"
        )

        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val authResponse = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java)
        accessToken = authResponse.accessToken
    }

    @Test
    fun `brands are seeded on startup`() {
        val result = mockMvc.perform(get("/api/brands"))
            .andExpect(status().isOk)
            .andReturn()

        val brands = objectMapper.readValue(
            result.response.contentAsString,
            objectMapper.typeFactory.constructCollectionType(List::class.java, BrandResponse::class.java)
        ) as List<*>

        assertTrue(brands.size >= 20, "Should have at least 20 seeded brands")
    }

    @Test
    fun `can create and retrieve a bottle`() {
        // Get a product to use
        val productsResult = mockMvc.perform(
            get("/api/products").param("search", "Old No. 7")
        )
            .andExpect(status().isOk)
            .andReturn()

        val productsJson = objectMapper.readTree(productsResult.response.contentAsString)
        val productId = productsJson[0]["id"].asText()

        // Create a bottle
        val createRequest = BottleCreateRequest(
            productId = productId,
            purchaseLocation = "Local Liquor Store",
            purchaseCost = java.math.BigDecimal("29.99"),
            rating = 4,
            notes = "Great for mixing"
        )

        val createResult = mockMvc.perform(
            post("/api/bottles")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val bottle = objectMapper.readValue(createResult.response.contentAsString, BottleResponse::class.java)
        assertEquals("UNOPENED", bottle.status.name)
        assertEquals(4, bottle.rating)
        assertEquals("Great for mixing", bottle.notes)

        // Retrieve it
        mockMvc.perform(
            get("/api/bottles/${bottle.id}")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(bottle.id))
            .andExpect(jsonPath("$.product.name").value("Old No. 7 Tennessee Whiskey"))
    }

    @Test
    fun `cannot access bottles without authentication`() {
        mockMvc.perform(get("/api/bottles"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `can delete a bottle`() {
        // Get a product
        val productsResult = mockMvc.perform(get("/api/products").param("search", "Jameson Irish"))
            .andExpect(status().isOk)
            .andReturn()
        val productId = objectMapper.readTree(productsResult.response.contentAsString)[0]["id"].asText()

        // Create a bottle
        val createResult = mockMvc.perform(
            post("/api/bottles")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(BottleCreateRequest(productId = productId)))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val bottleId = objectMapper.readTree(createResult.response.contentAsString)["id"].asText()

        // Delete it
        mockMvc.perform(
            delete("/api/bottles/$bottleId")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isNoContent)

        // Verify it's gone
        mockMvc.perform(
            get("/api/bottles/$bottleId")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `registration validates input`() {
        // Missing email
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "", "password": "password123"}""")
        )
            .andExpect(status().isBadRequest)

        // Short password
        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@example.com", "password": "short"}""")
        )
            .andExpect(status().isBadRequest)
    }
}
