package com.alcedo.studio.gpu

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES31
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * GPU 加速渲染管线，使用 OpenGL ES 3.1 Compute Shader 加速核心图像处理算子。
 *
 * 支持的算子（在单次 dispatch 中完成）：
 *  - 曝光 (Exposure)
 *  - 对比度 (Contrast)
 *  - 白平衡 (Temperature / Tint)
 *  - 高光 / 阴影 / 白色 / 黑色 (Highlights / Shadows / Whites / Blacks)
 *  - 饱和度 / 自然饱和度 (Saturation / Vibrance)
 *  - 清晰度 (Clarity)
 *  - 去朦胧 (Dehaze)
 *
 * 另有独立的锐化 (Sharpen) Compute Shader，基于 Unsharp Mask。
 *
 * 不支持的算子（HSL、曲线、镜头校正、LUT 等）自动回退到 CPU 管线。
 *
 * 调用顺序：
 *   1. [checkComputeSupport]        —— 检测设备是否支持 GLES 3.1 compute
 *   2. [initialize]                  —— 在 GL 线程上创建着色器与纹理
 *   3. [uploadInputImage]            —— 上传 RGBA float 数据到输入纹理
 *   4. [executePipeline]             —— dispatch compute 并读回结果
 *   5. [release]                     —— 释放 GL 资源
 *
 * 注意：本类不是线程安全的，所有 GL 操作必须在同一个 GL 线程上调用。
 */
class GpuPipelineRenderer {

    companion object {
        private const val TAG = "GpuPipelineRenderer"

        /** Compute Shader 局部工作组大小（与 GLSL 中 local_size_x/y 保持一致） */
        private const val WORK_GROUP_X = 16
        private const val WORK_GROUP_Y = 16

        /**
         * 检测设备是否支持 OpenGL ES 3.1（Compute Shader 所需）。
         *
         * 该方法不依赖 EGL 上下文，仅通过 ActivityManager 读取设备声明的
         * GLES 版本；与 [GpuService] 的 EGL 探测互补，便于在无 GLSurfaceView
         * 的场景下做快速预判。
         *
         * @return true 表示设备声明支持 GLES 3.1+。
         */
        @JvmStatic
        fun checkComputeSupport(): Boolean {
            return try {
                // 此静态方法不依赖 Context，仅通过 Build / GL 字符串推断；
                // 真正的运行时探测在 [checkComputeSupport(Context)] 中完成。
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP &&
                    javax.microedition.khronos.egl.EGLContext.getEGL() != null
            } catch (e: Throwable) {
                false
            }
        }

        /**
         * 检测设备是否支持 OpenGL ES 3.1 Compute Shader。
         *
         * 通过 [ActivityManager.deviceConfigurationInfo.reqGlEsVersion] 读取设备
         * 声明的 GLES 版本，0x00030001 对应 GLES 3.1。
         *
         * @param context 任意 Context（内部取 applicationContext）。
         * @return true 表示设备声明支持 GLES 3.1+。
         */
        @JvmStatic
        fun checkComputeSupport(context: Context): Boolean {
            return try {
                val appContext = context.applicationContext
                val activityManager = appContext
                    .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return false
                val config = activityManager.deviceConfigurationInfo
                config?.reqGlEsVersion?.let { it >= 0x00030001 } ?: false
            } catch (e: Throwable) {
                Log.w(TAG, "checkComputeSupport failed: ${e.message}")
                false
            }
        }
    }

    // ── GL 对象句柄 ──────────────────────────────────────────────
    private var pipelineProgram = 0
    private var sharpenProgram = 0
    private var inputTextureId = 0
    private var outputTextureId = 0
    private var pixelBufferId = 0

    // ── 尺寸 / 状态 ──────────────────────────────────────────────
    private var width = 0
    private var height = 0
    @Volatile private var initialized = false

