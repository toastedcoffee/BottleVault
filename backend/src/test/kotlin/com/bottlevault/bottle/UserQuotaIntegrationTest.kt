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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.ImageIO

@SpringBootTest(properties = [
    "app.limits.max-bottles-per-user=3",
    "app.limits.max-image-bytes-per-user=50000",
])
@AutoConfigureMockMvc
class UserQuotaIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private fun token(): String {
        val r = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"quota-${System.nanoTime()}@example.com","password":"password123"}""")).andReturn()
        return objectMapper.readValue(r.response.contentAsString, AuthResponse::class.java).accessToken
    }

    private fun createBottle(token: String) = mockMvc.perform(
        post("/api/bottles").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
            .content("""{"productId":"${productId(token)}"}""")
    )

    private fun productId(token: String): String = objectMapper.readTree(
        mockMvc.perform(get("/api/products").param("search", "Old No. 7").header("Authorization", "Bearer $token"))
            .andReturn().response.contentAsString
    )[0]["id"].asString()

    /** Random pixels: JPEG cannot compress them, so size scales with area. */
    private fun noise(side: Int) = ByteArrayOutputStream().also { out ->
        val img = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        val rnd = Random(side.toLong())
        for (x in 0 until side) for (y in 0 until side) img.setRGB(x, y, rnd.nextInt(0xFFFFFF))
        ImageIO.write(img, "png", out)
    }.toByteArray()

    private fun upload(token: String, bottleId: String, bytes: ByteArray) = mockMvc.perform(
        multipart("/api/bottles/$bottleId/image").file(MockMultipartFile("file", "p.png", "image/png", bytes))
            .header("Authorization", "Bearer $token")
    )

    @Test
    fun `the bottle cap stops the next bottle with a clear message`() {
        val t = token()
        repeat(3) { createBottle(t).andExpect(status().isCreated) }
        createBottle(t).andExpect(status().`is`(422))
            .andExpect(jsonPath("$.error").value("Quota Exceeded"))
            .andExpect(jsonPath("$.message").value("You've reached the limit of 3 bottles."))
    }

    @Test
    fun `replacing a photo does not double-count it, a photo past the quota is refused`() {
        val t = token()
        val b1 = objectMapper.readTree(createBottle(t).andReturn().response.contentAsString)["id"].asString()
        val b2 = objectMapper.readTree(createBottle(t).andReturn().response.contentAsString)["id"].asString()
        repeat(5) { upload(t, b1, noise(64)).andExpect(status().isOk) }       // ~4 KB, replaced each time
        upload(t, b2, noise(1000)).andExpect(status().`is`(422))              // hundreds of KB > 50 KB
            .andExpect(jsonPath("$.error").value("Quota Exceeded"))
    }
}
