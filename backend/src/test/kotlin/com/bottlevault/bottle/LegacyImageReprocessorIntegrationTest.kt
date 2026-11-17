// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.auth.User
import com.bottlevault.auth.UserRepository
import com.bottlevault.product.ProductRepository
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO

@SpringBootTest
class LegacyImageReprocessorIntegrationTest : AbstractPostgresIntegrationTest() {

    @Autowired lateinit var reprocessor: LegacyImageReprocessor
    @Autowired lateinit var bottles: BottleRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var products: ProductRepository
    @Value("\${app.uploads.dir}") lateinit var uploadsDir: String

    @Test
    fun `an old photo is re-encoded once, an unreadable one is left alone but not retried`() {
        val user = users.save(User(email = "legacy-${System.nanoTime()}@example.com", passwordHash = "x"))
        val product = products.findAll().first()
        val dir = Paths.get(uploadsDir, "bottles", user.id.toString()).also { Files.createDirectories(it) }

        val good = dir.resolve("old.png").also { ImageIO.write(BufferedImage(3000, 1000, BufferedImage.TYPE_INT_RGB), "png", it.toFile()) }
        val bad = dir.resolve("broken.png").also { Files.write(it, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)) }
        val b1 = bottles.save(Bottle(product = product, user = user, imagePath = "bottles/${user.id}/old.png"))
        val b2 = bottles.save(Bottle(product = product, user = user, imagePath = "bottles/${user.id}/broken.png"))

        reprocessor.reprocessLegacyImages()

        val r1 = bottles.findById(b1.id!!).get()
        assertTrue(r1.imagePath!!.endsWith(".jpg"))
        assertNotNull(r1.imageBytes)
        assertFalse(Files.exists(good), "the original is replaced")

        val r2 = bottles.findById(b2.id!!).get()
        assertEquals("bottles/${user.id}/broken.png", r2.imagePath)
        assertEquals(Files.size(bad), r2.imageBytes, "counted, and never retried")
    }
}
