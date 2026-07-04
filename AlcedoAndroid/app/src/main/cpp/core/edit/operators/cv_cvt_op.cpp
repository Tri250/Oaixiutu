#include "cv_cvt_op.h"
#include "oklab_cvt.h"
#include <cmath>
#include <algorithm>

namespace alcedo {

CVCvtColorOp::CVCvtColorOp() = default;

CVCvtColorOp::CVCvtColorOp(Code code) : code_(code) {}

// ============================================================
// RGB ↔ HSV conversion
// ============================================================

static void rgb_to_hsv(float r, float g, float b, float& h, float& s, float& v) {
    float mx = std::max({r, g, b});
    float mn = std::min({r, g, b});
    float delta = mx - mn;

    v = mx;

    if (delta < 1e-10f) {
        h = 0.0f;
        s = 0.0f;
        return;
    }

    s = (mx > 1e-10f) ? delta / mx : 0.0f;

    if (mx == r) {
        h = (g - b) / delta;
        if (h < 0.0f) h += 6.0f;
    } else if (mx == g) {
        h = 2.0f + (b - r) / delta;
    } else {
        h = 4.0f + (r - g) / delta;
    }
    h /= 6.0f; // Normalize to [0,1]
}

static void hsv_to_rgb(float h, float s, float v, float& r, float& g, float& b) {
    if (s < 1e-10f) {
        r = g = b = v;
        return;
    }

    h = std::fmod(h, 1.0f);
    if (h < 0.0f) h += 1.0f;

    h *= 6.0f;
    int i = static_cast<int>(std::floor(h));
    float f = h - i;
    float p = v * (1.0f - s);
    float q = v * (1.0f - s * f);
    float t = v * (1.0f - s * (1.0f - f));

    switch (i % 6) {
        case 0: r = v; g = t; b = p; break;
        case 1: r = q; g = v; b = p; break;
        case 2: r = p; g = v; b = t; break;
        case 3: r = p; g = q; b = v; break;
        case 4: r = t; g = p; b = v; break;
        case 5: r = v; g = p; b = q; break;
        default: r = v; g = t; b = p; break;
    }
}

// ============================================================
// RGB ↔ Lab (using Oklab as approximation for CIE Lab)
// ============================================================

static void rgb_to_lab(float r, float g, float b, float& L, float& a, float& lv) {
    OklabCvt::Oklab lab = OklabCvt::LinearRGB2Oklab(r, g, b);
    L = lab.l;
    a = lab.a;
    lv = lab.b;
}

static void lab_to_rgb(float L, float a, float lv, float& r, float& g, float& b) {
    OklabCvt::Oklab lab{L, a, lv};
    OklabCvt::Oklab2LinearRGB(lab, &r, &g, &b);
}

// ============================================================
// RGB ↔ YCrCb (ITU-R BT.601)
// ============================================================

static void rgb_to_ycrcb(float r, float g, float b, float& Y, float& Cr, float& Cb) {
    Y  = 0.299f * r + 0.587f * g + 0.114f * b;
    Cr = (r - Y) * 0.713f + 0.5f;
    Cb = (b - Y) * 0.564f + 0.5f;
}

static void ycrcb_to_rgb(float Y, float Cr, float Cb, float& r, float& g, float& b) {
    Cr -= 0.5f;
    Cb -= 0.5f;
    r = std::clamp(Y + 1.403f * Cr, 0.0f, 1.0f);
    g = std::clamp(Y - 0.714f * Cr - 0.344f * Cb, 0.0f, 1.0f);
    b = std::clamp(Y + 1.773f * Cb, 0.0f, 1.0f);
}

// ============================================================
// ApplyImpl
// ============================================================

void CVCvtColorOp::ApplyImpl(float* pixels, int width, int height, int channels) {
    int total = width * height;

    switch (code_) {
        case Code::RGB2BGR:
        case Code::BGR2RGB: {
            // Simple channel swap R↔B
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                std::swap(pixels[idx], pixels[idx + 2]);
            }
            break;
        }

        case Code::RGB2HSV: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float h, s, v;
                rgb_to_hsv(pixels[idx], pixels[idx + 1], pixels[idx + 2], h, s, v);
                pixels[idx]     = h;
                pixels[idx + 1] = s;
                pixels[idx + 2] = v;
            }
            break;
        }

        case Code::HSV2RGB: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float r, g, b;
                hsv_to_rgb(pixels[idx], pixels[idx + 1], pixels[idx + 2], r, g, b);
                pixels[idx]     = r;
                pixels[idx + 1] = g;
                pixels[idx + 2] = b;
            }
            break;
        }

        case Code::RGB2Lab: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float L, a, lv;
                rgb_to_lab(pixels[idx], pixels[idx + 1], pixels[idx + 2], L, a, lv);
                pixels[idx]     = L;
                pixels[idx + 1] = a;
                pixels[idx + 2] = lv;
            }
            break;
        }

        case Code::Lab2RGB: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float r, g, b;
                lab_to_rgb(pixels[idx], pixels[idx + 1], pixels[idx + 2], r, g, b);
                pixels[idx]     = r;
                pixels[idx + 1] = g;
                pixels[idx + 2] = b;
            }
            break;
        }

        case Code::RGB2YCrCb: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float Y, Cr, Cb;
                rgb_to_ycrcb(pixels[idx], pixels[idx + 1], pixels[idx + 2], Y, Cr, Cb);
                pixels[idx]     = Y;
                pixels[idx + 1] = Cr;
                pixels[idx + 2] = Cb;
            }
            break;
        }

        case Code::YCrCb2RGB: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float r, g, b;
                ycrcb_to_rgb(pixels[idx], pixels[idx + 1], pixels[idx + 2], r, g, b);
                pixels[idx]     = r;
                pixels[idx + 1] = g;
                pixels[idx + 2] = b;
            }
            break;
        }

        case Code::RGB2GRAY: {
            // Convert to grayscale, output 1 channel per pixel
            // For simplicity, store gray value in R channel and zero out G/B
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float gray = 0.299f * pixels[idx] + 0.587f * pixels[idx + 1] + 0.114f * pixels[idx + 2];
                pixels[idx]     = gray;
                pixels[idx + 1] = gray;
                pixels[idx + 2] = gray;
            }
            break;
        }
    }
}

} // namespace alcedo
