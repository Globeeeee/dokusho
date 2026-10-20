package eu.kanade.tachiyomi.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.tachiyomi.ui.reader.viewer.novel.NovelBlock
import java.io.Closeable
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Lays chapters out on A5 pages with [PdfDocument]: each chapter starts a page with its heading, text
 * flows with [StaticLayout], images fit the text width and page numbers sit in the footer.
 */
class PdfWriter(private val output: OutputStream) : Closeable {

    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 0f
    private var closed = false

    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = BODY_SIZE
        typeface = Typeface.SERIF
    }
    private val headingPaint = TextPaint(bodyPaint).apply {
        textSize = HEADING_SIZE
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val subheadingPaint = TextPaint(headingPaint).apply { textSize = SUBHEADING_SIZE }
    private val quotePaint = TextPaint(bodyPaint).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
    private val footerPaint = TextPaint(bodyPaint).apply {
        textSize = FOOTER_SIZE
        color = Color.DKGRAY
        textAlign = Paint.Align.CENTER
    }
    private val rulePaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 0.8f
    }
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    var chapterCount = 0
        private set

    /** One chapter on fresh pages. [image] returns the bytes of an image block, or null to leave it out. */
    suspend fun addChapter(title: String, blocks: List<NovelBlock>, image: suspend (String) -> ByteArray?) {
        chapterCount++
        newPage()
        drawText(SpannableStringBuilder(title), headingPaint, Layout.Alignment.ALIGN_CENTER)
        y += HEADING_GAP
        for (block in blocks) {
            when (block) {
                is NovelBlock.Paragraph -> drawText(block.text.toSpanned(), bodyPaint)
                is NovelBlock.Heading -> drawText(block.text.toSpanned(), subheadingPaint)
                is NovelBlock.Quote -> drawText(block.text.toSpanned(), quotePaint, indent = QUOTE_INDENT)
                NovelBlock.Divider -> drawDivider()
                is NovelBlock.Image -> image(block.url)?.let(::drawImage)
            }
        }
    }

    /** Writes the document. Nothing is written when no chapter was added. */
    fun finish() {
        if (closed) return
        finishPage()
        document.writeTo(output)
        close()
    }

    override fun close() {
        if (closed) return
        closed = true
        page?.let { document.finishPage(it) }
        page = null
        document.close()
    }

    private fun newPage() {
        finishPage()
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        y = MARGIN
    }

    private fun finishPage() {
        val current = page ?: return
        current.canvas.drawText(
            pageNumber.toString(),
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - MARGIN / 2f,
            footerPaint,
        )
        document.finishPage(current)
        page = null
    }

    private val canvas: Canvas
        get() = page?.canvas ?: run {
            newPage()
            page!!.canvas
        }

    private val bottom: Float
        get() = PAGE_HEIGHT - MARGIN - FOOTER_SPACE

    /** Draws text line by line, carrying on to new pages where it runs out of room. */
    private fun drawText(
        text: CharSequence,
        paint: TextPaint,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        indent: Float = 0f,
    ) {
        val width = (CONTENT_WIDTH - 2 * indent).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, LINE_SPACING)
            .setIncludePad(false)
            .build()
        var line = 0
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var end = line
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= bottom - y) end++
            if (end == line) {
                // Not even one line fits: next page, unless this page is still empty.
                if (y > MARGIN) {
                    newPage()
                    continue
                }
                end = line + 1
            }
            val lineBottom = layout.getLineBottom(end - 1)
            val c = canvas
            c.save()
            c.translate(MARGIN + indent, y - top)
            c.clipRect(0, top, width, lineBottom)
            layout.draw(c)
            c.restore()
            y += lineBottom - top
            line = end
            if (line < layout.lineCount) newPage()
        }
        y += PARAGRAPH_GAP
    }

    private fun drawDivider() {
        if (y + DIVIDER_SPACE > bottom) newPage()
        val mid = y + DIVIDER_SPACE / 2
        val quarter = CONTENT_WIDTH / 4
        canvas.drawLine(MARGIN + quarter * 1.5f, mid, MARGIN + quarter * 2.5f, mid, rulePaint)
        y += DIVIDER_SPACE
    }

    /** Decodes at no more than twice the drawn size so a big image never needs a big bitmap. */
    private fun drawImage(bytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= CONTENT_WIDTH * 2) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        ) ?: return
        try {
            val maxHeight = bottom - MARGIN
            val scale = min(CONTENT_WIDTH / bitmap.width, maxHeight / bitmap.height).coerceAtMost(1f * sample)
            val width = max(1f, bitmap.width * scale)
            val height = max(1f, bitmap.height * scale)
            if (y + height > bottom) newPage()
            val left = MARGIN + (CONTENT_WIDTH - width) / 2
            canvas.drawBitmap(
                bitmap,
                Rect(0, 0, bitmap.width, bitmap.height),
                RectF(left, y, left + width, y + height),
                imagePaint,
            )
            y += height + PARAGRAPH_GAP
        } finally {
            bitmap.recycle()
        }
    }

    private fun AnnotatedString.toSpanned(): CharSequence {
        val builder = SpannableStringBuilder(text)
        spanStyles.forEach { range ->
            val start = range.start.coerceIn(0, text.length)
            val end = range.end.coerceIn(start, text.length)
            if (range.item.fontStyle == FontStyle.Italic) {
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if ((range.item.fontWeight?.weight ?: 0) >= FontWeight.Bold.weight) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    private companion object {
        // A5 in PostScript points (148 x 210 mm).
        const val PAGE_WIDTH = 420
        const val PAGE_HEIGHT = 595
        const val MARGIN = 42f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val FOOTER_SPACE = 10f
        const val BODY_SIZE = 11f
        const val HEADING_SIZE = 17f
        const val SUBHEADING_SIZE = 13f
        const val FOOTER_SIZE = 9f
        const val LINE_SPACING = 1.25f
        const val PARAGRAPH_GAP = 6f
        const val HEADING_GAP = 12f
        const val QUOTE_INDENT = 18f
        const val DIVIDER_SPACE = 24f
    }
}
