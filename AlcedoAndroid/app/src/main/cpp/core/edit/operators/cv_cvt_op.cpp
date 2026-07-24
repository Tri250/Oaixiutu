#include "cv_cvt_op.h"
#include "oklab_cvt.h"
#include <cmath>
#include <algorithm>

namespace {

// ============================================================
// RGB ↔ HSV conversion
// ============================================================

static void do_RGB2HSV(float r, float g, float b, float& h, float& s, float& v) {
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

static void do_HSV2RGB(float h, float s, float v, float& r, float& g, float& b) {
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

static void do_RGB2Lab(float r, float g, float b, float& L, float& a, float& lv) {
    alcedo::OklabCvt::Oklab lab = alcedo::OklabCvt::LinearRGB2Oklab(r, g, b);
    L = lab.l;
    a = lab.a;
    lv = lab.b;
}

static void do_Lab2RGB(float L, float a, float lv, float& r, float& g, float& b) {
    alcedo::OklabCvt::Oklab lab{L, a, lv};
    alcedo::OklabCvt::Oklab2LinearRGB(lab, &r, &g, &b);
}

// ============================================================
// RGB ↔ YCrCb (ITU-R BT.601)
// ============================================================

static void do_RGB2YCrCb(float r, float g, float b, float& Y, float& Cr, float& Cb) {
    // BT.709 luminance weights — consistent with all other operators
    Y  = 0.2126f * r + 0.7152f * g + 0.0722f * b;
    Cr = (r - Y) * 0.713f + 0.5f;
    Cb = (b - Y) * 0.564f + 0.5f;
}

static float clamp_v(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

static void do_YCrCb2RGB(float Y, float Cr, float Cb, float& r, float& g, float& b) {
    Cr -= 0.5f;
    Cb -= 0.5f;
    r = clamp_v(Y + 1.403f * Cr, 0.0f, 1.0f);
    g = clamp_v(Y - 0.714f * Cr - 0.344f * Cb, 0.0f, 1.0f);
    b = clamp_v(Y + 1.773f * Cb, 0.0f, 1.0f);
}

} // namespace

namespace alcedo {

CVCvtColorOp::CVCvtColorOp() = default;

CVCvtColorOp::CVCvtColorOp(Code code) : code_(code) {}

// ============================================================
// ApplyImpl
// ============================================================

void CVCvtColorOp::ApplyImpl(float* pixels, int width, int height, int channels) {
    size_t total = static_cast<size_t>(width) * height;

    switch (code_) {
        case RGB2BGR:
        case BGR2RGB: {
            // Simple channel swap R↔B
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                std::swap(pixels[idx], pixels[idx + 2]);
            }
            break;
        }

        case RGB2HSV: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float h, s, v;
                do_RGB2HSV(pixels[idx], pixels[idx + 1], pixels[idx + 2], h, s, v);
                pixels[idx]     = h;
                pixels[idx + 1] = s;
                pixels[idx + 2] = v;
            }
            break;
        }

        case HSV2RGB: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float r, g, b;
                do_HSV2RGB(pixels[idx], pixels[idx + 1], pixels[idx + 2], r, g, b);
                pixels[idx]     = r;
                pixels[idx + 1] = g;
                pixels[idx + 2] = b;
            }
            break;
        }

        case RGB2Lab: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float L, a, lv;
                do_RGB2Lab(pixels[idx], pixels[idx + 1], pixels[idx + 2], L, a, lv);
                pixels[idx]     = L;
                pixels[idx + 1] = a;
                pixels[idx + 2] = lv;
            }
            break;
        }

        case Lab2RGB: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float r, g, b;
                do_Lab2RGB(pixels[idx], pixels[idx + 1], pixels[idx + 2], r, g, b);
                // Oklab → linear RGB can produce out-of-gamut values; clamp to valid range
                pixels[idx]     = std::clamp(r, 0.0f, 1.0f);
                pixels[idx + 1] = std::clamp(g, 0.0f, 1.0f);
                pixels[idx + 2] = std::clamp(b, 0.0f, 1.0f);
            }
            break;
        }

        case RGB2YCrCb: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float Y, Cr, Cb;
                do_RGB2YCrCb(pixels[idx], pixels[idx + 1], pixels[idx + 2], Y, Cr, Cb);
                pixels[idx]     = Y;
                pixels[idx + 1] = Cr;
                pixels[idx + 2] = Cb;
            }
            break;
        }

        case YCrCb2RGB: {
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float r, g, b;
                do_YCrCb2RGB(pixels[idx], pixels[idx + 1], pixels[idx + 2], r, g, b);
                pixels[idx]     = r;
                pixels[idx + 1] = g;
                pixels[idx + 2] = b;
            }
            break;
        }

        case RGB2GRAY: {
            // Convert to grayscale using BT.709 luminance weights
            for (int i = 0; i < total; ++i) {
                int idx = i * channels;
                float gray = 0.2126f * pixels[idx] + 0.7152f * pixels[idx + 1] + 0.0722f * pixels[idx + 2];
                pixels[idx]     = gray;
                pixels[idx + 1] = gray;
                pixels[idx + 2] = gray;
            }
            break;
        }
    }
}

} // namespace alcedo