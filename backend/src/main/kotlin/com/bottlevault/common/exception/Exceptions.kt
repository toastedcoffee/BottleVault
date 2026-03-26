package com.bottlevault.common.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class ResourceNotFoundException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.CONFLICT)
class ResourceAlreadyExistsException(message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.FORBIDDEN)
class AccessDeniedException(message: String) : RuntimeException(message)
