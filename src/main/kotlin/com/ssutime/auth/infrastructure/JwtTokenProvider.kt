package com.ssutime.auth.infrastructure

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiry-minutes}") private val expiryMinutes: Long,
) {
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    fun generateToken(userId: Long): String =
        Jwts
            .builder()
            .subject(userId.toString())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiryMinutes * 60 * 1000))
            .signWith(key)
            .compact()

    fun getUserId(token: String): Long? =
        runCatching {
            Jwts
                .parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
                .subject
                .toLong()
        }.getOrElse { e ->
            when (e) {
                is JwtException, is IllegalArgumentException -> null
                else -> throw e
            }
        }
}
