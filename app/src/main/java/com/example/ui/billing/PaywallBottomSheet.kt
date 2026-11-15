package com.example.ui.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.billing.QuotaFeature
import com.example.data.billing.SubscriptionTier
import com.example.data.billing.UsageQuotaSnapshot
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.VerifiedEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallBottomSheet(
    quotaState: UsageQuotaSnapshot,
    triggeredFeature: QuotaFeature? = null,
    onUpgradeToPro: () -> Unit,
    onClaimBonusCredits: () -> Unit,
    onEnableEcoLite: (() -> Unit)? = null,
    onSignInWithGoogle: (() -> Unit)? = null,
    onSaveOfflineFallback: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isExceeded = triggeredFeature != null && quotaState.isExceeded(triggeredFeature)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("paywall_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Banner
            Surface(
                color = ObsidianBackground,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    StudioAmber.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            color = StudioAmber,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isExceeded) {
                                    stringResource(
                                        R.string.paywall_title_limit_reached,
                                        stringResource(triggeredFeature.labelRes)
                                    )
                                } else {
                                    stringResource(R.string.paywall_title_upgrade)
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = if (isExceeded) {
                                    stringResource(R.string.paywall_subtitle_limit_reached)
                                } else {
                                    stringResource(R.string.paywall_subtitle_upgrade)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
            }

            // Current Monthly Usage Summary + Value Receipt + Reset Countdown
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                val countdown = quotaState.resetCountdown()
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.paywall_usage_header),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (quotaState.tier == SubscriptionTier.FREE) {
                            Text(
                                text = stringResource(R.string.quota_reset_countdown, countdown.days, countdown.hours),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (quotaState.totalValueActionsThisMonth > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.paywall_value_receipt,
                                    quotaState.aiMemosUsed,
                                    quotaState.factChecksUsed,
                                    quotaState.digestsUsed
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                    QuotaProgressRow(
                        label = stringResource(R.string.quota_feature_ai_memo),
                        used = quotaState.aiMemosUsed,
                        limit = quotaState.effectiveAiMemoLimit
                    )
                    QuotaProgressRow(
                        label = stringResource(R.string.quota_feature_fact_check),
                        used = quotaState.factChecksUsed,
                        limit = quotaState.limits.maxFactChecksPerMonth
                    )
                    QuotaProgressRow(
                        label = stringResource(R.string.quota_feature_digest),
                        used = quotaState.digestsUsed,
                        limit = quotaState.limits.maxDigestsPerMonth
                    )
                }
            }

            // Pro Benefits
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.paywall_pro_included_header),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                listOf(
                    stringResource(R.string.paywall_benefit_memos),
                    stringResource(R.string.paywall_benefit_factcheck),
                    stringResource(R.string.paywall_benefit_digests),
                    stringResource(R.string.paywall_benefit_sync)
                ).forEach { benefit ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = VerifiedEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = benefit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Primary Upgrade CTA
            Button(
                onClick = onUpgradeToPro,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioAmber,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("paywall_upgrade_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.paywall_cta_trial),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Graceful Free-Tier Fallbacks & Self-Serve Recovery
            if (quotaState.isGuest && onSignInWithGoogle != null) {
                OutlinedButton(
                    onClick = onSignInWithGoogle,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("paywall_signin_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.paywall_cta_sign_in_unlock),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (isExceeded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (quotaState.canClaimGraceCredits) {
                        OutlinedButton(
                            onClick = onClaimBonusCredits,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("paywall_bonus_credits_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CardGiftcard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.paywall_cta_bonus_credits),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (!quotaState.ecoLiteMode && quotaState.tier == SubscriptionTier.FREE && onEnableEcoLite != null) {
                        OutlinedButton(
                            onClick = onEnableEcoLite,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("paywall_enable_eco_lite_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.paywall_cta_enable_eco_lite),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (onSaveOfflineFallback != null) {
                        OutlinedButton(
                            onClick = onSaveOfflineFallback,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("paywall_save_draft_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.paywall_cta_save_draft),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.paywall_cta_maybe_later),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuotaProgressRow(
    label: String,
    used: Int,
    limit: Int,
    modifier: Modifier = Modifier
) {
    val isUnlimited = limit == Int.MAX_VALUE
    val progress = if (isUnlimited || limit <= 0) 0f else (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    val isExhausted = !isUnlimited && used >= limit
    val barColor = when {
        isExhausted -> MaterialTheme.colorScheme.error
        progress >= 0.8f -> StudioAmber
        else -> VerifiedEmerald
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isUnlimited) {
                    stringResource(R.string.quota_unlimited)
                } else {
                    stringResource(R.string.quota_usage_ratio, used, limit)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        if (!isUnlimited) {
            LinearProgressIndicator(
                progress = { progress },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}
