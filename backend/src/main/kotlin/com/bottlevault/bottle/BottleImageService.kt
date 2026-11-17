// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.bottle.dto.BottleResponse
import com.bottlevault.common.exception.ApiException
import com.bottlevault.common.exception.ResourceNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

@Service
class BottleImageService(
    private val bottleRepository: BottleRepository,
    private val imageProcessor: ImageProcessor,
    @Value("\${app.uploads.dir}") private val uploadsDirRaw: String
) {
    private val uploadsDir: Path by lazy {
        Paths.get(uploadsDirRaw).toAbsolutePath().normalize().also { Files.createDirectories(it) }
    }

    @Transactional
    fun uploadImage(bottleId: UUID, userId: UUID, file: MultipartFile): BottleResponse {
        if (file.isEmpty) throw IllegalArgumentException("File is empty")
        if (file.size > MAX_BYTES) {
            throw ApiException(HttpStatus.CONTENT_TOO_LARGE, "Payload Too Large", "Photos must be under ${MAX_BYTES / 1024 / 1024} MB.")
        }
        // Ownership first: a stranger gets the same 404 whatever they upload, and
        // no image work is done on their behalf.
        val bottle = bottleRepository.findByIdAndUserId(bottleId, userId)
            ?: throw ResourceNotFoundException("Bottle not found")

        // The file's own bytes decide its type; the client's Content-Type and
        // file name are ignored. What is stored is always a fresh JPEG.
        val processed = imageProcessor.process(file.bytes)

        val userDir = uploadsDir.resolve("bottles").resolve(userId.toString())
        val filename = "${UUID.randomUUID()}.jpg"
        try {
            Files.createDirectories(userDir)
            Files.write(userDir.resolve(filename), processed.jpeg, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        } catch (e: IOException) {
            throw if (isStorageFull(e)) {
                ApiException(HttpStatus.INSUFFICIENT_STORAGE, "Insufficient Storage", "Storage is full right now. Please try again later.")
            } else {
                e
            }
        }

        bottle.imagePath?.let { deleteFileIfExists(it) }
        bottle.imagePath = "bottles/$userId/$filename"
        bottle.imageBytes = processed.jpeg.size.toLong()
        bottle.updatedAt = Instant.now()
        return BottleResponse.from(bottleRepository.save(bottle))
    }

    @Transactional
    fun deleteImage(bottleId: UUID, userId: UUID): BottleResponse {
        val bottle = bottleRepository.findByIdAndUserId(bottleId, userId)
            ?: throw ResourceNotFoundException("Bottle not found")
        bottle.imagePath?.let { deleteFileIfExists(it) }
        bottle.imagePath = null
        bottle.imageBytes = null
        bottle.updatedAt = Instant.now()
        return BottleResponse.from(bottleRepository.save(bottle))
    }

    fun getImage(bottleId: UUID, userId: UUID): ImageDownload {
        val bottle = bottleRepository.findByIdAndUserId(bottleId, userId)
            ?: throw ResourceNotFoundException("Bottle not found")
        val rel = bottle.imagePath ?: throw ResourceNotFoundException("Bottle has no image")
        val path = resolveSafe(rel) ?: throw ResourceNotFoundException("Bottle has no image")
        if (!Files.exists(path)) throw ResourceNotFoundException("Bottle has no image")
        val mediaType = when (path.toString().substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> MediaType.IMAGE_JPEG
            "png" -> MediaType.IMAGE_PNG
            "webp" -> MediaType.parseMediaType("image/webp")
            else -> MediaType.APPLICATION_OCTET_STREAM
        }
        return ImageDownload(FileSystemResource(path), mediaType, Files.size(path))
    }

    /**
     * Called from BottleService#deleteBottle so the file on disk is removed alongside the row.
     */
    fun deleteFileForBottle(bottle: Bottle) {
        bottle.imagePath?.let { deleteFileIfExists(it) }
    }

    private fun deleteFileIfExists(relative: String) {
        val path = resolveSafe(relative) ?: return
        runCatching { Files.deleteIfExists(path) }
    }

    private fun resolveSafe(relative: String): Path? {
        val resolved = uploadsDir.resolve(relative).normalize()
        // Defence-in-depth: refuse paths that escape the uploads root.
        return if (resolved.startsWith(uploadsDir)) resolved else null
    }

    /** ENOSPC, or a ZFS dataset quota (EDQUOT): the disk cannot take this photo. */
    private fun isStorageFull(e: IOException): Boolean {
        val message = (e as? FileSystemException)?.reason ?: e.message ?: return false
        return message.contains("No space left", ignoreCase = true) || message.contains("quota", ignoreCase = true)
    }

    data class ImageDownload(val resource: Resource, val mediaType: MediaType, val sizeBytes: Long)

    companion object {
        private const val MAX_BYTES = 5L * 1024 * 1024
    }
}
