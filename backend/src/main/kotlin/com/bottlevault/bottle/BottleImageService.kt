package com.bottlevault.bottle

import com.bottlevault.bottle.dto.BottleResponse
import com.bottlevault.common.exception.ResourceNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

@Service
class BottleImageService(
    private val bottleRepository: BottleRepository,
    @Value("\${app.uploads.dir}") private val uploadsDirRaw: String
) {
    private val uploadsDir: Path by lazy {
        Paths.get(uploadsDirRaw).toAbsolutePath().normalize().also { Files.createDirectories(it) }
    }

    @Transactional
    fun uploadImage(bottleId: UUID, userId: UUID, file: MultipartFile): BottleResponse {
        if (file.isEmpty) throw IllegalArgumentException("File is empty")
        val contentType = file.contentType ?: throw IllegalArgumentException("Missing content type")
        val ext = ALLOWED_TYPES[contentType]
            ?: throw IllegalArgumentException("Unsupported image type: $contentType (allowed: image/jpeg, image/png, image/webp)")
        if (file.size > MAX_BYTES) {
            throw IllegalArgumentException("File exceeds maximum size of ${MAX_BYTES / 1024 / 1024} MB")
        }

        val bottle = bottleRepository.findByIdAndUserId(bottleId, userId)
            ?: throw ResourceNotFoundException("Bottle not found")

        val userDir = uploadsDir.resolve("bottles").resolve(userId.toString())
        Files.createDirectories(userDir)
        val filename = "${UUID.randomUUID()}.$ext"
        val target = userDir.resolve(filename)
        file.inputStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }

        bottle.imagePath?.let { deleteFileIfExists(it) }
        val relative = "bottles/${userId}/$filename"
        bottle.imagePath = relative
        bottle.updatedAt = Instant.now()
        return BottleResponse.from(bottleRepository.save(bottle))
    }

    @Transactional
    fun deleteImage(bottleId: UUID, userId: UUID): BottleResponse {
        val bottle = bottleRepository.findByIdAndUserId(bottleId, userId)
            ?: throw ResourceNotFoundException("Bottle not found")
        bottle.imagePath?.let { deleteFileIfExists(it) }
        bottle.imagePath = null
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

    data class ImageDownload(val resource: Resource, val mediaType: MediaType, val sizeBytes: Long)

    companion object {
        private const val MAX_BYTES = 5L * 1024 * 1024
        private val ALLOWED_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp"
        )
    }
}
