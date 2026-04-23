package com.bottlevault.bottle

import com.bottlevault.auth.UserRepository
import com.bottlevault.bottle.dto.*
import com.bottlevault.common.exception.AccessDeniedException
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.common.model.BottleStatus
import com.bottlevault.product.ProductRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BottleService(
    private val bottleRepository: BottleRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) {
    fun getBottles(
        userId: UUID,
        status: BottleStatus?,
        type: String?,
        search: String?,
        page: Int,
        size: Int,
        sort: String?
    ): PageResponse<BottleResponse> {
        val sortOrder = parseSortOrder(sort)
        val pageable = PageRequest.of(page, size, sortOrder)

        val bottlePage = when {
            !search.isNullOrBlank() -> bottleRepository.searchByUserIdAndQuery(userId, search, pageable)
            status != null -> bottleRepository.findByUserIdAndStatus(userId, status, pageable)
            !type.isNullOrBlank() -> bottleRepository.findByUserIdAndProductType(userId, type, pageable)
            else -> bottleRepository.findByUserId(userId, pageable)
        }

        return PageResponse(
            content = bottlePage.content.map { BottleResponse.from(it) },
            page = bottlePage.number,
            size = bottlePage.size,
            totalElements = bottlePage.totalElements,
            totalPages = bottlePage.totalPages
        )
    }

    fun getBottleById(id: UUID, userId: UUID): BottleResponse {
        val bottle = findUserBottle(id, userId)
        return BottleResponse.from(bottle)
    }

    @Transactional
    fun createBottle(request: BottleCreateRequest, userId: UUID): BottleResponse {
        val product = productRepository.findById(UUID.fromString(request.productId))
            .orElseThrow { ResourceNotFoundException("Product not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        val bottle = Bottle(
            product = product,
            user = user,
            status = request.status,
            percentageLeft = request.percentageLeft,
            purchaseDate = request.purchaseDate,
            purchaseLocation = request.purchaseLocation,
            purchaseCost = request.purchaseCost,
            notes = request.notes,
            rating = request.rating,
            storageLocation = request.storageLocation
        )
        return BottleResponse.from(bottleRepository.save(bottle))
    }

    @Transactional
    fun updateBottle(id: UUID, request: BottleUpdateRequest, userId: UUID): BottleResponse {
        val bottle = findUserBottle(id, userId)

        request.status?.let { bottle.status = it }
        request.percentageLeft?.let { bottle.percentageLeft = it }
        request.purchaseDate?.let { bottle.purchaseDate = it }
        request.purchaseLocation?.let { bottle.purchaseLocation = it }
        request.purchaseCost?.let { bottle.purchaseCost = it }
        request.notes?.let { bottle.notes = it }
        request.rating?.let { bottle.rating = it }
        request.storageLocation?.let { bottle.storageLocation = it }
        bottle.updatedAt = Instant.now()

        return BottleResponse.from(bottleRepository.save(bottle))
    }

    @Transactional
    fun updateBottleStatus(id: UUID, status: BottleStatus, userId: UUID): BottleResponse {
        val bottle = findUserBottle(id, userId)
        bottle.status = status
        bottle.updatedAt = Instant.now()
        return BottleResponse.from(bottleRepository.save(bottle))
    }

    @Transactional
    fun deleteBottle(id: UUID, userId: UUID) {
        val bottle = findUserBottle(id, userId)
        bottleRepository.delete(bottle)
    }

    private fun findUserBottle(bottleId: UUID, userId: UUID): Bottle =
        bottleRepository.findByIdAndUserId(bottleId, userId)
            ?: throw ResourceNotFoundException("Bottle not found")

    private fun parseSortOrder(sort: String?): Sort {
        if (sort.isNullOrBlank()) return Sort.by(Sort.Direction.DESC, "updatedAt")
        val parts = sort.split(",")
        val field = parts[0]
        val direction = if (parts.size > 1 && parts[1].equals("asc", ignoreCase = true))
            Sort.Direction.ASC else Sort.Direction.DESC
        return Sort.by(direction, field)
    }
}
