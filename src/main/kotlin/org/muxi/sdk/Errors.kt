package org.muxi.sdk

open class MuxiException(
    val errorCode: String,
    message: String,
    val statusCode: Int,
    val details: Map<String, Any?>? = null
) : Exception(if (errorCode.isNotEmpty()) "$errorCode: $message" else message)

class AuthenticationException(errorCode: String, message: String, statusCode: Int, details: Map<String, Any?>? = null) :
    MuxiException(errorCode, message, statusCode, details)

class AuthorizationException(errorCode: String, message: String, statusCode: Int, details: Map<String, Any?>? = null) :
    MuxiException(errorCode, message, statusCode, details)

class NotFoundException(errorCode: String, message: String, statusCode: Int, details: Map<String, Any?>? = null) :
    MuxiException(errorCode, message, statusCode, details)

class ConflictException(errorCode: String, message: String, statusCode: Int, details: Map<String, Any?>? = null) :
    MuxiException(errorCode, message, statusCode, details)

class ValidationException(errorCode: String, message: String, statusCode: Int, details: Map<String, Any?>? = null) :
    MuxiException(errorCode, message, statusCode, details)

class RateLimitException(
    message: String,
    statusCode: Int,
    val retryAfter: Int? = null,
    details: Map<String, Any?>? = null
) : MuxiException("RATE_LIMITED", message, statusCode, details)

class ServerException(errorCode: String, message: String, statusCode: Int, details: Map<String, Any?>? = null) :
    MuxiException(errorCode, message, statusCode, details)

class ConnectionException(message: String) : MuxiException("CONNECTION_ERROR", message, 0)

object ErrorMapper {
    fun map(status: Int, code: String?, message: String, details: Map<String, Any?>? = null, retryAfter: Int? = null): MuxiException {
        return when (status) {
            401 -> AuthenticationException(code ?: "UNAUTHORIZED", message, status, details)
            403 -> AuthorizationException(code ?: "FORBIDDEN", message, status, details)
            404 -> NotFoundException(code ?: "NOT_FOUND", message, status, details)
            409 -> ConflictException(code ?: "CONFLICT", message, status, details)
            422 -> ValidationException(code ?: "VALIDATION_ERROR", message, status, details)
            429 -> RateLimitException(message.ifEmpty { "Too Many Requests" }, status, retryAfter, details)
            in 500..599 -> ServerException(code ?: "SERVER_ERROR", message, status, details)
            else -> MuxiException(code ?: "ERROR", message, status, details)
        }
    }
}
