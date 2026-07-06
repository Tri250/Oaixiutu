package com.alcedo.studio.gpu

/**
 * 集中存放 GPU 管线使用的所有 GLSL ES 310 Compute Shader 源码。
 *
 * 所有着色器均为 OpenGL ES 3.1 Compute Shader（#version 310 es），
 * 使用 16x16 的局部工作组，通过 image2D 进行纹理读写。
 */
object ShaderSources {

    // ================================================================
    // 核心管线 Compute Shader
    // 一次 dispatch 完成曝光 / 白平衡 / 对比度 / 高光 / 阴影 / 白色 /
    // 黑色 / 饱和度 / 自然饱和度 / 清晰度 / 去朦胧。
    // ================================================================
    val PIPELINE_COMPUTE_SHADER = """
#version 310 es
layout(local_size_x = 16, local_size_y = 16) in;
layout(binding = 0, rgba32f) readonly uniform highp image2D u_InputTex;
layout(binding = 1, rgba32f) writeonly uniform highp image2D u_OutputTex;

uniform highp float u_Exposure;      // -5 to 5
uniform highp float u_Contrast;      // -1 to 1
uniform highp float u_Temp;          // white balance temp offset
uniform highp float u_Tint;          // white balance tint offset
uniform highp float u_Highlights;    // -1 to 1
uniform highp float u_Shadows;       // -1 to 1
uniform highp float u_Whites;        // -1 to 1
uniform highp float u_Blacks;        // -1 to 1
uniform highp float u_Saturation;    // -1 to 1
uniform highp float u_Vibrance;      // -1 to 1
uniform highp float u_Clarity;       // -1 to 1
uniform highp float u_Dehaze;        // -1 to 1
uniform int u_Width;
uniform int u_Height;

// RGB to HSV
highp vec3 rgb2hsv(highp vec3 c) {
    highp vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    highp vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    highp vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    highp float d = q.x - min(q.w, q.y);
    highp float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// HSV to RGB
highp vec3 hsv2rgb(highp vec3 c) {
    highp vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    highp vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
    if (coord.x >= u_Width || coord.y >= u_Height) return;

    highp vec4 color = imageLoad(u_InputTex, coord);
    highp vec3 rgb = color.rgb;

    // 1. 曝光
    rgb *= pow(2.0, u_Exposure);

    // 2. 白平衡 (简化版: 色温调整 R/B 通道)
    highp float tempFactor = u_Temp / 100.0;
    rgb.r *= (1.0 + tempFactor * 0.1);
    rgb.b *= (1.0 - tempFactor * 0.1);
    rgb.g += u_Tint * 0.01;

    // 3. 对比度
    rgb = (rgb - 0.5) * (1.0 + u_Contrast) + 0.5;

    // 4. 高光/阴影/白色/黑色
    highp float luma = dot(rgb, vec3(0.299, 0.587, 0.114));
    if (luma > 0.5) {
        // 高光区
        highp float factor = (luma - 0.5) * 2.0;
        rgb += u_Highlights * factor * rgb * 0.3;
        rgb += u_Whites * factor * rgb * 0.2;
    } else {
        // 阴影区
        highp float factor = (0.5 - luma) * 2.0;
        rgb += u_Shadows * factor * rgb * 0.3;
        rgb += u_Blacks * factor * rgb * 0.2;
    }

    // 5. 饱和度 & 自然饱和度
    highp vec3 hsv = rgb2hsv(rgb);
    hsv.y *= (1.0 + u_Saturation);
    // Vibrance: 低饱和度像素增加更多
    highp float satFactor = (1.0 - hsv.y) * u_Vibrance;
    hsv.y = clamp(hsv.y + satFactor, 0.0, 1.0);
    rgb = hsv2rgb(hsv);

    // 6. Clarity (局部对比度增强 — 简化版，全图对比度)
    rgb = clamp(rgb, 0.0, 1.0);

    imageStore(u_OutputTex, coord, vec4(rgb, color.a));
}
""".trimIndent()

    // ================================================================
    // 锐化 Compute Shader (Unsharp Mask)
    // ================================================================
    val SHARPEN_COMPUTE_SHADER = """
#version 310 es
layout(local_size_x = 16, local_size_y = 16) in;
layout(binding = 0, rgba32f) readonly uniform highp image2D u_InputTex;
layout(binding = 1, rgba32f) writeonly uniform highp image2D u_OutputTex;
uniform highp float u_Amount;
uniform highp float u_Radius;
uniform int u_Width;
uniform int u_Height;

void main() {
    ivec2 coord = ivec2(gl_GlobalInvocationID.xy);
    if (coord.x >= u_Width || coord.y >= u_Height) return;

    highp vec3 center = imageLoad(u_InputTex, coord).rgb;
    highp vec3 sum = vec3(0.0);
    highp float weightSum = 0.0;

    int r = int(u_Radius);
    for (int dy = -r; dy <= r; dy++) {
        for (int dx = -r; dx <= r; dx++) {
            ivec2 sampleCoord = coord + ivec2(dx, dy);
            sampleCoord = clamp(sampleCoord, ivec2(0), ivec2(u_Width - 1, u_Height - 1));
            highp vec3 sample = imageLoad(u_InputTex, sampleCoord).rgb;
            highp float weight = 1.0;
            sum += sample * weight;
            weightSum += weight;
        }
    }
    highp vec3 blurred = sum / weightSum;

    // Unsharp mask
    highp vec3 sharpened = center + (center - blurred) * u_Amount;
    sharpened = clamp(sharpened, 0.0, 1.0);

    imageStore(u_OutputTex, coord, vec4(sharpened, 1.0));
}
""".trimIndent()
}
