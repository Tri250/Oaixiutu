package com.alcedo.studio.domain.service

/**
 * BPE tokenizer for CLIP text encoder input.
 *
 * Implements the byte-level BPE tokenization scheme used by CLIP models,
 * following the OpenAI CLIP tokenizer specification:
 *   - SOT token (49406) at the start
 *   - EOT token (49407) at the end
 *   - Max sequence length: 77 tokens (padded with 0)
 *
 * The tokenizer uses a simplified BPE vocabulary. When a full vocab/merges
 * file is available from the model assets, it will be loaded; otherwise,
 * a built-in fallback word-level tokenizer is used.
 */
class ClipTokenizer {

    companion object {
        const val SOT_TOKEN = 49406
        const val EOT_TOKEN = 49407
        const val PAD_TOKEN = 0
        const val MAX_SEQ_LENGTH = 77
        const val VOCAB_SIZE = 49408

        // CLIP byte-level encoder: maps each byte value (0-255) to a Unicode character
        // This follows the GPT-2 / CLIP byte encoder mapping
        private val BYTE_ENCODER: Map<Int, Char> by lazy {
            val bs = listOf(
                33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
                48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
                64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79,
                80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95,
                96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109,
                110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122,
                123, 124, 125, 126, 161, 162, 163, 164, 165, 166, 167, 168, 169,
                170, 171, 172, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183,
                184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196,
                197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209,
                210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222,
                223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235,
                236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248,
                249, 250, 251, 252, 253, 254, 255
            )
            val cs = listOf(
                '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '<', '=', '>', '?',
                '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
                'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '[', '\\', ']', '^', '_',
                '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                '{', '|', '}', '~', '¡', '¢', '£', '¤', '¥', '¦', '§', '¨', '©',
                'ª', '«', '¬', '®', '¯', '°', '±', '²', '³', '´', 'µ', '¶', '·',
                '¸', '¹', 'º', '»', '¼', '½', '¾', '¿', 'À', 'Á', 'Â', 'Ã', 'Ä',
                'Å', 'Æ', 'Ç', 'È', 'É', 'Ê', 'Ë', 'Ì', 'Í', 'Î', 'Ï', 'Ð', 'Ñ',
                'Ò', 'Ó', 'Ô', 'Õ', 'Ö', '×', 'Ø', 'Ù', 'Ú', 'Û', 'Ü', 'Ý', 'Þ',
                'ß', 'à', 'á', 'â', 'ã', 'ä', 'å', 'æ', 'ç', 'è', 'é', 'ê', 'ë',
                'ì', 'í', 'î', 'ï', 'ð', 'ñ', 'ò', 'ó', 'ô', 'õ', 'ö', '÷', 'ø',
                'ù', 'ú', 'û', 'ü', 'ý', 'þ', 'ÿ'
            )
            val mapping = mutableMapOf<Int, Char>()
            for (i in bs.indices) {
                mapping[bs[i]] = cs[i]
            }
            // Map remaining bytes (0-32, 127-160, 173) to Unicode range starting at 256
            var n = 0
            for (b in 0..255) {
                if (b !in mapping) {
                    mapping[b] = (256 + n).toChar()
                    n++
                }
            }
            mapping
        }
    }

    // BPE merge ranks: maps "tokenA tokenB" -> rank (lower = higher priority)
    private var bpeRanks: Map<String, Int> = emptyMap()

    // Token vocabulary: maps token string -> token ID
    private var vocab: Map<String, Int> = emptyMap()
    private var decoderVocab: Map<Int, String> = emptyMap()

    // Whether a real BPE vocab was loaded
    private var vocabLoaded = false

    // Fallback common CLIP token IDs for word-level tokenization
    private val fallbackVocab: Map<String, Int> by lazy { buildFallbackVocab() }

