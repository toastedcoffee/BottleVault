package com.bottlevault.bottle

import com.bottlevault.bottle.dto.*
import com.bottlevault.common.model.BottleStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/bottles")
class BottleController(private val bottleService: BottleService) {

    @GetMapping
    fun getBottles(
        @RequestParam status: BottleStatus?,
        @RequestParam type: String?,
        @RequestParam search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam sort: String?,
        auth: Authentication
    ): PageResponse<BottleResponse> =
        bottleService.getBottles(userId(auth), status, type, search, page, size, sort)

    @GetMapping("/{id}")
    fun getBottleById(@PathVariable id: UUID, auth: Authentication): BottleResponse =
        bottleService.getBottleById(id, userId(auth))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBottle(
        @Valid @RequestBody request: BottleCreateRequest,
        auth: Authentication
    ): BottleResponse =
        bottleService.createBottle(request, userId(auth))

    @PutMapping("/{id}")
    fun updateBottle(
        @PathVariable id: UUID,
        @Valid @RequestBody request: BottleUpdateRequest,
        auth: Authentication
    ): BottleResponse =
        bottleService.updateBottle(id, request, userId(auth))

    @PatchMapping("/{id}/status")
    fun updateBottleStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: StatusUpdateRequest,
        auth: Authentication
    ): BottleResponse =
        bottleService.updateBottleStatus(id, request.status, userId(auth))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBottle(@PathVariable id: UUID, auth: Authentication) =
        bottleService.deleteBottle(id, userId(auth))

    private fun userId(auth: Authentication): UUID =
        UUID.fromString(auth.principal as String)
}
