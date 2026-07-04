#include "vulkan_compute.h"
#include <cstring>
#include <dlfcn.h>

namespace alcedo {
namespace gpu {

// ============================================================
// Additional Vulkan API function pointers
// ============================================================
namespace {

// Buffer functions
typedef int (*PFN_vkCreateBuffer)(void* device, const void* pCreateInfo, const void* pAllocator, void** pBuffer);
typedef void (*PFN_vkDestroyBuffer)(void* device, void* buffer, const void* pAllocator);
typedef int (*PFN_vkAllocateMemory)(void* device, const void* pAllocateInfo, const void* pAllocator, void** pMemory);
typedef void (*PFN_vkFreeMemory)(void* device, void* memory, const void* pAllocator);
typedef int (*PFN_vkMapMemory)(void* device, void* memory, uint64_t offset, uint64_t size, uint32_t flags, void** ppData);
typedef void (*PFN_vkUnmapMemory)(void* device, void* memory);
typedef int (*PFN_vkBindBufferMemory)(void* device, void* buffer, void* memory, uint64_t offset);

// Pipeline functions
typedef int (*PFN_vkCreateShaderModule)(void* device, const void* pCreateInfo, const void* pAllocator, void** pShaderModule);
typedef void (*PFN_vkDestroyShaderModule)(void* device, void* shaderModule, const void* pAllocator);
typedef int (*PFN_vkCreateDescriptorSetLayout)(void* device, const void* pCreateInfo, const void* pAllocator, void** pSetLayout);
typedef void (*PFN_vkDestroyDescriptorSetLayout)(void* device, void* descriptorSetLayout, const void* pAllocator);
typedef int (*PFN_vkCreatePipelineLayout)(void* device, const void* pCreateInfo, const void* pAllocator, void** pPipelineLayout);
typedef void (*PFN_vkDestroyPipelineLayout)(void* device, void* pipelineLayout, const void* pAllocator);
typedef int (*PFN_vkCreateComputePipelines)(void* device, void* pipelineCache, uint32_t createInfoCount, const void* pCreateInfos, const void* pAllocator, void** pPipelines);
typedef void (*PFN_vkDestroyPipeline)(void* device, void* pipeline, const void* pAllocator);
typedef int (*PFN_vkAllocateDescriptorSets)(void* device, const void* pAllocateInfo, void** pDescriptorSets);
typedef void (*PFN_vkUpdateDescriptorSets)(void* device, uint32_t descriptorWriteCount, const void* pDescriptorWrites, uint32_t descriptorCopyCount, const void* pDescriptorCopies);

// Command buffer functions
typedef int (*PFN_vkAllocateCommandBuffers)(void* device, const void* pAllocateInfo, void** pCommandBuffers);
typedef void (*PFN_vkFreeCommandBuffers)(void* device, void* commandPool, uint32_t commandBufferCount, const void* pCommandBuffers);
typedef int (*PFN_vkBeginCommandBuffer)(void* commandBuffer, const void* pBeginInfo);
typedef int (*PFN_vkEndCommandBuffer)(void* commandBuffer);
typedef int (*PFN_vkQueueSubmit)(void* queue, uint32_t submitCount, const void* pSubmits, void* fence);
typedef int (*PFN_vkQueueWaitIdle)(void* queue);

// Pipeline bind/dispatch
typedef void (*PFN_vkCmdBindPipeline)(void* commandBuffer, uint32_t pipelineBindPoint, void* pipeline);
typedef void (*PFN_vkCmdBindDescriptorSets)(void* commandBuffer, uint32_t pipelineBindPoint, void* layout, uint32_t firstSet, uint32_t descriptorSetCount, const void* pDescriptorSets, uint32_t dynamicOffsetCount, const uint32_t* pDynamicOffsets);
typedef void (*PFN_vkCmdDispatch)(void* commandBuffer, uint32_t groupCountX, uint32_t groupCountY, uint32_t groupCountZ);
typedef void (*PFN_vkCmdPipelineBarrier)(void* commandBuffer, uint32_t srcStageMask, uint32_t dstStageMask, uint32_t dependencyFlags, uint32_t memoryBarrierCount, const void* pMemoryBarriers, uint32_t bufferMemoryBarrierCount, const void* pBufferMemoryBarriers, uint32_t imageMemoryBarrierCount, const void* pImageMemoryBarriers);
typedef void (*PFN_vkCmdPushConstants)(void* commandBuffer, void* layout, uint32_t stageFlags, uint32_t offset, uint32_t size, const void* pValues);

// Fence
typedef int (*PFN_vkCreateFence)(void* device, const void* pCreateInfo, const void* pAllocator, void** pFence);
typedef void (*PFN_vkDestroyFence)(void* device, void* fence, const void* pAllocator);
typedef int (*PFN_vkWaitForFences)(void* device, uint32_t fenceCount, const void* pFences, uint32_t waitAll, uint64_t timeout);
typedef int (*PFN_vkResetFences)(void* device, uint32_t fenceCount, const void* pFences);

struct VulkanComputeAPI {
    PFN_vkCreateBuffer vkCreateBuffer = nullptr;
    PFN_vkDestroyBuffer vkDestroyBuffer = nullptr;
    PFN_vkAllocateMemory vkAllocateMemory = nullptr;
    PFN_vkFreeMemory vkFreeMemory = nullptr;
    PFN_vkMapMemory vkMapMemory = nullptr;
    PFN_vkUnmapMemory vkUnmapMemory = nullptr;
    PFN_vkBindBufferMemory vkBindBufferMemory = nullptr;
    PFN_vkCreateShaderModule vkCreateShaderModule = nullptr;
    PFN_vkDestroyShaderModule vkDestroyShaderModule = nullptr;
    PFN_vkCreateDescriptorSetLayout vkCreateDescriptorSetLayout = nullptr;
    PFN_vkDestroyDescriptorSetLayout vkDestroyDescriptorSetLayout = nullptr;
    PFN_vkCreatePipelineLayout vkCreatePipelineLayout = nullptr;
    PFN_vkDestroyPipelineLayout vkDestroyPipelineLayout = nullptr;
    PFN_vkCreateComputePipelines vkCreateComputePipelines = nullptr;
    PFN_vkDestroyPipeline vkDestroyPipeline = nullptr;
    PFN_vkAllocateDescriptorSets vkAllocateDescriptorSets = nullptr;
    PFN_vkUpdateDescriptorSets vkUpdateDescriptorSets = nullptr;
    PFN_vkAllocateCommandBuffers vkAllocateCommandBuffers = nullptr;
    PFN_vkFreeCommandBuffers vkFreeCommandBuffers = nullptr;
    PFN_vkBeginCommandBuffer vkBeginCommandBuffer = nullptr;
    PFN_vkEndCommandBuffer vkEndCommandBuffer = nullptr;
    PFN_vkQueueSubmit vkQueueSubmit = nullptr;
    PFN_vkQueueWaitIdle vkQueueWaitIdle = nullptr;
    PFN_vkCmdBindPipeline vkCmdBindPipeline = nullptr;
    PFN_vkCmdBindDescriptorSets vkCmdBindDescriptorSets = nullptr;
    PFN_vkCmdDispatch vkCmdDispatch = nullptr;
    PFN_vkCmdPipelineBarrier vkCmdPipelineBarrier = nullptr;
    PFN_vkCmdPushConstants vkCmdPushConstants = nullptr;
    PFN_vkCreateFence vkCreateFence = nullptr;
    PFN_vkDestroyFence vkDestroyFence = nullptr;
    PFN_vkWaitForFences vkWaitForFences = nullptr;
    PFN_vkResetFences vkResetFences = nullptr;

