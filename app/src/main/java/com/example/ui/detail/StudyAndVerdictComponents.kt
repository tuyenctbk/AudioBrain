package com.example.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ClaimVerdict
import com.example.data.model.ClaimVerdictStatus
import com.example.data.model.FlashcardItem
import com.example.data.model.NoteCategory
import com.example.ui.common.displayLabelRes

@Composable
fun ReadAloudChipButton(
    isSpeakingThis: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSpeakingThis) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSpeakingThis) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (isSpeakingThis) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (isSpeakingThis) {
                    stringResource(R.string.action_stop_listening)
                } else {
                    stringResource(R.string.action_listen)
                },
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isSpeakingThis) {
                    stringResource(R.string.action_stop_listening)
                } else {
                    stringResource(R.string.action_listen)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun KeyDecisionsCard(
    decisions: List<String>,
    modifier: Modifier = Modifier
) {
    if (decisions.isEmpty()) return

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("key_decisions_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.key_decisions_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            decisions.forEach { decision ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = decision,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun StudyFlashcardsCard(
    category: String,
    flashcards: List<FlashcardItem>,
    isGenerating: Boolean,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentIndex by remember(flashcards) { mutableIntStateOf(0) }
    var showAnswer by remember(flashcards, currentIndex) { mutableStateOf(false) }

    val flipRotation by animateFloatAsState(
        targetValue = if (showAnswer) 180f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "flashcard_flip"
    )

    val isStudyPriority = category.equals(NoteCategory.LECTURE.label, ignoreCase = true) ||
        category.equals(NoteCategory.RESEARCH.label, ignoreCase = true)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isStudyPriority) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isStudyPriority) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("study_flashcards_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.study_flashcards_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (flashcards.isEmpty()) {
                                stringResource(R.string.study_flashcards_empty_subtitle)
                            } else {
                                stringResource(
                                    R.string.study_flashcards_progress,
                                    currentIndex + 1,
                                    flashcards.size
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (flashcards.isNotEmpty() && !isGenerating) {
                    TextButton(
                        onClick = onGenerate,
                        modifier = Modifier.testTag("regenerate_flashcards_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_regenerate_flashcards),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (flashcards.isEmpty()) {
                Button(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_flashcards_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.study_flashcards_building))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_generate_flashcards))
                    }
                }
            } else {
                val safeIndex = currentIndex.coerceIn(0, flashcards.lastIndex)
                val activeCard = flashcards[safeIndex]
                val showingBack = flipRotation > 90f

                Surface(
                    color = if (showingBack) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (showingBack) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp)
                        .graphicsLayer {
                            rotationY = flipRotation
                            cameraDistance = 12f * density
                        }
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showAnswer = !showAnswer }
                        .testTag("flashcard_flip_surface")
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .padding(16.dp)
                            .graphicsLayer {
                                if (showingBack) {
                                    rotationY = 180f
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(
                                    if (showingBack) R.string.flashcard_answer_label
                                    else R.string.flashcard_question_label
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (showingBack) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Rounded.Flip,
                                contentDescription = stringResource(R.string.cd_flip_flashcard),
                                tint = if (showingBack) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (showingBack) activeCard.answer else activeCard.question,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (showingBack) FontWeight.Normal else FontWeight.Medium
                            ),
                            color = if (showingBack) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            currentIndex = if (safeIndex == 0) flashcards.lastIndex else safeIndex - 1
                            showAnswer = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_previous_card),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_prev))
                    }
                    Button(
                        onClick = { showAnswer = !showAnswer },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            stringResource(
                                if (showAnswer) R.string.action_question else R.string.action_reveal
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            currentIndex = (safeIndex + 1) % flashcards.size
                            showAnswer = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(stringResource(R.string.action_next))
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = stringResource(R.string.cd_next_card),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StaleFactCheckBanner(
    daysOld: Long,
    isFactChecking: Boolean,
    onReverifyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("stale_fact_check_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stale_fact_check_age, daysOld),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.stale_fact_check_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            Button(
                onClick = onReverifyClick,
                enabled = !isFactChecking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                modifier = Modifier.height(40.dp)
            ) {
                Text(stringResource(R.string.action_reverify_topic))
            }
        }
    }
}

@Composable
fun ClaimVerdictCard(
    verdict: ClaimVerdict,
    isFactChecking: Boolean,
    onVerifySingleClaim: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = verdict.normalizedStatus()
    val (containerColor, badgeContainer, badgeContent, icon) = when (status) {
        ClaimVerdictStatus.SUPPORTED -> VerdictVisuals(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            badgeContainer = MaterialTheme.colorScheme.secondaryContainer,
            badgeContent = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Rounded.CheckCircle
        )
        ClaimVerdictStatus.UPDATED -> VerdictVisuals(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            badgeContainer = MaterialTheme.colorScheme.primaryContainer,
            badgeContent = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Rounded.Info
        )
        ClaimVerdictStatus.DISPUTED -> VerdictVisuals(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            badgeContainer = MaterialTheme.colorScheme.errorContainer,
            badgeContent = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Rounded.ErrorOutline
        )
        ClaimVerdictStatus.UNVERIFIED -> VerdictVisuals(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
            badgeContainer = MaterialTheme.colorScheme.tertiaryContainer,
            badgeContent = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.AutoMirrored.Rounded.HelpOutline
        )
    }

    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, badgeContainer),
        modifier = modifier.fillMaxWidth()
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
                Surface(
                    color = badgeContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeContent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(status.displayLabelRes()),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeContent
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isFactChecking) { onVerifySingleClaim(verdict.claim) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.cd_reverify_this_claim),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_recheck),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = verdict.claim,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (verdict.explanation.isNotBlank()) {
                Text(
                    text = verdict.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class VerdictVisuals(
    val containerColor: androidx.compose.ui.graphics.Color,
    val badgeContainer: androidx.compose.ui.graphics.Color,
    val badgeContent: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
