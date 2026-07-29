package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.AiModelAsset
import com.alcedo.studio.data.model.AiModelKind

/**
 * Static catalogue of downloadable AI model assets. Each entry records the
 * model id, kind, version, size, download URL and SHA-256 for integrity
 * verification. The [ModelDownloadService] fetches and verifies these.
 */
object ModelAssetCatalog {

    val CLIP_VIT_BASE_PATCH32 = AiModelAsset(
        id = "clip-vit-base-patch32",
        name = "CLIP ViT-B/32",
        kind = AiModelKind.CLIP,
        version = "1.0",
        sizeBytes = 350_000_000L,
        downloadUrl = "https://huggingface.co/openai/clip-vit-base-patch32/resolve/main/model.onnx",
        sha256 = "",
        localPath = null,
        isDownloaded = false,
        isDefault = true,
        dimensions = 512,
        description = "OpenAI CLIP ViT-B/32 image+text encoder (INT8) for semantic search.",
    )

    val SIGLIP_BASE_PATCH16 = AiModelAsset(
        id = "siglip-base-patch16-224",
        name = "SigLIP Base/16",
        kind = AiModelKind.SIGLIP,
        version = "1.0",
        sizeBytes = 420_000_000L,
        downloadUrl = "https://huggingface.co/google/siglip-base-patch16-224/resolve/main/model.onnx",
        sha256 = "",
        localPath = null,
        isDownloaded = false,
        isDefault = false,
        dimensions = 768,
        description = "SigLIP base image+text encoder, higher accuracy than CLIP.",
    )

    val MASK_SAM_TINY = AiModelAsset(
        id = "sam-tiny-segment",
        name = "SAM Tiny (segment)",
        kind = AiModelKind.MASK_SEGMENT,
        version = "1.0",
        sizeBytes = 180_000_000L,
        downloadUrl = "https://huggingface.co/facebook/sam-vit-tiny/resolve/main/model.onnx",
        sha256 = "",
        localPath = null,
        isDownloaded = false,
        isDefault = true,
        dimensions = 0,
        description = "Lightweight subject/background/sky segmentation mask model.",
    )

    val CAPTION_BLIP_TINY = AiModelAsset(
        id = "blip-tiny-caption",
        name = "BLIP Tiny (caption)",
        kind = AiModelKind.IMAGE_CAPTIONER,
        version = "1.0",
        sizeBytes = 220_000_000L,
        downloadUrl = "https://huggingface.co/Salesforce/blip-image-captioning-base/resolve/main/model.onnx",
        sha256 = "",
        localPath = null,
        isDownloaded = false,
        isDefault = true,
        dimensions = 0,
        description = "Image captioner for automatic semantic tag generation.",
    )

    val ALL: List<AiModelAsset> = listOf(
        CLIP_VIT_BASE_PATCH32,
        SIGLIP_BASE_PATCH16,
        MASK_SAM_TINY,
        CAPTION_BLIP_TINY,
    )

    fun byId(id: String): AiModelAsset? = ALL.firstOrNull { it.id == id }

    fun byKind(kind: AiModelKind): List<AiModelAsset> = ALL.filter { it.kind == kind }

    fun defaultFor(kind: AiModelKind): AiModelAsset? = ALL.firstOrNull { it.kind == kind && it.isDefault }
}
