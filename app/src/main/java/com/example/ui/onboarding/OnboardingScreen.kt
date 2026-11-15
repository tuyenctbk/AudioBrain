package com.example.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SearchCobalt
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.VerifiedEmerald
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin

private data class OnboardingPageData(
    val badge: String,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
    val highlights: List<Pair<ImageVector, String>>
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        OnboardingPageData(
            badge = stringResource(R.string.onboarding_page1_badge),
            title = stringResource(R.string.onboarding_page1_title),
            subtitle = stringResource(R.string.onboarding_page1_subtitle),
            accentColor = StudioAmber,
            highlights = listOf(
                Icons.Rounded.Mic to stringResource(R.string.onboarding_page1_highlight1),
                Icons.Rounded.GraphicEq to stringResource(R.string.onboarding_page1_highlight2)
            )
        ),
        OnboardingPageData(
            badge = stringResource(R.string.onboarding_page2_badge),
            title = stringResource(R.string.onboarding_page2_title),
            subtitle = stringResource(R.string.onboarding_page2_subtitle),
            accentColor = SearchCobalt,
            highlights = listOf(
                Icons.Rounded.TravelExplore to stringResource(R.string.onboarding_page2_highlight1),
                Icons.AutoMirrored.Rounded.FactCheck to stringResource(R.string.onboarding_page2_highlight2)
            )
        ),
        OnboardingPageData(
            badge = stringResource(R.string.onboarding_page3_badge),
            title = stringResource(R.string.onboarding_page3_title),
            subtitle = stringResource(R.string.onboarding_page3_subtitle),
            accentColor = VerifiedEmerald,
            highlights = listOf(
                Icons.Rounded.CheckCircle to stringResource(R.string.onboarding_page3_highlight1),
                Icons.Rounded.School to stringResource(R.string.onboarding_page3_highlight2)
            )
        ),
        OnboardingPageData(
            badge = stringResource(R.string.onboarding_page4_badge),
            title = stringResource(R.string.onboarding_page4_title),
            subtitle = stringResource(R.string.onboarding_page4_subtitle),
            accentColor = StudioAmber,
            highlights = listOf(
                Icons.Rounded.AutoAwesome to stringResource(R.string.onboarding_page4_highlight1),
                Icons.Rounded.Shield to stringResource(R.string.onboarding_page4_highlight2)
            )
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
            .safeDrawingPadding()
            .testTag("onboarding_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Brand + Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (!isLastPage) {
                    TextButton(
                        onClick = onFinishOnboarding,
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction).absoluteValue
                val scale = (1f - (pageOffset * 0.08f)).coerceIn(0.9f, 1f)
                val alpha = (1f - (pageOffset * 0.45f)).coerceIn(0.4f, 1f)

                OnboardingPageView(
                    pageIndex = pageIndex,
                    data = pages[pageIndex],
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                )
            }

            // Page Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (selected) 28.dp else 8.dp,
                        animationSpec = spring(stiffness = 400f),
                        label = "indicator_width"
                    )
                    val color by animateColorAsState(
                        targetValue = if (selected) {
                            pages[pagerState.currentPage].accentColor
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        animationSpec = tween(250),
                        label = "indicator_color"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Primary Action Button
            Button(
                onClick = {
                    if (isLastPage) {
                        onFinishOnboarding()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = pagerState.currentPage + 1,
                                animationSpec = tween(380, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pages[pagerState.currentPage].accentColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_next_button")
            ) {
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                    label = "onboarding_cta_text"
                ) { last ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (last) {
                                stringResource(R.string.onboarding_get_started)
                            } else {
                                stringResource(R.string.onboarding_continue)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OnboardingPageView(
    pageIndex: Int,
    data: OnboardingPageData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated Hero Visual Card
        OnboardingHeroIllustration(
            pageIndex = pageIndex,
            accentColor = data.accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            color = data.accentColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, data.accentColor.copy(alpha = 0.25f))
        ) {
            Text(
                text = data.badge,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = data.accentColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = data.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            data.highlights.forEach { (icon, text) ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(data.accentColor.copy(alpha = 0.14f))
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = data.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeroIllustration(
    pageIndex: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "onboarding_hero_$pageIndex")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val centerIcon = when (pageIndex) {
        0 -> Icons.Rounded.Mic
        1 -> Icons.Rounded.TravelExplore
        2 -> Icons.Rounded.School
        else -> Icons.Rounded.AutoAwesome
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = ObsidianBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val barCount = 26
                val spacing = size.width / (barCount + 4)

                // Ambient glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(size.width / 2f, centerY),
                        radius = size.height * 0.65f
                    ),
                    center = Offset(size.width / 2f, centerY),
                    radius = size.height * 0.65f
                )

                // Animated acoustic wave bars
                for (i in 0 until barCount) {
                    val x = (i + 2.5f) * spacing
                    val norm = (i - barCount / 2f) / (barCount / 2f)
                    val envelope = (1f - norm * norm * 0.65f).coerceAtLeast(0.2f)
                    val wave = 0.45f + 0.55f * ((sin(phase + i * 0.55f) + 1f) / 2f)
                    val barHeight = size.height * 0.52f * envelope * wave
                    drawLine(
                        color = accentColor.copy(alpha = 0.35f + 0.35f * envelope),
                        start = Offset(x, centerY - barHeight / 2f),
                        end = Offset(x, centerY + barHeight / 2f),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Concentric verification ring
                drawCircle(
                    color = accentColor.copy(alpha = 0.28f),
                    radius = 52.dp.toPx() * pulse,
                    center = Offset(size.width / 2f, centerY),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Surface(
                color = ObsidianSurface,
                shape = CircleShape,
                border = BorderStroke(2.dp, accentColor),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(76.dp)
                    .scale(pulse)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = centerIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}
