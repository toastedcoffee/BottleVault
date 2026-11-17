// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@SpringBootTest(properties = ["app.rate-limit.user.uploads-per-hour=2"])
@AutoConfigureMockMvc
class UploadRateLimitIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private fun token(): String {
        val r = mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"uploads-${System.nanoTime()}@example.com","password":"password123"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readValue(r.response.contentAsString, AuthResponse::class.java).accessToken
    }

    private fun createBottle(token: String): String {
        val productId = objectMapper.readTree(
            mockMvc.perform(get("/api/products").param("search", "Old No. 7").header("Authorization", "Bearer $token"))
                .andReturn().response.contentAsString
        )[0]["id"].asString()
        val created = mockMvc.perform(
            post("/api/bottles").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"productId":"$productId"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(created.response.contentAsString)["id"].asString()
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    private fun upload(token: String, bottleId: String) = mockMvc.perform(
        multipart("/api/bottles/$bottleId/image").file(MockMultipartFile("file", "p.png", "image/png", png()))
            .header("Authorization", "Bearer $token")
    )

    @Test
    fun `more than the hourly upload budget is refused with a retry time`() {
        val t = token()
        val b = createBottle(t)
        repeat(2) { upload(t, b).andExpect(status().isOk) }
        upload(t, b).andExpect(status().isTooManyRequests).andExpect(header().exists("Retry-After"))
    }

    @Test
    fun `the budget is per user`() {
        val first = token()
        val firstBottle = createBottle(first)
        repeat(2) { upload(first, firstBottle).andExpect(status().isOk) }
        upload(first, firstBottle).andExpect(status().isTooManyRequests)

        val second = token()
        upload(second, createBottle(second)).andExpect(status().isOk)
    }

    @Test
    fun `a stranger's upload neither spends the owner's budget nor reveals the bottle`() {
        val owner = token()
        val stranger = token()
        val b = createBottle(owner)
        repeat(3) { upload(stranger, b).andExpect(status().isNotFound) }
        repeat(2) { upload(owner, b).andExpect(status().isOk) }
    }
}
