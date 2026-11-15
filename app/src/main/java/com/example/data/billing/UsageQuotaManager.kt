package com.example.data.billing

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Locale

enum class SubscriptionTier(@param:StringRes val displayNameRes: Int) {
    FREE(R.string.tier_starter_free),
    PRO(R.string.tier_pro)
}

enum class QuotaFeature(@param:StringRes val labelRes: Int) {
    AI_MEMO(R.string.quota_feature_ai_memo),
    FACT_CHECK(R.string.quota_feature_fact_check),
    DIGEST(R.string.quota_feature_digest)
}

data class QuotaLimits(
    val maxAiMemosPerMonth: Int,
    val maxFactChecksPerMonth: Int,
    val maxDigestsPerMonth: Int,
    val maxRecordingSeconds: Int
)

data class ResetCountdown(
    val days: Int,
    val hours: Int
)

data class UsageQuotaSnapshot(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val isGuest: Boolean = false,
    val aiMemosUsed: Int = 0,
    val factChecksUsed: Int = 0,
    val digestsUsed: Int = 0,
    val bonusCredits: Int = 0,
    val graceClaimed: Boolean = false,
    val ecoLiteMode: Boolean = false,
    val fallbackCascadeCount: Int = 0,
    val periodIso: String = "",
    val limits: QuotaLimits = FREE_LIMITS
) {
    val effectiveAiMemoLimit: Int
        get() = if (tier == SubscriptionTier.PRO) Int.MAX_VALUE else limits.maxAiMemosPerMonth + bonusCredits

    val aiMemosRemaining: Int
        get() = if (tier == SubscriptionTier.PRO) Int.MAX_VALUE else (effectiveAiMemoLimit - aiMemosUsed).coerceAtLeast(0)

    val factChecksRemaining: Int
        get() = if (tier == SubscriptionTier.PRO) Int.MAX_VALUE else (limits.maxFactChecksPerMonth - factChecksUsed).coerceAtLeast(0)

    val digestsRemaining: Int
        get() = if (tier == SubscriptionTier.PRO) Int.MAX_VALUE else (limits.maxDigestsPerMonth - digestsUsed).coerceAtLeast(0)

    val canClaimGraceCredits: Boolean
        get() = tier != SubscriptionTier.PRO && !graceClaimed

    val totalValueActionsThisMonth: Int
        get() = aiMemosUsed + factChecksUsed + digestsUsed

    fun isExceeded(feature: QuotaFeature): Boolean {
        if (tier == SubscriptionTier.PRO) return false
        return when (feature) {
            QuotaFeature.AI_MEMO -> aiMemosUsed >= effectiveAiMemoLimit
            QuotaFeature.FACT_CHECK -> factChecksUsed >= limits.maxFactChecksPerMonth
            QuotaFeature.DIGEST -> digestsUsed >= limits.maxDigestsPerMonth
        }
    }

    fun isNearLimit(feature: QuotaFeature): Boolean {
        if (tier == SubscriptionTier.PRO) return false
        return when (feature) {
            QuotaFeature.AI_MEMO -> aiMemosUsed >= (effectiveAiMemoLimit * 0.8f).toInt() && !isExceeded(feature)
            QuotaFeature.FACT_CHECK -> factChecksUsed >= (limits.maxFactChecksPerMonth * 0.8f).toInt() && !isExceeded(feature)
            QuotaFeature.DIGEST -> digestsUsed >= (limits.maxDigestsPerMonth * 0.8f).toInt() && !isExceeded(feature)
        }
    }

    fun anyNearLimit(): Boolean =
        QuotaFeature.entries.any { isNearLimit(it) }

    fun shouldPreferEcoModel(feature: QuotaFeature? = null): Boolean {
        if (tier == SubscriptionTier.PRO) return ecoLiteMode
        return ecoLiteMode || (feature != null && isNearLimit(feature)) || anyNearLimit()
    }

    fun resetCountdown(nowMillis: Long = System.currentTimeMillis()): ResetCountdown {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nextMonthCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }
        val diffHoursTotal = ((nextMonthCal.timeInMillis - nowCal.timeInMillis) / (1000L * 60L * 60L)).coerceAtLeast(1L)
        val days = (diffHoursTotal / 24L).toInt()
        val hours = (diffHoursTotal % 24L).toInt()
        return ResetCountdown(days = days, hours = hours)
    }

    companion object {
        val GUEST_LIMITS = QuotaLimits(
            maxAiMemosPerMonth = 3,
            maxFactChecksPerMonth = 2,
            maxDigestsPerMonth = 1,
            maxRecordingSeconds = 180 // 3 minutes per recording in Guest trial
        )

        val FREE_LIMITS = QuotaLimits(
            maxAiMemosPerMonth = 15,
            maxFactChecksPerMonth = 10,
            maxDigestsPerMonth = 5,
            maxRecordingSeconds = 300 // 5 minutes per recording on Starter Free tier
        )

        val PRO_LIMITS = QuotaLimits(
            maxAiMemosPerMonth = Int.MAX_VALUE,
            maxFactChecksPerMonth = Int.MAX_VALUE,
            maxDigestsPerMonth = Int.MAX_VALUE,
            maxRecordingSeconds = 3600 // 60 minutes per recording on Pro
        )
    }
}

