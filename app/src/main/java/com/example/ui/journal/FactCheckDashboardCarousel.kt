package com.example.ui.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.FactCheckFreshnessHelper
import com.example.data.model.FactCheckStatus
import com.example.data.model.FactSource
import com.example.data.model.VoiceNote
import com.example.ui.common.displayLabelRes
import com.example.ui.common.localizedCategoryLabel

enum class FactCheckDashboardFilter(@androidx.annotation.StringRes val labelRes: Int) {
    ALL(R.string.dashboard_filter_all),
    NEEDS_ATTENTION(R.string.dashboard_filter_needs_attention),
    CONFIRMED(R.string.dashboard_filter_confirmed),
    STALE(R.string.dashboard_filter_stale)
}

data class FactCheckDashboardItem(
    val id: String,
    val noteId: String,
    val noteTitle: String,
    val noteCategory: String,
    val claim: String,
    val status: ClaimVerdictStatus,
    val explanation: String,
    val primarySource: FactSource?,
    val isStale: Boolean,
    val updatedAtMillis: Long
)

fun buildFactCheckDashboardItems(
    notes: List<VoiceNote>,
    nowMillis: Long = System.currentTimeMillis(),
    fallbackNoteTitle: String = "Voice Note",
    fallbackClaimTitle: String = "Verified Note Claim",
    fallbackExplanation: String = "Tap Re-verify to cross-reference this claim with live Google Search."
): List<FactCheckDashboardItem> {
    val rawItems = mutableListOf<FactCheckDashboardItem>()

    for (note in notes) {
        val updatedAtMillis = (note.updatedAt ?: note.createdAt)?.toDate()?.time ?: 0L
        val isStale = FactCheckFreshnessHelper.isFactCheckStale(note, nowMillis)
        val primarySource = note.sources.firstOrNull()

        if (note.claimVerdicts.isNotEmpty()) {
            note.claimVerdicts.forEachIndexed { idx, verdict ->
                val status = verdict.normalizedStatus()
                val claimText = verdict.claim.trim()
                if (claimText.isNotEmpty()) {
                    rawItems.add(
                        FactCheckDashboardItem(
                            id = "${note.id}_v_$idx",
                            noteId = note.id,
                            noteTitle = note.title.ifBlank { fallbackNoteTitle },
                            noteCategory = note.category,
                            claim = claimText,
                            status = status,
                            explanation = verdict.explanation.trim().ifBlank {
                                note.factCheckSummary.replace(Regex("[#*_`]"), "").trim().take(200)
                            },
                            primarySource = primarySource,
                            isStale = isStale,
                            updatedAtMillis = updatedAtMillis
                        )
                    )
                }
            }
        } else if (note.factCheckStatus == FactCheckStatus.VERIFIED.value ||
            note.factCheckSummary.isNotBlank() ||
            note.claimsToVerify.isNotEmpty()
        ) {
            val fallbackClaim = note.claimsToVerify.firstOrNull()?.trim().orEmpty()
                .ifBlank { note.title.ifBlank { fallbackClaimTitle } }
            val fallbackStatus = when {
                note.factCheckStatus == FactCheckStatus.VERIFIED.value || note.sources.isNotEmpty() ->
                    ClaimVerdictStatus.SUPPORTED
                else -> ClaimVerdictStatus.UNVERIFIED
            }
            val cleanSummary = note.factCheckSummary
                .replace(Regex("[#*_`]"), "")
                .trim()
                .take(220)
                .ifBlank { fallbackExplanation }

            rawItems.add(
                FactCheckDashboardItem(
                    id = "${note.id}_fallback",
                    noteId = note.id,
                    noteTitle = note.title.ifBlank { fallbackNoteTitle },
                    noteCategory = note.category,
                    claim = fallbackClaim,
                    status = fallbackStatus,
                    explanation = cleanSummary,
                    primarySource = primarySource,
                    isStale = isStale,
                    updatedAtMillis = updatedAtMillis
                )
            )
        }
    }

    return rawItems.sortedWith(
        compareBy<FactCheckDashboardItem> { item ->
            when {
                item.status == ClaimVerdictStatus.DISPUTED -> 0
                item.status == ClaimVerdictStatus.UPDATED -> 1
                item.isStale || item.status == ClaimVerdictStatus.UNVERIFIED -> 2
                else -> 3
            }
        }.thenByDescending { it.updatedAtMillis }
    ).take(20)
}