    bool loaded = false;

    bool load(void* instance) {
        if (loaded) return true;
        auto getProc = reinterpret_cast<PFN_vkGetInstanceProcAddr*>(
            dlsym(RTLD_DEFAULT, "vkGetInstanceProcAddr"));
        if (!getProc) {
            void* lib = dlopen("libvulkan.so", RTLD_NOW);
            if (lib) {
                getProc = reinterpret_cast<PFN_vkGetInstanceProcAddr*>(dlsym(lib, "vkGetInstanceProcAddr"));
            }
        }
        if (!getProc) return false;

        auto loadProc = [&](auto& ptr, const char* name) {
            ptr = reinterpret_cast<std::remove_reference_t<decltype(ptr)>>(getProc(instance, name));
        };

        loadProc(vkCreateBuffer, "vkCreateBuffer");
        loadProc(vkDestroyBuffer, "vkDestroyBuffer");
        loadProc(vkAllocateMemory, "vkAllocateMemory");
        loadProc(vkFreeMemory, "vkFreeMemory");
        loadProc(vkMapMemory, "vkMapMemory");
        loadProc(vkUnmapMemory, "vkUnmapMemory");
        loadProc(vkBindBufferMemory, "vkBindBufferMemory");
        loadProc(vkCreateShaderModule, "vkCreateShaderModule");
        loadProc(vkDestroyShaderModule, "vkDestroyShaderModule");
        loadProc(vkCreateDescriptorSetLayout, "vkCreateDescriptorSetLayout");
        loadProc(vkDestroyDescriptorSetLayout, "vkDestroyDescriptorSetLayout");
        loadProc(vkCreatePipelineLayout, "vkCreatePipelineLayout");
        loadProc(vkDestroyPipelineLayout, "vkDestroyPipelineLayout");
        loadProc(vkCreateComputePipelines, "vkCreateComputePipelines");
        loadProc(vkDestroyPipeline, "vkDestroyPipeline");
        loadProc(vkAllocateDescriptorSets, "vkAllocateDescriptorSets");
        loadProc(vkUpdateDescriptorSets, "vkUpdateDescriptorSets");
        loadProc(vkAllocateCommandBuffers, "vkAllocateCommandBuffers");
        loadProc(vkFreeCommandBuffers, "vkFreeCommandBuffers");
        loadProc(vkBeginCommandBuffer, "vkBeginCommandBuffer");
        loadProc(vkEndCommandBuffer, "vkEndCommandBuffer");
        loadProc(vkQueueSubmit, "vkQueueSubmit");
        loadProc(vkQueueWaitIdle, "vkQueueWaitIdle");
        loadProc(vkCmdBindPipeline, "vkCmdBindPipeline");
        loadProc(vkCmdBindDescriptorSets, "vkCmdBindDescriptorSets");
        loadProc(vkCmdDispatch, "vkCmdDispatch");
        loadProc(vkCmdPipelineBarrier, "vkCmdPipelineBarrier");
        loadProc(vkCmdPushConstants, "vkCmdPushConstants");
        loadProc(vkCreateFence, "vkCreateFence");
        loadProc(vkDestroyFence, "vkDestroyFence");
        loadProc(vkWaitForFences, "vkWaitForFences");
        loadProc(vkResetFences, "vkResetFences");

        loaded = true;
        return true;
    }
};

static VulkanComputeAPI g_vkCompute;

// Vulkan structs for compute pipeline
struct VkBufferCreateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint32_t flags = 0;
    uint64_t size = 0;
    uint32_t usage = 0;
    uint32_t sharingMode = 0;
};

struct VkShaderModuleCreateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint32_t flags = 0;
    size_t codeSize = 0;
    const uint32_t* pCode = nullptr;
};

struct VkComputePipelineCreateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint32_t flags = 0;
    // stage
    uint32_t stageSType = 0;
    const void* stagePNext = nullptr;
    uint32_t stageFlags = 0;
    uint32_t stageStage = 0;
    void* stageModule = nullptr;
    const char* stagePName = nullptr;
    const void* stagePSpecializationInfo = nullptr;
    // layout
    void* layout = nullptr;
    void* basePipelineHandle = nullptr;
    int32_t basePipelineIndex = 0;
};

