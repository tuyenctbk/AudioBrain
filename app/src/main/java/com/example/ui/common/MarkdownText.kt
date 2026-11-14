package com.example.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lightweight Compose Markdown renderer for Gemini fact-check and chat responses.
 * Renders headings (#..###), bulleted/numbered lists, **bold**, *italic*, and `inline code`.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    codeBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val lines = remember(markdown) { markdown.lines() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        lines.forEach { rawLine ->
            val trimmed = rawLine.trim()
            when {
                trimmed.isEmpty() -> {
                    // Subtle vertical breather handled by spacedBy + small spacer
                }

                trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ") -> {
                    val headingText = trimmed.dropWhile { it == '#' }.trim()
                    val headingStyle = if (trimmed.startsWith("### ")) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = parseInlineMarkdown(headingText, accentColor, codeBackgroundColor),
                        style = headingStyle,
                        color = color,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val content = trimmed.drop(2).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                        Text(
                            text = parseInlineMarkdown(content, accentColor, codeBackgroundColor),
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                NUMBERED_LIST_REGEX.matches(trimmed) -> {
                    val match = NUMBERED_LIST_REGEX.find(trimmed)
                    val prefix = match?.groupValues?.getOrNull(1).orEmpty()
                    val content = match?.groupValues?.getOrNull(2).orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$prefix.",
                            style = style.copy(fontWeight = FontWeight.SemiBold),
                            color = accentColor
                        )
                        Text(
                            text = parseInlineMarkdown(content, accentColor, codeBackgroundColor),
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                else -> {
                    Text(
                        text = parseInlineMarkdown(trimmed, accentColor, codeBackgroundColor),
                        style = style,
                        color = color
                    )
                }
            }
        }
    }
}

private val NUMBERED_LIST_REGEX = Regex("^(\\d+)\\.\\s+(.+)$")

private fun parseInlineMarkdown(
    input: String,
    accentColor: Color,
    codeBackgroundColor: Color
): AnnotatedString {
    // Strip markdown links [label](url) down to clean label text so prose reads naturally
    val normalized = LINK_REGEX.replace(input) { match -> match.groupValues[1] }

    return buildAnnotatedString {
        var i = 0
        while (i < normalized.length) {
            when {
                normalized.startsWith("**", i) -> {
                    val end = normalized.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(normalized.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }

                normalized[i] == '`' -> {
                    val end = normalized.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackgroundColor,
                                color = accentColor
                            )
                        ) {
                            append(" ${normalized.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else {
                        append('`')
                        i += 1
                    }
                }

                normalized[i] == '*' && (i + 1 >= normalized.length || normalized[i + 1] != ' ') -> {
                    val end = normalized.indexOf('*', i + 1)
                    if (end != -1 && end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(normalized.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append('*')
                        i += 1
                    }
                }

                else -> {
                    append(normalized[i])
                    i += 1
                }
            }
        }
    }
}

private val LINK_REGEX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
