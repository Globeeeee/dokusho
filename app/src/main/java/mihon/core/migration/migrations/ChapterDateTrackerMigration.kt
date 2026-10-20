package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.chapter.repository.ChapterRepository

/**
 * Clears upload dates that were stamped at fetch time, so the chapter lists stop saying "today".
 * Real dates come back on the next refresh through [eu.kanade.tachiyomi.data.library.ChapterDateTracker].
 * Runs once; the app's versionCode is below the upstream migration numbers, so it is gated by a flag.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class ChapterDateTrackerMigration(
    private val chapterRepository: ChapterRepository,
    private val preferenceStore: PreferenceStore,
) : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val done = preferenceStore.getBoolean(DONE_KEY, false)
        if (!done.get()) {
            chapterRepository.clearFetchStampedUploadDates()
            done.set(true)
        }
        true
    }

    private companion object {
        const val DONE_KEY = "dokusho_chapter_fetch_dates_cleared"
    }
}