/**
 * Hybrid quota ledger backed by SharedPreferences for instant cold-start reads
 * and mirrored to Cloud Firestore (/users/{userId}/usageQuota/{periodIso}) for
 * authenticated users. Supports automatic monthly rollover, atomic deduct-with-refund,
 * Eco Lite Mode persistence, and one-time monthly grace credits.
 */
class UsageQuotaManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var activeUserId: String? = null

    @Volatile
    private var activeIsGuest: Boolean = false

    private val _hasCompletedOnboarding = MutableStateFlow(
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _quotaState = MutableStateFlow(loadSnapshot())
    val quotaState: StateFlow<UsageQuotaSnapshot> = _quotaState.asStateFlow()

    fun setAuthContext(userId: String?, isGuest: Boolean) {
        activeUserId = userId
        activeIsGuest = isGuest
        ensureCurrentMonth()
        _quotaState.value = loadSnapshot()
        if (!userId.isNullOrBlank() && !isGuest) {
            syncFromFirestore(userId)
        }
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        _hasCompletedOnboarding.value = true
    }

    fun resetOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, false).apply()
        _hasCompletedOnboarding.value = false
    }

    fun canUseFeature(feature: QuotaFeature): Boolean {
        ensureCurrentMonth()
        return !_quotaState.value.isExceeded(feature)
    }

    fun shouldPreferEcoModel(feature: QuotaFeature? = null): Boolean {
        ensureCurrentMonth()
        return _quotaState.value.shouldPreferEcoModel(feature)
    }

    fun recordUsage(feature: QuotaFeature) {
        ensureCurrentMonth()
        val current = _quotaState.value
        if (current.tier == SubscriptionTier.PRO) return

        val editor = prefs.edit()
        when (feature) {
            QuotaFeature.AI_MEMO -> editor.putInt(KEY_AI_MEMOS_USED, current.aiMemosUsed + 1)
            QuotaFeature.FACT_CHECK -> editor.putInt(KEY_FACT_CHECKS_USED, current.factChecksUsed + 1)
            QuotaFeature.DIGEST -> editor.putInt(KEY_DIGESTS_USED, current.digestsUsed + 1)
        }
        editor.apply()
        val updated = loadSnapshot()
        _quotaState.value = updated
        pushToFirestore(updated)
    }

    /**
     * Refunds a previously recorded interaction when a cloud AI call fails or times out,
     * ensuring users never lose free-tier credits due to transient network errors.
     */
    fun refundInteraction(feature: QuotaFeature) {
        ensureCurrentMonth()
        val current = _quotaState.value
        if (current.tier == SubscriptionTier.PRO) return

        val editor = prefs.edit()
        when (feature) {
            QuotaFeature.AI_MEMO -> editor.putInt(KEY_AI_MEMOS_USED, (current.aiMemosUsed - 1).coerceAtLeast(0))
            QuotaFeature.FACT_CHECK -> editor.putInt(KEY_FACT_CHECKS_USED, (current.factChecksUsed - 1).coerceAtLeast(0))
            QuotaFeature.DIGEST -> editor.putInt(KEY_DIGESTS_USED, (current.digestsUsed - 1).coerceAtLeast(0))
        }
        editor.apply()
        val updated = loadSnapshot()
        _quotaState.value = updated
        pushToFirestore(updated)
    }

    fun grantBonusAiMemoCredits(credits: Int = 3): Boolean {
        ensureCurrentMonth()
        val current = _quotaState.value
        if (current.graceClaimed && credits <= 3) return false
        prefs.edit()
            .putInt(KEY_BONUS_CREDITS, current.bonusCredits + credits)
            .putBoolean(KEY_GRACE_CLAIMED, true)
            .apply()
        val updated = loadSnapshot()
        _quotaState.value = updated
        pushToFirestore(updated)
        return true
    }

    fun setEcoLiteMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ECO_LITE_MODE, enabled).apply()
        val updated = loadSnapshot()
        _quotaState.value = updated
        pushToFirestore(updated)
    }

    fun recordFallbackCascade() {
        val next = prefs.getInt(KEY_FALLBACK_CASCADE_COUNT, 0) + 1
        prefs.edit().putInt(KEY_FALLBACK_CASCADE_COUNT, next).apply()
        val updated = loadSnapshot()
        _quotaState.value = updated
        pushToFirestore(updated)
    }

    fun setSubscriptionTier(tier: SubscriptionTier) {
        prefs.edit().putString(KEY_TIER, tier.name).apply()
        val updated = loadSnapshot()
        _quotaState.value = updated
        pushToFirestore(updated)
    }

    private fun ensureCurrentMonth() {
        val currentMonthKey = currentYearMonthKey()
        val savedMonthKey = prefs.getString(KEY_USAGE_MONTH, null)
        if (savedMonthKey != currentMonthKey) {
            // Preserve ecoLiteMode & tier across monthly rollover; reset counters & graceClaimed.
            prefs.edit()
                .putString(KEY_USAGE_MONTH, currentMonthKey)
                .putInt(KEY_AI_MEMOS_USED, 0)
                .putInt(KEY_FACT_CHECKS_USED, 0)
                .putInt(KEY_DIGESTS_USED, 0)
                .putInt(KEY_BONUS_CREDITS, 0)
                .putBoolean(KEY_GRACE_CLAIMED, false)
                .putInt(KEY_FALLBACK_CASCADE_COUNT, 0)
                .apply()
            _quotaState.value = loadSnapshot()
        }
    }

    private fun loadSnapshot(): UsageQuotaSnapshot {
        val tierName = prefs.getString(KEY_TIER, SubscriptionTier.FREE.name)
        val tier = runCatching { SubscriptionTier.valueOf(tierName ?: SubscriptionTier.FREE.name) }
            .getOrDefault(SubscriptionTier.FREE)
        val periodIso = prefs.getString(KEY_USAGE_MONTH, null) ?: currentYearMonthKey()
        val limits = when {
            tier == SubscriptionTier.PRO -> UsageQuotaSnapshot.PRO_LIMITS
            activeIsGuest -> UsageQuotaSnapshot.GUEST_LIMITS
            else -> UsageQuotaSnapshot.FREE_LIMITS
        }
        return UsageQuotaSnapshot(
            tier = tier,
            isGuest = activeIsGuest,
            aiMemosUsed = prefs.getInt(KEY_AI_MEMOS_USED, 0),
            factChecksUsed = prefs.getInt(KEY_FACT_CHECKS_USED, 0),
            digestsUsed = prefs.getInt(KEY_DIGESTS_USED, 0),
            bonusCredits = prefs.getInt(KEY_BONUS_CREDITS, 0),
            graceClaimed = prefs.getBoolean(KEY_GRACE_CLAIMED, false),
            ecoLiteMode = prefs.getBoolean(KEY_ECO_LITE_MODE, false),
            fallbackCascadeCount = prefs.getInt(KEY_FALLBACK_CASCADE_COUNT, 0),
            periodIso = periodIso,
            limits = limits
        )
    }

    private fun syncFromFirestore(userId: String) {
        val periodIso = currentYearMonthKey()
        val db = runCatching { FirebaseFirestore.getInstance() }.getOrNull() ?: return
        db.collection("users")
            .document(userId)
            .collection("usageQuota")
            .document(periodIso)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val remotePeriod = doc.getString("periodIso")
                    if (remotePeriod == periodIso) {
                        val remoteTier = doc.getString("tier")
                            ?.let { runCatching { SubscriptionTier.valueOf(it) }.getOrNull() }
                            ?: SubscriptionTier.FREE
                        val remoteMemos = (doc.getLong("aiMemosUsed") ?: 0L).toInt().coerceAtLeast(0)
                        val remoteChecks = (doc.getLong("factChecksUsed") ?: 0L).toInt().coerceAtLeast(0)
                        val remoteDigests = (doc.getLong("digestsUsed") ?: 0L).toInt().coerceAtLeast(0)
                        val remoteBonus = (doc.getLong("bonusCredits") ?: 0L).toInt().coerceAtLeast(0)
                        val remoteGrace = doc.getBoolean("graceClaimed") ?: false
                        val remoteEco = doc.getBoolean("ecoLiteMode") ?: prefs.getBoolean(KEY_ECO_LITE_MODE, false)
                        val remoteCascade = (doc.getLong("fallbackCascadeCount") ?: 0L).toInt().coerceAtLeast(0)

                        val local = loadSnapshot()
                        prefs.edit()
                            .putString(KEY_USAGE_MONTH, periodIso)
                            .putString(KEY_TIER, if (local.tier == SubscriptionTier.PRO) SubscriptionTier.PRO.name else remoteTier.name)
                            .putInt(KEY_AI_MEMOS_USED, maxOf(local.aiMemosUsed, remoteMemos))
                            .putInt(KEY_FACT_CHECKS_USED, maxOf(local.factChecksUsed, remoteChecks))
                            .putInt(KEY_DIGESTS_USED, maxOf(local.digestsUsed, remoteDigests))
                            .putInt(KEY_BONUS_CREDITS, maxOf(local.bonusCredits, remoteBonus))
                            .putBoolean(KEY_GRACE_CLAIMED, local.graceClaimed || remoteGrace)
                            .putBoolean(KEY_ECO_LITE_MODE, remoteEco)
                            .putInt(KEY_FALLBACK_CASCADE_COUNT, maxOf(local.fallbackCascadeCount, remoteCascade))
                            .apply()
                        _quotaState.value = loadSnapshot()
                    }
                } else {
                    pushToFirestore(loadSnapshot())
                }
            }
    }

    private fun pushToFirestore(snapshot: UsageQuotaSnapshot) {
        val userId = activeUserId
        if (userId.isNullOrBlank() || activeIsGuest) return
        val periodIso = currentYearMonthKey()
        val db = runCatching { FirebaseFirestore.getInstance() }.getOrNull() ?: return
        val payload = hashMapOf(
            "userId" to userId,
            "periodIso" to periodIso,
            "tier" to snapshot.tier.name,
            "aiMemosUsed" to snapshot.aiMemosUsed,
            "factChecksUsed" to snapshot.factChecksUsed,
            "digestsUsed" to snapshot.digestsUsed,
            "bonusCredits" to snapshot.bonusCredits,
            "graceClaimed" to snapshot.graceClaimed,
            "ecoLiteMode" to snapshot.ecoLiteMode,
            "fallbackCascadeCount" to snapshot.fallbackCascadeCount,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users")
            .document(userId)
            .collection("usageQuota")
            .document(periodIso)
            .set(payload, SetOptions.merge())
    }

    private fun currentYearMonthKey(): String {
        val cal = Calendar.getInstance()
        return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    companion object {
        private const val PREFS_NAME = "audiobrain_quota_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_TIER = "subscription_tier"
        private const val KEY_USAGE_MONTH = "usage_month"
        private const val KEY_AI_MEMOS_USED = "ai_memos_used"
        private const val KEY_FACT_CHECKS_USED = "fact_checks_used"
        private const val KEY_DIGESTS_USED = "digests_used"
        private const val KEY_BONUS_CREDITS = "bonus_credits"
        private const val KEY_GRACE_CLAIMED = "grace_claimed"
        private const val KEY_ECO_LITE_MODE = "eco_lite_mode"
        private const val KEY_FALLBACK_CASCADE_COUNT = "fallback_cascade_count"

        @Volatile
        private var instance: UsageQuotaManager? = null

        fun getInstance(context: Context): UsageQuotaManager {
            return instance ?: synchronized(this) {
                instance ?: UsageQuotaManager(context).also { instance = it }
            }
        }
    }
}
