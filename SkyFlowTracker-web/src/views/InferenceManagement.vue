<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import Toast from 'primevue/toast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import Checkbox from 'primevue/checkbox'
import { useToast } from 'primevue/usetoast'
import { getInferenceTasksList, getInferenceTasksDetail, deleteInferenceTasks, startOfflineInference } from '../api/inference'
import { generateSummaryStream } from '../api/ai'
import { createShare } from '../api/share'
import { checkToken } from '../api/auth'
import MarkdownIt from 'markdown-it'
import { toMediaUrl } from '../utils/media'

const md = new MarkdownIt({ breaks: true, linkify: true })

const toast = useToast()
const isAdmin = ref(false)
const loading = ref(true)
const tasks = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedTasks = ref([])
const deleteLoading = ref(false)

// 详情对话框
const showDetailDialog = ref(false)
const detailLoading = ref(false)
const taskDetail = ref(null)

// 视频播放对话框
const showVideoDialog = ref(false)
const videoUrl = ref('')

// AI 智能摘要
const summaryLoading = ref(false)
const summaryContent = ref('')
const summaryHtml = computed(() => summaryContent.value ? md.render(summaryContent.value) : '')

// 分享功能
const showShareDialog = ref(false)
const shareLoading = ref(false)
const shareExpireHours = ref(24)
const shareResult = ref(null)
const shareCopied = ref(false)

// 离线推理
const showOfflineDialog = ref(false)
const offlineLoading = ref(false)
const selectedVideo = ref(null)
const offlineConfidence = ref(0.5)
const offlineSessionId = ref(null)
const offlineWs = ref(null)
const offlineFrame = ref('')
const offlineProgress = ref('')
const offlineRunning = ref(false)

// 帧缓冲队列 - 实现流畅播放
const frameBuffer = []
let playbackTimer = null
let playbackInterval = 40 // 默认约25fps，收到视频帧率后动态调整

const expireOptions = [
  { label: '1 小时', value: 1 },
  { label: '24 小时', value: 24 },
  { label: '3 天', value: 72 },
  { label: '7 天', value: 168 }
]

const shareLink = computed(() => {
  if (!shareResult.value?.shareCode) return ''
  return `${window.location.origin}/share/${shareResult.value.shareCode}`
})

const openShareDialog = () => {
  shareResult.value = null
  shareCopied.value = false
  shareExpireHours.value = 24
  showShareDialog.value = true
}

const handleCreateShare = async () => {
  if (!taskDetail.value) return
  shareLoading.value = true
  try {
    const res = await createShare(taskDetail.value.id, shareExpireHours.value)
    if (res.code === 1) {
      shareResult.value = res.data
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '创建分享失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '创建分享失败', life: 3000 })
  } finally {
    shareLoading.value = false
  }
}

const copyShareLink = async () => {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(shareLink.value)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = shareLink.value
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    shareCopied.value = true
    toast.add({ severity: 'success', summary: '成功', detail: '链接已复制到剪贴板', life: 2000 })
    setTimeout(() => { shareCopied.value = false }, 2000)
  } catch {
    toast.add({ severity: 'error', summary: '错误', detail: '复制失败，请手动复制', life: 3000 })
  }
}

const openVideo = (url) => {
  videoUrl.value = toMediaUrl(url)
  showVideoDialog.value = true
}

const pageSizeOptions = [
  { label: '10 条/页', value: 10 },
  { label: '20 条/页', value: 20 },
  { label: '50 条/页', value: 50 },
  { label: '100 条/页', value: 100 }
]

const isAllSelected = computed({
  get: () => tasks.value.length > 0 && selectedTasks.value.length === tasks.value.length,
  set: (value) => {
    selectedTasks.value = value ? tasks.value.map(t => t.id) : []
  }
})

const fetchTasks = async () => {
  loading.value = true
  try {
    const res = await getInferenceTasksList(currentPage.value, pageSize.value)
    if (res.code === 1) {
      tasks.value = res.data.tasksList || []
      total.value = res.data.pagination?.total || 0
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '获取推理任务失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取推理任务失败', life: 3000 })
  } finally {
    loading.value = false
  }
}

const openDetail = async (task) => {
  showDetailDialog.value = true
  detailLoading.value = true
  taskDetail.value = null
  try {
    const res = await getInferenceTasksDetail(task.id)
    if (res.code === 1) {
      taskDetail.value = res.data
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '获取详情失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取详情失败', life: 3000 })
  } finally {
    detailLoading.value = false
  }
}

