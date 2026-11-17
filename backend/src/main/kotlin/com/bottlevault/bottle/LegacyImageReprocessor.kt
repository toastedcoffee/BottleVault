// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.common.exception.RateLimitedException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

/**
 * Photos uploaded before re-encoding existed still carry their EXIF (GPS
 * included). Once, at startup, re-encode them: every bottle with a photo but no
 * recorded `image_bytes`.
 *
 * Idempotent, and it never loses a photo: the new JPEG is written and the row
 * updated before the original is deleted. A photo that cannot be processed
 * keeps its original file and gets its current size recorded, so it counts
 * toward the owner's quota and is never retried. A photo skipped only because
 * the processor was busy is left as it was and tried again on the next start.
 */
@Component
class LegacyImageReprocessor(
    private val bottles: BottleRepository,
    private val imageProcessor: ImageProcessor,
    private val transactions: TransactionTemplate,
    @Value("\${app.uploads.dir}") uploadsDirRaw: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val uploadsDir = Paths.get(uploadsDirRaw).toAbsolutePath().normalize()

    /**
     * In the background: listeners for ApplicationReadyEvent run before Spring
     * reports readiness, so doing this inline would hold the API "not ready"
     * (and fail its healthcheck) for as long as the backlog takes.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun startInBackground() {
        Thread({
            runCatching { reprocessLegacyImages() }
                .onFailure { log.warn("Reprocessing photos uploaded before photo processing stopped: {}", it.toString()) }
        }, "legacy-photo-reprocessor").apply { isDaemon = true }.start()
    }

    fun reprocessLegacyImages() {
        val ids = bottles.findByImagePathIsNotNullAndImageBytesIsNull().mapNotNull { it.id }
        if (ids.isEmpty()) return
        var done = 0
        for (id in ids) {
            val ok = runCatching { reprocess(id) }
                .onFailure { log.warn("Could not reprocess the photo of bottle {}: {}", id, it.toString()) }
                .getOrDefault(false)
            if (ok) done++
        }
        log.info("Re-encoded {} of {} photos uploaded before photo processing", done, ids.size)
    }

    private fun reprocess(id: UUID): Boolean {
        // The row is read, and later updated, in short transactions; the decode
        // in between holds no database connection.
        val relative = transactions.execute {
            bottles.findById(id).orElse(null)?.takeIf { it.imageBytes == null }?.imagePath
        } ?: return false
        val original = uploadsDir.resolve(relative).normalize()
        if (!original.startsWith(uploadsDir) || !Files.isRegularFile(original)) {
            record(id, relative, newPath = null, bytes = 0)
            return false
        }

        val processed = try {
            imageProcessor.process(Files.readAllBytes(original))
        } catch (busy: RateLimitedException) {
            throw busy // not the photo's fault: leave it for the next start
        } catch (e: Exception) {
            log.warn("Photo of bottle {} could not be re-encoded and is kept as it is: {}", id, e.message)
            record(id, relative, newPath = null, bytes = Files.size(original))
            return false
        }

        val target = original.resolveSibling("${UUID.randomUUID()}.jpg")
        Files.write(target, processed.jpeg)
        val newRelative = uploadsDir.relativize(target).toString().replace('\\', '/')
        if (!record(id, relative, newRelative, processed.jpeg.size.toLong())) {
            Files.deleteIfExists(target) // the row changed meanwhile (a new upload or a deletion)
            return false
        }
        Files.deleteIfExists(original)
        return true
    }

    /** Records the outcome, but only if the row still points at [expectedPath] unprocessed. */
    private fun record(id: UUID, expectedPath: String, newPath: String?, bytes: Long): Boolean =
        transactions.execute {
            val bottle = bottles.findById(id).orElse(null)
            if (bottle == null || bottle.imagePath != expectedPath || bottle.imageBytes != null) {
                false
            } else {
                if (newPath != null) bottle.imagePath = newPath
                bottle.imageBytes = bytes
                bottles.save(bottle)
                true
            }
        } ?: false
}
