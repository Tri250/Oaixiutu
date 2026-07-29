// AlcedoAndroid - Application services layer.
// Declares all app-level service classes that orchestrate the core subsystems
// (sleeve, storage, pipeline, decoders, AI) for the Android UI / JNI bridge.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <filesystem>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "edit/history/edit_history.hpp"
#include "edit/pipeline/pipeline.hpp"
#include "image/image.hpp"
#include "sleeve/sleeve_element/sleeve_file.hpp"
#include "sleeve/sleeve_filesystem.hpp"
#include "sleeve/sleeve_manager.hpp"
#include "storage/controller/ai_controller.hpp"
#include "storage/controller/db_controller.hpp"
#include "storage/controller/image_controller.hpp"
#include "storage/controller/semantic_controller.hpp"
#include "storage/controller/sleeve_element_controller.hpp"
#include "storage/image_pool/image_pool_manager.hpp"
#include "type/type.hpp"

namespace alcedo {

// 1. Project open/close/save lifecycle.
class ProjectService {
 public:
  ProjectService();
  auto Open(const std::filesystem::path& db_path) -> bool;
  void Close();
  auto IsOpen() const -> bool;
  auto GetSleeveManager() -> SleeveManager&;
  auto SaveAll() -> bool;
  auto GetProjectPath() const -> const std::filesystem::path&;
 private:
  std::unique_ptr<SleeveManager> sleeve_;
  std::filesystem::path          project_path_;
};

// 2. Image import into the sleeve library.
class ImportService {
 public:
  ImportService(SleeveManager& sleeve, ImageController& img_ctrl);
  auto ImportImage(const std::filesystem::path& file_path) -> std::shared_ptr<SleeveFile>;
  auto ImportBatch(const std::vector<std::filesystem::path>& paths)
      -> std::vector<std::shared_ptr<SleeveFile>>;
 private:
  SleeveManager&   sleeve_;
  ImageController& img_ctrl_;
  auto DetectImageType(const std::filesystem::path& path) -> ImageType;
};

// 3. Image export to JPEG/PNG/TIFF/UltraHDR.
class ExportService {
 public:
  ExportService();
  auto Export(const std::shared_ptr<Image>& image, const std::filesystem::path& out_path,
              const std::string& format, int quality) -> bool;
};

// 4. Thumbnail generation.
class ThumbnailService {
 public:
  ThumbnailService(ImageController& img_ctrl);
  auto GetThumbnail(image_id_t image_id) -> std::shared_ptr<Image>;
  void GenerateThumbnail(const std::shared_ptr<Image>& image, uint32_t target_size);
 private:
  ImageController& img_ctrl_;
  // Serializes thumbnail generation. GenerateThumbnail reads the source CPU
  // buffer and writes the thumbnail buffer / state flags of an image; without
  // this guard concurrent generation or concurrent edits race on those buffers.
  std::mutex thumb_mtx_;
};

// 5. Thumbnail disk cache.
class ThumbnailDiskCacheService {
 public:
  explicit ThumbnailDiskCacheService(std::filesystem::path cache_dir);
  auto Load(image_id_t id) -> std::optional<std::vector<uint8_t>>;
  void Store(image_id_t id, const std::vector<uint8_t>& data);
  void Evict(image_id_t id);
  void Clear();
 private:
  std::filesystem::path cache_dir_;
  auto PathFor(image_id_t id) const -> std::filesystem::path;
};

// 6. Edit history management (undo/redo/branch).
class HistoryMgmtService {
 public:
  HistoryMgmtService(SleeveManager& sleeve);
  // Undo/redo now actually apply the history changes by driving the pipeline
  // service's executor through the version's transaction list. Previously these
  // only reported whether the cursor *could* move without moving it.
  auto Undo(sl_element_id_t file_id, PipelineAppService& pipeline_svc) -> bool;
  auto Redo(sl_element_id_t file_id, PipelineAppService& pipeline_svc) -> bool;
  auto GetHistory(sl_element_id_t file_id) -> std::shared_ptr<EditHistory>;
  auto GetVersionCount(sl_element_id_t file_id) -> size_t;
 private:
  SleeveManager& sleeve_;
};

// 7. Pipeline execution service.
class PipelineAppService {
 public:
  PipelineAppService();
  auto Execute(const std::shared_ptr<Image>& image, const std::string& param_json)
      -> std::shared_ptr<Image>;
  // Returns the long-lived PipelineExecutor owned by this service. Parameter
  // import/export and render-region mutations must target this executor so
  // state persists across calls instead of being applied to a throwaway object.
  auto GetExecutor() -> std::shared_ptr<PipelineExecutor>;
  // Apply / revert one transaction on the active version of the given history
  // by replaying through the persistent executor. Returns false if the cursor
  // cannot move or the executor is unavailable.
  auto Undo(EditHistory& history) -> bool;
  auto Redo(EditHistory& history) -> bool;
 private:
  std::shared_ptr<PipelineExecutor> executor_;
};

// 8. Sleeve filtering service.
class SleeveFilterService {
 public:
  explicit SleeveFilterService(SleeveManager& sleeve);
  auto FilterFolder(const std::filesystem::path& folder, const std::string& sql_predicate)
      -> std::vector<std::shared_ptr<SleeveElement>>;
  auto SearchByText(const std::string& query) -> std::vector<sl_element_id_t>;
 private:
  SleeveManager& sleeve_;
};

// 9. Sleeve operations service (create/move/delete folders).
class SleeveAppService {
 public:
  explicit SleeveAppService(SleeveManager& sleeve);
  auto CreateFolder(const std::filesystem::path& parent, const std::string& name) -> bool;
  auto MoveElement(const std::filesystem::path& src, const std::filesystem::path& dest) -> bool;
  auto DeleteElement(const std::filesystem::path& path) -> bool;
 private:
  SleeveManager& sleeve_;
};

// 10. Image pool management service.
class ImagePoolService {
 public:
  explicit ImagePoolService(std::shared_ptr<ImagePoolManager> pool);
  auto PinImage(image_id_t id) -> ImagePoolManager::PinnedImageHandle;
  void UnpinAll();
  auto PoolSize() -> size_t;
 private:
  std::shared_ptr<ImagePoolManager> pool_;
};

// 11. Album browsing service.
class AlbumBrowseService {
 public:
  explicit AlbumBrowseService(SleeveManager& sleeve);
  auto ListAlbums() -> std::vector<std::shared_ptr<SleeveElement>>;
  auto BrowseFolder(const std::filesystem::path& folder, size_t offset, size_t limit)
      -> std::vector<std::shared_ptr<SleeveFile>>;
 private:
  SleeveManager& sleeve_;
};

// 12. Adjustment transfer (copy edit settings between images).
class AdjustmentTransferService {
 public:
  AdjustmentTransferService(SleeveManager& sleeve);
  auto CopyAdjustments(sl_element_id_t src_file_id, sl_element_id_t dest_file_id) -> bool;
  auto CopyAdjustmentsBatch(sl_element_id_t src_file_id,
                            const std::vector<sl_element_id_t>& dest_file_ids) -> size_t;
 private:
  SleeveManager& sleeve_;
};

// 13. AI credential store.
class AiCredentialStore {
 public:
  void SetCredential(const std::string& provider_id, const std::string& api_key);
  auto GetCredential(const std::string& provider_id) const -> std::optional<std::string>;
  void RemoveCredential(const std::string& provider_id);
 private:
  std::unordered_map<std::string, std::string> credentials_;
};

// 14. AI provider profile configuration.
class AiProviderProfile {
 public:
  void SetProfile(const std::string& provider_id, const std::string& base_url,
                  const std::string& model_id);
  auto GetProfile(const std::string& provider_id) const
      -> std::optional<std::pair<std::string, std::string>>;
 private:
  struct Profile { std::string base_url; std::string model_id; };
  std::unordered_map<std::string, Profile> profiles_;
};

// 15. ML model asset catalog.
class ModelAssetCatalog {
 public:
  void RegisterModel(const std::string& model_key, const std::string& asset_path,
                     int64_t size_bytes);
  auto GetModelPath(const std::string& model_key) const -> std::optional<std::string>;
  auto ListModels() const -> std::vector<std::string>;
 private:
  struct Asset { std::string path; int64_t size; };
  std::unordered_map<std::string, Asset> assets_;
};

// 16. ML model download service.
class ModelDownloadService {
 public:
  explicit ModelDownloadService(std::filesystem::path model_dir);
  auto Download(const std::string& url, const std::string& model_key) -> bool;
  auto GetLocalPath(const std::string& model_key) const -> std::filesystem::path;
 private:
  std::filesystem::path model_dir_;
};

// 17. Semantic embedding generation service.
class SemanticGenerationService {
 public:
  SemanticGenerationService(SemanticStorageController& semantic_ctrl);
  void GenerateEmbedding(sl_element_id_t file_id, image_id_t image_id,
                         const std::shared_ptr<Image>& thumb);
 private:
  SemanticStorageController& semantic_ctrl_;
};

// 18. Image analysis encoder (preprocess image for AI inference).
class ImageAnalysisEncoder {
 public:
  auto Encode(const std::shared_ptr<Image>& image, int target_size)
      -> std::vector<float>;
};

// 19. Image analysis service (caption/rating via AI).
class ImageAnalysisService {
 public:
  ImageAnalysisService(AiStorageController& ai_ctrl);
  auto Analyze(sl_element_id_t file_id, const std::shared_ptr<Image>& image)
      -> std::optional<AiImageUnderstandingRecord>;
  auto Rate(sl_element_id_t file_id, const std::shared_ptr<Image>& image)
      -> std::optional<AiImageRatingRecord>;
 private:
  AiStorageController& ai_ctrl_;
};

// 20. Search query classifier.
class SearchQueryClassifier {
 public:
  enum class QueryType { TEXT, EXIF, DATE, RATING, SEMANTIC, UNKNOWN };
  auto Classify(const std::string& query) -> QueryType;
  auto ToSqlFilter(const std::string& query) -> std::string;
};

// 21. Project package (export/import whole project archive).
class ProjectPackageService {
 public:
  explicit ProjectPackageService(SleeveManager& sleeve);
  auto Package(const std::filesystem::path& out_archive) -> bool;
  auto Unpackage(const std::filesystem::path& archive) -> bool;
 private:
  SleeveManager& sleeve_;
};

}  // namespace alcedo
