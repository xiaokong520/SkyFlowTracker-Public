<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import Toast from 'primevue/toast'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import { useToast } from 'primevue/usetoast'
import { checkToken, getUsersList } from '../api/auth'
import { getDevicesList } from '../api/devices'
import { startInference, stopInference, startDemo, stopDemo, startLatencyTest, stopLatencyTest } from '../api/inference'

const toast = useToast()
const isAdmin = ref(false)
const loading = ref(true)

// 用户选择（管理员用）
const users = ref([])
const selectedUserId = ref(null)

// 设备选择
const devices = ref([])
const selectedSn = ref(null)

// WebSocket 与画面
const ws = ref(null)
const connected = ref(false)
const frameUrl = ref('')
const videoCanvas = ref(null)
const hasFrame = ref(false)
const detectTotal = ref(0)
const detections = ref([])
const summary = ref({})

// 推理相关
const inferenceTaskId = ref(null)
const inferring = ref(false)
const inferenceLoading = ref(false)

// 演示模式相关
const demoActive = ref(false)
const demoFlightId = ref(null)
const demoLoading = ref(false)

// 延迟测试模式相关
const latencyTestActive = ref(false)
const latencyTestFlightId = ref(null)
const latencyTestLoading = ref(false)

// 加载用户列表（管理员）
const fetchUsers = async () => {
  try {
    const res = await getUsersList(1, 9999)
    if (res.code === 1) {
      users.value = (res.data.userInfoList || []).map(u => ({
        label: `${u.userName} (${u.nickName || '-'})`,
        value: u.userId
      }))
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取用户列表失败', life: 3000 })
  }
}

// 加载设备列表
const fetchDevices = async (userId = '') => {
  try {
    const res = await getDevicesList(1, 9999, '', userId)
    if (res.code === 1) {
      devices.value = (res.data.devicesList || []).map(d => ({
        label: `${d.sn}-${d.model || '未知型号'}`,
        value: d.sn
      }))
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取设备列表失败', life: 3000 })
  }
}

// 管理员切换用户时重新加载设备
watch(selectedUserId, (val) => {
  selectedSn.value = null
  disconnect()
  if (val) fetchDevices(val)
  else devices.value = []
})

// 建立 WebSocket 连接
const connect = () => {
  if (!selectedSn.value) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请先选择设备', life: 3000 })
    return
  }
  disconnect()
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/inference?sn=${selectedSn.value}`
  ws.value = new WebSocket(wsUrl)

  ws.value.binaryType = 'arraybuffer'

  ws.value.onopen = () => {
    connected.value = true
    toast.add({ severity: 'success', summary: '已连接', detail: `正在查看设备 ${selectedSn.value}`, life: 2000 })
  }
  ws.value.onmessage = (event) => {
    try {
      if (event.data instanceof ArrayBuffer) {
        // 二进制协议: [4字节元数据长度(big-endian)][元数据JSON][JPEG图片]
        const view = new DataView(event.data)
        const metaLen = view.getUint32(0)
        const metaBytes = new Uint8Array(event.data, 4, metaLen)
        const metaJson = new TextDecoder().decode(metaBytes)
        const msg = JSON.parse(metaJson)

        if (msg.code === 1 && msg.data) {
          const jpegData = new Uint8Array(event.data, 4 + metaLen)
          const blob = new Blob([jpegData], { type: 'image/jpeg' })

          // 端到端延迟追踪: 记录接收时刻
          const recvTs = Date.now()
          const recvFrameId = msg.data._frame_id

          // 直接渲染（Flask 端已通过跳帧保证时间同步）
          renderBlobToCanvas(blob, () => {
            // 渲染完成后写入延迟日志
            const renderTs = Date.now()
            if (recvFrameId != null) {
              console.log('LATENCY|' + recvFrameId + '|4|' + renderTs)
            }
          })
          hasFrame.value = true
          updateDetectionData(msg.data)
        }
      } else {
        // 文本消息 fallback（兼容离线推理等场景）
        const msg = JSON.parse(event.data)
        if (msg.type === 'auto_stopped') {
          inferenceTaskId.value = null
          inferring.value = false
          demoActive.value = false
          demoFlightId.value = null
          toast.add({ severity: 'info', summary: '提示', detail: '演示视频播放结束，推理已自动停止', life: 4000 })
        } else if (msg.code === 1 && msg.data) {
          const base64 = msg.data.annotated_image
          if (base64) {
            renderFrameToCanvas(base64)
            hasFrame.value = true
          }
          updateDetectionData(msg.data)
        }
      }
    } catch (e) {}
  }
  ws.value.onclose = () => { connected.value = false }
  ws.value.onerror = () => {
    connected.value = false
    toast.add({ severity: 'error', summary: '连接错误', detail: 'WebSocket 连接失败', life: 3000 })
  }
}

// 断开连接
const disconnect = () => {
  if (ws.value) { ws.value.close(); ws.value = null }
  connected.value = false
  frameUrl.value = ''
  hasFrame.value = false
  detectTotal.value = 0
  detections.value = []
  // 断开时清理延迟测试状态
  if (latencyTestActive.value) {
    stopLatencyTest(selectedSn.value).catch(() => {})
  }
  latencyTestActive.value = false
  latencyTestFlightId.value = null
}

// 高效渲染帧到 Canvas（避免 img data URI 的重复解码开销）
const renderFrameToCanvas = (base64, onDone) => {
  const canvas = videoCanvas.value
  if (!canvas) return
  const byteStr = atob(base64)
  const bytes = new Uint8Array(byteStr.length)
  for (let i = 0; i < byteStr.length; i++) bytes[i] = byteStr.charCodeAt(i)
  const blob = new Blob([bytes], { type: 'image/jpeg' })
  renderBlobToCanvas(blob, onDone)
}

// 直接从 Blob 渲染到 Canvas（二进制帧直推时使用，跳过 Base64 解码）
const renderBlobToCanvas = (blob, onDone) => {
  const canvas = videoCanvas.value
  if (!canvas) return
  createImageBitmap(blob).then(bitmap => {
    if (canvas.width !== bitmap.width || canvas.height !== bitmap.height) {
      canvas.width = bitmap.width
      canvas.height = bitmap.height
    }
    const ctx = canvas.getContext('2d')
    ctx.drawImage(bitmap, 0, 0)
    bitmap.close()
    if (onDone) onDone()
  }).catch(() => {
    if (onDone) onDone()
  })
}

// 更新检测数据（统一处理二进制和文本消息的检测结果）
const updateDetectionData = (data) => {
  detectTotal.value = data.total || 0
  detections.value = data.detections || []
  if (data.summary && Object.keys(data.summary).length > 0) {
    summary.value = data.summary
  } else {
    const counts = {}
    for (const d of detections.value) {
      const cls = d.class || d.label || 'unknown'
      counts[cls] = (counts[cls] || 0) + 1
    }
    summary.value = counts
  }
}

// 开始推理
const handleStartInference = async () => {
  inferenceLoading.value = true
  try {
    const res = await startInference(selectedSn.value, 'yolov12m', demoFlightId.value || latencyTestFlightId.value)
    if (res.code === 1) {
      inferenceTaskId.value = res.data.taskId
      inferring.value = true
      toast.add({ severity: 'success', summary: '成功', detail: '推理已开始', life: 2000 })
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '开始推理失败', life: 3000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '开始推理失败', life: 3000 })
  } finally {
    inferenceLoading.value = false
  }
}

// 停止推理
const handleStopInference = async () => {
  if (!inferenceTaskId.value) return
  inferenceLoading.value = true
  try {
    const res = await stopInference(inferenceTaskId.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '推理已停止', life: 2000 })
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '停止推理失败', life: 3000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '停止推理失败', life: 3000 })
  } finally {
    inferenceTaskId.value = null
    inferring.value = false
    inferenceLoading.value = false
    demoActive.value = false
    demoFlightId.value = null
  }
}

// 开始演示视频
const handleStartDemo = async () => {
  demoLoading.value = true
  try {
    const res = await startDemo(selectedSn.value)
    if (res.code === 1) {
      demoActive.value = true
      demoFlightId.value = res.data.flightId
      toast.add({ severity: 'success', summary: '成功', detail: '演示视频已开始', life: 2000 })
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '启动演示失败', life: 3000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '启动演示失败', life: 3000 })
  } finally {
    demoLoading.value = false
  }
}

// 停止演示视频
const handleStopDemo = async () => {
  demoLoading.value = true
  try {
    const res = await stopDemo(selectedSn.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '演示已停止', life: 2000 })
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '停止演示失败', life: 3000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '停止演示失败', life: 3000 })
  } finally {
    demoActive.value = false
    demoFlightId.value = null
    demoLoading.value = false
    if (inferring.value) {
      handleStopInference()
    }
  }
}

// 开始延迟测试（Android 端播放本地视频 + 正常推理管道）
const handleStartLatencyTest = async () => {
  latencyTestLoading.value = true
  try {
    const res = await startLatencyTest(selectedSn.value)
    if (res.code === 1) {
      latencyTestActive.value = true
      latencyTestFlightId.value = res.data.flightId
      toast.add({ severity: 'success', summary: '成功', detail: '延迟测试已启动，Android端播放本地视频', life: 2000 })
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '启动延迟测试失败', life: 3000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '启动延迟测试失败', life: 3000 })
  } finally {
    latencyTestLoading.value = false
  }
}

// 停止延迟测试
const handleStopLatencyTest = async () => {
  latencyTestLoading.value = true
  try {
    const res = await stopLatencyTest(selectedSn.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '延迟测试已停止', life: 2000 })
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '停止延迟测试失败', life: 3000 })
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: '错误', detail: '停止延迟测试失败', life: 3000 })
  } finally {
    latencyTestActive.value = false
    latencyTestFlightId.value = null
    latencyTestLoading.value = false
    if (inferring.value) {
      handleStopInference()
    }
  }
}

// 页面关闭前自动停止推理
const handleBeforeUnload = async () => {
  if (inferring.value && inferenceTaskId.value) {
    // 使用 sendBeacon 确保请求能发出（即使页面正在关闭）
    const token = localStorage.getItem('token')
    const data = JSON.stringify({ taskId: inferenceTaskId.value })
    const blob = new Blob([data], { type: 'application/json' })
    navigator.sendBeacon(`/api/inference/stop?token=${token}`, blob)
  }
}

onMounted(async () => {
  try {
    const res = await checkToken()
    if (res.code === 1) {
      isAdmin.value = res.data.role === 1
      if (isAdmin.value) await fetchUsers()
      else await fetchDevices()
    }
  } catch (e) {}
  loading.value = false

  // 监听页面关闭事件
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  disconnect()
  // 组件卸载时也停止推理
  if (inferring.value && inferenceTaskId.value) {
    stopInference(inferenceTaskId.value).catch(() => {})
  }
  // 停止延迟测试
  if (latencyTestActive.value) {
    stopLatencyTest(selectedSn.value).catch(() => {})
  }
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <div>
    <Toast />

    <div class="bg-white rounded-2xl shadow-lg p-8 mb-6">
      <h2 class="text-2xl font-bold text-gray-900 mb-6">实时追踪</h2>

      <!-- 加载中 -->
      <div v-if="loading" class="flex justify-center py-12">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
      </div>

      <div v-else>
        <!-- 选择区域 -->
        <div class="flex flex-wrap items-end gap-4 mb-6">
          <!-- 管理员：选择用户 -->
          <div v-if="isAdmin" class="flex flex-col">
            <label class="text-sm font-medium text-gray-700 mb-2">选择用户</label>
            <Dropdown v-model="selectedUserId" :options="users" optionLabel="label"
              optionValue="value" placeholder="请选择用户" filter class="w-64" />
          </div>

          <!-- 选择设备 -->
          <div class="flex flex-col">
            <label class="text-sm font-medium text-gray-700 mb-2">选择设备</label>
            <Dropdown v-model="selectedSn" :options="devices" optionLabel="label"
              optionValue="value" placeholder="请选择设备 (SN码-型号)" filter
              :disabled="isAdmin && !selectedUserId" class="w-72" />
          </div>

          <!-- 查看按钮 -->
          <Button v-if="!connected" label="开始查看" icon="pi pi-play" @click="connect"
            :disabled="!selectedSn"
            class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg" />
          <Button v-else label="停止查看" icon="pi pi-stop" @click="disconnect"
            class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg" />
        </div>

        <!-- 推理控制 -->
        <div v-if="connected" class="flex flex-wrap items-end gap-4 mb-6">
          <Button v-if="!demoActive" label="演示视频" icon="pi pi-video"
            @click="handleStartDemo"
            :loading="demoLoading"
            :disabled="latencyTestActive"
            class="!bg-purple-600 hover:!bg-purple-700 !border-0 !text-white !rounded-lg" />
          <Button v-else label="停止演示" icon="pi pi-stop"
            @click="handleStopDemo" :loading="demoLoading"
            class="!bg-purple-600 hover:!bg-purple-700 !border-0 !text-white !rounded-lg" />

          <Button v-if="!latencyTestActive" label="延迟测试" icon="pi pi-clock"
            @click="handleStartLatencyTest"
            :loading="latencyTestLoading"
            :disabled="demoActive"
            class="!bg-indigo-600 hover:!bg-indigo-700 !border-0 !text-white !rounded-lg" />
          <Button v-else label="停止测试" icon="pi pi-stop"
            @click="handleStopLatencyTest" :loading="latencyTestLoading"
            class="!bg-indigo-600 hover:!bg-indigo-700 !border-0 !text-white !rounded-lg" />

          <Button v-if="!inferring" label="开始推理" icon="pi pi-bolt"
            @click="handleStartInference"
            :loading="inferenceLoading"
            :disabled="!demoActive && !latencyTestActive"
            class="!bg-green-600 hover:!bg-green-700 !border-0 !text-white !rounded-lg" />
          <Button v-else label="停止推理" icon="pi pi-stop-circle"
            @click="handleStopInference" :loading="inferenceLoading"
            class="!bg-orange-600 hover:!bg-orange-700 !border-0 !text-white !rounded-lg" />

          <span v-if="demoActive && !inferring" class="text-sm text-purple-600 font-medium flex items-center">
            <i class="pi pi-spin pi-spinner mr-1"></i> 演示视频传输中...
          </span>
          <span v-if="latencyTestActive && !inferring" class="text-sm text-indigo-600 font-medium flex items-center">
            <i class="pi pi-spin pi-spinner mr-1"></i> 延迟测试视频传输中...
          </span>
          <span v-if="inferring" class="text-sm text-green-600 font-medium flex items-center">
            <i class="pi pi-spin pi-spinner mr-1"></i> 推理中...
          </span>
        </div>

        <!-- 画面显示区域 -->
        <div class="w-full rounded-lg overflow-hidden bg-gray-900 relative" style="min-height: 400px;">
          <canvas v-show="hasFrame" ref="videoCanvas" class="w-full h-auto" />
          <div v-if="!hasFrame" class="flex items-center justify-center h-full" style="min-height: 400px;">
            <div class="text-center">
              <i class="pi pi-video text-6xl text-gray-500 mb-4"></i>
              <p class="text-gray-400">{{ connected ? '等待画面...' : '请选择设备并开始查看' }}</p>
            </div>
          </div>

          <!-- 检测信息浮层 -->
          <div v-if="connected && hasFrame && inferring"
            class="absolute top-4 right-4 bg-black/60 text-white px-4 py-3 rounded-lg text-sm space-y-1">
            <div class="font-medium">检测目标: {{ detectTotal }}</div>
            <div v-for="(count, cls) in summary" :key="cls" class="text-gray-300">
              {{ cls }}: {{ count }}
            </div>
          </div>
        </div>

        <!-- 检测详情 -->
        <div v-if="detections.length > 0" class="mt-6 bg-white rounded-2xl shadow-lg p-6">
          <h3 class="text-lg font-bold text-gray-900 mb-4">检测详情</h3>
          <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
            <div v-for="(det, i) in detections" :key="i" class="bg-gray-50 rounded-lg p-3 text-sm">
              <span class="font-medium text-gray-900">{{ det.class || det.label || '目标' }}</span>
              <span v-if="det.confidence" class="text-gray-500 ml-1">
                {{ (det.confidence * 100).toFixed(1) }}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.p-dropdown) {
  background-color: #ffffff !important;
}
</style>