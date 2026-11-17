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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Invariant: a bottle photo is only ever served to the bottle's owner. */
@SpringBootTest
@AutoConfigureMockMvc
class PhotoPrivacyIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private fun token(tag: String): String {
        val result = mockMvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$tag-${System.nanoTime()}@example.com","password":"password123"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java).accessToken
    }

    private fun bottle(token: String): String {
        val products = mockMvc.perform(get("/api/products").param("search", "Old No. 7").header("Authorization", "Bearer $token"))
            .andReturn().response.contentAsString
        val productId = objectMapper.readTree(products)[0]["id"].asString()
        val created = mockMvc.perform(
            post("/api/bottles").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"productId":"$productId"}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return objectMapper.readTree(created)["id"].asString()
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    @Test
    fun `only the owner can see, replace or delete a bottle's photo`() {
        val owner = token("owner")
        val stranger = token("stranger")
        val id = bottle(owner)
        mockMvc.perform(multipart("/api/bottles/$id/image").file(MockMultipartFile("file", "p.png", "image/png", png()))
            .header("Authorization", "Bearer $owner")).andExpect(status().isOk)

        mockMvc.perform(get("/api/bottles/$id/image").header("Authorization", "Bearer $owner"))
            .andExpect(status().isOk).andExpect(header().string("Content-Type", "image/jpeg"))

        // 404, not 403: a stranger cannot even learn that the bottle exists.
        mockMvc.perform(get("/api/bottles/$id/image").header("Authorization", "Bearer $stranger")).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/bottles/$id/image").header("Authorization", "Bearer $stranger")).andExpect(status().isNotFound)
        mockMvc.perform(multipart("/api/bottles/$id/image").file(MockMultipartFile("file", "p.png", "image/png", png()))
            .header("Authorization", "Bearer $stranger")).andExpect(status().isNotFound)
    }
}
