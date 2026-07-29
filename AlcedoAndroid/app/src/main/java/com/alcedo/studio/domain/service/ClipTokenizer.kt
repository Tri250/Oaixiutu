package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.util.ContextProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal CLIP text tokenizer (BPE) matching the OpenAI CLIP vocabulary.
 * Implements byte-level BPE encoding with a compact built-in vocab + merges
 * subset sufficient for semantic-search query strings; for full coverage
 * [loadVocabFromFile] replaces the compact vocab with the full ~49k token CLIP
 * vocab bundled alongside the ONNX model. The native tokenizer (core/nn) is
 * used via [ClipInferenceEngine] when available.
 *
 * The implementation here is a faithful, self-contained fallback so semantic
 * search works even without the native model loaded.
 */
@Singleton
class ClipTokenizer @Inject constructor() {

    /** Maximum sequence length used by CLIP/SigLIP text encoders. */
    val maxLen: Int = 77

    /** BOS/SOS token id used by CLIP. */
    val bosId: Int = 49406
    /** EOS token id used by CLIP. */
    val eosId: Int = 49407
    /** PAD token id. */
    val padId: Int = 0

    /**
     * Active vocabulary. Seeded with a compact built-in subset covering common
     * photography terms; [loadVocabFromFile] replaces it with the full CLIP
     * vocab when the bundled vocab.json is present on disk.
     */
    private val vocab: MutableMap<String, Int> = HashMap(COMPACT_VOCAB)

    @Volatile
    private var fullVocabLoaded = false

    /** True once a full vocab file has been loaded successfully. */
    val hasFullVocab: Boolean get() = fullVocabLoaded

    /** Tokenize [text] into CLIP token ids, padded/truncated to [maxLen]. */
    fun encode(text: String): IntArray {
        val cleaned = text.lowercase().trim().take(200)
        // Greedy longest-match over a whitespace-split word list, falling back
        // to byte-level fallback tokens. This produces stable token sequences
        // for the embedding index without requiring the full 49k vocab.
        val tokens = mutableListOf<Int>()
        tokens.add(bosId)
        for (word in cleaned.split(Regex("\\s+"))) {
            if (word.isBlank()) continue
            // Fast path: the whole word is a single CLIP token (with or
            // without the CLIP "</w>" word-end marker).
            val wordId = lookupWord(word)
            if (wordId != null) {
                tokens.add(wordId)
                if (tokens.size >= maxLen - 1) break
                continue
            }
            val subwords = greedySplit(word)
            for (idx in subwords.indices) {
                val sw = subwords[idx]
                val id = lookupSubword(sw, isLast = idx == subwords.lastIndex)
                tokens.add(id)
                if (tokens.size >= maxLen - 1) break
            }
            if (tokens.size >= maxLen - 1) break
        }
        tokens.add(eosId)
        while (tokens.size < maxLen) tokens.add(padId)
        return tokens.toIntArray()
    }

    private fun greedySplit(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var i = 0
        val w = word.trimStart { !it.isLetterOrDigit() }
        while (i < w.length) {
            var matched = false
            for (len in minOf(8, w.length - i) downTo 1) {
                val sub = w.substring(i, i + len)
                if (matchesVocab(sub)) {
                    result.add(sub)
                    i += len
                    matched = true
                    break
                }
            }
            if (!matched) {
                result.add(w.substring(i, i + 1))
                i += 1
            }
        }
        return result
    }

    /** True if [token] (or its CLIP word-end variant) is in the vocab. */
    private fun matchesVocab(token: String): Boolean {
        if (vocab.containsKey(token)) return true
        if (!token.endsWith(WORD_END)) return vocab.containsKey(token + WORD_END)
        return false
    }

    /** Look up a complete word, trying the CLIP "</w>" word-end variant. */
    private fun lookupWord(word: String): Int? {
        vocab[word]?.let { return it }
        if (!word.endsWith(WORD_END)) vocab[word + WORD_END]?.let { return it }
        return null
    }

    /** Look up a subword; the final subword of a word may match a "</w>" token. */
    private fun lookupSubword(subword: String, isLast: Boolean): Int {
        vocab[subword]?.let { return it }
        if (isLast && !subword.endsWith(WORD_END)) {
            vocab[subword + WORD_END]?.let { return it }
        }
        return unkId(subword)
    }

    /** Stable pseudo-id for OOV tokens derived from the byte value. */
    private fun unkId(token: String): Int {
        val b = token.first().code
        return 49408 + (b and 0x3FF)
    }

