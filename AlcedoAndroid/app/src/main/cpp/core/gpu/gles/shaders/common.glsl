// Common GLSL utilities for Alcedo Studio GPU shaders
// Version: 1.0 - GLSL ES 3.1 Compute Shaders

#ifndef ALCEDO_COMMON_GLSL
#define ALCEDO_COMMON_GLSL

// ============================================================
// Constants
// ============================================================
const float PI = 3.14159265359;
const float TAU = 6.28318530718;
const float INV_PI = 0.31830988618;
const float EPSILON = 1e-6;
const float FLT_MAX = 3.402823466e+38;

// ============================================================
// Color space conversion
// ============================================================

// sRGB to linear
float srgb_to_linear(float c) {
    return (c <= 0.04045) ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4);
}

vec3 srgb_to_linear(vec3 c) {
    return vec3(srgb_to_linear(c.r), srgb_to_linear(c.g), srgb_to_linear(c.b));
}

vec4 srgb_to_linear(vec4 c) {
    return vec4(srgb_to_linear(c.rgb), c.a);
}

// Linear to sRGB
float linear_to_srgb(float c) {
    return (c <= 0.0031308) ? 12.92 * c : 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

vec3 linear_to_srgb(vec3 c) {
    return vec3(linear_to_srgb(c.r), linear_to_srgb(c.g), linear_to_srgb(c.b));
}

vec4 linear_to_srgb(vec4 c) {
    return vec4(linear_to_srgb(c.rgb), c.a);
}

// ============================================================
// RGB <-> XYZ
// ============================================================
const mat3 RGB_TO_XYZ = mat3(
    0.4124564, 0.2126729, 0.0193339,
    0.3575761, 0.7151522, 0.1191920,
    0.1804375, 0.0721750, 0.9503041
);

const mat3 XYZ_TO_RGB = mat3(
     3.2404542, -0.9692660,  0.0556434,
    -1.5371385,  1.8760108, -0.2040259,
    -0.4985314,  0.0415560,  1.0572252
);

vec3 rgb_to_xyz(vec3 rgb) {
    return RGB_TO_XYZ * rgb;
}

vec3 xyz_to_rgb(vec3 xyz) {
    return XYZ_TO_RGB * xyz;
}

// ============================================================
// RGB <-> HSV
// ============================================================
vec3 rgb_to_hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = EPSILON;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv_to_rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// ============================================================
// RGB <-> HSL
// ============================================================
vec3 rgb_to_hsl(vec3 c) {
    float minC = min(min(c.r, c.g), c.b);
    float maxC = max(max(c.r, c.g), c.b);
    float delta = maxC - minC;
    float l = (maxC + minC) * 0.5;
    float s = 0.0;
    float h = 0.0;

    if (delta > EPSILON) {
        s = l < 0.5 ? delta / (maxC + minC) : delta / (2.0 - maxC - minC);
        float delR = (((maxC - c.r) / 6.0) + (delta / 2.0)) / delta;
        float delG = (((maxC - c.g) / 6.0) + (delta / 2.0)) / delta;
        float delB = (((maxC - c.b) / 6.0) + (delta / 2.0)) / delta;

        if (c.r == maxC)      h = delB - delG;
        else if (c.g == maxC) h = (1.0 / 3.0) + delR - delB;
        else if (c.b == maxC) h = (2.0 / 3.0) + delG - delR;

        if (h < 0.0) h += 1.0;
        if (h > 1.0) h -= 1.0;
    }
    return vec3(h, s, l);
}

float hue_to_rgb(float v1, float v2, float h) {
    if (h < 0.0) h += 1.0;
    if (h > 1.0) h -= 1.0;
    if (6.0 * h < 1.0) return v1 + (v2 - v1) * 6.0 * h;
    if (2.0 * h < 1.0) return v2;
    if (3.0 * h < 2.0) return v1 + (v2 - v1) * ((2.0 / 3.0) - h) * 6.0;
    return v1;
}

vec3 hsl_to_rgb(vec3 hsl) {
    if (hsl.y < EPSILON)
        return vec3(hsl.z);

    float v2 = hsl.z < 0.5 ? hsl.z * (1.0 + hsl.y) : hsl.z + hsl.y - hsl.z * hsl.y;
    float v1 = 2.0 * hsl.z - v2;
    return vec3(
        hue_to_rgb(v1, v2, hsl.x + 1.0 / 3.0),
        hue_to_rgb(v1, v2, hsl.x),
        hue_to_rgb(v1, v2, hsl.x - 1.0 / 3.0)
    );
}

// ============================================================
// ACES / OpenDRT color science
// ============================================================

// ACES AP0 to AP1
const mat3 AP0_TO_AP1 = mat3(
    1.4514393161, -0.0765537734,  0.0083161484,
   -0.2365107469,  1.1762296998, -0.0060324498,
   -0.2149285693, -0.0996759264,  0.9977163014
);

// AP1 to AP0
const mat3 AP1_TO_AP0 = mat3(
    0.6954522414, 0.0447945634, 0.0013505844,
    0.1406786965, 0.8596711185, 0.0040252103,
    0.1638690622, 0.0955343182, 0.9946242054
);

// ACES RRT (Reference Rendering Transform) - simplified
vec3 aces_rrt(vec3 aces) {
    // Glow module
    float saturation = max(max(aces.r, aces.g), aces.b) - min(min(aces.r, aces.g), aces.b);
    saturation = clamp(saturation, 0.0, 1.0);
    float glow = 0.1 * saturation;

    aces *= 1.0 + glow;

    // Red modifier
    float hue = rgb_to_hsv(aces).x;
    float redMod = smoothstep(0.9, 1.0, hue) + smoothstep(0.0, 0.1, hue);
    aces.r *= 1.0 + 0.15 * redMod;

    // Dark toe
    float luminance = dot(aces, vec3(0.2126, 0.7152, 0.0722));
    float toe = clamp((luminance - 0.01) / 0.05, 0.0, 1.0);
    toe = toe * toe;
    aces = mix(aces * 0.5, aces, toe);

    return aces;
}

// ACES ODT (Output Device Transform) - sRGB
vec3 aces_odt_srgb(vec3 aces) {
    vec3 ap1 = AP0_TO_AP1 * aces;
    vec3 rrt = aces_rrt(ap1);

    // Global tone mapping
    vec3 tone = rrt * (rrt + 0.0245786) / (rrt * (rrt * 1.0 + 0.033251) + 0.0008785);

    // Apply AP1 to sRGB matrix
    const mat3 AP1_TO_SRGB = mat3(
         1.7050509, -0.1302569, -0.0240033,
        -0.6217921,  1.1408047, -0.1289693,
        -0.0832588, -0.0105487,  1.1529726
    );
    vec3 srgb = AP1_TO_SRGB * tone;

    return srgb;
}

// ============================================================
// Tone mapping operators
// ============================================================

// Reinhard
vec3 reinhard_tone_map(vec3 color) {
    return color / (color + vec3(1.0));
}

// Reinhard extended (luminance-based)
vec3 reinhard_extended(vec3 color, float maxWhite) {
    float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float mappedLum = lum * (1.0 + lum / (maxWhite * maxWhite)) / (1.0 + lum);
    return color * (mappedLum / max(lum, EPSILON));
}

// Uncharted 2 filmic
vec3 uncharted2_tonemap(vec3 x) {
    const float A = 0.15;
    const float B = 0.50;
    const float C = 0.10;
    const float D = 0.20;
    const float E = 0.02;
    const float F = 0.30;
    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
}

// ============================================================
// Luminance helpers
// ============================================================
float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float luminance_bt709(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float luminance_bt601(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

// ============================================================
// Math helpers
// ============================================================
float lerp(float a, float b, float t) {
    return a + t * (b - a);
}

vec3 lerp(vec3 a, vec3 b, float t) {
    return a + t * (b - a);
}

float clamp01(float v) {
    return clamp(v, 0.0, 1.0);
}

vec3 clamp01(vec3 v) {
    return clamp(v, 0.0, 1.0);
}

// Safe division
float safe_div(float a, float b) {
    return a / max(b, EPSILON);
}

// ============================================================
// White balance
// ============================================================
vec3 apply_white_balance(vec3 rgb, float temperature, float tint) {
    // Temperature to RGB multipliers (Kelvin scale)
    float temp = clamp(temperature, 2000.0, 50000.0);
    float r, g, b;

    if (temp <= 6500.0) {
        r = 1.0;
        g = 0.390081 + 0.609919 * (temp - 2000.0) / 4500.0;
        b = 0.132400 + 0.867600 * (temp - 2000.0) / 4500.0;
    } else {
        r = 1.0 - 0.3 * (temp - 6500.0) / 43500.0;
        g = 1.0;
        b = 0.132400 + 0.867600 * (temp - 2000.0) / 4500.0;
    }

    vec3 wb = vec3(r, g, b);
    // Tint: adjust green/magenta axis
    wb.g *= 1.0 + tint * 0.5;

    return rgb * wb;
}

// ============================================================
// Curve evaluation
// ============================================================
float cubic_hermite(float p0, float m0, float p1, float m1, float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return (2.0 * t3 - 3.0 * t2 + 1.0) * p0 +
           (t3 - 2.0 * t2 + t) * m0 +
           (-2.0 * t3 + 3.0 * t2) * p1 +
           (t3 - t2) * m1;
}

// ============================================================
// 3D LUT evaluation (tetrahedral interpolation)
// ============================================================
vec3 sample_lut3d(sampler2D lut, vec3 rgb, int lutSize) {
    float coordScale = (float(lutSize) - 1.0) / float(lutSize);
    float coordOffset = 1.0 / (2.0 * float(lutSize));

    vec3 coord = rgb * coordScale + coordOffset;
    coord = clamp(coord, 0.0, 1.0);

    float idx = coord.b * float(lutSize - 1);
    int b0 = int(floor(idx));
    int b1 = min(b0 + 1, lutSize - 1);
    float df = idx - float(b0);

    float u = coord.r;
    float v = coord.g;
    float v0 = (float(b0) * float(lutSize) + v) / float(lutSize * lutSize);
    float v1 = (float(b1) * float(lutSize) + v) / float(lutSize * lutSize);

    vec3 c0 = textureLod(lut, vec2(u, v0), 0.0).rgb;
    vec3 c1 = textureLod(lut, vec2(u, v1), 0.0).rgb;

    return mix(c0, c1, df);
}

#endif // ALCEDO_COMMON_GLSL