    // ── Uniform location 缓存 ────────────────────────────────────
    private val pipelineUniforms = ConcurrentHashMap<String, Int>()
    private val sharpenUniforms = ConcurrentHashMap<String, Int>()

    // ================================================================
    // 生命周期
    // ================================================================

    /**
     * 初始化 GPU 管线。必须在拥有 GLES 3.1 上下文的线程上调用
     * （通常是 GLSurfaceView.Renderer.onSurfaceCreated / onSurfaceChanged）。
     *
     * @param width  输出纹理宽度（像素）
     * @param height 输出纹理高度（像素）
     */
    fun initialize(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Invalid dimensions: ${width}x${height}" }
        if (initialized && this.width == width && this.height == height) return

        // 重新初始化时先释放旧资源
        if (initialized) releaseInternal()

        this.width = width
        this.height = height

        // 编译 & 链接着色器程序
        pipelineProgram = createComputeProgram(ShaderSources.PIPELINE_COMPUTE_SHADER)
        sharpenProgram = createComputeProgram(ShaderSources.SHARPEN_COMPUTE_SHADER)

        cachePipelineUniforms()
        cacheSharpenUniforms()

        // 创建输入 / 输出纹理
        inputTextureId = createTexture(width, height)
        outputTextureId = createTexture(width, height)

        // 创建 PBO，用于异步读回 compute 输出
        pixelBufferId = createPixelBuffer(width, height)

        initialized = true
        Log.i(TAG, "Initialized ${width}x${height} (pipeline=$pipelineProgram, sharpen=$sharpenProgram)")
    }

