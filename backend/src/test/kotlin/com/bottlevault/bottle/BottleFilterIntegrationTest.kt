package com.bottlevault.bottle

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.auth.dto.RegisterRequest
import com.bottlevault.bottle.dto.BottleCreateRequest
import com.bottlevault.common.model.BottleStatus
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

/**
 * Covers GET /api/bottles with every filter combination the Collection page can
 * send. The unfiltered listing was already covered; the filtered variants were
 * not, which is how two bugs reached production: a 500-level failure on the type
 * filter, and filters silently not combining.
 *
 * Fixture: one UNOPENED whiskey and one OPENED vodka, so a combination that
 * should match nothing is distinguishable from one that matches a single bottle.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BottleFilterIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private var accessToken: String = ""

    @BeforeEach
    fun setup() {
        val register = RegisterRequest(
            email = "filter-${System.nanoTime()}@example.com",
            password = "password123",
            displayName = "Test User"
        )
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register))
        )
            .andExpect(status().isCreated)
            .andReturn()
        accessToken = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).accessToken

        createBottle("Old No. 7", BottleStatus.UNOPENED)
        createBottle("Grey Goose", BottleStatus.OPENED)
    }

    // --- single filters ---

    @Test
    fun `status filter returns only matching bottles`() {
        listBottles("status" to "UNOPENED")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].product.name").value("Old No. 7 Tennessee Whiskey"))
    }

    @Test
    fun `status filter with no matches returns empty page`() {
        listBottles("status" to "EMPTY")
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `type filter returns only matching bottles`() {
        listBottles("type" to "WHISKEY")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].product.type").value("WHISKEY"))
    }

    @Test
    fun `type filter with no matches returns empty page`() {
        listBottles("type" to "TEQUILA")
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `search filter matches product name`() {
        listBottles("search" to "Grey Goose")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].product.type").value("VODKA"))
    }

    @Test
    fun `unknown type is rejected rather than silently ignored`() {
        mockMvc.perform(
            get("/api/bottles")
                .param("type", "NOT_A_TYPE")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isBadRequest)
    }

    // --- combinations: every filter must narrow the result, not replace it ---

    @Test
    fun `status and type combine`() {
        // The vodka is OPENED, so UNOPENED+VODKA matches nothing.
        listBottles("status" to "UNOPENED", "type" to "VODKA")
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `status and type combine on a matching pair`() {
        listBottles("status" to "OPENED", "type" to "VODKA")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].product.type").value("VODKA"))
    }

    @Test
    fun `search and type combine`() {
        listBottles("search" to "Old No. 7", "type" to "VODKA")
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `search and status combine`() {
        listBottles("search" to "Grey Goose", "status" to "UNOPENED")
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `all three filters combine`() {
        listBottles("search" to "Old No. 7", "status" to "UNOPENED", "type" to "WHISKEY")
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].product.name").value("Old No. 7 Tennessee Whiskey"))
    }

    @Test
    fun `blank search is treated as no filter`() {
        listBottles("search" to "", "type" to "WHISKEY")
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `no filters returns the whole collection`() {
        listBottles()
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    private fun listBottles(vararg params: Pair<String, String>) =
        mockMvc.perform(
            get("/api/bottles")
                .param("size", "50")
                .header("Authorization", "Bearer $accessToken")
                .apply { params.forEach { (k, v) -> param(k, v) } }
        )
            .andExpect(status().isOk)

    private fun createBottle(productSearch: String, status: BottleStatus) {
        val products = mockMvc.perform(get("/api/products").param("search", productSearch))
            .andExpect(status().isOk)
            .andReturn()
        val productId = objectMapper.readTree(products.response.contentAsString)[0]["id"].asString()

        mockMvc.perform(
            post("/api/bottles")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(BottleCreateRequest(productId = productId, status = status)))
        ).andExpect(status().isCreated)
    }
}
