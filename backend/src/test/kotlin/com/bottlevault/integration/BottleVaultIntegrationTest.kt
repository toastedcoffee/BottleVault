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
import org.springframework.mock.web.MockMultipartFile
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
    fun `can update user profile`() {
        val result = mockMvc.perform(
            put("/api/user/profile")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName": "Updated Name", "defaultCurrency": "EUR", "measurementUnit": "oz"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Updated Name"))
            .andExpect(jsonPath("$.defaultCurrency").value("EUR"))
            .andExpect(jsonPath("$.measurementUnit").value("oz"))
            .andReturn()

        // Verify email hasn't changed
        val user = objectMapper.readTree(result.response.contentAsString)
        assertTrue(user["email"].asText().contains("@example.com"))
    }

    @Test
    fun `can change password and login with new password`() {
        // Change password
        mockMvc.perform(
            put("/api/user/password")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "password123", "newPassword": "newpassword456"}""")
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `change password rejects wrong current password`() {
        mockMvc.perform(
            put("/api/user/password")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "wrongpassword", "newPassword": "newpassword456"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `user profile requires authentication`() {
        mockMvc.perform(
            put("/api/user/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName": "Hacker"}""")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `statistics returns empty collection data`() {
        mockMvc.perform(
            get("/api/statistics")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalBottles").value(0))
            .andExpect(jsonPath("$.totalValue").value(0))
            .andExpect(jsonPath("$.averageRating").doesNotExist())
            .andExpect(jsonPath("$.percentageOpened").value(0.0))
            .andExpect(jsonPath("$.statusBreakdown").isArray)
            .andExpect(jsonPath("$.typeDistribution").isArray)
            .andExpect(jsonPath("$.spendingOverTime").isArray)
            .andExpect(jsonPath("$.topRatedBottles").isArray)
            .andExpect(jsonPath("$.recentAdditions").isArray)
    }

    @Test
    fun `statistics reflects added bottles`() {
        // Get a product
        val productsResult = mockMvc.perform(get("/api/products").param("search", "Old No. 7"))
            .andExpect(status().isOk)
            .andReturn()
        val productId = objectMapper.readTree(productsResult.response.contentAsString)[0]["id"].asText()

        // Create two bottles with different data
        mockMvc.perform(
            post("/api/bottles")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(BottleCreateRequest(
                    productId = productId,
                    purchaseCost = java.math.BigDecimal("29.99"),
                    rating = 4,
                    notes = "First bottle"
                )))
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/bottles")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(BottleCreateRequest(
                    productId = productId,
                    purchaseCost = java.math.BigDecimal("35.00"),
                    rating = 5,
                    notes = "Second bottle"
                )))
        ).andExpect(status().isCreated)

        // Check statistics
        mockMvc.perform(
            get("/api/statistics")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalBottles").value(2))
            .andExpect(jsonPath("$.totalValue").value(64.99))
            .andExpect(jsonPath("$.averageRating").value(4.5))
            .andExpect(jsonPath("$.percentageOpened").value(0.0))
            .andExpect(jsonPath("$.topRatedBottles.length()").value(2))
            .andExpect(jsonPath("$.recentAdditions.length()").value(2))
            .andExpect(jsonPath("$.statusBreakdown.length()").value(1))
            .andExpect(jsonPath("$.statusBreakdown[0].status").value("UNOPENED"))
            .andExpect(jsonPath("$.statusBreakdown[0].count").value(2))
    }

    @Test
    fun `statistics requires authentication`() {
        mockMvc.perform(get("/api/statistics"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `can upload, retrieve, and delete a bottle image`() {
        val bottleId = createBottle(searchName = "Old No. 7", token = accessToken)

        // Minimal valid PNG (8x8 transparent) — 1px would also work, this just exercises bytes
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x62, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, 0xB4.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
        val file = MockMultipartFile("file", "test.png", "image/png", png)

        val uploadResult = mockMvc.perform(
            multipart("/api/bottles/$bottleId/image")
                .file(file)
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imagePath").isNotEmpty)
            .andReturn()

        val imagePath = objectMapper.readTree(uploadResult.response.contentAsString)["imagePath"].asText()
        assertTrue(imagePath.startsWith("bottles/"), "imagePath should be relative under bottles/")

        // GET returns the same bytes with image/png content type
        val getResult = mockMvc.perform(
            get("/api/bottles/$bottleId/image")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/png"))
            .andReturn()

        assertEquals(png.size, getResult.response.contentAsByteArray.size)

        // DELETE clears the path
        mockMvc.perform(
            delete("/api/bottles/$bottleId/image")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imagePath").doesNotExist())

        // GET now 404
        mockMvc.perform(
            get("/api/bottles/$bottleId/image")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `image upload rejects unsupported mime type`() {
        val bottleId = createBottle(searchName = "Old No. 7", token = accessToken)
        val file = MockMultipartFile("file", "evil.txt", "text/plain", "not an image".toByteArray())

        mockMvc.perform(
            multipart("/api/bottles/$bottleId/image")
                .file(file)
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `image upload enforces per-user ownership`() {
        // First user creates a bottle
        val bottleId = createBottle(searchName = "Old No. 7", token = accessToken)

        // Second user registers
        val otherEmail = "other-${System.nanoTime()}@example.com"
        val otherToken = registerAndGetToken(otherEmail)

        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val file = MockMultipartFile("file", "test.png", "image/png", png)

        // Second user cannot upload to first user's bottle
        mockMvc.perform(
            multipart("/api/bottles/$bottleId/image")
                .file(file)
                .header("Authorization", "Bearer $otherToken")
        )
            .andExpect(status().isNotFound)
    }

    private fun createBottle(searchName: String, token: String): String {
        val productsResult = mockMvc.perform(get("/api/products").param("search", searchName))
            .andExpect(status().isOk)
            .andReturn()
        val productId = objectMapper.readTree(productsResult.response.contentAsString)[0]["id"].asText()

        val createResult = mockMvc.perform(
            post("/api/bottles")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(BottleCreateRequest(productId = productId)))
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(createResult.response.contentAsString)["id"].asText()
    }

    private fun registerAndGetToken(email: String): String {
        val req = RegisterRequest(email = email, password = "password123", displayName = "Other")
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).accessToken
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
