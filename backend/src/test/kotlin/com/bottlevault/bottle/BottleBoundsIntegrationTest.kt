// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
class BottleBoundsIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    private lateinit var token: String
    private lateinit var productId: String

    @BeforeEach
    fun setUp() {
        val result = mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"bounds-${System.nanoTime()}@example.com","password":"password123"}""")
        ).andExpect(status().isCreated).andReturn()
        token = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).accessToken
        val products = mockMvc.perform(
            get("/api/products").param("search", "Old No. 7").header("Authorization", "Bearer $token")
        ).andExpect(status().isOk).andReturn()
        productId = objectMapper.readTree(products.response.contentAsString)[0]["id"].asString()
    }

    private fun create(extra: String) = mockMvc.perform(
        post("/api/bottles").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
            .content("""{"productId":"$productId"$extra}""")
    )

    private fun list(vararg params: Pair<String, String>) = mockMvc.perform(
        get("/api/bottles").header("Authorization", "Bearer $token")
            .apply { params.forEach { (k, v) -> param(k, v) } }
    )

    @Test
    fun `oversize text is a 400 that names the field, not a database error`() {
        create(""","notes":"${"n".repeat(5001)}"""").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("notes")))
        create(""","purchaseLocation":"${"p".repeat(256)}"""").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("purchaseLocation")))
        create(""","storageLocation":"${"s".repeat(256)}"""").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("storageLocation")))
    }

    @Test
    fun `an over-long product id is a 400, not a lookup`() {
        create("").andExpect(status().isCreated) // the fixture's id itself is accepted
        mockMvc.perform(
            post("/api/bottles").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"productId":"${"x".repeat(37)}"}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("productId")))
    }

    @Test
    fun `money is non-negative and fits the column`() {
        create(""","purchaseCost":-1""").andExpect(status().isBadRequest)
        create(""","purchaseCost":123456789.00""").andExpect(status().isBadRequest)
        create(""","purchaseCost":12.345""").andExpect(status().isBadRequest)
        create(""","purchaseCost":49.99,"notes":"${"n".repeat(5000)}","purchaseLocation":"${"p".repeat(255)}"""")
            .andExpect(status().isCreated)
    }

    @Test
    fun `updates carry the same bounds as creation`() {
        val created = create("").andExpect(status().isCreated).andReturn()
        val id = objectMapper.readTree(created.response.contentAsString)["id"].asString()
        fun update(body: String) = mockMvc.perform(
            put("/api/bottles/{id}", id).header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON).content(body)
        )
        update("""{"notes":"${"n".repeat(5001)}"}""").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("notes")))
        update("""{"storageLocation":"${"s".repeat(256)}"}""").andExpect(status().isBadRequest)
        update("""{"purchaseCost":-0.01}""").andExpect(status().isBadRequest)
        update("""{"purchaseCost":12.345}""").andExpect(status().isBadRequest)
        update("""{"purchaseCost":99999999.99,"notes":"ok"}""").andExpect(status().isOk)
    }

    @Test
    fun `page size is clamped and negative pages are treated as the first`() {
        list("size" to "1000").andExpect(status().isOk).andExpect(jsonPath("$.size").value(100))
        list("size" to "0").andExpect(status().isOk).andExpect(jsonPath("$.size").value(1))
        list("size" to "-5").andExpect(status().isOk).andExpect(jsonPath("$.size").value(1))
        list("page" to "-3").andExpect(status().isOk).andExpect(jsonPath("$.page").value(0))
        // page * size is an Int offset in Spring Data; past Int.MAX_VALUE it overflows
        // and the query fails as a 500.
        list("page" to "2147483647", "size" to "100").andExpect(status().isOk)
        list("page" to "2147483647", "size" to "1").andExpect(status().isOk)
    }
}
