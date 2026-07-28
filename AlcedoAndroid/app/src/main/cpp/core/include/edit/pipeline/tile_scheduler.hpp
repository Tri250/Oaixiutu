// AlcedoAndroid - Tile scheduler for the static kernel stream (CPU path).
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstddef>
#include <memory>
#include <vector>

#include "edit/operators/op_kernel.hpp"
#include "image/image_buffer.hpp"

namespace alcedo {

// Splits an image into tiles and runs a static kernel stream over each tile.
// Templated on the stream type so the fused kernel chain is inlined per stage.
template <typename StreamT>
class StaticTileScheduler {
 public:
  StaticTileScheduler(std::shared_ptr<ImageBuffer> img, StreamT& stream)
      : img_(std::move(img)), stream_(stream) {}

  void Run(OperatorParams& params, int tile_size = 256) {
    if (!img_) return;
    FloatMat& mat = img_->GetCPUData();
    int w = mat.Width(), h = mat.Height();
    for (int y = 0; y < h; y += tile_size) {
      for (int x = 0; x < w; x += tile_size) {
        Tile tile;
        tile.mat_ = &mat;
        tile.width_ = std::min(tile_size, w - x);
        tile.height_ = std::min(tile_size, h - y);
        tile.x_offset_ = x;
        tile.y_offset_ = y;
        stream_.ProcessTile(tile, params);
      }
    }
  }

 private:
  std::shared_ptr<ImageBuffer> img_;
  StreamT&                     stream_;
};

}  // namespace alcedo