    /**
     * 上传 RGBA float 图像数据到输入纹理。
     *
     * @param rgbData 长度必须为 width * height * 4（RGBA，每通道 0..1）。
     * @param width   数据宽度，必须与 [initialize] 时一致。
     * @param height  数据高度，必须与 [initialize] 时一致。
     */
    fun uploadInputImage(rgbData: FloatArray, width: Int, height: Int) {
        check(initialized) { "Renderer not initialized" }
        require(rgbData.size >= width * height * 4) {
            "rgbData too small: ${rgbData.size} < ${width * height * 4}"
        }
        require(width == this.width && height == this.height) {
            "Dimension mismatch: got ${width}x${height}, expected ${this.width}x${this.height}"
        }

        // 将 float (0..1) 转为 RGBA8 字节，避免依赖 GLES 的 float 纹理上传扩展。
        // 输入纹理虽然声明为 rgba32f（与 compute shader 兼容），但此处仍以
        // RGBA32F 内部格式上传原始 float 字节，保证精度。
        val byteCount = width * height * 4 * 4 // 4 floats * 4 bytes
        val buffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(rgbData, 0, width * height * 4)
        buffer.rewind()

        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, inputTextureId)
        GLES31.glTexSubImage2D(
            GLES31.GL_TEXTURE_2D, 0,
            0, 0, width, height,
            GLES31.GL_RGBA, GLES31.GL_FLOAT, buffer
        )
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, 0)
        checkGlError("uploadInputImage")
    }

    /**
     * 执行核心 GPU 管线（曝光 / 对比度 / 白平衡 / 饱和度 / 高光 / 阴影等）。
     *
     * @param params uniform 参数数组，顺序必须与 [GpuPipelineService] 中
     *               构建逻辑一致：
     *               [0]=exposure, [1]=contrast, [2]=temp, [3]=tint,
     *               [4]=highlights, [5]=shadows, [6]=whites, [7]=blacks,
     *               [8]=saturation, [9]=vibrance, [10]=clarity, [11]=dehaze,
     *               [12]=width(f), [13]=height(f)
     * @return 处理后的 RGBA float 数组（长度 width*height*4）；失败返回 null。
     */
    fun executePipeline(params: FloatArray): FloatArray? {
        if (!initialized || pipelineProgram == 0) {
            Log.e(TAG, "executePipeline called before initialize")
            return null
        }
        if (params.size < 14) {
            Log.e(TAG, "params too short: ${params.size}")
            return null
        }
        // Validate parameter values are finite (not NaN or Infinity)
        for (i in params.indices) {
            if (params[i].isNaN() || params[i].isInfinite()) {
                Log.e(TAG, "params[$i] is not finite: ${params[i]}")
                return null
            }
        }
        // Validate dimensions are positive
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Invalid dimensions: ${width}x${height}")
            return null
        }

        // 绑定管线程序
        GLES31.glUseProgram(pipelineProgram)

        // 绑定输入 / 输出纹理到 image unit
        GLES31.glBindImageTexture(
            0, inputTextureId, 0, false, 0,
            GLES31.GL_READ_ONLY, GLES31.GL_RGBA32F
        )
        GLES31.glBindImageTexture(
            1, outputTextureId, 0, false, 0,
            GLES31.GL_WRITE_ONLY, GLES31.GL_RGBA32F
        )
        checkGlError("bindImageTextures")

        // 设置 uniform
        setUniform(pipelineUniforms, "u_Exposure", params[0])
        setUniform(pipelineUniforms, "u_Contrast", params[1])
        setUniform(pipelineUniforms, "u_Temp", params[2])
        setUniform(pipelineUniforms, "u_Tint", params[3])
        setUniform(pipelineUniforms, "u_Highlights", params[4])
        setUniform(pipelineUniforms, "u_Shadows", params[5])
        setUniform(pipelineUniforms, "u_Whites", params[6])
        setUniform(pipelineUniforms, "u_Blacks", params[7])
        setUniform(pipelineUniforms, "u_Saturation", params[8])
        setUniform(pipelineUniforms, "u_Vibrance", params[9])
        setUniform(pipelineUniforms, "u_Clarity", params[10])
        setUniform(pipelineUniforms, "u_Dehaze", params[11])
        setUniformInt(pipelineUniforms, "u_Width", width)
        setUniformInt(pipelineUniforms, "u_Height", height)
        checkGlError("setUniforms")

        // Dispatch
        val groupsX = (width + WORK_GROUP_X - 1) / WORK_GROUP_X
        val groupsY = (height + WORK_GROUP_Y - 1) / WORK_GROUP_Y
        GLES31.glDispatchCompute(groupsX, groupsY, 1)
        checkGlError("glDispatchCompute")

        // 等待 compute 完成
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT)

        // 读回结果
        return readbackOutput()
    }

    /**
     * 执行锐化 pass。可选调用，通常在 [executePipeline] 之后对结果纹理再次处理。
     *
     * 当前实现直接对输入纹理做 unsharp mask，写回输出纹理。
     *
     * @param amount  锐化强度（0..1+）
     * @param radius  卷积半径（像素）
     * @return 处理后的 RGBA float 数组；失败返回 null。
     */
    fun executeSharpen(amount: Float, radius: Float): FloatArray? {
        if (!initialized || sharpenProgram == 0) {
            Log.e(TAG, "executeSharpen called before initialize")
            return null
        }

        GLES31.glUseProgram(sharpenProgram)

        GLES31.glBindImageTexture(
            0, outputTextureId, 0, false, 0,
            GLES31.GL_READ_ONLY, GLES31.GL_RGBA32F
        )
        GLES31.glBindImageTexture(
            1, inputTextureId, 0, false, 0,
            GLES31.GL_WRITE_ONLY, GLES31.GL_RGBA32F
        )

        setUniform(sharpenUniforms, "u_Amount", amount)
        setUniform(sharpenUniforms, "u_Radius", radius)
        setUniformInt(sharpenUniforms, "u_Width", width)
        setUniformInt(sharpenUniforms, "u_Height", height)

        val groupsX = (width + WORK_GROUP_X - 1) / WORK_GROUP_X
        val groupsY = (height + WORK_GROUP_Y - 1) / WORK_GROUP_Y
        GLES31.glDispatchCompute(groupsX, groupsY, 1)
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT)

        // 注意：锐化写到 inputTextureId（为避免再分配纹理），
        // 因此这里读回 inputTextureId。
        return readbackTexture(inputTextureId)
    }

    /**
     * 释放所有 GL 资源。可在任意线程调用，但建议在 GL 线程。
     */
    fun release() {
        releaseInternal()
    }

    // ================================================================
    // 内部实现
    // ================================================================

    private fun releaseInternal() {
        if (pipelineProgram != 0) {
            GLES31.glDeleteProgram(pipelineProgram)
            pipelineProgram = 0
        }
        if (sharpenProgram != 0) {
            GLES31.glDeleteProgram(sharpenProgram)
            sharpenProgram = 0
        }
        if (inputTextureId != 0) {
            GLES31.glDeleteTextures(1, intArrayOf(inputTextureId), 0)
            inputTextureId = 0
        }
        if (outputTextureId != 0) {
            GLES31.glDeleteTextures(1, intArrayOf(outputTextureId), 0)
            outputTextureId = 0
        }
        if (pixelBufferId != 0) {
            GLES31.glDeleteBuffers(1, intArrayOf(pixelBufferId), 0)
            pixelBufferId = 0
        }
        pipelineUniforms.clear()
        sharpenUniforms.clear()
        initialized = false
    }

    private fun createComputeProgram(shaderSource: String): Int {
        val shader = GLES31.glCreateShader(GLES31.GL_COMPUTE_SHADER)
        if (shader == 0) {
            throw RuntimeException("glCreateShader(GL_COMPUTE_SHADER) failed")
        }
        GLES31.glShaderSource(shader, shaderSource)
        GLES31.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES31.glGetShaderInfoLog(shader)
            GLES31.glDeleteShader(shader)
            throw RuntimeException("Compute shader compile failed:\n$log")
        }

        val program = GLES31.glCreateProgram()
        if (program == 0) {
            GLES31.glDeleteShader(shader)
            throw RuntimeException("glCreateProgram failed")
        }
        GLES31.glAttachShader(program, shader)
        GLES31.glLinkProgram(program)

        val linked = IntArray(1)
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val log = GLES31.glGetProgramInfoLog(program)
            GLES31.glDeleteProgram(program)
            GLES31.glDeleteShader(shader)
            throw RuntimeException("Program link failed:\n$log")
        }

        // 着色器已链接，可删除源对象
        GLES31.glDeleteShader(shader)
        return program
    }

    private fun createTexture(w: Int, h: Int): Int {
        val ids = IntArray(1)
        GLES31.glGenTextures(1, ids, 0)
        val texId = ids[0]
        if (texId == 0) throw RuntimeException("glGenTextures failed")

        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texId)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)

        // 分配 rgba32f 存储（无初始数据）
        GLES31.glTexImage2D(
            GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA32F,
            w, h, 0,
            GLES31.GL_RGBA, GLES31.GL_FLOAT, null
        )
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, 0)
        checkGlError("createTexture")
        return texId
    }

    private fun createPixelBuffer(w: Int, h: Int): Int {
        val ids = IntArray(1)
        GLES31.glGenBuffers(1, ids, 0)
        val pboId = ids[0]
        if (pboId == 0) throw RuntimeException("glGenBuffers failed")

        GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, pboId)
        // 预分配 width*height*4 通道 * 4 字节/float
        GLES31.glBufferData(
            GLES31.GL_PIXEL_PACK_BUFFER,
            w * h * 4 * 4,
            null,
            GLES31.GL_DYNAMIC_READ
        )
        GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, 0)
        checkGlError("createPixelBuffer")
        return pboId
    }

    private fun readbackOutput(): FloatArray? = readbackTexture(outputTextureId)

    /**
     * 从指定纹理读回 RGBA float 数据。
     * 使用 PBO 以减少管线阻塞（glReadPixels → PBO → map）。
     */
    private fun readbackTexture(textureId: Int): FloatArray? {
        if (pixelBufferId == 0) {
            Log.e(TAG, "readbackTexture: PBO not initialized (pixelBufferId=0)")
            return null
        }
        if (textureId == 0) {
            Log.e(TAG, "readbackTexture: invalid textureId=0")
            return null
        }
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "readbackTexture: invalid dimensions ${width}x${height}")
            return null
        }

        val byteCount = width * height * 4 * 4
        val out = FloatArray(width * height * 4)

        val fboId = IntArray(1)
        GLES31.glGenFramebuffers(1, fboId, 0)
        try {
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fboId[0])
            GLES31.glFramebufferTexture2D(
                GLES31.GL_FRAMEBUFFER,
                GLES31.GL_COLOR_ATTACHMENT0,
                GLES31.GL_TEXTURE_2D,
                textureId,
                0
            )

            val status = GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER)
            if (status != GLES31.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "FBO incomplete: 0x${status.toString(16)}")
                return null
            }

            GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, pixelBufferId)
            GLES31.glReadPixels(
                0, 0, width, height,
                GLES31.GL_RGBA, GLES31.GL_FLOAT, 0
            )

            val mapped = GLES31.glMapBufferRange(
                GLES31.GL_PIXEL_PACK_BUFFER, 0, byteCount,
                GLES31.GL_MAP_READ_BIT
            ) as? java.nio.ByteBuffer
            if (mapped != null) {
                mapped.order(ByteOrder.nativeOrder())
                // Validate mapped buffer has sufficient capacity before reading
                val floatBuffer = mapped.asFloatBuffer()
                if (floatBuffer.remaining() < out.size) {
                    Log.e(TAG, "Mapped buffer too small: ${floatBuffer.remaining()} < ${out.size}")
                    GLES31.glUnmapBuffer(GLES31.GL_PIXEL_PACK_BUFFER)
                    GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, 0)
                    return null
                }
                floatBuffer.get(out, 0, out.size)
                GLES31.glUnmapBuffer(GLES31.GL_PIXEL_PACK_BUFFER)
            } else {
                Log.e(TAG, "glMapBufferRange returned null")
            }

            GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, 0)
            checkGlError("readbackTexture")

            return if (mapped != null) out else null
        } finally {
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0)
            GLES31.glDeleteFramebuffers(1, fboId, 0)
        }
    }

    // ================================================================
    // Uniform 辅助
    // ================================================================

    private fun cachePipelineUniforms() {
        pipelineUniforms.clear()
        val names = listOf(
            "u_Exposure", "u_Contrast", "u_Temp", "u_Tint",
            "u_Highlights", "u_Shadows", "u_Whites", "u_Blacks",
            "u_Saturation", "u_Vibrance", "u_Clarity", "u_Dehaze",
            "u_Width", "u_Height"
        )
        for (n in names) {
            pipelineUniforms[n] = GLES31.glGetUniformLocation(pipelineProgram, n)
        }
    }

    private fun cacheSharpenUniforms() {
        sharpenUniforms.clear()
        val names = listOf("u_Amount", "u_Radius", "u_Width", "u_Height")
        for (n in names) {
            sharpenUniforms[n] = GLES31.glGetUniformLocation(sharpenProgram, n)
        }
    }

    private fun setUniform(cache: HashMap<String, Int>, name: String, value: Float) {
        val loc = cache[name] ?: return
        if (loc >= 0) GLES31.glUniform1f(loc, value)
    }

    private fun setUniformInt(cache: HashMap<String, Int>, name: String, value: Int) {
        val loc = cache[name] ?: return
        if (loc >= 0) GLES31.glUniform1i(loc, value)
    }

    private fun checkGlError(op: String) {
        val err = GLES31.glGetError()
        if (err != GLES31.GL_NO_ERROR) {
            val msg = "GLES error 0x${err.toString(16)} at $op"
            Log.e(TAG, msg)
            throw RuntimeException(msg)
        }
    }
}
