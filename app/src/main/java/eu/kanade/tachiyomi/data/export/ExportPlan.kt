package eu.kanade.tachiyomi.data.export

import eu.kanade.tachiyomi.util.storage.DiskUtil

enum class ExportRange {
    ALL,
    DOWNLOADED,
    FROM_TO,
}

/** One output file: chapters [first] to [last] by reading position (1-based), and the positions it holds. */
data class ExportPart(val first: Int, val last: Int, val positions: List<Int>)

/**
 * Which chapters go into which file, and what the files are called. Positions are 1-based places in
 * reading order, so `ch0001` is always the first chapter of the novel.
 */
object ExportPlan {

    /** Reading positions picked by [range]; [from] and [to] are only read for [ExportRange.FROM_TO]. */
    fun select(
        chapterCount: Int,
        range: ExportRange,
        from: Int = 1,
        to: Int = chapterCount,
        isDownloaded: (position: Int) -> Boolean = { false },
    ): List<Int> {
        if (chapterCount <= 0) return emptyList()
        val all = 1..chapterCount
        return when (range) {
            ExportRange.ALL -> all.toList()
            ExportRange.DOWNLOADED -> all.filter(isDownloaded)
            ExportRange.FROM_TO -> {
                val start = minOf(from, to).coerceIn(1, chapterCount)
                val end = maxOf(from, to).coerceIn(1, chapterCount)
                (start..end).toList()
            }
        }
    }

    /** Splits [positions] into files of [chaptersPerFile] chapters; 0 or less keeps them in one file. */
    fun split(positions: List<Int>, chaptersPerFile: Int): List<ExportPart> {
        if (positions.isEmpty()) return emptyList()
        val chunks = if (chaptersPerFile <= 0) listOf(positions) else positions.chunked(chaptersPerFile)
        return chunks.map { ExportPart(it.first(), it.last(), it) }
    }

    /** `<Title> - ch0001-0100.epub`. */
    fun fileName(title: String, part: ExportPart, extension: String): String {
        val range = "ch%04d-%04d".format(part.first, part.last)
        return "${safeTitle(title)} - $range.$extension"
    }

    /** A title that is a valid file or folder name everywhere: no slash, no dash other than a plain hyphen. */
    fun safeTitle(title: String): String {
        val plain = title
            .replace(DASHES, "-")
            .replace(WHITESPACE, " ")
            .trim()
        return DiskUtil.buildValidFilename(plain, maxBytes = MAX_TITLE_BYTES)
            .trim()
            .ifEmpty { "Untitled" }
    }

    // Em dash, en dash, figure dash, horizontal bar, minus sign and friends.
    private val DASHES = Regex("[\\x{2010}-\\x{2015}\\x{2212}\\x{FE58}\\x{FE63}\\x{FF0D}]")
    private val WHITESPACE = Regex("\\s+")

    // Leaves room for " - ch0001-0100.epub" under the 255-byte limit.
    private const val MAX_TITLE_BYTES = 200
}
