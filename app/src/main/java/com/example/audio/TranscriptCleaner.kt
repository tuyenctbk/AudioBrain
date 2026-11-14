package com.example.audio

/**
 * Deterministic post-processor that complements Gemini's multimodal noise-suppression
 * and disfluency-removal pipeline by stripping residual acoustic noise markers,
 * verbal fillers ("um", "uh", "erm", "ah", "you know", "I mean", etc.), and accidental
 * word stutters while preserving [MM:SS] timestamps and speaker attributions.
 */
object TranscriptCleaner {

    private val TIMESTAMP_SPEAKER_PREFIX_REGEX = Regex(
        pattern = """^(\s*(?:\[\d{1,2}:\d{2}(?::\d{2})?]\s*)?(?:Speaker\s+\d+\s*:\s*|[A-Z][a-zA-Z0-9 _-]{0,20}:\s*)?)(.*)$"""
    )

    // Matches bracketed or parenthesized non-speech acoustic noise annotations,
    // while never matching [MM:SS] or [HH:MM:SS] timestamps.
    private val NOISE_MARKER_REGEX = Regex(
        pattern = """[\[(]\s*(?:background\s+noise|noise|static|mic\s+bump|wind(?:\s+noise)?|cough(?:ing)?|clears?\s+throat|throat\s+clearing|sigh(?:s|ing)?|breath(?:ing)?|inhales?|exhales?|silence|pause|inaudible|unintelligible|crosstalk|ambient\s+noise|music|applause|laughter| laughs?)\s*[])]""",
        option = RegexOption.IGNORE_CASE
    )

    // Standalone hesitation sounds ("um", "uh", "erm", "er", "ah", "hmm", "uh-huh", "mhm")
    // followed by optional punctuation.
    private val HESITATION_FILLER_REGEX = Regex(
        pattern = """(?<![a-zA-Z0-9])(?:u+m+|u+h+|e+r+m+|e+r+|a+h+|h+m+|uh-huh|mm-hmm|mhm)(?![a-zA-Z0-9])[\s,.;:-]*""",
        option = RegexOption.IGNORE_CASE
    )

    // Discourse crutches when used as parenthetical or sentence-initial fillers with commas
    // e.g., "you know,", "I mean,", "like,", "basically,", "sort of,", "kind of,"
    private val DISCOURSE_CRUTCH_WITH_COMMA_REGEX = Regex(
        pattern = """(?:^|(?<=[\s,;:.!?]))(?:you\s+know|i\s+mean|basically|literally)\s*,\s*""",
        option = RegexOption.IGNORE_CASE
    )

    // Sentence-initial or comma-delimited ", like," / "Like, "
    private val INTERJECTED_LIKE_REGEX = Regex(
        pattern = """(?:^\s*like\s*,\s*|,\s*like\s*,\s*)""",
        option = RegexOption.IGNORE_CASE
    )

    // Accidental immediate single-word stutters: "I I", "the the", "we we", "to to", "and and"
    private val STUTTER_REPEAT_REGEX = Regex(
        pattern = """\b(i|we|you|they|he|she|it|the|a|an|to|in|on|at|for|of|is|are|was|were|that|this|and|but|so|or|if)\s+\1\b[\s,]*""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Cleans acoustic noise markers, hesitation fillers, discourse crutches, and stutters
     * line-by-line while preserving `[MM:SS]` timestamps and `Speaker X:` prefixes.
     */
    fun cleanFillerWordsAndNoiseMarkers(rawTranscript: String): String {
        if (rawTranscript.isBlank()) return ""

        return rawTranscript
            .lines()
            .map { line -> cleanSingleLine(line) }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun cleanSingleLine(line: String): String {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return ""

        val match = TIMESTAMP_SPEAKER_PREFIX_REGEX.matchEntire(trimmed)
        val prefix = match?.groupValues?.get(1).orEmpty()
        var body = match?.groupValues?.get(2) ?: trimmed

        // 1. Strip non-speech noise markers like [background noise], [cough], (clears throat)
        body = NOISE_MARKER_REGEX.replace(body, " ")

        // 2. Strip comma-delimited ", like, " or leading "Like, "
        body = INTERJECTED_LIKE_REGEX.replace(body) { matchResult ->
            if (matchResult.value.trimStart().startsWith(",")) ", " else ""
        }

        // 3. Strip discourse crutches followed by comma ("you know,", "I mean,", "basically,")
        body = DISCOURSE_CRUTCH_WITH_COMMA_REGEX.replace(body, "")

        // 4. Strip hesitation fillers ("um", "uh", "erm", "ah", "hmm")
        body = HESITATION_FILLER_REGEX.replace(body, " ")

        // 5. Collapse immediate accidental word stutters ("I I", "the the", "we we")
        body = STUTTER_REPEAT_REGEX.replace(body) { m ->
            "${m.groupValues[1]} "
        }

        // 6. Normalize spacing & punctuation artifacts left after filler removal
        body = body
            .replace(Regex("""\s+([,.;:!?])"""), "$1")
            .replace(Regex("""([,.;:])\s*[,.;:]+"""), "$1")
            .replace(Regex("""^\s*[,;:-]+\s*"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        if (body.isEmpty()) {
            return ""
        }

        // 7. Ensure the first alphabetical character of the spoken sentence is capitalized
        // if a leading filler word ("Um, we..." -> "we...") was removed.
        val firstChar = body.first()
        if (firstChar.isLowerCase()) {
            body = firstChar.uppercaseChar() + body.substring(1)
        }

        val normalizedPrefix = prefix.trim().let { if (it.isNotEmpty()) "$it " else "" }
        return "$normalizedPrefix$body".trim()
    }
}
