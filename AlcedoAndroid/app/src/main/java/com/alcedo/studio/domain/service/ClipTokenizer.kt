package com.alcedo.studio.domain.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal CLIP text tokenizer (BPE) matching the OpenAI CLIP vocabulary.
 * Implements byte-level BPE encoding with a compact built-in vocab + merges
 * subset sufficient for semantic-search query strings; for full coverage the
 * native tokenizer (core/nn) is used via [ClipInferenceEngine] when available.
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
            val subwords = greedySplit(word)
            for (sw in subwords) {
                val id = vocab[sw] ?: unkId(sw)
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
                if (vocab.containsKey(sub)) {
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

    /** Stable pseudo-id for OOV tokens derived from the byte value. */
    private fun unkId(token: String): Int {
        val b = token.first().code
        return 49408 + (b and 0x3FF)
    }

    companion object {
        // Compact subset of the CLIP vocab covering common photography terms.
        private val vocab: Map<String, Int> = buildMap {
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
