package com.example.domain.security

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedPassword(
    val hashHex: String,
    val saltHex: String,
    val parameters: String
)

object PasswordHasher {
    private const val SALT_BYTES = 16
    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH_BITS = 256
    private const val PREFERRED_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val FALLBACK_ALGORITHM = "PBKDF2WithHmacSHA1"

    private val random = SecureRandom()

    fun hashPassword(password: String): HashedPassword {
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        val algorithm = resolveAvailableAlgorithm()
        val hash = pbkdf2(password.toCharArray(), salt, algorithm, ITERATIONS, KEY_LENGTH_BITS)
        return HashedPassword(
            hashHex = bytesToHex(hash),
            saltHex = bytesToHex(salt),
            parameters = "$algorithm:$ITERATIONS:$KEY_LENGTH_BITS"
        )
    }

    fun verifyPassword(
        password: String,
        expectedHashHex: String,
        saltHex: String,
        parameters: String
    ): Boolean {
        val parts = parameters.split(":")
        val algorithm = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: PREFERRED_ALGORITHM
        val iterations = parts.getOrNull(1)?.toIntOrNull() ?: ITERATIONS
        val keyLength = parts.getOrNull(2)?.toIntOrNull() ?: KEY_LENGTH_BITS
        val salt = hexToBytes(saltHex) ?: return false
        val expectedHash = hexToBytes(expectedHashHex) ?: return false
        val actualHash = pbkdf2(password.toCharArray(), salt, algorithm, iterations, keyLength)
        return MessageDigest.isEqual(actualHash, expectedHash)
    }

    private fun resolveAvailableAlgorithm(): String {
        return try {
            SecretKeyFactory.getInstance(PREFERRED_ALGORITHM)
            PREFERRED_ALGORITHM
        } catch (_: NoSuchAlgorithmException) {
            FALLBACK_ALGORITHM
        }
    }

    private fun pbkdf2(
        password: CharArray,
        salt: ByteArray,
        algorithm: String,
        iterations: Int,
        keyLengthBits: Int
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        return try {
            val factory = SecretKeyFactory.getInstance(algorithm)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
