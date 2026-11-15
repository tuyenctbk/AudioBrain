package com.example.data.ai

/**
 * Capability roles for model-agnostic AI routing.
 * UI screens and ViewModels never reference raw model IDs directly.
 */
enum class AiCapabilityRole {
    PRIMARY_REASONING,
    ECONOMY_LITE
}

/**
 * Centralized model configuration supporting automatic Tier 1 -> Tier 2 cascade
 * on 429/rate-limits, >=80% quota usage, or user-enabled Eco Lite Mode.
 */
object AiModelConfig {
    const val PRIMARY_REASONING_MODEL = "gemini-3.8-flash"
    const val ECONOMY_LITE_MODEL = "gemini-3.5-flash-lite"

    const val DEDUP_CACHE_TTL_MS = 40_000L
    const val CHAT_SLIDING_WINDOW_TURNS = 6

    fun resolveModel(
        role: AiCapabilityRole,
        preferEconomy: Boolean = false
    ): String = when {
        preferEconomy -> ECONOMY_LITE_MODEL
        role == AiCapabilityRole.PRIMARY_REASONING -> PRIMARY_REASONING_MODEL
        else -> ECONOMY_LITE_MODEL
    }

    fun isRateLimitOrQuotaError(throwable: Throwable?): Boolean {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 6) {
            val msg = current.message.orEmpty().lowercase()
            if (msg.contains("429") ||
                msg.contains("resource_exhausted") ||
                msg.contains("quota") ||
                msg.contains("rate limit") ||
                msg.contains("too many requests")
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }
}