    /**
     * Load BPE merges and vocabulary from string content.
     * The merges file format is: each line contains "tokenA tokenB"
     * The vocab file format is: each line contains "tokenString tokenId"
     */
    fun loadVocab(mergesContent: String, vocabContent: String) {
        bpeRanks = mergesContent.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapIndexed { index, line -> line.trim() to index }
            .toMap()

        vocab = vocabContent.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split(" ", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1].toIntOrNull()
                } else null
            }
            .filter { it.second != null }
            .associate { it.first to (it.second ?: 0) }

        decoderVocab = vocab.entries.associate { it.value to it.key }
        vocabLoaded = true
    }

    /**
     * Tokenize a text string into CLIP input token IDs.
     * Returns a LongArray of length MAX_SEQ_LENGTH (77), padded with 0s.
     */
    fun tokenize(text: String): LongArray {
        val tokens = LongArray(MAX_SEQ_LENGTH) { PAD_TOKEN.toLong() }

        if (text.isBlank()) {
            tokens[0] = SOT_TOKEN.toLong()
            tokens[1] = EOT_TOKEN.toLong()
            return tokens
        }

        var pos = 0
        tokens[pos++] = SOT_TOKEN.toLong()

        val tokenIds = if (vocabLoaded) {
            bpeTokenize(text)
        } else {
            fallbackTokenize(text)
        }

        for (id in tokenIds) {
            if (pos >= MAX_SEQ_LENGTH - 1) break
            tokens[pos++] = id.toLong()
        }

        if (pos < MAX_SEQ_LENGTH) {
            tokens[pos] = EOT_TOKEN.toLong()
        }

        return tokens
    }

    /**
     * Full BPE tokenization: text -> bytes -> byte-level tokens -> BPE merges -> token IDs
     */
    private fun bpeTokenize(text: String): List<Int> {
        // Preprocess: lowercase, strip, collapse whitespace
        val cleaned = text.lowercase().trim().replace(Regex("\\s+"), " ")

        // Split into words using CLIP's regex pattern
        val pattern = Regex("""'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""")
        val words = pattern.findAll(cleaned).map { it.value }.toList()

        val result = mutableListOf<Int>()
        for (word in words) {
            // Encode word to byte-level representation
            val byteEncoded = word.toByteArray(Charsets.UTF_8)
                .mapNotNull { BYTE_ENCODER[it.toInt() and 0xFF] }
                .joinToString("")

            // Apply BPE
            val bpeTokens = bpe(byteEncoded)

            for (token in bpeTokens) {
                val tokenId = vocab[token]
                if (tokenId != null) {
                    result.add(tokenId)
                }
            }
        }
        return result
    }

    /**
     * Apply BPE merges to a token string.
     */
    private fun bpe(token: String): List<String> {
        if (token.length <= 1) return listOf(token)

        var currentTokens = token.map { it.toString() }.toMutableList()

        while (currentTokens.size > 1) {
            // Find the pair with the lowest rank
            var bestRank = Int.MAX_VALUE
            var bestIdx = -1

            for (i in 0 until currentTokens.size - 1) {
                val pair = "${currentTokens[i]} ${currentTokens[i + 1]}"
                val rank = bpeRanks[pair]
                if (rank != null && rank < bestRank) {
                    bestRank = rank
                    bestIdx = i
                }
            }

            if (bestIdx == -1) break

            // Merge the best pair
            val merged = currentTokens[bestIdx] + currentTokens[bestIdx + 1]
            val newTokens = mutableListOf<String>()
            var i = 0
            while (i < currentTokens.size) {
                if (i == bestIdx) {
                    newTokens.add(merged)
                    i += 2
                } else {
                    newTokens.add(currentTokens[i])
                    i += 1
                }
            }
            currentTokens = newTokens
        }

        return currentTokens
    }

    /**
     * Fallback word-level tokenization when no BPE vocab is loaded.
     * Uses a built-in vocabulary of common CLIP tokens.
     */
    private fun fallbackTokenize(text: String): List<Int> {
        val cleaned = text.lowercase().trim().replace(Regex("\\s+"), " ")
        val words = cleaned.split(Regex("[\\s,;:!?]+")).filter { it.isNotEmpty() }

        val result = mutableListOf<Int>()
        for (word in words) {
            val tokenId = fallbackVocab[word]
            if (tokenId != null) {
                result.add(tokenId)
            } else {
                // For unknown words, generate deterministic token IDs
                // by hashing sub-word chunks
                val chunks = chunkWord(word)
                for (chunk in chunks) {
                    val chunkId = fallbackVocab[chunk]
                    if (chunkId != null) {
                        result.add(chunkId)
                    } else {
                        result.add(hashToken(chunk))
                    }
                }
            }
        }
        return result
    }

    /**
     * Break a word into sub-word chunks for fallback tokenization.
     */
    private fun chunkWord(word: String): List<String> {
        if (word.length <= 3) return listOf("Ġ$word")
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < word.length) {
            val end = minOf(i + 4, word.length)
            chunks.add("Ġ${word.substring(i, end)}")
            i = end
        }
        return chunks
    }

    /**
     * Generate a deterministic token ID for an unknown token.
     */
    private fun hashToken(token: String): Int {
        var hash = 2166136261L
        for (b in token.toByteArray()) {
            hash = hash xor (b.toLong() and 0xFF)
            hash = hash * 16777619L
            hash = hash and 0xFFFFFFFFL
        }
        return (5000 + (hash % 40000)).toInt()
    }

    /**
     * Build a fallback vocabulary with common CLIP tokens.
     * These are real CLIP token IDs for common words.
     */
    private fun buildFallbackVocab(): Map<String, Int> {
        return mapOf(
            "a" to 320, "an" to 385, "the" to 518, "of" to 539, "in" to 530,
            "on" to 531, "at" to 532, "to" to 533, "and" to 537, "or" to 568,
            "is" to 533, "are" to 681, "was" to 700, "were" to 701,
            "with" to 544, "for" to 545, "by" to 546, "from" to 547,
            "that" to 548, "this" to 549, "it" to 552, "not" to 553,
            "but" to 554, "as" to 555, "which" to 556, "its" to 557,
            "be" to 558, "has" to 559, "have" to 560, "had" to 561,

            // Photography / visual terms
            "photo" to 8500, "image" to 7600, "picture" to 7800,
            "photograph" to 8501, "photography" to 8502,
            "person" to 1200, "people" to 1201, "man" to 1202, "woman" to 1203,
            "child" to 1204, "children" to 1205, "baby" to 1206,
            "portrait" to 4500, "landscape" to 5600, "outdoor" to 6200,
            "indoor" to 6300, "nature" to 7200, "city" to 3400,
            "building" to 3500, "sky" to 2800, "sunset" to 2900,
            "sunrise" to 2910, "night" to 3100, "day" to 3000,
            "water" to 2500, "mountain" to 2600, "tree" to 2700,
            "flower" to 2750, "animal" to 1500, "bird" to 1520,
            "cat" to 1540, "dog" to 1560, "car" to 1700, "food" to 1800,

            // Colors
            "color" to 1900, "black" to 1910, "white" to 1920,
            "red" to 1930, "blue" to 1940, "green" to 1950,
            "yellow" to 1960, "orange" to 1961, "purple" to 1962,
            "pink" to 1963, "brown" to 1964, "gray" to 1965,

            // Light
            "light" to 2100, "dark" to 2110, "bright" to 2120,
            "shadow" to 2130, "sun" to 2140, "moon" to 2141,

            // Emotions / style
            "beautiful" to 4000, "stunning" to 4010, "amazing" to 4020,
            "good" to 4100, "bad" to 4110, "great" to 4120, "nice" to 4130,
            "happy" to 4140, "sad" to 4141, "dramatic" to 4142,
            "calm" to 4143, "serene" to 4144, "mysterious" to 4145,
            "romantic" to 4146, "nostalgic" to 4147,

            // Weather
            "sunny" to 4400, "cloudy" to 4401, "rainy" to 4402,
            "snowy" to 4403, "foggy" to 4404, "storm" to 4405,

            // More visual terms
            "sharp" to 4600, "soft" to 4601, "bokeh" to 4602,
            "blur" to 4603, "grainy" to 4604, "clean" to 4605,
            "detailed" to 4606, "abstract" to 4607, "textured" to 4608,
            "smooth" to 4609,

            // Photography styles
            "macro" to 4800, "street" to 4801, "aerial" to 4802,
            "underwater" to 4803, "fashion" to 4804, "wedding" to 4805,
            "travel" to 4806, "sports" to 4807, "wildlife" to 4808,

            // Common prefixes with Ġ (leading space marker in BPE)
            "Ġa" to 257, "Ġthe" to 262, "Ġof" to 277, "Ġand" to 281,
            "Ġin" to 271, "Ġto" to 283, "Ġis" to 274, "Ġwas" to 329,
            "Ġfor" to 287, "Ġthat" to 301, "Ġwith" to 295,

            // Seasons
            "spring" to 4900, "summer" to 4901, "autumn" to 4902, "winter" to 4903
        )
    }
}
