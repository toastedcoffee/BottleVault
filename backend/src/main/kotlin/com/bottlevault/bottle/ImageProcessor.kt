// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.common.exception.ApiException
import com.bottlevault.common.exception.RateLimitedException
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class ProcessedImage(val jpeg: ByteArray, val width: Int, val height: Int)

/**
 * Every uploaded photo becomes a fresh JPEG: at most [outputMaxSide] px on the
 * long edge, upright, sRGB, no transparency, and no metadata at all — GPS
 * included. Re-encoding also destroys anything smuggled inside the original.
 *
 * Memory is bounded three ways: dimensions are read from the header and huge
 * images refused before any pixel is decoded; decoding subsamples so the raster
 * is at most about twice the output size; and only [maxConcurrent] photos are
 * processed at once.
 */
@Component
class ImageProcessor(
    @Value("\${app.images.max-pixels:50000000}") private val maxPixels: Long,
    @Value("\${app.images.max-side:12000}") private val maxSide: Int,
    @Value("\${app.images.output-max-side:2048}") private val outputMaxSide: Int,
    @Value("\${app.images.jpeg-quality:0.85}") private val jpegQuality: Float,
    @Value("\${app.images.max-concurrent:2}") maxConcurrent: Int,
    @Value("\${app.images.slot-wait:10s}") private val slotWait: Duration,
    // The WebP reader decodes at full size whatever subsampling is asked for
    // (measured: a 24 MP WebP needs ~80 MB while decoding), so WebP gets a
    // tighter pixel cap than JPEG and PNG, whose readers really subsample.
    @Value("\${app.images.max-webp-pixels:16777216}") private val maxWebpPixels: Long = 16_777_216,
) {
    private val slots = Semaphore(maxConcurrent)

    init {
        // In a Spring Boot fat jar the plugin jars are nested; make ImageIO look.
        ImageIO.scanForPlugins()
    }

    fun process(input: ByteArray): ProcessedImage {
        if (!isJpeg(input) && !isPng(input) && !isWebp(input)) {
            throw ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", "Only JPEG, PNG and WebP photos are supported.")
        }
        if (!slots.tryAcquire(slotWait.toMillis(), TimeUnit.MILLISECONDS)) {
            throw RateLimitedException("Too many photos are being processed right now. Try again in a moment.", 10)
        }
        try {
            val decoded = decodeBounded(input)
            val upright = applyOrientation(decoded, orientationOf(input))
            val scaled = scaleToFit(upright)
            return ProcessedImage(encodeJpeg(scaled), scaled.width, scaled.height)
        } finally {
            slots.release()
        }
    }

    private fun decodeBounded(input: ByteArray): BufferedImage {
        val unreadable = ApiException(HttpStatus.valueOf(422), "Unprocessable Entity", "We couldn't read that photo. Try a JPEG, PNG or WebP.")
        ImageIO.createImageInputStream(ByteArrayInputStream(input)).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: throw unreadable
            try {
                reader.setInput(stream, true, true)
                val w = runCatching { reader.getWidth(0) }.getOrElse { throw unreadable }
                val h = runCatching { reader.getHeight(0) }.getOrElse { throw unreadable }
                val pixelCap = if (isWebp(input)) minOf(maxPixels, maxWebpPixels) else maxPixels
                if (w <= 0 || h <= 0 || w > maxSide || h > maxSide || w.toLong() * h > pixelCap) {
                    throw ApiException(
                        HttpStatus.valueOf(422), "Unprocessable Entity",
                        "That photo is too large (${w}x$h). Photos up to ${pixelCap / 1_000_000} megapixels are supported.",
                    )
                }
                val factor = max(1, ceil(max(w, h) / (2.0 * outputMaxSide)).toInt())
                val param = reader.defaultReadParam.apply { setSourceSubsampling(factor, factor, 0, 0) }
                return runCatching { reader.read(0, param) }.getOrElse { throw unreadable }
            } finally {
                reader.dispose()
            }
        }
    }

    private fun orientationOf(input: ByteArray): Int = runCatching {
        ImageMetadataReader.readMetadata(ByteArrayInputStream(input))
            .getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            ?.takeIf { it.containsTag(ExifIFD0Directory.TAG_ORIENTATION) }
            ?.getInt(ExifIFD0Directory.TAG_ORIENTATION)
    }.getOrNull() ?: 1

    /** EXIF orientations 1–8; anything else is treated as upright. */
    private fun applyOrientation(src: BufferedImage, orientation: Int): BufferedImage {
        if (orientation !in 2..8) return src
        val w = src.width.toDouble()
        val h = src.height.toDouble()
        val swap = orientation >= 5
        val t = AffineTransform()
        when (orientation) {
            2 -> { t.translate(w, 0.0); t.scale(-1.0, 1.0) }
            3 -> { t.translate(w, h); t.rotate(Math.PI) }
            4 -> { t.translate(0.0, h); t.scale(1.0, -1.0) }
            5 -> { t.rotate(Math.PI / 2); t.scale(1.0, -1.0) }
            6 -> { t.translate(h, 0.0); t.rotate(Math.PI / 2) }
            7 -> { t.scale(-1.0, 1.0); t.translate(-h, 0.0); t.translate(0.0, w); t.rotate(3 * Math.PI / 2) }
            8 -> { t.translate(0.0, w); t.rotate(3 * Math.PI / 2) }
        }
        val out = BufferedImage(if (swap) src.height else src.width, if (swap) src.width else src.height, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, out.width, out.height)
            drawImage(src, t, null)
            dispose()
        }
        return out
    }

    /** Scales down to fit, flattening any transparency onto white; never enlarges. */
    private fun scaleToFit(src: BufferedImage): BufferedImage {
        val longest = max(src.width, src.height)
        val ratio = if (longest > outputMaxSide) outputMaxSide.toDouble() / longest else 1.0
        val w = max(1, (src.width * ratio).roundToInt())
        val h = max(1, (src.height * ratio).roundToInt())
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            color = Color.WHITE
            fillRect(0, 0, w, h)
            drawImage(src, 0, 0, w, h, null)
            dispose()
        }
        return out
    }

    private fun encodeJpeg(img: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = jpegQuality
                }
                // null metadata: a plain JFIF header, so nothing from the original survives.
                writer.write(null, IIOImage(img, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }

    private fun isJpeg(b: ByteArray) = b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()
    private fun isPng(b: ByteArray) = b.size > 8 &&
        b.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    private fun isWebp(b: ByteArray) = b.size > 12 &&
        String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP"
}