struct VkDescriptorSetLayoutBinding {
    uint32_t binding = 0;
    uint32_t descriptorType = 0;
    uint32_t descriptorCount = 0;
    uint32_t stageFlags = 0;
    const void* pImmutableSamplers = nullptr;
};

struct VkDescriptorSetLayoutCreateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint32_t flags = 0;
    uint32_t bindingCount = 0;
    const VkDescriptorSetLayoutBinding* pBindings = nullptr;
};

struct VkPipelineLayoutCreateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint32_t flags = 0;
    uint32_t setLayoutCount = 0;
    const void* pSetLayouts = nullptr;
    uint32_t pushConstantRangeCount = 0;
    const void* pPushConstantRanges = nullptr;
};

struct VkDescriptorSetAllocateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    void* descriptorPool = nullptr;
    uint32_t descriptorSetCount = 0;
    const void* pSetLayouts = nullptr;
};

struct VkCommandBufferAllocateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    void* commandPool = nullptr;
    uint32_t level = 0; // 0 = primary
    uint32_t commandBufferCount = 0;
};

struct VkCommandBufferBeginInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint32_t flags = 0;
    const void* pInheritanceInfo = nullptr;
};

struct VkSubmitInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint32_t waitSemaphoreCount = 0;
    const void* pWaitSemaphores = nullptr;
    const uint32_t* pWaitDstStageMask = nullptr;
    uint32_t commandBufferCount = 0;
    const void* pCommandBuffers = nullptr;
    uint32_t signalSemaphoreCount = 0;
    const void* pSignalSemaphores = nullptr;
};

