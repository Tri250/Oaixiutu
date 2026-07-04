package com.alcedo.studio.utils

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

object IdGenerator {
    private val sequence = AtomicLong(System.currentTimeMillis())

    fun nextId(): Long = sequence.incrementAndGet()

    fun nextHash128(): String = UUID.randomUUID().toString().replace("-", "")

    fun nextVersionId(): String = "v-${System.currentTimeMillis()}-${(0..999).random()}"
}
