package com.alcedo.studio.ndk

/**
 * JNI bridge for AI NDK operations.
 *
 * Native methods for HNSW index operations, embedding generation,
 * and similarity search, implemented in the alcedo_core native library.
 */
object AiNdkBridge {
    init {
        System.loadLibrary("alcedo_core")
    }

    // ── HNSW Index Operations ──

    /**
     * Create a new HNSW index handle with the given embedding dimension.
     * @return Native pointer to the HnswIndex instance (as Long).
     */
    external fun hnswCreateIndex(embeddingDim: Int, M: Int = 16, efConstruction: Int = 200, efSearch: Int = 50): Long

    /**
     * Destroy an HNSW index and free native memory.
     */
    external fun hnswDestroyIndex(indexHandle: Long)

    /**
     * Clear all entries from the HNSW index.
     */
    external fun hnswClear(indexHandle: Long)

    /**
     * Insert a single vector into the HNSW index.
     * @param indexHandle Native pointer to HnswIndex
     * @param id Image ID
     * @param embedding Float array of dimension embeddingDim
     */
    external fun hnswInsert(indexHandle: Long, id: Long, embedding: FloatArray)

    /**
     * Bulk insert multiple vectors into the HNSW index.
     * @param ids Array of image IDs
     * @param embeddings Flattened float array of dimension [n * embeddingDim]
     */
    external fun hnswInsertBatch(indexHandle: Long, ids: LongArray, embeddings: FloatArray, count: Int)

    /**
     * Search for the k nearest neighbors of a query embedding.
     * @return Array of [id, distance, id, distance, ...] interleaved, or null if empty
     */
    external fun hnswSearch(indexHandle: Long, queryEmbedding: FloatArray, k: Int): FloatArray?

    /**
     * Remove an entry from the HNSW index.
     */
    external fun hnswRemove(indexHandle: Long, id: Long)

    /**
     * Get the number of entries in the HNSW index.
     */
    external fun hnswSize(indexHandle: Long): Int

    /**
     * Serialize the HNSW index to a byte array for persistence.
     */
    external fun hnswSerialize(indexHandle: Long): ByteArray?

    /**
     * Deserialize an HNSW index from a byte array.
     * @return New native handle, or 0 on failure.
     */
    external fun hnswDeserialize(indexHandle: Long, data: ByteArray): Boolean

    // ── Embedding Utilities ──

    /**
     * L2-normalize a float vector in-place (native implementation).
     */
    external fun normalizeEmbedding(embedding: FloatArray)

    /**
     * Compute cosine similarity between two vectors.
     */
    external fun cosineSimilarity(a: FloatArray, b: FloatArray): Float

    /**
     * Compute cosine similarity between raw (non-normalized) vectors.
     */
    external fun cosineSimilarityRaw(a: FloatArray, b: FloatArray): Float

    /**
     * Compute L2 (Euclidean) distance between two vectors.
     */
    external fun l2Distance(a: FloatArray, b: FloatArray): Float

    // ── CLIP/SigLIP Inference ──

    /**
     * Create a CLIP inference session.
     * @param modelPath Absolute path to the ONNX model file
     * @param imageSize Input image resolution (e.g., 224)
     * @param embeddingDim Output embedding dimension (e.g., 512)
     * @param textMaxLength Max text token length
     * @param numThreads Number of inference threads
     * @return Native pointer to the ClipInference instance.
     */
    external fun clipCreateSession(
        modelPath: String,
        imageSize: Int = 224,
        embeddingDim: Int = 512,
        textMaxLength: Int = 77,
        numThreads: Int = 4
    ): Long

    /**
     * Destroy a CLIP inference session.
     */
    external fun clipDestroySession(sessionHandle: Long)

    /**
     * Check if the CLIP session has loaded the model successfully.
     */
    external fun clipIsLoaded(sessionHandle: Long): Boolean

    /**
     * Encode an image (preprocessed RGB pixel data) to a normalized embedding.
     * @param rgbData RGB pixel data (3 bytes per pixel, row-major)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @return Normalized embedding float array, or null on failure.
     */
    external fun clipEncodeImage(sessionHandle: Long, rgbData: ByteArray, width: Int, height: Int): FloatArray?

    /**
     * Encode a text string to a normalized embedding.
     * @return Normalized embedding float array, or null on failure.
     */
    external fun clipEncodeText(sessionHandle: Long, text: String): FloatArray?

    /**
     * Zero-shot image classification.
     * @param labels Candidate label strings
     * @return Array of [score, label_index, score, label_index, ...] interleaved by score descending.
     */
    external fun clipClassifyImage(
        sessionHandle: Long,
        rgbData: ByteArray,
        width: Int,
        height: Int,
        labels: Array<String>
    ): FloatArray?
}