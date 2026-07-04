package com.alcedo.studio.ndk

object AiNdkBridge {
    init {
        System.loadLibrary("alcedo")
    }

    external fun nativeClipEncodeImage(imagePixels: FloatArray, width: Int, height: Int): FloatArray
    external fun nativeClipEncodeText(text: String): FloatArray
    external fun nativeClipComputeSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float
    external fun nativeClipZeroShotClassify(imagePixels: FloatArray, width: Int, height: Int, labels: Array<String>): FloatArray
    external fun nativeBuildHnswIndex(embeddings: Array<FloatArray>, ids: LongArray): Boolean
    external fun nativeHnswSearch(query: FloatArray, topK: Int): LongArray

    fun encodeImage(imagePixels: FloatArray, width: Int, height: Int): FloatArray {
        return nativeClipEncodeImage(imagePixels, width, height)
    }

    fun encodeText(text: String): FloatArray {
        return nativeClipEncodeText(text)
    }

    fun computeSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return nativeClipComputeSimilarity(embedding1, embedding2)
    }

    fun zeroShotClassify(imagePixels: FloatArray, width: Int, height: Int, labels: Array<String>): FloatArray {
        return nativeClipZeroShotClassify(imagePixels, width, height, labels)
    }
}
