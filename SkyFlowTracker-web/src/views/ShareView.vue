<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getShareInfo } from '../api/share'
import { toMediaUrl } from '../utils/media'

const route = useRoute()

const loading = ref(true)
const error = ref('')
const shareData = ref(null)

const videoSrc = computed(() => {
  if (!shareData.value?.videoPath) return ''
  return toMediaUrl(shareData.value.videoPath)
})

const formatTime = (time) => {
  if (!time) return '-'
  return time.replace('T', ' ')
}

const fetchShareInfo = async () => {
  const shareCode = route.params.code
  if (!shareCode) {
    error.value = '无效的分享链接'
    loading.value = false
    return
  }

  try {
    const res = await getShareInfo(shareCode)
    if (res.code === 1) {
      shareData.value = res.data
    } else {
      error.value = res.message || '获取分享信息失败'
    }
  } catch (err) {
    const status = err.response?.status
    const msg = err.response?.data?.message
    if (status === 404 || msg?.includes('不存在')) {
      error.value = '分享链接不存在或已被删除'
    } else if (status === 410 || msg?.includes('过期')) {
      error.value = '分享链接已过期'
    } else {
      error.value = msg || '获取分享信息失败'
    }
  } finally {
    loading.value = false
  }
}

onMounted(fetchShareInfo)
</script>

<template>
  <div class="share-page min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 flex items-center justify-center p-4">
    <!-- 加载中 -->
    <div v-if="loading" class="text-center">
      <div class="w-12 h-12 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin mx-auto mb-4"></div>
      <p class="text-gray-500">加载分享内容中...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="bg-white rounded-2xl shadow-lg p-10 max-w-md w-full text-center">
      <div class="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
        <i class="pi pi-exclamation-triangle text-3xl text-red-500"></i>
      </div>
      <h2 class="text-xl font-bold text-gray-800 mb-2">无法访问</h2>
      <p class="text-gray-500 mb-6">{{ error }}</p>
      <p class="text-sm text-gray-400">请联系分享者获取新的链接</p>
    </div>

    <!-- 正常展示 -->
    <div v-else-if="shareData" class="bg-white rounded-2xl shadow-lg max-w-4xl w-full overflow-hidden">
      <!-- 头部 -->
      <div class="bg-gradient-to-r from-blue-600 to-indigo-600 px-8 py-5">
        <h1 class="text-xl font-bold text-white">推理视频分享</h1>
        <p class="text-blue-100 text-sm mt-1">SkyFlowTracker 无人机交通监测分析结果</p>
      </div>

      <div class="p-8 space-y-6">
        <!-- 视频播放器 -->
        <div v-if="shareData.videoPath">
          <video :src="videoSrc" controls class="w-full rounded-xl bg-black" />
        </div>
        <div v-else class="bg-gray-100 rounded-xl p-12 text-center">
          <i class="pi pi-video text-4xl text-gray-400 mb-2"></i>
          <p class="text-gray-500">暂无视频</p>
        </div>

        <!-- 基础信息 -->
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div class="bg-blue-50 rounded-xl p-4 text-center">
            <p class="text-xs text-blue-500 mb-1">模型名称</p>
            <p class="text-sm font-semibold text-gray-800">{{ shareData.modelName || '-' }}</p>
          </div>
          <div class="bg-green-50 rounded-xl p-4 text-center">
            <p class="text-xs text-green-500 mb-1">检测总数</p>
            <p class="text-sm font-semibold text-gray-800">{{ shareData.totalDetections ?? '-' }}</p>
          </div>
          <div class="bg-purple-50 rounded-xl p-4 text-center">
            <p class="text-xs text-purple-500 mb-1">开始时间</p>
            <p class="text-sm font-semibold text-gray-800">{{ formatTime(shareData.startTime) }}</p>
          </div>
          <div class="bg-orange-50 rounded-xl p-4 text-center">
            <p class="text-xs text-orange-500 mb-1">结束时间</p>
            <p class="text-sm font-semibold text-gray-800">{{ formatTime(shareData.endTime) }}</p>
          </div>
        </div>

        <!-- 详细结果 -->
        <div v-if="shareData.resultData && Object.keys(shareData.resultData).length > 0">
          <h3 class="text-sm font-semibold text-gray-700 mb-3">检测详情</h3>
          <div class="bg-gray-50 rounded-xl p-5 grid grid-cols-2 md:grid-cols-3 gap-4">
            <div v-if="shareData.resultData.frame_count != null">
              <p class="text-xs text-gray-500">处理帧数</p>
              <p class="text-lg font-bold text-gray-800">{{ shareData.resultData.frame_count }}</p>
            </div>
            <div v-if="shareData.resultData.duration != null">
              <p class="text-xs text-gray-500">推理时长</p>
              <p class="text-lg font-bold text-gray-800">{{ Number(shareData.resultData.duration).toFixed(1) }} 秒</p>
            </div>
            <template v-if="shareData.resultData.class_counts">
              <div v-for="(count, cls) in shareData.resultData.class_counts" :key="cls">
                <p class="text-xs text-gray-500">{{ cls }}</p>
                <p class="text-lg font-bold text-gray-800">{{ count }}</p>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 底部品牌信息 -->
      <div class="border-t border-gray-100 px-8 py-4 text-center">
        <p class="text-xs text-gray-400">Powered by <span class="font-semibold text-gray-500">SkyFlowTracker</span></p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
</style>
