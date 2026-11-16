// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class ResourceNotFoundException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.CONFLICT)
class ResourceAlreadyExistsException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.FORBIDDEN)
class AccessDeniedException(message: String) : RuntimeException(message)

/** An error the client can act on, carrying the status and message it should see. */
open class ApiException(val status: HttpStatus, val error: String, message: String) : RuntimeException(message)

/** 429 with a Retry-After header. */
class RateLimitedException(message: String, val retryAfterSeconds: Long) :
    ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", message)
