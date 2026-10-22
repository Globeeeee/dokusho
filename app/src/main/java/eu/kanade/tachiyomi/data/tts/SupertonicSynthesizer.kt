package eu.kanade.tachiyomi.data.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

/** Voices shipped with the Supertonic 3 weights. */
internal object SupertonicVoices {
    const val DEFAULT = "F1"

    val all: List<Pair<String, String>> = listOf(
        "F1" to "Female 1",
        "F2" to "Female 2",
        "F3" to "Female 3",
        "F4" to "Female 4",
        "F5" to "Female 5",
        "M1" to "Male 1",
        "M2" to "Male 2",
        "M3" to "Male 3",
        "M4" to "Male 4",
        "M5" to "Male 5",
    )

    fun resolve(id: String?): String {
        val name = id?.substringBefore('.').orEmpty()
        return all.firstOrNull { it.first == name }?.first ?: DEFAULT
    }
}

/**
 * On-device Supertonic 3 synthesis. One utterance goes through the duration predictor, the text
 * encoder, a few denoising steps, then the vocoder. The result is mono audio at [sampleRate].
 */
internal class SupertonicSynthesizer private constructor(
    private val env: OrtEnvironment,
    private val durationPredictor: OrtSession,
    private val textEncoder: OrtSession,
    private val vectorEstimator: OrtSession,
    private val vocoder: OrtSession,
    private val indexer: IntArray,
    val sampleRate: Int,
    private val baseChunkSize: Int,
    private val chunkCompressFactor: Int,
    private val latentDim: Int,
    private val voices: File,
) {
    private val lock = Any()
    private var closed = false
    private val random = Random()
    private val styles = HashMap<String, Style>()

    fun synthesize(text: String, lang: String, voiceId: String, speed: Float): FloatArray? {
        val processed = preprocess(text, lang)
        if (processed.isEmpty()) return FloatArray(0)
        val ids = LongArray(processed.length) { index ->
            val code = processed[index].code
            if (code in indexer.indices) indexer[code].toLong() else -1L
        }
        val mask = FloatArray(ids.size) { 1f }
        val style = loadStyle(voiceId)
        val safeSpeed = speed.coerceIn(0.5f, 3f)
        val duration = runFloat(
            durationPredictor,
            mapOf(
                "text_ids" to longTensor(ids, longArrayOf(1, ids.size.toLong())),
                "style_dp" to floatTensor(style.dp, style.dpShape),
                "text_mask" to floatTensor(mask, longArrayOf(1, 1, mask.size.toLong())),
            ),
        ) ?: return null
        if (duration.data.isEmpty()) return null
        val seconds = (duration.data[0] / safeSpeed).coerceAtLeast(0.05f)
        val textEmb = runFloat(
            textEncoder,
            mapOf(
                "text_ids" to longTensor(ids, longArrayOf(1, ids.size.toLong())),
                "style_ttl" to floatTensor(style.ttl, style.ttlShape),
                "text_mask" to floatTensor(mask, longArrayOf(1, 1, mask.size.toLong())),
            ),
        ) ?: return null
        var latent = noisyLatent(seconds)
        val total = FloatArray(1) { STEPS.toFloat() }
        for (step in 0 until STEPS) {
            val next = runFloat(
                vectorEstimator,
                mapOf(
                    "noisy_latent" to floatTensor(latent.data, latent.shape),
                    "text_emb" to floatTensor(textEmb.data, textEmb.shape),
                    "style_ttl" to floatTensor(style.ttl, style.ttlShape),
                    "text_mask" to floatTensor(mask, longArrayOf(1, 1, mask.size.toLong())),
                    "latent_mask" to floatTensor(latent.mask, longArrayOf(1, 1, latent.shape[2])),
                    "current_step" to floatTensor(floatArrayOf(step.toFloat()), longArrayOf(1)),
                    "total_step" to floatTensor(total, longArrayOf(1)),
                ),
            ) ?: return null
            latent = Latent(next.data, next.shape, latent.mask)
        }
        val wav = runFloat(vocoder, mapOf("latent" to floatTensor(latent.data, latent.shape))) ?: return null
        return wav.data
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            durationPredictor.close()
            textEncoder.close()
            vectorEstimator.close()
            vocoder.close()
        }
    }

    private fun noisyLatent(seconds: Float): Latent {
        val wavLen = seconds * sampleRate
        val wavFrames = wavLen.toLong()
        val chunkSize = baseChunkSize * chunkCompressFactor
        val latentLen = ((wavLen + chunkSize - 1.0) / chunkSize).toInt().coerceAtLeast(1)
        val channels = latentDim * chunkCompressFactor
        val latentSize = baseChunkSize * chunkCompressFactor
        val valid = ((wavFrames + latentSize - 1) / latentSize).toInt().coerceIn(1, latentLen)
        val mask = FloatArray(latentLen) { index -> if (index < valid) 1f else 0f }
        val data = FloatArray(channels * latentLen)
        for (channel in 0 until channels) {
            val row = channel * latentLen
            for (time in 0 until latentLen) {
                data[row + time] = random.nextGaussian().toFloat() * mask[time]
            }
        }
        return Latent(data, longArrayOf(1, channels.toLong(), latentLen.toLong()), mask)
    }

    private fun loadStyle(voiceId: String): Style {
        val name = SupertonicVoices.resolve(voiceId)
        styles[name]?.let { return it }
        val file = File(voices, "$name.json")
        val json = JSONObject(file.readText())
        val ttl = json.getJSONObject("style_ttl").toMatrix()
        val dp = json.getJSONObject("style_dp").toMatrix()
        return Style(ttl.first, ttl.second, dp.first, dp.second).also { styles[name] = it }
    }

    private fun runFloat(session: OrtSession, inputs: Map<String, OnnxTensor>): Tensor? {
        try {
            synchronized(lock) {
                if (closed) return null
                session.run(inputs).use { result ->
                    val value = result.get(0) as OnnxTensor
                    val buffer = value.floatBuffer
                    buffer.rewind()
                    val data = FloatArray(buffer.remaining())
                    buffer.get(data)
                    return Tensor(data, value.info.shape)
                }
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun floatTensor(data: FloatArray, shape: LongArray): OnnxTensor {
        val bytes = ByteBuffer.allocateDirect(data.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        val buffer = bytes.asFloatBuffer()
        buffer.put(data)
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    private fun longTensor(data: LongArray, shape: LongArray): OnnxTensor {
        val bytes = ByteBuffer.allocateDirect(data.size * Long.SIZE_BYTES).order(ByteOrder.nativeOrder())
        val buffer = bytes.asLongBuffer()
        buffer.put(data)
        buffer.rewind()
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    private class Tensor(val data: FloatArray, val shape: LongArray)

    private class Latent(val data: FloatArray, val shape: LongArray, val mask: FloatArray)

    private class Style(
        val ttl: FloatArray,
        val ttlShape: LongArray,
        val dp: FloatArray,
        val dpShape: LongArray,
    )

    companion object {
        private const val STEPS = 5

        fun load(context: Context): SupertonicSynthesizer {
            val app = context.applicationContext
            val config = JSONObject(SupertonicModel.file(app, "onnx/tts.json").readText())
            val indexerJson = JSONArray(SupertonicModel.file(app, "onnx/unicode_indexer.json").readText())
            val indexer = IntArray(indexerJson.length()) { indexerJson.getInt(it) }
            val env = OrtEnvironment.getEnvironment()
            fun session(name: String): OrtSession {
                val options = OrtSession.SessionOptions().apply {
                    // Synthesis has to outpace speech for read-ahead to stay ahead; two threads were not
                    // enough on mid-range phones. Leave the other cores to playback and the UI.
                    setIntraOpNumThreads((Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4))
                    // Basic rewrites (constant folding, redundant nodes) cost little at load and speed up
                    // every sentence; the extended ones are what made the first load slow.
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                options.use {
                    return env.createSession(SupertonicModel.file(app, "onnx/$name").absolutePath, options)
                }
            }
            return SupertonicSynthesizer(
                env = env,
                durationPredictor = session("duration_predictor.onnx"),
                textEncoder = session("text_encoder.onnx"),
                vectorEstimator = session("vector_estimator.onnx"),
                vocoder = session("vocoder.onnx"),
                indexer = indexer,
                sampleRate = config.getJSONObject("ae").getInt("sample_rate"),
                baseChunkSize = config.getJSONObject("ae").getInt("base_chunk_size"),
                chunkCompressFactor = config.getJSONObject("ttl").getInt("chunk_compress_factor"),
                latentDim = config.getJSONObject("ttl").getInt("latent_dim"),
                voices = SupertonicModel.file(app, "voice_styles"),
            )
        }
    }
}

private fun preprocess(text: String, lang: String): String {
    var cleaned = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKD)
    cleaned = buildString(cleaned.length) {
        for (char in cleaned) {
            val code = char.code
            if (code > 0xFFFF || code in 0x2600..0x27BF) continue
            append(REPLACEMENTS[char] ?: char)
        }
    }
    cleaned = cleaned.replace("@", " at ")
        .replace("e.g.,", "for example, ")
        .replace("i.e.,", "that is, ")
        .replace(Regex("[♥☆♡©\\\\]"), "")
        .replace(Regex("\\s+([,!.?;:])"), "$1")
        .replace(Regex("\\s+'"), "'")
        .replace(Regex("([\"'`])\\1+"), "$1")
        .replace(Regex("\\s+"), " ")
        // An ellipsis inside a line is a pause, not the end of a sentence; read it like a comma.
        .replace(Regex("\\s*(?:\\.{2,}|…)+\\s*(?=\\S)"), ", ")
        .replace(Regex("(?:\\.{2,}|…)+$"), ".")
        .trim()
    // Nothing to say ("...", "* * *"): no clip, so playback moves straight on instead of stalling on noise.
    if (cleaned.none { it.isLetterOrDigit() }) return ""
    if (cleaned.last() !in ".!?;:,'\"')}…。」』】〉》›»") cleaned += "."
    return "<$lang>$cleaned</$lang>"
}

private fun JSONObject.toMatrix(): Pair<FloatArray, LongArray> {
    val dims = getJSONArray("dims")
    val shape = LongArray(dims.length()) { dims.getLong(it) }
    val count = shape.fold(1L) { acc, dim -> acc * dim }.toInt()
    val data = FloatArray(count)
    flatten(getJSONArray("data"), data, 0)
    return data to shape
}

private fun flatten(array: JSONArray, out: FloatArray, index: Int): Int {
    var cursor = index
    for (i in 0 until array.length()) {
        when (val value = array.get(i)) {
            is JSONArray -> cursor = flatten(value, out, cursor)
            is Number -> if (cursor < out.size) out[cursor++] = value.toFloat()
        }
    }
    return cursor
}

private val REPLACEMENTS = mapOf(
    '–' to '-',
    '‑' to '-',
    '—' to '-',
    '_' to ' ',
    '“' to '"',
    '”' to '"',
    '‘' to '\'',
    '’' to '\'',
    '´' to '\'',
    '`' to '\'',
    '[' to ' ',
    ']' to ' ',
    '|' to ' ',
    '/' to ' ',
    '#' to ' ',
    '→' to ' ',
    '←' to ' ',
)

/**
 * Splits a paragraph into the clips Supertonic voices, in order. Each clip is an exact slice of [text], so
 * playback can find it again for highlighting. Sentences end at `.`, `!` or `?` followed by space, never at
 * an ellipsis ("Wait... no"), which is a pause inside one sentence. Very short sentences ("Yes.") join their
 * neighbour: each clip pays the model's fixed cost, and a run of tiny clips sounds choppy.
 */
internal fun supertonicChunks(text: String, maxLen: Int = 240): List<String> {
    val sentences = mutableListOf<IntRange>()
    var from = 0
    for (match in SENTENCE_END.findAll(text)) {
        val end = match.range.first + match.value.trimEnd().length
        sentenceRange(text, from, end)?.let(sentences::add)
        from = match.range.last + 1
    }
    sentenceRange(text, from, text.length)?.let(sentences::add)
    if (sentences.isEmpty()) return emptyList()

    val merged = mutableListOf<IntRange>()
    for (sentence in sentences) {
        val last = merged.lastOrNull()
        val short = last != null && (last.length() < SHORT_SENTENCE || sentence.length() < SHORT_SENTENCE)
        if (short && sentence.last - last!!.first + 1 <= MERGED_MAX) {
            merged[merged.lastIndex] = last.first..sentence.last
        } else {
            merged += sentence
        }
    }
    return merged.flatMap { range ->
        val sentence = text.substring(range)
        if (sentence.length <= maxLen) listOf(sentence) else splitAtLength(sentence, maxLen)
    }
}

/** The trimmed range of `text[from until to]`, or null when it is blank. */
private fun sentenceRange(text: String, from: Int, to: Int): IntRange? {
    var start = from
    var end = to
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return if (start < end) start until end else null
}

private fun IntRange.length() = last - first + 1

/** A sentence end: `.`, `!` or `?` not part of an ellipsis, closing quotes or brackets, then whitespace. */
private val SENTENCE_END = Regex("""(?<![.…])[.!?](?![.…])["'”’)\]]*\s+""")
private const val SHORT_SENTENCE = 40
private const val MERGED_MAX = 160

private fun splitAtLength(text: String, maxLen: Int): List<String> {
    val chunks = mutableListOf<String>()
    var rest = text.trim()
    while (rest.length > maxLen) {
        val window = rest.take(maxLen)
        val breakAt = window.lastIndexOf(' ').takeIf { it > maxLen / 2 } ?: maxLen
        chunks += rest.take(breakAt).trim()
        rest = rest.substring(breakAt).trim()
    }
    if (rest.isNotEmpty()) chunks += rest
    return chunks
}

/** Languages the Supertonic 3 model reads, as the codes its text tags use. */
object SupertonicLanguages {
    private val SUPPORTED = setOf(
        "en", "ko", "ja", "ar", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "hi", "hr", "hu",
        "id", "it", "lt", "lv", "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "vi",
    )

    fun supports(language: String) = language in SUPPORTED

    /** A source language ("pt-BR", "es-419", "all") as a plain language code, or null when it names none. */
    fun fromSource(lang: String?): String? =
        lang?.substringBefore('-')?.lowercase()?.takeUnless { it.isBlank() || it == "all" || it == "other" }
}

/**
 * The language of [text] from the letters it uses. Latin text can't be told apart this way and reads as
 * English; the source's language is preferred when it names one. Han text without kana comes back as
 * "zh" and Thai as "th", which Supertonic can't read.
 */
internal fun supertonicLanguage(text: String): String {
    var hangul = 0
    var kana = 0
    var han = 0
    var latin = 0
    var cyrillic = 0
    var ukrainian = 0
    var greek = 0
    var arabic = 0
    var devanagari = 0
    var thai = 0
    for (char in text) {
        when (char.code) {
            in 0xAC00..0xD7A3 -> hangul++
            in 0x3040..0x30FF -> kana++
            in 0x4E00..0x9FFF -> han++
            in 'A'.code..'Z'.code, in 'a'.code..'z'.code -> latin++
            in 0x0400..0x04FF -> {
                cyrillic++
                if (char in "іїєґІЇЄҐ") ukrainian++
            }
            in 0x0370..0x03FF -> greek++
            in 0x0600..0x06FF -> arabic++
            in 0x0900..0x097F -> devanagari++
            in 0x0E00..0x0E7F -> thai++
        }
    }
    val scripts = listOf(
        "ko" to hangul,
        // Japanese mixes kana with kanji; Han alone is Chinese.
        "ja" to if (kana > 0) kana + han else 0,
        "zh" to if (kana == 0) han else 0,
        (if (ukrainian > 0) "uk" else "ru") to cyrillic,
        "el" to greek,
        "ar" to arabic,
        "hi" to devanagari,
        "th" to thai,
    )
    val (language, count) = scripts.maxBy { it.second }
    return if (count > latin) language else "en"
}
