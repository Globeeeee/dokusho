package eu.kanade.tachiyomi.data.tts

import android.content.Context
import java.io.File

/**
 * Pinned Supertonic 3 weights. Speech synthesis is separate; this only describes the files a
 * download has to place under the app's files directory.
 */
internal object SupertonicModel {
    private const val REVISION = "aafc6e32416a594460b32413efc49d7fe4ce6d46"
    private const val REPO = "supertone-oss-archive/supertonic-3"

    val files: List<ModelFile> = listOf(
        ModelFile("onnx/duration_predictor.onnx", 3_700_147),
        ModelFile("onnx/text_encoder.onnx", 36_416_150),
        ModelFile("onnx/tts.json", 8_253),
        ModelFile("onnx/unicode_indexer.json", 277_676),
        ModelFile("onnx/vector_estimator.onnx", 256_534_781),
        ModelFile("onnx/vocoder.onnx", 101_424_195),
        ModelFile("voice_styles/F1.json", 292_046),
        ModelFile("voice_styles/F2.json", 292_423),
        ModelFile("voice_styles/F3.json", 290_794),
        ModelFile("voice_styles/F4.json", 291_808),
        ModelFile("voice_styles/F5.json", 291_479),
        ModelFile("voice_styles/M1.json", 291_748),
        ModelFile("voice_styles/M2.json", 292_055),
        ModelFile("voice_styles/M3.json", 290_198),
        ModelFile("voice_styles/M4.json", 291_522),
        ModelFile("voice_styles/M5.json", 291_469),
    )

    val totalBytes: Long = files.sumOf { it.bytes }

    fun directory(context: Context): File = File(context.filesDir, "tts/supertonic-3")

    fun file(context: Context, path: String): File = File(directory(context), path)

    fun partial(context: Context, path: String): File {
        val dest = file(context, path)
        return File(dest.parentFile, dest.name + ".partial")
    }

    fun url(path: String): String = "https://huggingface.co/$REPO/resolve/$REVISION/$path?download=true"

    fun isReady(context: Context): Boolean = files.all { model ->
        file(context, model.path).length() == model.bytes
    }

    /** Bytes already on disk, including a partial file for the current download. */
    fun storedBytes(context: Context): Long = files.sumOf { model ->
        val complete = file(context, model.path)
        if (complete.length() == model.bytes) {
            model.bytes
        } else {
            partial(context, model.path).length().coerceAtMost(model.bytes)
        }
    }
}

internal data class ModelFile(val path: String, val bytes: Long)
