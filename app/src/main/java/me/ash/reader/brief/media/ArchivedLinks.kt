package me.ash.reader.brief.media

/**
 * Read You remembers the link of every article its clean-up clears (archived_article), so a local
 * feed that still lists it doesn't add it again. It never forgot them, so the list only grew.
 * Here a link is forgotten once the feed has stopped listing it: missing for [MISSES] syncs in a
 * row and for at least [MIN_MISSING_MS]. A feed that couldn't be read (nothing listed) doesn't
 * count. Only local feeds use these notes; for other accounts they aren't kept (see BriefCleanup).
 * The counts are kept in Brief You's own database (archived_miss). Brief You.
 */
object ArchivedLinks {
    const val MISSES = 5
    private const val MIN_MISSING_MS = 3L * 24 * 60 * 60 * 1000

    /**
     * After a local feed's sync: [archived] is what it remembers, [listed] the links the feed
     * lists now.
     */
    suspend fun tidy(feedId: String, archived: Set<String>, listed: List<String>) {
        runCatching {
            if (listed.isEmpty()) return // couldn't be read, or empty for now: nothing is counted
            val dao = ArticleMedia.dao ?: return
            val articles = ArticleMedia.articles ?: return
            val known = dao.misses(feedId).associateBy { it.link }
            if (archived.isEmpty() && known.isEmpty()) return
            val listedNow = listed.toHashSet()
            val now = System.currentTimeMillis()
            val changed = mutableListOf<ArchivedMiss>()
            val forget = mutableListOf<String>()
            for (link in archived) {
                if (link in listedNow) continue
                val miss = known[link]
                val misses = (miss?.misses ?: 0) + 1
                val since = miss?.since ?: now
                when {
                    misses >= MISSES && now - since >= MIN_MISSING_MS -> forget += link
                    // Counted enough: waits for the days to pass without being written again.
                    miss != null && miss.misses >= MISSES -> Unit
                    else -> changed += ArchivedMiss(feedId, link, misses.coerceAtMost(MISSES), since)
                }
            }
            // Listed again (the count starts over), forgotten, or no longer remembered.
            val drop = known.keys.filter { it in listedNow || it !in archived } + forget
            forget.chunked(CHUNK).forEach { articles.forgetArchivedLinks(feedId, it) }
            drop.chunked(CHUNK).forEach { dao.deleteMisses(feedId, it) }
            changed.chunked(CHUNK).forEach { dao.putMisses(it) }
        }
    }

    private const val CHUNK = 500
}
