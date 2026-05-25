package com.ssutime.common.exception

sealed class SsutimeException(
    message: String,
) : RuntimeException(message)

class ResourceNotFoundException(
    message: String,
) : SsutimeException(message)

class InvalidRequestException(
    message: String,
) : SsutimeException(message)

class UnauthorizedException(
    message: String,
) : SsutimeException(message)