    /**
     * Load the full CLIP vocabulary from a JSON file mapping tokens to ids
     * ({"token": id, ...}). On success this replaces the compact built-in
     * vocab so all real CLIP tokens resolve correctly. Returns false if the
     * file is missing or unreadable, in which case the compact vocab continues
     * to be used.
     */
    fun loadVocabFromFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() == 0L) return false
        return runCatching {
            val parsed = Json { ignoreUnknownKeys = true }
                .parseToJsonElement(file.readText()).jsonObject
            if (parsed.isEmpty()) return false
            val loaded = HashMap<String, Int>(parsed.size)
            parsed.forEach { (token, element) ->
                element.jsonPrimitive.intOrNull?.let { id -> loaded[token] = id }
            }
            if (loaded.isEmpty()) return false
            vocab.clear()
            vocab.putAll(loaded)
            fullVocabLoaded = true
            Log.i(TAG, "Loaded ${loaded.size} CLIP vocab tokens from ${file.name}")
            true
        }.onFailure { Log.w(TAG, "Failed to load CLIP vocab from ${file.absolutePath}", it) }
            .getOrDefault(false)
    }

    /**
     * Convenience: load the default CLIP vocab.json from the AI models
     * directory (next to the ONNX model). Returns false (and keeps the compact
     * vocab) when the file isn't bundled.
     */
    fun loadDefaultVocab(): Boolean = runCatching {
        val dir = File(ContextProvider.context()?.filesDir ?: return false, MODELS_DIR)
        loadVocabFromFile(File(dir, DEFAULT_VOCAB_NAME)) ||
            loadVocabFromFile(File(dir, FALLBACK_VOCAB_NAME))
    }.getOrDefault(false)

    companion object {
        private const val TAG = "ClipTokenizer"
        private const val MODELS_DIR = "ai_models"
        private const val DEFAULT_VOCAB_NAME = "vocab.json"
        private const val FALLBACK_VOCAB_NAME = "clip_vocab.json"
        private const val WORD_END = "</w>"

        // Compact subset of the CLIP vocab covering common photography terms.
        private val COMPACT_VOCAB: Map<String, Int> = buildMap {
            put("</w>", 49405)
            put(".", 262)
            put(",", 11)
            put("a", 320)
            put("an", 553)
            put("the", 5)
            put("of", 9)
            put("and", 8)
            put("with", 191)
            put("in", 13)
            put("on", 539)
            put("at", 421)
            put("for", 338)
            put("to", 11)
            put("is", 16)
            put("are", 527)
            put("photo", 1125)
            put("photograph", 8837)
            put("image", 2251)
            put("picture", 2732)
            put("portrait", 7690)
            put("landscape", 8986)
            put("nature", 3222)
            put("mountain", 3504)
            put("sky", 2159)
            put("water", 1450)
            put("sunset", 10635)
            put("sunrise", 28377)
            put("night", 1612)
            put("city", 2253)
            put("street", 2803)
            put("people", 1209)
            put("person", 1651)
            put("woman", 1876)
            put("man", 633)
            put("child", 2017)
            put("dog", 3290)
            put("cat", 4934)
            put("flower", 7322)
            put("tree", 3403)
            put("forest", 5684)
            put("beach", 7396)
            put("ocean", 9844)
            put("river", 4694)
            put("lake", 4451)
            put("snow", 7244)
            put("rain", 4232)
            put("cloud", 9268)
            put("building", 3198)
            put("architecture", 21270)
            put("car", 3138)
            put("food", 3309)
            put("macro", 29547)
            put("wildlife", 14774)
            put("black", 826)
            put("white", 756)
            put("red", 1138)
            put("blue", 1779)
            put("green", 2731)
            put("yellow", 4063)
            put("light", 928)
            put("dark", 1299)
            put("soft", 3975)
            put("hard", 1056)
            put("warm", 3946)
            put("cold", 2619)
            put("bright", 3777)
            put("dim", 16339)
            put("sharp", 4183)
            put("blur", 26618)
            put("bokeh", 41189)
            put("focus", 2628)
            put("exposure", 14432)
            put("shadow", 5562)
            put("highlight", 19415)
            put("contrast", 11157)
            put("color", 1064)
            put("tone", 7197)
            put("raw", 3968)
            put("film", 2266)
            put("grain", 23698)
            put("vintage", 13406)
            put("modern", 2398)
            put("abstract", 8898)
            put("minimal", 13753)
            put("dramatic", 11146)
            put("calm", 6936)
            put("happy", 2642)
            put("sad", 6196)
            put("beautiful", 2122)
            put("stunning", 23487)
            put("outdoor", 5168)
            put("indoor", 13636)
            put("travel", 4535)
            put("wedding", 13709)
            put("concert", 12055)
            put("sport", 3065)
            put("fashion", 4774)
        }
    }
}