struct VkFenceCreateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint32_t flags = 0;
};

struct VkMemoryAllocateInfo {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    uint64_t allocationSize = 0;
    uint32_t memoryTypeIndex = 0;
};

struct VkDescriptorBufferInfo {
    void* buffer = nullptr;
    uint64_t offset = 0;
    uint64_t range = 0;
};

struct VkWriteDescriptorSet {
    uint32_t sType = 0;
    const void* pNext = nullptr;
    void* dstSet = nullptr;
    uint32_t dstBinding = 0;
    uint32_t dstArrayElement = 0;
    uint32_t descriptorCount = 0;
    uint32_t descriptorType = 0;
    const void* pImageInfo = nullptr;
    const void* pBufferInfo = nullptr;
    const void* pTexelBufferView = nullptr;
};

// Constants
#define VK_BUFFER_USAGE_STORAGE_BUFFER_BIT 0x00000020
#define VK_BUFFER_USAGE_TRANSFER_SRC_BIT 0x00000001
#define VK_BUFFER_USAGE_TRANSFER_DST_BIT 0x00000002
#define VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT 0x00000001
#define VK_MEMORY_PROPERTY_HOST_COHERENT_BIT 0x00000004
#define VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT 0x00000002
#define VK_SHADER_STAGE_COMPUTE_BIT 0x00000020
#define VK_DESCRIPTOR_TYPE_STORAGE_BUFFER 0
#define VK_DESCRIPTOR_TYPE_STORAGE_IMAGE 1
#define VK_PIPELINE_BIND_POINT_COMPUTE 1
#define VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT 0x00000400
#define VK_ACCESS_SHADER_READ_BIT 0x00000020
#define VK_ACCESS_SHADER_WRITE_BIT 0x00000040
#define VK_FENCE_CREATE_SIGNALED_BIT 0x00000001

} // anonymous namespace

// ============================================================
// VulkanBuffer implementation
// ============================================================

