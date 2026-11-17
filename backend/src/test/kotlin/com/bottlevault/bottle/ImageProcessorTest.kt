// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.common.exception.ApiException
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.GpsDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import javax.imageio.ImageIO

class ImageProcessorTest {
    private val processor = ImageProcessor(
        maxPixels = 50_000_000, maxSide = 12_000, outputMaxSide = 2048,
        jpegQuality = 0.85f, maxConcurrent = 2, slotWait = Duration.ofSeconds(1),
    )

    private fun image(w: Int, h: Int, type: Int = BufferedImage.TYPE_INT_RGB, fill: Color = Color.RED) =
        BufferedImage(w, h, type).apply { createGraphics().apply { color = fill; fillRect(0, 0, w, h); dispose() } }

    private fun encode(img: BufferedImage, format: String) =
        ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()

    /** A JPEG carrying an EXIF APP1 segment with GPS and an orientation tag. */
    private fun jpegWithExif(w: Int, h: Int, orientation: Int): ByteArray {
        val plain = encode(image(w, h), "jpg")
        val exif = ExifFixture.app1(orientation = orientation, withGps = true)
        // ImageIO writes SOI (FF D8) then the JFIF APP0 segment (FF E0 + length).
        // Insert APP1 after APP0, where cameras put it, so strict readers accept it.
        check(plain[2] == 0xFF.toByte() && plain[3] == 0xE0.toByte()) { "expected JFIF APP0 after SOI" }
        val afterApp0 = 4 + (((plain[4].toInt() and 0xff) shl 8) or (plain[5].toInt() and 0xff))
        return plain.copyOfRange(0, afterApp0) + exif + plain.copyOfRange(afterApp0, plain.size)
    }

    private fun decoded(bytes: ByteArray) = ImageIO.read(ByteArrayInputStream(bytes))

    @Test
    fun `output is a JPEG no larger than 2048 on the long edge`() {
        val out = processor.process(encode(image(4000, 3000), "png"))
        assertTrue(out.jpeg[0] == 0xFF.toByte() && out.jpeg[1] == 0xD8.toByte(), "JPEG magic")
        assertEquals(2048, maxOf(out.width, out.height))
        assertEquals(2048, decoded(out.jpeg).width)
    }

    @Test
    fun `small photos are not enlarged`() {
        val out = processor.process(encode(image(640, 480), "jpg"))
        assertEquals(640 to 480, out.width to out.height)
    }

    @Test
    fun `EXIF, including GPS, is gone from the stored photo`() {
        val out = processor.process(jpegWithExif(800, 600, orientation = 1))
        val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(out.jpeg))
        assertNull(metadata.getFirstDirectoryOfType(GpsDirectory::class.java))
        assertNull(metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java))
    }

    @Test
    fun `a sideways phone photo is stored upright`() {
        // Orientation 6 = rotate 90° clockwise to display.
        val out = processor.process(jpegWithExif(800, 600, orientation = 6))
        assertEquals(600 to 800, out.width to out.height)
    }

    /**
     * Every EXIF orientation, 1 to 8. The upright picture has four coloured
     * quadrants; it is stored the way a camera would store it for each
     * orientation, and the processed photo must come back upright.
     */
    @Test
    fun `all eight EXIF orientations come back upright`() {
        val upright = BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply {
                color = Color.RED; fillRect(0, 0, 40, 20)
                color = Color.GREEN; fillRect(40, 0, 40, 20)
                color = Color.BLUE; fillRect(0, 20, 40, 20)
                color = Color.WHITE; fillRect(40, 20, 40, 20)
                dispose()
            }
        }
        for (orientation in 1..8) {
            val swap = orientation >= 5
            val sw = if (swap) upright.height else upright.width
            val sh = if (swap) upright.width else upright.height
            // Stored pixel (x, y) is displayed at display(x, y) once the tag is applied.
            fun display(x: Int, y: Int): Pair<Int, Int> = when (orientation) {
                2 -> (sw - 1 - x) to y
                3 -> (sw - 1 - x) to (sh - 1 - y)
                4 -> x to (sh - 1 - y)
                5 -> y to x
                6 -> (sh - 1 - y) to x
                7 -> (sh - 1 - y) to (sw - 1 - x)
                8 -> y to (sw - 1 - x)
                else -> x to y
            }
            val stored = BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until sw) for (y in 0 until sh) {
                val (dx, dy) = display(x, y)
                stored.setRGB(x, y, upright.getRGB(dx, dy))
            }
            val plain = encode(stored, "jpg")
            val afterApp0 = 4 + (((plain[4].toInt() and 0xff) shl 8) or (plain[5].toInt() and 0xff))
            val tagged = plain.copyOfRange(0, afterApp0) + ExifFixture.app1(orientation, withGps = false) +
                plain.copyOfRange(afterApp0, plain.size)

            val out = processor.process(tagged)
            assertEquals(80 to 40, out.width to out.height, "orientation $orientation size")
            val img = decoded(out.jpeg)
            fun near(c: Color, x: Int, y: Int) = Color(img.getRGB(x, y)).let {
                Math.abs(it.red - c.red) < 60 && Math.abs(it.green - c.green) < 60 && Math.abs(it.blue - c.blue) < 60
            }
            assertTrue(near(Color.RED, 20, 10), "orientation $orientation top-left")
            assertTrue(near(Color.GREEN, 60, 10), "orientation $orientation top-right")
            assertTrue(near(Color.BLUE, 20, 30), "orientation $orientation bottom-left")
            assertTrue(near(Color.WHITE, 60, 30), "orientation $orientation bottom-right")
        }
    }

    @Test
    fun `transparency is flattened onto white`() {
        val clear = image(10, 10, BufferedImage.TYPE_INT_ARGB, Color(0, 0, 0, 0))
        val out = decoded(processor.process(encode(clear, "png")).jpeg)
        val rgb = Color(out.getRGB(5, 5))
        assertTrue(rgb.red > 240 && rgb.green > 240 && rgb.blue > 240, "expected white, got $rgb")
    }

    @Test
    fun `the file's bytes decide its type, not its name or claimed type`() {
        val html = "<html><script>alert(1)</script></html>".toByteArray()
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(ApiException::class.java) { processor.process(html) }.status)
    }

    @Test
    fun `a decompression bomb is refused from its header, before any pixels are decoded`() {
        // A 20000 x 20000 PNG compresses to a few hundred KB but would need 1.6 GB decoded.
        val header = PngFixture.headerOnly(width = 20_000, height = 20_000)
        val before = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val ex = assertThrows(ApiException::class.java) { processor.process(header) }
        assertEquals(422, ex.status.value())
        val after = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        assertTrue(after - before < 200L * 1024 * 1024, "no large allocation")
    }

    @Test
    fun `garbage with a valid signature is unreadable, not a crash`() {
        val fake = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
        assertEquals(422, assertThrows(ApiException::class.java) { processor.process(fake) }.status.value())
    }

    @Test
    fun `WebP photos are accepted`() {
        val out = processor.process(WebpFixture.lossless2x2())
        assertEquals(2 to 2, out.width to out.height)
    }

    @Test
    fun `CMYK JPEGs, which the JDK's own reader rejects, are converted`() {
        val out = processor.process(WebpFixture.cmykJpeg8x8())
        assertEquals(8 to 8, out.width to out.height)
        val pixel = Color(decoded(out.jpeg).getRGB(4, 4))
        assertTrue(pixel.red > 200 && pixel.green < 80 && pixel.blue < 80, "expected red, got $pixel")
    }
}