const handleGenerateSummary = (taskId) => {
  summaryLoading.value = true
  summaryContent.value = ''

  generateSummaryStream(taskId, {
    onChunk: (chunk) => {
      summaryContent.value += chunk
    },
    onDone: () => {
      summaryLoading.value = false
    },
    onError: (err) => {
      summaryLoading.value = false
      toast.add({ severity: 'error', summary: '错误', detail: err || 'AI 摘要服务请求失败', life: 3000 })
    }
  })
}

const handleDelete = async (task) => {
  if (!confirm(`确定要删除推理任务 #${task.id} 吗？`)) return
  deleteLoading.value = true
  try {
    const res = await deleteInferenceTasks([task.id])
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      fetchTasks()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

const handleBatchDelete = async () => {
  if (selectedTasks.value.length === 0) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请先选择要删除的记录', life: 3000 })
    return
  }
  if (!confirm(`确定要删除选中的 ${selectedTasks.value.length} 条推理任务吗？`)) return
  deleteLoading.value = true
  try {
    const res = await deleteInferenceTasks(selectedTasks.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      selectedTasks.value = []
      fetchTasks()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

const handleVideoSelect = (event) => {
  const file = event.target.files[0]
  if (file && file.type.startsWith('video/')) {
    selectedVideo.value = file
  } else {
    toast.add({ severity: 'warn', summary: '提示', detail: '请选择视频文件', life: 3000 })
  }
}

const connectOfflineWs = (sessionId) => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const ws = new WebSocket(`${protocol}//${window.location.host}/ws/inference?sn=${sessionId}`)
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'frame') {
        if (data.fps && !playbackTimer) {
          // 每3帧推送一次，播放间隔 = 3帧的时间
          playbackInterval = Math.round(3000 / data.fps)
        }
        frameBuffer.push({
          src: 'data:image/jpeg;base64,' + data.annotated_image,
          progress: `帧: ${data.frame_number}/${data.total_frames} | 检测车辆: ${data.total_detections}`
        })
        if (!playbackTimer) startPlayback()
      } else if (data.type === 'complete') {
        // 等缓冲区播完再显示完成
        const waitForBuffer = () => {
          if (frameBuffer.length === 0) {
            stopPlayback()
            offlineRunning.value = false
            offlineProgress.value = '推理完成!'
            toast.add({ severity: 'success', summary: '成功', detail: '离线推理已完成', life: 3000 })
            fetchTasks()
            setTimeout(() => {
              ws.close()
              offlineWs.value = null
            }, 2000)
          } else {
            setTimeout(waitForBuffer, 100)
          }
        }
        waitForBuffer()
      }
    } catch (e) {
      console.error('解析离线推理消息失败:', e)
    }
  }
  ws.onerror = () => {
    console.error('离线推理WebSocket连接错误')
  }
  ws.onclose = () => {
    offlineWs.value = null
  }
  offlineWs.value = ws
}

const startPlayback = () => {
  if (playbackTimer) return
  playbackTimer = setInterval(() => {
    if (frameBuffer.length > 0) {
      const frame = frameBuffer.shift()
      offlineFrame.value = frame.src
      offlineProgress.value = frame.progress
    }
  }, playbackInterval)
}

const stopPlayback = () => {
  if (playbackTimer) {
    clearInterval(playbackTimer)
    playbackTimer = null
  }
}

const handleStartOffline = async () => {
  if (!selectedVideo.value) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请先选择视频文件', life: 3000 })
    return
  }
  offlineLoading.value = true
  offlineFrame.value = ''
  offlineProgress.value = ''
  try {
    const formData = new FormData()
    formData.append('video', selectedVideo.value)
    formData.append('modelName', 'yolov12m')
    formData.append('confidence', offlineConfidence.value)

    const res = await startOfflineInference(formData)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '离线推理已启动', life: 3000 })
      offlineRunning.value = true
      offlineSessionId.value = res.data.sessionId
      // 连接WebSocket接收实时画面
      connectOfflineWs(res.data.sessionId)
      fetchTasks()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '推理失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '推理失败', life: 3000 })
  } finally {
    offlineLoading.value = false
  }
}