bool VulkanBuffer::create(VulkanContext* ctx, size_t size, uint32_t usageFlags) {
    release();
    context_ = ctx;
    size_ = size;

    if (!g_vkCompute.load(context_->getInstance())) {
        GPU_LOGE("Failed to load Vulkan compute API");
        return false;
    }

    VkBufferCreateInfo bufferInfo;
    bufferInfo.sType = 0; // VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO
    bufferInfo.size = size;
    bufferInfo.usage = usageFlags | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bufferInfo.sharingMode = 0; // VK_SHARING_MODE_EXCLUSIVE

    if (g_vkCompute.vkCreateBuffer(context_->getDevice(), &bufferInfo, nullptr, &buffer_) != 0) {
        GPU_LOGE("vkCreateBuffer failed");
        return false;
    }

    // Allocate memory
    VkMemoryAllocateInfo allocInfo;
    allocInfo.sType = 0; // VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
    allocInfo.allocationSize = size * 2; // rough estimate
    allocInfo.memoryTypeIndex = 0;

    if (g_vkCompute.vkAllocateMemory(context_->getDevice(), &allocInfo, nullptr, &memory_) != 0) {
        GPU_LOGE("vkAllocateMemory failed");
        return false;
    }

    g_vkCompute.vkBindBufferMemory(context_->getDevice(), buffer_, memory_, 0);

    return true;
}

void VulkanBuffer::release() {
    if (memory_ && g_vkCompute.vkFreeMemory) {
        g_vkCompute.vkFreeMemory(context_->getDevice(), memory_, nullptr);
        memory_ = nullptr;
    }
    if (buffer_ && g_vkCompute.vkDestroyBuffer) {
        g_vkCompute.vkDestroyBuffer(context_->getDevice(), buffer_, nullptr);
        buffer_ = nullptr;
    }
    size_ = 0;
}

void* VulkanBuffer::map(size_t size, size_t offset) {
    if (!memory_) return nullptr;
    void* data = nullptr;
    size_t mapSize = (size == 0) ? size_ : size;
    if (g_vkCompute.vkMapMemory(context_->getDevice(), memory_, offset, mapSize, 0, &data) != 0) {
        return nullptr;
    }
    return data;
}

void VulkanBuffer::unmap() {
    if (memory_) {
        g_vkCompute.vkUnmapMemory(context_->getDevice(), memory_);
    }
}

void VulkanBuffer::upload(const void* data, size_t size) {
    void* mapped = map(size);
    if (mapped) {
        memcpy(mapped, data, size);
        unmap();
    }
}

void VulkanBuffer::download(void* data, size_t size) {
    void* mapped = map(size);
    if (mapped) {
        memcpy(data, mapped, size);
        unmap();
    }
}

// ============================================================
// VulkanComputePipeline implementation
// ============================================================

