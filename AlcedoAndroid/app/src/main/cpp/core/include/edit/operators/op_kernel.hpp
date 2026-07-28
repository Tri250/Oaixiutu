// AlcedoAndroid - operator kernel tags + Tile primitive.
// Mirrors the desktop op_kernel.hpp: point/neighbor tags and a Tile view over a
// FloatMat used by the static kernel stream CPU path.
// SPDX-License-Identifier: GPL-3.0-only
#pragma once

#include <cstdint>

#include "image/image_buffer.hpp"

namespace alcedo {

// Tags identifying whether a kernel is a pure point op or needs neighbors.
struct PointOpTag {};
struct NeighborOpTag {};

// A contiguous tile view over a FloatMat. Point chains iterate the tile's
// pixels directly; neighbor ops get a pointer to the underlying buffer.
struct Tile {
  FloatMat* mat_       = nullptr;
  int       width_      = 0;
  int       height_     = 0;
  int       x_offset_   = 0;
  int       y_offset_   = 0;

  Pixel& at(int y, int x) { return mat_->PixelAt(y_offset_ + y, x_offset_ + x); }
  const Pixel& at(int y, int x) const { return mat_->PixelAt(y_offset_ + y, x_offset_ + x); }
};

// A 2D control point used by curve operators.
struct CurvePoint {
  float x = 0.0f;
  float y = 0.0f;
};

}  // namespace alcedo
