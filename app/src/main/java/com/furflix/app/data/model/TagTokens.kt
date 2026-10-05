package com.furflix.app.data.model

/**
 * FA extended-mode tag tokens: a bare "tag" includes, "!tag" excludes.
 * Chips display the base word (strikethrough when excluded) and flip
 * polarity in place, so token order is preserved across edits.
 */
fun normalizeTagToken(raw: String): String? {
    val excluded = raw.trimStart().startsWith("!")
    val base = raw.trim().trimStart('!').trim().lowercase()
    return when {
        base.isEmpty() -> null
        excluded -> "!$base"
        else -> base
    }
}

/** Replaces any existing polarity of the same base and appends at the end. */
fun addTagToken(tags: List<String>, raw: String): List<String> {
    val token = normalizeTagToken(raw) ?: return tags
    val base = token.removePrefix("!")
    return (tags - base - "!$base") + token
}

/** Flips the polarity of [token] in place; unknown tokens are returned unchanged. */
fun toggleTagExclusion(tags: List<String>, token: String): List<String> {
    val index = tags.indexOf(token)
    if (index < 0) return tags
    val flipped = if (token.startsWith("!")) token.drop(1) else "!$token"
    return tags.toMutableList().also { it[index] = flipped }
}