bool VulkanComputePipeline::create(VulkanContext* ctx, const PipelineDesc& desc) {
    release();
    context_ = ctx;

    if (!g_vkCompute.load(context_->getInstance())) {
        GPU_LOGE("Failed to load Vulkan compute API");
        return false;
    }

    // Create shader module
    VkShaderModuleCreateInfo shaderInfo;
    shaderInfo.sType = 0; // VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO
    shaderInfo.codeSize = desc.spirvSize;
    shaderInfo.pCode = desc.spirvCode;

    if (g_vkCompute.vkCreateShaderModule(context_->getDevice(), &shaderInfo, nullptr, &shaderModule_) != 0) {
        GPU_LOGE("vkCreateShaderModule failed");
        return false;
    }

    // Create descriptor set layout
    VkDescriptorSetLayoutBinding bindings[2];
    bindings[0].binding = 0;
    bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    bindings[0].descriptorCount = 1;
    bindings[0].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    bindings[1].binding = 1;
    bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    bindings[1].descriptorCount = 1;
    bindings[1].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;

    VkDescriptorSetLayoutCreateInfo layoutInfo;
    layoutInfo.sType = 0;
    layoutInfo.bindingCount = 2;
    layoutInfo.pBindings = bindings;

    if (g_vkCompute.vkCreateDescriptorSetLayout(context_->getDevice(), &layoutInfo, nullptr, &descriptorSetLayout_) != 0) {
        GPU_LOGE("vkCreateDescriptorSetLayout failed");
        return false;
    }

    // Create pipeline layout
    VkPipelineLayoutCreateInfo pipelineLayoutInfo;
    pipelineLayoutInfo.sType = 0;
    pipelineLayoutInfo.setLayoutCount = 1;
    pipelineLayoutInfo.pSetLayouts = &descriptorSetLayout_;

    if (g_vkCompute.vkCreatePipelineLayout(context_->getDevice(), &pipelineLayoutInfo, nullptr, &pipelineLayout_) != 0) {
        GPU_LOGE("vkCreatePipelineLayout failed");
        return false;
    }

    // Create compute pipeline
    VkComputePipelineCreateInfo pipelineInfo;
    pipelineInfo.sType = 0; // VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO
    pipelineInfo.stageSType = 0; // VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO
    pipelineInfo.stageStage = VK_SHADER_STAGE_COMPUTE_BIT;
    pipelineInfo.stageModule = shaderModule_;
    pipelineInfo.stagePName = "main";
    pipelineInfo.layout = pipelineLayout_;

    if (g_vkCompute.vkCreateComputePipelines(context_->getDevice(), nullptr, 1, &pipelineInfo, nullptr, &pipeline_) != 0) {
        GPU_LOGE("vkCreateComputePipelines failed");
        return false;
    }

    // Allocate descriptor set
    VkDescriptorSetAllocateInfo allocInfo;
    allocInfo.sType = 0;
    allocInfo.descriptorPool = context_->getDescriptorPool();
    allocInfo.descriptorSetCount = 1;
    allocInfo.pSetLayouts = &descriptorSetLayout_;

    if (g_vkCompute.vkAllocateDescriptorSets(context_->getDevice(), &allocInfo, &descriptorSet_) != 0) {
        GPU_LOGE("vkAllocateDescriptorSets failed");
        return false;
    }

    // Update descriptor set with buffers
    if (!desc.storageBuffers.empty()) {
        // Simplified: bind first two buffers
        VkDescriptorBufferInfo bufferInfo[2];
        VkWriteDescriptorSet writes[2];

        for (int i = 0; i < 2 && i < static_cast<int>(desc.storageBuffers.size()); i++) {
            bufferInfo[i].buffer = desc.storageBuffers[i]->getBuffer();
            bufferInfo[i].offset = 0;
            bufferInfo[i].range = desc.storageBuffers[i]->size();

            writes[i].sType = 0;
            writes[i].dstSet = descriptorSet_;
            writes[i].dstBinding = static_cast<uint32_t>(i);
            writes[i].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            writes[i].descriptorCount = 1;
            writes[i].pBufferInfo = &bufferInfo[i];
        }

        g_vkCompute.vkUpdateDescriptorSets(context_->getDevice(), 2, writes, 0, nullptr);
    }

    return true;
}

void VulkanComputePipeline::release() {
    if (pipeline_ && g_vkCompute.vkDestroyPipeline) {
        g_vkCompute.vkDestroyPipeline(context_->getDevice(), pipeline_, nullptr);
        pipeline_ = nullptr;
    }
    if (pipelineLayout_ && g_vkCompute.vkDestroyPipelineLayout) {
        g_vkCompute.vkDestroyPipelineLayout(context_->getDevice(), pipelineLayout_, nullptr);
        pipelineLayout_ = nullptr;
    }
    if (descriptorSetLayout_ && g_vkCompute.vkDestroyDescriptorSetLayout) {
        g_vkCompute.vkDestroyDescriptorSetLayout(context_->getDevice(), descriptorSetLayout_, nullptr);
        descriptorSetLayout_ = nullptr;
    }
    if (shaderModule_ && g_vkCompute.vkDestroyShaderModule) {
        g_vkCompute.vkDestroyShaderModule(context_->getDevice(), shaderModule_, nullptr);
        shaderModule_ = nullptr;
    }
    descriptorSet_ = nullptr;
    context_ = nullptr;
}

