package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiCapabilityRole
import com.example.data.ai.AiModelConfig
import com.example.data.billing.QuotaFeature
import com.example.data.billing.SubscriptionTier
import com.example.data.billing.UsageQuotaManager
import com.example.data.billing.UsageQuotaSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsageQuotaManagerTest {

    private lateinit var manager: UsageQuotaManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("audiobrain_quota_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        manager = UsageQuotaManager(context)
    }

    @Test
    fun onboardingState_defaultsFalse_andPersistsCompletion() {
        assertFalse(manager.hasCompletedOnboarding.value)
        manager.completeOnboarding()
        assertTrue(manager.hasCompletedOnboarding.value)
        manager.resetOnboarding()
        assertFalse(manager.hasCompletedOnboarding.value)
    }

    @Test
    fun freeTier_exceededBehavior_blocksOnlyAtLimitAndUnlocksWithBonusOrPro() {
        val freeMax = UsageQuotaSnapshot.FREE_LIMITS.maxAiMemosPerMonth
        repeat(freeMax) {
            assertTrue(manager.canUseFeature(QuotaFeature.AI_MEMO))
            manager.recordUsage(QuotaFeature.AI_MEMO)
        }

        // Now free tier should be exceeded
        assertFalse(manager.canUseFeature(QuotaFeature.AI_MEMO))
        assertTrue(manager.quotaState.value.isExceeded(QuotaFeature.AI_MEMO))

        // Granting +3 bonus credits restores access without subscription
        manager.grantBonusAiMemoCredits(3)
        assertTrue(manager.canUseFeature(QuotaFeature.AI_MEMO))
        assertEquals(3, manager.quotaState.value.aiMemosRemaining)

        // Upgrading to Pro grants unlimited access
        manager.setSubscriptionTier(SubscriptionTier.PRO)
        assertFalse(manager.quotaState.value.isExceeded(QuotaFeature.AI_MEMO))
        assertFalse(manager.quotaState.value.isExceeded(QuotaFeature.DIGEST))
    }

    @Test
    fun nearLimitDetection_triggersAtEightyPercent() {
        val snapshot = UsageQuotaSnapshot(
            tier = SubscriptionTier.FREE,
            aiMemosUsed = 12 // 12 / 15 = 80%
        )
        assertTrue(snapshot.isNearLimit(QuotaFeature.AI_MEMO))
        assertFalse(snapshot.isExceeded(QuotaFeature.AI_MEMO))
        assertTrue(snapshot.shouldPreferEcoModel(QuotaFeature.AI_MEMO))
    }

    @Test
    fun refundInteraction_restoresQuota_andNeverGoesNegative() {
        manager.recordUsage(QuotaFeature.FACT_CHECK)
        assertEquals(1, manager.quotaState.value.factChecksUsed)
        manager.refundInteraction(QuotaFeature.FACT_CHECK)
        assertEquals(0, manager.quotaState.value.factChecksUsed)
        manager.refundInteraction(QuotaFeature.FACT_CHECK)
        assertEquals(0, manager.quotaState.value.factChecksUsed)
    }

    @Test
    fun graceCredits_canOnlyBeClaimedOncePerPeriod() {
        assertTrue(manager.grantBonusAiMemoCredits(3))
        assertFalse(manager.grantBonusAiMemoCredits(3))
        assertTrue(manager.quotaState.value.graceClaimed)
        assertEquals(3, manager.quotaState.value.bonusCredits)
    }

    @Test
    fun ecoLiteMode_persistsAndTriggersModelCascade() {
        assertFalse(manager.shouldPreferEcoModel(QuotaFeature.AI_MEMO))
        manager.setEcoLiteMode(true)
        assertTrue(manager.quotaState.value.ecoLiteMode)
        assertTrue(manager.shouldPreferEcoModel(QuotaFeature.AI_MEMO))
        assertEquals(
            AiModelConfig.ECONOMY_LITE_MODEL,
            AiModelConfig.resolveModel(AiCapabilityRole.PRIMARY_REASONING, preferEconomy = true)
        )
        assertTrue(AiModelConfig.isRateLimitOrQuotaError(RuntimeException("HTTP 429 RESOURCE_EXHAUSTED")))
        assertFalse(AiModelConfig.isRateLimitOrQuotaError(RuntimeException("Network timeout")))
    }

    @Test
    fun guestContext_enforcesGuestLimits_andCountdownIsPositive() {
        manager.setAuthContext(userId = "guest_local", isGuest = true)
        assertTrue(manager.quotaState.value.isGuest)
        assertEquals(UsageQuotaSnapshot.GUEST_LIMITS.maxAiMemosPerMonth, manager.quotaState.value.effectiveAiMemoLimit)
        val countdown = manager.quotaState.value.resetCountdown()
        assertTrue(countdown.days >= 0 && countdown.hours >= 0)
        assertTrue(countdown.days > 0 || countdown.hours > 0)
    }
}
