package com.duitku.app.domain.usecase.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Hash PIN dengan PBKDF2-HMAC-SHA256 + salt acak. PIN tidak pernah disimpan apa adanya. */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    data class Hashed(val hash: String, val salt: String)

    fun isValidFormat(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    /** PIN yang terlalu mudah ditebak (0000, 1234, 1111, ...). */
    fun isWeak(pin: String): Boolean {
        if (pin.isEmpty()) return true
        if (pin.all { it == pin[0] }) return true
        val ascending = pin.zipWithNext().all { (a, b) -> b - a == 1 }
        val descending = pin.zipWithNext().all { (a, b) -> a - b == 1 }
        return ascending || descending
    }

    fun hash(pin: String, saltHex: String = randomSaltHex()): Hashed =
        Hashed(derive(pin, saltHex), saltHex)

    fun verify(pin: String, hashHex: String, saltHex: String): Boolean {
        if (hashHex.isEmpty() || saltHex.isEmpty()) return false
        // Perbandingan waktu-konstan agar tidak membocorkan panjang kecocokan.
        return MessageDigest.isEqual(derive(pin, saltHex).toByteArray(), hashHex.toByteArray())
    }

    private fun derive(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.hexToBytes(), ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomSaltHex(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/** Pembatasan percobaan PIN: 5 salah pertama gratis, lalu jeda 30 dtk, 1 mnt, 5 mnt, 15 mnt, 1 jam. */
object PinAttemptPolicy {
    const val FREE_ATTEMPTS = 4

    fun lockoutSeconds(consecutiveFailures: Int): Long = when {
        consecutiveFailures <= FREE_ATTEMPTS -> 0L
        consecutiveFailures == FREE_ATTEMPTS + 1 -> 30L
        consecutiveFailures == FREE_ATTEMPTS + 2 -> 60L
        consecutiveFailures == FREE_ATTEMPTS + 3 -> 300L
        consecutiveFailures == FREE_ATTEMPTS + 4 -> 900L
        else -> 3_600L
    }

    fun remainingLockMillis(consecutiveFailures: Int, lastFailureAtMillis: Long, nowMillis: Long): Long {
        val total = lockoutSeconds(consecutiveFailures) * 1_000
        return (lastFailureAtMillis + total - nowMillis).coerceAtLeast(0L)
    }
}