@Composable
fun FactCheckDashboardCarousel(
    notes: List<VoiceNote>,
    reverifyingNoteIds: Set<String>,
    onOpenNote: (String) -> Unit,
    onReverifyNote: (VoiceNote) -> Unit,
    onOpenSourceUrl: (String) -> Unit,
    onOpenSearchHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allItems = remember(notes, context) {
        buildFactCheckDashboardItems(
            notes = notes,
            fallbackNoteTitle = context.getString(R.string.fallback_voice_note_title),
            fallbackClaimTitle = context.getString(R.string.fallback_verified_claim),
            fallbackExplanation = context.getString(R.string.fallback_reverify_explanation)
        )
    }
    if (allItems.isEmpty()) return

    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var selectedFilter by rememberSaveable { mutableStateOf(FactCheckDashboardFilter.ALL) }

    val filteredItems = remember(allItems, selectedFilter) {
        when (selectedFilter) {
            FactCheckDashboardFilter.ALL -> allItems
            FactCheckDashboardFilter.NEEDS_ATTENTION -> allItems.filter {
                it.status == ClaimVerdictStatus.DISPUTED ||
                    it.status == ClaimVerdictStatus.UPDATED ||
                    it.status == ClaimVerdictStatus.UNVERIFIED
            }
            FactCheckDashboardFilter.CONFIRMED -> allItems.filter {
                it.status == ClaimVerdictStatus.SUPPORTED
            }
            FactCheckDashboardFilter.STALE -> allItems.filter { it.isStale }
        }
    }

    val confirmedCount = remember(allItems) {
        allItems.count { it.status == ClaimVerdictStatus.SUPPORTED }
    }
    val attentionCount = remember(allItems) {
        allItems.count {
            it.status == ClaimVerdictStatus.DISPUTED ||
                it.status == ClaimVerdictStatus.UPDATED
        }
    }

    val notesById = remember(notes) { notes.associateBy { it.id } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("fact_check_dashboard_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.fact_check_dashboard_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${allItems.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_confirmed_subtitle, confirmedCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (attentionCount > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_attention_count, attentionCount),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenSearchHistory != null) {
                    IconButton(
                        onClick = onOpenSearchHistory,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_search_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = stringResource(R.string.cd_fact_check_search_history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("fact_check_dashboard_toggle")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.cd_collapse_dashboard else R.string.cd_expand_dashboard
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    FactCheckDashboardFilter.entries.forEach { filter ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = stringResource(filter.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("dashboard_filter_${filter.name.lowercase()}")
                        )
                    }
                }

                if (filteredItems.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.dashboard_empty_filter,
                                stringResource(selectedFilter.labelRes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fact_check_dashboard_carousel")
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            val parentNote = notesById[item.noteId]
                            val isReverifying = reverifyingNoteIds.contains(item.noteId)
                            FactCheckCarouselCard(
                                item = item,
                                isReverifying = isReverifying,
                                onOpenNote = { onOpenNote(item.noteId) },
                                onReverify = {
                                    if (parentNote != null) {
                                        onReverifyNote(parentNote)
                                    }
                                },
                                onOpenSourceUrl = onOpenSourceUrl
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactCheckCarouselCard(
    item: FactCheckDashboardItem,
    isReverifying: Boolean,
    onOpenNote: () -> Unit,
    onReverify: () -> Unit,
    onOpenSourceUrl: (String) -> Unit
) {
    val (badgeContainer, badgeContent, badgeIcon) = when (item.status) {
        ClaimVerdictStatus.SUPPORTED -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Rounded.CheckCircle
        )
        ClaimVerdictStatus.UPDATED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Rounded.Info
        )
        ClaimVerdictStatus.DISPUTED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Rounded.WarningAmber
        )
        ClaimVerdictStatus.UNVERIFIED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Rounded.TravelExplore
        )
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .width(300.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpenNote)
            .testTag("dashboard_claim_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = badgeContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeContent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(item.status.displayLabelRes()),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = badgeContent
                            )
                        }
                    }

                    if (item.isStale) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_badge_stale),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Text(
                    text = localizedCategoryLabel(item.noteCategory),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = item.claim,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.primarySource != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenSourceUrl(item.primarySource.url) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.cd_open_source),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = item.primarySource.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.noteTitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                TextButton(
                    onClick = onReverify,
                    enabled = !isReverifying,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("dashboard_reverify_${item.noteId}")
                ) {
                    if (isReverifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.status_checking),
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                if (item.status == ClaimVerdictStatus.UNVERIFIED) R.string.action_verify
                                else R.string.action_reverify_topic
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