void VulkanComputePipeline::bind() {
    // Binding is done via command buffer
}

void VulkanComputePipeline::dispatch(uint32_t groupsX, uint32_t groupsY, uint32_t groupsZ) {
    // Dispatch requires a command buffer
}

void VulkanComputePipeline::barrier() {
    // Memory barrier
}

void VulkanComputePipeline::pushConstants(const void* /*data*/, uint32_t /*size*/, uint32_t /*offset*/) {
    // Push constants
}

void VulkanComputePipeline::finish() {
    if (context_) {
        context_->finish();
    }
}

// ============================================================
// VulkanCommandBuffer implementation
// ============================================================

bool VulkanCommandBuffer::create(VulkanContext* ctx) {
    release();
    context_ = ctx;

    if (!g_vkCompute.load(context_->getInstance())) {
        return false;
    }

    VkCommandBufferAllocateInfo allocInfo;
    allocInfo.sType = 0; // VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO
    allocInfo.commandPool = context_->getCommandPool();
    allocInfo.level = 0; // VK_COMMAND_BUFFER_LEVEL_PRIMARY
    allocInfo.commandBufferCount = 1;

    if (g_vkCompute.vkAllocateCommandBuffers(context_->getDevice(), &allocInfo, &cmdBuffer_) != 0) {
        GPU_LOGE("vkAllocateCommandBuffers failed");
        return false;
    }

    // Create fence
    VkFenceCreateInfo fenceInfo;
    fenceInfo.sType = 0; // VK_STRUCTURE_TYPE_FENCE_CREATE_INFO
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

    if (g_vkCompute.vkCreateFence(context_->getDevice(), &fenceInfo, nullptr, &fence_) != 0) {
        GPU_LOGE("vkCreateFence failed");
        return false;
    }

    return true;
}

void VulkanCommandBuffer::release() {
    if (fence_ && g_vkCompute.vkDestroyFence) {
        g_vkCompute.vkDestroyFence(context_->getDevice(), fence_, nullptr);
        fence_ = nullptr;
    }
    if (cmdBuffer_ && g_vkCompute.vkFreeCommandBuffers) {
        g_vkCompute.vkFreeCommandBuffers(context_->getDevice(), context_->getCommandPool(), 1, &cmdBuffer_);
        cmdBuffer_ = nullptr;
    }
    context_ = nullptr;
}

void VulkanCommandBuffer::begin() {
    VkCommandBufferBeginInfo beginInfo;
    beginInfo.sType = 0; // VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
    beginInfo.flags = 0; // VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT

    g_vkCompute.vkBeginCommandBuffer(cmdBuffer_, &beginInfo);
}

void VulkanCommandBuffer::end() {
    g_vkCompute.vkEndCommandBuffer(cmdBuffer_);
}

void VulkanCommandBuffer::submit() {
    VkSubmitInfo submitInfo;
    submitInfo.sType = 0; // VK_STRUCTURE_TYPE_SUBMIT_INFO
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &cmdBuffer_;

    g_vkCompute.vkQueueSubmit(context_->getComputeQueue(), 1, &submitInfo, fence_);
    g_vkCompute.vkWaitForFences(context_->getDevice(), 1, &fence_, 1, UINT64_MAX);
    g_vkCompute.vkResetFences(context_->getDevice(), 1, &fence_);
}

void VulkanCommandBuffer::reset() {
    // Reset command buffer for reuse
}

// ============================================================
// SpirvCompiler implementation
// ============================================================

bool SpirvCompiler::compileGlslToSpirv(const std::string& /*glslSource*/,
                                       std::vector<uint32_t>& /*outSpirv*/) {
    // On Android, SPIR-V compilation is typically done offline using glslangValidator
    // or shaderc during the build process. At runtime, we use pre-compiled SPIR-V.
    GPU_LOGE("Runtime SPIR-V compilation not supported - use pre-compiled SPIR-V binaries");
    return false;
}

} // namespace gpu
} // namespace alcedo