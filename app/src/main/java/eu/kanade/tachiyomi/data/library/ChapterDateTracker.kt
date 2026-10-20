package eu.kanade.tachiyomi.data.library

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Decides the upload date stored for a chapter.
 *
 * A date from the source always wins. When the source has none, a date that was only filled in at
 * fetch time (so the whole list said "today") is dropped, and a date stored from an earlier fetch
 * is kept.
 */
object ChapterDateTracker {

    fun uploadDate(source: Instant?, stored: Instant?, fetchedAt: Instant?): Instant? {
        if (source != null) return source
        if (stored != null && !isFetchStamp(stored, fetchedAt)) return stored
        return null
    }

    /** True when [upload] was invented at fetch time, within a couple of minutes of [fetchedAt]. */
    private fun isFetchStamp(upload: Instant, fetchedAt: Instant?): Boolean {
        if (fetchedAt == null) return false
        val gap = if (upload >= fetchedAt) upload - fetchedAt else fetchedAt - upload
        return gap < 2.minutes
    }
}
