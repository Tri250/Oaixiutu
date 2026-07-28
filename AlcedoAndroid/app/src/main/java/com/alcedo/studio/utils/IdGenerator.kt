package com.alcedo.studio.utils

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Identifier generation utilities for entities that need stable, unique ids
 * without relying on the database. Produces ULID-like monotonic ids, UUIDs and
 * content hashes.
 */
object IdGenerator {

    private val counter = AtomicLong(System.currentTimeMillis())

    /** Monotonic, time-prefixed 26-char id (Crockford base32, ULID-like). */
    fun newId(prefix: String = ""): String {
        val ts = System.currentTimeMillis()
        val rand = counter.incrementAndGet()
        val raw = "%013d%011d".format(ts, rand and 0xFFFFFFFFFFL)
        return if (prefix.isEmpty()) encodeBase32(raw) else "${prefix}_${encodeBase32(raw)}"
    }

    /** A standard random UUID string. */
    fun newUuid(): String = UUID.randomUUID().toString()

    /** SHA-256 hex digest of [input], used for content-addressing files. */
    fun contentHash(input: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Short, file-system-safe random id, suitable for temp file names. */
    fun shortId(): String {
        val ts = System.currentTimeMillis().toString(36)
        val rnd = (UUID.randomUUID().leastSignificantBits and Long.MAX_VALUE).toString(36)
        return "$ts$rnd".take(16)
    }

    /** Derive a stable id from a file path (content-addressing for thumbnails). */
    fun fromPath(path: String): String =
        contentHash(path.toByteArray()).substring(0, 24)

    private val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private fun encodeBase32(numeric: String): String {
        // Encode the numeric string as a big integer into Crockford base32.
        var value = numeric.toBigIntegerOrNull() ?: return numeric.take(26)
        if (value == java.math.BigInteger.ZERO) return "0".padStart(26, '0')
        val sb = StringBuilder()
        val base = java.math.BigInteger.valueOf(32)
        while (value > java.math.BigInteger.ZERO) {
            val divmod = value.divideAndRemainder(base)
            value = divmod[0]
            sb.append(CROCKFORD_ALPHABET[divmod[1].toInt()])
        }
        return sb.reverse().toString().padStart(26, '0')
    }
}
