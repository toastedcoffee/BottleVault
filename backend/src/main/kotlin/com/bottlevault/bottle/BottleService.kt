// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.bottle

import com.bottlevault.auth.UserRepository
import com.bottlevault.bottle.dto.*
import com.bottlevault.common.exception.AccessDeniedException
import com.bottlevault.common.exception.ResourceNotFoundException
import com.bottlevault.common.model.AlcoholType
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
    private val userRepository: UserRepository,
    private val bottleImageService: BottleImageService
) {
    fun getBottles(
        userId: UUID,
        status: BottleStatus?,
        type: AlcoholType?,
        search: String?,
        page: Int,
        size: Int
    ): PageResponse<BottleResponse> {
        val pageable = PageRequest.of(page, size, DEFAULT_SORT)

        val bottlePage = bottleRepository.findFiltered(
            userId = userId,
            status = status,
            type = type,
            // A blank search box is no filter: the empty needle matches every row.
            search = search?.trim().orEmpty(),
            pageable = pageable
        )

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
        bottleImageService.deleteFileForBottle(bottle)
        bottleRepository.delete(bottle)
    }

    private fun findUserBottle(bottleId: UUID, userId: UUID): Bottle =
        bottleRepository.findByIdAndUserId(bottleId, userId)
            ?: throw ResourceNotFoundException("Bottle not found")

    private companion object {
        // The only order the app has ever used. There is deliberately no
        // client-chosen sort: the parameter never had a caller, and the free-form
        // value it took went straight into the JPQL ORDER BY. id breaks
        // updatedAt ties, so the order (and paging) is deterministic.
        val DEFAULT_SORT: Sort = Sort.by(Sort.Direction.DESC, "updatedAt", "id")
    }
}