const closeOfflineDialog = () => {
  if (offlineWs.value) {
    offlineWs.value.close()
    offlineWs.value = null
  }
  stopPlayback()
  frameBuffer.length = 0
  playbackInterval = 40
  showOfflineDialog.value = false
  offlineRunning.value = false
  offlineFrame.value = ''
  offlineProgress.value = ''
  selectedVideo.value = null
}

onUnmounted(() => {
  if (offlineWs.value) {
    offlineWs.value.close()
  }
  stopPlayback()
  frameBuffer.length = 0
})

const handlePageSizeChange = () => {
  currentPage.value = 1
  fetchTasks()
}

const formatStatus = (status) => {
  const map = { 0: '进行中', 1: '已完成' }
  return map[status] ?? '未知'
}

const statusClass = (status) => {
  const map = {
    0: 'bg-blue-100 text-blue-800',
    1: 'bg-green-100 text-green-800'
  }
  return map[status] ?? 'bg-gray-100 text-gray-800'
}

const formatTime = (time) => time || '-'

onMounted(async () => {
  try {
    const res = await checkToken()
    if (res.code === 1) {
      isAdmin.value = res.data.role === 1
    }
  } catch (error) {}
  fetchTasks()
})
</script>

<template>
  <div>
    <Toast />

    <div class="bg-white rounded-2xl shadow-lg p-8">
      <!-- 顶部操作栏 -->
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-2xl font-bold text-gray-900">推理任务管理</h2>
        <div class="flex gap-3">
          <Button
            label="离线推理"
            icon="pi pi-upload"
            @click="showOfflineDialog = true"
            class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
          />
          <Button
            v-if="isAdmin"
            label="批量删除"
            icon="pi pi-trash"
            severity="danger"
            @click="handleBatchDelete"
            :disabled="selectedTasks.length === 0 || deleteLoading"
            :loading="deleteLoading"
            class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg"
          />
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="flex justify-center py-12">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
      </div>

      <!-- 空状态 -->
      <div v-else-if="tasks.length === 0" class="text-center py-12">
        <i class="pi pi-microchip text-6xl text-gray-400 mb-4"></i>
        <p class="text-gray-600">暂无推理任务</p>
      </div>

      <!-- 推理任务表格 -->
      <div v-else>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="border-b border-gray-200">
                <th v-if="isAdmin" class="text-left py-3 px-4 w-12">
                  <Checkbox v-model="isAllSelected" :binary="true" />
                </th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700 w-16">序号</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">飞行记录ID</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">模型</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">开始时间</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">结束时间</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">检测总数</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">状态</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(task, index) in tasks" :key="task.id" class="border-b border-gray-100 hover:bg-gray-50">
                <td v-if="isAdmin" class="py-3 px-4">
                  <Checkbox v-model="selectedTasks" :value="task.id" :binary="false" />
                </td>
                <td class="py-3 px-4 text-sm text-gray-900">{{ (currentPage - 1) * pageSize + index + 1 }}</td>
                <td class="py-3 px-4 text-sm text-gray-900 font-mono">
                  <span v-if="task.flightId" >{{ task.flightId }}</span>
                  <span v-else class="px-2 py-0.5 text-xs font-semibold rounded-full bg-purple-100 text-purple-800">离线推理</span>
                </td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ task.modelName || '-' }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ formatTime(task.startTime) }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ formatTime(task.endTime) }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ task.totalDetections ?? '-' }}</td>
                <td class="py-3 px-4">
                  <span :class="['px-2 py-1 text-xs font-semibold rounded-full', statusClass(task.status)]">
                    {{ formatStatus(task.status) }}
                  </span>
                </td>
                <td class="py-3 px-4">
                  <button @click="openDetail(task)" class="text-blue-600 hover:text-blue-700 mr-3 cursor-pointer" title="查看详情">
                    <i class="pi pi-eye"></i>
                  </button>
                  <button v-if="isAdmin" @click="handleDelete(task)" class="text-red-600 hover:text-red-700 cursor-pointer" title="删除">
                    <i class="pi pi-trash"></i>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="flex justify-between items-center mt-6">
          <div class="flex items-center gap-3">
            <p class="text-sm text-gray-600">共 {{ total }} 条记录</p>
            <Dropdown
              v-model="pageSize"
              :options="pageSizeOptions"
              optionLabel="label"
              optionValue="value"
              @change="handlePageSizeChange"
              class="w-32"
            />
          </div>
          <div class="flex items-center gap-3">
            <button
              @click="currentPage--, fetchTasks()"
              :disabled="currentPage === 1"
              class="w-10 h-10 flex items-center justify-center rounded-lg border-2 transition-colors"
              :class="currentPage === 1
                ? 'border-gray-300 text-gray-400 cursor-not-allowed'
                : 'border-blue-600 text-blue-600 hover:bg-blue-50 cursor-pointer'"
            >
              <i class="pi pi-chevron-left"></i>
            </button>
            <span class="text-sm text-gray-700">
              第 {{ currentPage }} 页 / 共 {{ Math.ceil(total / pageSize) || 1 }} 页
            </span>
            <button
              @click="currentPage++, fetchTasks()"
              :disabled="currentPage * pageSize >= total"
              class="w-10 h-10 flex items-center justify-center rounded-lg border-2 transition-colors"
              :class="currentPage * pageSize >= total
                ? 'border-gray-300 text-gray-400 cursor-not-allowed'
                : 'border-blue-600 text-blue-600 hover:bg-blue-50 cursor-pointer'"
            >
              <i class="pi pi-chevron-right"></i>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 推理任务详情对话框 -->
    <Dialog v-model:visible="showDetailDialog" modal header="推理任务详情" :style="{ width: '600px' }">
      <div v-if="detailLoading" class="flex justify-center py-8">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
      </div>

      <div v-else-if="taskDetail" class="space-y-6">
        <div class="grid grid-cols-2 gap-4">
          <div>
            <p class="text-sm text-gray-500 mb-1">任务ID</p>
            <p class="text-sm text-gray-900 font-semibold">{{ taskDetail.id }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500 mb-1">飞行记录ID</p>
            <p v-if="taskDetail.flightId" class="text-sm text-gray-900 font-mono">{{ taskDetail.flightId }}</p>
            <span v-else class="px-2 py-0.5 text-xs font-semibold rounded-full bg-purple-100 text-purple-800">离线推理</span>
          </div>
          <div>
            <p class="text-sm text-gray-500 mb-1">模型名称</p>
            <p class="text-sm text-gray-900">{{ taskDetail.modelName || '-' }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500 mb-1">状态</p>
            <span :class="['px-2 py-1 text-xs font-semibold rounded-full', statusClass(taskDetail.status)]">
              {{ formatStatus(taskDetail.status) }}
            </span>
          </div>
          <div>
            <p class="text-sm text-gray-500 mb-1">开始时间</p>
            <p class="text-sm text-gray-900">{{ formatTime(taskDetail.startTime) }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500 mb-1">结束时间</p>
            <p class="text-sm text-gray-900">{{ formatTime(taskDetail.endTime) }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500 mb-1">检测总数</p>
            <p class="text-sm text-gray-900">{{ taskDetail.totalDetections ?? '-' }}</p>
          </div>
          <div>
            <p class="text-sm text-gray-500 mb-1">推理视频</p>
            <div class="flex gap-2">
              <Button v-if="taskDetail.videoPath" label="播放视频" icon="pi pi-play"
                @click="openVideo(taskDetail.videoPath)" size="small"
                class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg" />
              <Button v-if="taskDetail.status === 1 && taskDetail.videoPath" label="生成分享链接" icon="pi pi-share-alt"
                @click="openShareDialog" size="small"
                class="!bg-teal-600 hover:!bg-teal-700 !border-0 !text-white !rounded-lg" />
              <p v-if="!taskDetail.videoPath" class="text-sm text-gray-900">-</p>
            </div>
          </div>
        </div>

        <div v-if="taskDetail.resultData && Object.keys(taskDetail.resultData).length > 0">
          <p class="text-sm text-gray-500 mb-2">推理结果数据</p>
          <div class="bg-gray-50 rounded-lg p-4 grid grid-cols-2 gap-3">
            <div v-if="taskDetail.resultData.frame_count != null">
              <p class="text-xs text-gray-500">处理帧数</p>
              <p class="text-sm text-gray-900 font-semibold">{{ taskDetail.resultData.frame_count }}</p>
            </div>
            <div v-if="taskDetail.resultData.duration != null">
              <p class="text-xs text-gray-500">推理时长</p>
              <p class="text-sm text-gray-900 font-semibold">{{ Number(taskDetail.resultData.duration).toFixed(1) }} 秒</p>
            </div>
            <template v-if="taskDetail.resultData.class_counts">
              <div v-for="(count, cls) in taskDetail.resultData.class_counts" :key="cls">
                <p class="text-xs text-gray-500">{{ cls }}</p>
                <p class="text-sm text-gray-900 font-semibold">{{ count }}</p>
              </div>
            </template>
          </div>
        </div>

        <!-- AI 智能摘要 -->
        <div v-if="taskDetail.status === 1">
          <div class="flex items-center justify-between mb-2">
            <p class="text-sm text-gray-500">AI 智能摘要</p>
            <Button
              label="生成摘要"
              icon="pi pi-microchip-ai"
              @click="handleGenerateSummary(taskDetail.id)"
              :loading="summaryLoading"
              size="small"
              class="!bg-purple-600 hover:!bg-purple-700 !border-0 !text-white !rounded-lg"
            />
          </div>
          <div v-if="summaryLoading && !summaryContent" class="bg-purple-50 rounded-lg p-4 text-sm text-purple-600">
            <i class="pi pi-spin pi-spinner mr-1"></i> AI 正在分析中...
          </div>
          <div v-if="summaryContent" class="bg-purple-50 rounded-lg p-4 text-sm text-gray-800 leading-relaxed markdown-body" v-html="summaryHtml">
          </div>
        </div>
      </div>

      <template #footer>
        <Button
          label="关闭"
          @click="showDetailDialog = false; summaryContent = ''"
          outlined
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
      </template>
    </Dialog>

    <!-- 视频播放对话框 -->
    <Dialog v-model:visible="showVideoDialog" modal header="推理视频回放" :style="{ width: '800px' }">
      <video v-if="showVideoDialog" :src="videoUrl" controls autoplay class="w-full rounded-lg" />
    </Dialog>

    <!-- 分享链接对话框 -->
    <Dialog v-model:visible="showShareDialog" modal header="生成分享链接" :style="{ width: '480px' }">
      <div class="space-y-5">
        <!-- 未生成状态 -->
        <div v-if="!shareResult">
          <p class="text-sm text-gray-600 mb-4">生成一个公开链接，外部人员无需登录即可查看推理视频和检测信息。</p>
          <div class="mb-4">
            <label class="text-sm text-gray-700 font-medium mb-2 block">有效期</label>
            <Dropdown
              v-model="shareExpireHours"
              :options="expireOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
          </div>
          <Button
            label="生成链接"
            icon="pi pi-link"
            @click="handleCreateShare"
            :loading="shareLoading"
            class="!bg-teal-600 hover:!bg-teal-700 !border-0 !text-white !rounded-lg w-full"
          />
        </div>

        <!-- 已生成状态 -->
        <div v-else>
          <div class="flex items-center gap-2 mb-4">
            <i class="pi pi-check-circle text-green-500 text-xl"></i>
            <span class="text-sm font-semibold text-green-700">分享链接已生成</span>
          </div>
          <div class="bg-gray-50 rounded-lg p-3 flex items-center gap-2">
            <input
              type="text"
              :value="shareLink"
              readonly
              class="flex-1 bg-transparent text-sm text-gray-800 outline-none font-mono"
            />
            <Button
              :label="shareCopied ? '已复制' : '复制'"
              :icon="shareCopied ? 'pi pi-check' : 'pi pi-copy'"
              @click="copyShareLink"
              size="small"
              :class="shareCopied
                ? '!bg-green-600 !border-0 !text-white !rounded-lg'
                : '!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg'"
            />
          </div>
          <p class="text-xs text-gray-400 mt-3">
            过期时间：{{ shareResult.expireTime?.replace('T', ' ') }}
          </p>
        </div>
      </div>

      <template #footer>
        <Button
          label="关闭"
          @click="showShareDialog = false"
          outlined
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
      </template>
    </Dialog>

    <!-- 离线推理对话框 -->
    <Dialog v-model:visible="showOfflineDialog" modal header="离线视频推理" :style="{ width: offlineRunning || offlineFrame ? '800px' : '500px' }" @hide="closeOfflineDialog">
      <div class="space-y-4">
        <!-- 实时推理画面 -->
        <div v-if="offlineFrame" class="relative">
          <img :src="offlineFrame" class="w-full rounded-lg" alt="推理画面" />
          <div v-if="offlineProgress" class="absolute bottom-2 left-2 bg-black/70 text-white text-xs px-3 py-1.5 rounded-lg">
            {{ offlineProgress }}
          </div>
          <div v-if="offlineRunning" class="absolute top-2 right-2 bg-red-600 text-white text-xs px-2 py-1 rounded-full flex items-center gap-1">
            <span class="w-2 h-2 bg-white rounded-full animate-pulse"></span> 推理中
          </div>
        </div>

        <template v-if="!offlineRunning">
          <div>
            <label class="text-sm text-gray-700 font-medium mb-2 block">选择视频文件</label>
            <input
              type="file"
              accept="video/*"
              @change="handleVideoSelect"
              class="w-full text-sm text-gray-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
            />
            <p v-if="selectedVideo" class="text-xs text-gray-500 mt-2">已选择: {{ selectedVideo.name }}</p>
          </div>

          <div>
            <label class="text-sm text-gray-700 font-medium mb-2 block">置信度阈值: {{ offlineConfidence }}</label>
            <input
              type="range"
              v-model.number="offlineConfidence"
              min="0.1"
              max="0.9"
              step="0.05"
              class="w-full"
            />
          </div>
        </template>
      </div>

      <template #footer>
        <Button
          :label="offlineRunning ? '关闭' : '取消'"
          @click="closeOfflineDialog"
          outlined
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
        <Button
          v-if="!offlineRunning && !offlineFrame"
          label="开始推理"
          icon="pi pi-play"
          @click="handleStartOffline"
          :loading="offlineLoading"
          :disabled="!selectedVideo"
          class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
:deep(.p-dialog) {
  background-color: #ffffff !important;
}
:deep(.p-dialog .p-dialog-header) {
  background-color: #ffffff !important;
  color: #1F2937 !important;
}
:deep(.p-dialog .p-dialog-content) {
  background-color: #ffffff !important;
  color: #1F2937 !important;
}
:deep(.p-dialog .p-dialog-footer) {
  background-color: #ffffff !important;
}
:deep(.p-checkbox) {
  width: 1.25rem;
  height: 1.25rem;
  min-width: 1.25rem;
  max-width: 1.25rem;
  flex-shrink: 0;
}
:deep(.p-checkbox .p-checkbox-box) {
  width: 1.25rem;
  height: 1.25rem;
  background-color: #ffffff;
  border: 2px solid #d1d5db;
  cursor: pointer;
}
:deep(.p-checkbox .p-checkbox-box.p-highlight) {
  background-color: #ffffff;
  border-color: #2563eb;
}
:deep(.p-checkbox .p-checkbox-box .p-checkbox-icon) {
  color: #2563eb;
  font-size: 0.875rem;
}
:deep(.p-dropdown) {
  background-color: #ffffff !important;
}

/* Markdown 渲染样式 */
.markdown-body :deep(p) {
  margin: 0.4em 0;
  line-height: 1.6;
}
.markdown-body :deep(p:first-child) {
  margin-top: 0;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(strong) {
  font-weight: 700;
}
.markdown-body :deep(em) {
  font-style: italic;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  font-weight: 700;
  margin: 0.6em 0 0.3em;
  line-height: 1.4;
}
.markdown-body :deep(h1) { font-size: 1.25em; }
.markdown-body :deep(h2) { font-size: 1.15em; }
.markdown-body :deep(h3) { font-size: 1.05em; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.5em;
}
.markdown-body :deep(ul) { list-style-type: disc; }
.markdown-body :deep(ol) { list-style-type: decimal; }
.markdown-body :deep(li) {
  margin: 0.2em 0;
}
.markdown-body :deep(li > ul),
.markdown-body :deep(li > ol) {
  margin: 0.1em 0;
}
.markdown-body :deep(code) {
  background-color: rgba(0, 0, 0, 0.06);
  padding: 0.15em 0.35em;
  border-radius: 4px;
  font-size: 0.9em;
  font-family: 'Courier New', Courier, monospace;
}
.markdown-body :deep(pre) {
  background-color: #1e1e1e;
  color: #d4d4d4;
  padding: 0.75em 1em;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0.5em 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #d1d5db;
  padding-left: 0.75em;
  margin: 0.4em 0;
  color: #6b7280;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 0.6em 0;
}
.markdown-body :deep(a) {
  color: #2563eb;
  text-decoration: underline;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.5em 0;
  width: 100%;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #d1d5db;
  padding: 0.35em 0.6em;
  text-align: left;
}
.markdown-body :deep(th) {
  background-color: #f3f4f6;
  font-weight: 600;
}
</style>
