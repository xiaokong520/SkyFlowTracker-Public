<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import Toast from 'primevue/toast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import Checkbox from 'primevue/checkbox'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import { getDevicesList, addDevicesBySn, addDevicesByQrCode, updateDevice, deleteDevice } from '../api/devices'
import { checkToken } from '../api/auth'

const toast = useToast()
const isAdmin = ref(false)
const loading = ref(true)
const devices = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedDevices = ref([])
const searchSn = ref('')
const deleteLoading = ref(false)

// 设备在线状态
const onlineDevices = ref(new Set())
let statusWs = null
let reconnectTimer = null

const connectDeviceStatus = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/device-status`
  statusWs = new WebSocket(wsUrl)

  statusWs.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'init') {
        onlineDevices.value = new Set(msg.onlineDevices || [])
      } else if (msg.type === 'online') {
        onlineDevices.value = new Set([...onlineDevices.value, msg.sn])
      } else if (msg.type === 'offline') {
        const next = new Set(onlineDevices.value)
        next.delete(msg.sn)
        onlineDevices.value = next
      }
    } catch (e) {
      console.error('解析设备状态消息失败', e)
    }
  }

  statusWs.onclose = () => {
    reconnectTimer = setTimeout(connectDeviceStatus, 3000)
  }

  statusWs.onerror = () => {
    statusWs?.close()
  }
}

const isDeviceOnline = (sn) => onlineDevices.value.has(sn)

// 添加设备对话框
const showAddDialog = ref(false)
const addLoading = ref(false)
const addMode = ref('sn') // 'sn' 或 'qrcode'
const addForm = ref({
  snCode: '',
  targetUserId: ''
})
const qrCodeFile = ref(null)
const fileInputRef = ref(null)

// 编辑设备对话框
const showEditDialog = ref(false)
const editLoading = ref(false)
const editForm = ref({
  sn: '',
  model: '',
  flightControllerSerialNumber: ''
})
const editErrors = ref({})

// 每页条数选项
const pageSizeOptions = [
  { label: '10 条/页', value: 10 },
  { label: '20 条/页', value: 20 },
  { label: '50 条/页', value: 50 },
  { label: '100 条/页', value: 100 }
]

// 是否全选
const isAllSelected = computed({
  get: () => devices.value.length > 0 && selectedDevices.value.length === devices.value.length,
  set: (value) => {
    selectedDevices.value = value ? devices.value.map(d => d.sn) : []
  }
})

// 获取设备列表
const fetchDevices = async () => {
  loading.value = true
  try {
    const res = await getDevicesList(currentPage.value, pageSize.value, searchSn.value)
    if (res.code === 1) {
      devices.value = res.data.devicesList || []
      total.value = res.data.pagination?.total || 0
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '获取设备列表失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取设备列表失败', life: 3000 })
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  fetchDevices()
}

// 重置搜索
const handleReset = () => {
  searchSn.value = ''
  currentPage.value = 1
  fetchDevices()
}

// 打开添加对话框
const openAddDialog = () => {
  addMode.value = 'sn'
  addForm.value = { snCode: '', targetUserId: '' }
  qrCodeFile.value = null
  showAddDialog.value = true
}

// 选择二维码文件
const handleQrFileChange = (event) => {
  const file = event.target.files?.[0]
  if (file) {
    qrCodeFile.value = file
  }
}

// 提交添加设备
const handleAdd = async () => {
  addLoading.value = true
  try {
    let res
    if (addMode.value === 'sn') {
      if (!addForm.value.snCode.trim()) {
        toast.add({ severity: 'warn', summary: '提示', detail: '请输入SN码', life: 3000 })
        addLoading.value = false
        return
      }
      res = await addDevicesBySn(addForm.value.snCode.trim(), addForm.value.targetUserId)
    } else {
      if (!qrCodeFile.value) {
        toast.add({ severity: 'warn', summary: '提示', detail: '请选择二维码图片', life: 3000 })
        addLoading.value = false
        return
      }
      res = await addDevicesByQrCode(qrCodeFile.value, addForm.value.targetUserId)
    }
    if (res.code === 1) {
      const data = res.data || {}
      const errorMsg = data.errorMsg || ''
      if (errorMsg) {
        toast.add({ severity: 'warn', summary: '部分成功', detail: `成功${data.successCount}个，失败${data.failCount}个：${errorMsg}`, life: 6000 })
      } else {
        toast.add({ severity: 'success', summary: '成功', detail: `成功添加${data.successCount || ''}个设备`, life: 3000 })
      }
      showAddDialog.value = false
      fetchDevices()
    } else {
      const data = res.data || {}
      const errorMsg = data.errorMsg || ''
      toast.add({ severity: 'error', summary: '错误', detail: errorMsg || res.message || '添加失败', life: 6000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '添加失败', life: 3000 })
  } finally {
    addLoading.value = false
  }
}

// 打开编辑对话框
const openEditDialog = (device) => {
  editForm.value = {
    sn: device.sn,
    model: device.model || '',
    flightControllerSerialNumber: device.flightControllerSerialNumber || ''
  }
  editErrors.value = {}
  showEditDialog.value = true
}

// 提交编辑
const handleEdit = async () => {
  editLoading.value = true
  try {
    const res = await updateDevice(editForm.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '修改成功', life: 3000 })
      showEditDialog.value = false
      fetchDevices()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '修改失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '修改失败', life: 3000 })
  } finally {
    editLoading.value = false
  }
}

// 删除单个设备
const handleDelete = async (device) => {
  if (!confirm(`确定要删除设备 "${device.sn}" 吗？`)) return
  deleteLoading.value = true
  try {
    // 管理员删除他人设备时传 userId，普通用户不传
    const res = await deleteDevice([device.sn], isAdmin.value ? device.userId : '')
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      fetchDevices()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedDevices.value.length === 0) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请先选择要删除的设备', life: 3000 })
    return
  }
  if (!confirm(`确定要删除选中的 ${selectedDevices.value.length} 个设备吗？`)) return
  deleteLoading.value = true
  try {
    // 按 userId 分组，管理员可能选中不同用户的设备
    const groupedByUser = {}
    for (const sn of selectedDevices.value) {
      const device = devices.value.find(d => d.sn === sn)
      if (device) {
        const uid = device.userId || ''
        if (!groupedByUser[uid]) groupedByUser[uid] = []
        groupedByUser[uid].push(sn)
      }
    }
    let allSuccess = true
    for (const [userId, sns] of Object.entries(groupedByUser)) {
      // 管理员删除时传 userId，普通用户不传
      const res = await deleteDevice(sns, isAdmin.value ? userId : '')
      if (res.code !== 1) allSuccess = false
    }
    if (allSuccess) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
    } else {
      toast.add({ severity: 'warn', summary: '提示', detail: '部分设备删除失败', life: 3000 })
    }
    selectedDevices.value = []
    fetchDevices()
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

// 改变每页条数
const handlePageSizeChange = () => {
  currentPage.value = 1
  fetchDevices()
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  return time
}

// 格式化航程距离（米 -> 可读格式）
const formatDistance = (meters) => {
  if (!meters || meters <= 0) return '0m'
  if (meters >= 1000) {
    return (meters / 1000).toFixed(2) + 'km'
  }
  return meters + 'm'
}

// 格式化飞行时长（秒 -> 可读格式）
const formatDuration = (seconds) => {
  if (!seconds || seconds <= 0) return '0秒'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  let result = ''
  if (h > 0) result += h + '时'
  if (m > 0) result += m + '分'
  result += s + '秒'
  return result
}

onMounted(async () => {
  try {
    const res = await checkToken()
    if (res.code === 1) {
      isAdmin.value = res.data.role === 1
    }
  } catch (error) {}
  fetchDevices()
  connectDeviceStatus()
})

onUnmounted(() => {
  clearTimeout(reconnectTimer)
  if (statusWs) {
    statusWs.onclose = null // 阻止重连
    statusWs.close()
  }
})
</script>

<template>
  <div>
    <Toast />
    
    <div class="bg-white rounded-2xl shadow-lg p-8">
      <!-- 顶部操作栏 -->
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-2xl font-bold text-gray-900">设备管理</h2>
        <div class="flex gap-3">
          <Button
            label="添加设备"
            icon="pi pi-plus"
            @click="openAddDialog"
            class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
          />
          <Button
            label="批量删除"
            icon="pi pi-trash"
            severity="danger"
            @click="handleBatchDelete"
            :disabled="selectedDevices.length === 0 || deleteLoading"
            :loading="deleteLoading"
            class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg"
          />
        </div>
      </div>
      
      <!-- 搜索栏 -->
      <div class="flex gap-3 mb-6">
        <InputText
          v-model="searchSn"
          placeholder="输入SN码搜索"
          class="w-72"
          @keyup.enter="handleSearch"
        />
        <Button
          label="搜索"
          icon="pi pi-search"
          @click="handleSearch"
          class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
        />
        <Button
          label="重置"
          icon="pi pi-refresh"
          @click="handleReset"
          outlined
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
      </div>
      
      <!-- 加载状态 -->
      <div v-if="loading" class="flex justify-center py-12">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
      </div>
      
      <!-- 空状态 -->
      <div v-else-if="devices.length === 0" class="text-center py-12">
        <i class="pi pi-box text-6xl text-gray-400 mb-4"></i>
        <p class="text-gray-600">暂无设备数据</p>
      </div>
      
      <!-- 设备表格 -->
      <div v-else>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="border-b border-gray-200">
                <th class="text-left py-3 px-4 w-12">
                  <Checkbox v-model="isAllSelected" :binary="true" />
                </th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700 w-16">序号</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">SN码</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">所属用户</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">型号</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">飞控SN</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">累计航程</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">飞行时间</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">最后在线</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">创建时间</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">状态</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(device, index) in devices" :key="device.sn" class="border-b border-gray-100 hover:bg-gray-50">
                <td class="py-3 px-4">
                  <Checkbox v-model="selectedDevices" :value="device.sn" :binary="false" />
                </td>
                <td class="py-3 px-4 text-sm text-gray-900">{{ (currentPage - 1) * pageSize + index + 1 }}</td>
                <td class="py-3 px-4 text-sm text-gray-900 font-mono">{{ device.sn }}</td>
                <td class="py-3 px-4 text-sm text-gray-900">{{ device.userName || '-' }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ device.model || '-' }}</td>
                <td class="py-3 px-4 text-sm text-gray-600 font-mono">{{ device.flightControllerSerialNumber || '-' }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ formatDistance(device.accumulatedVoyage) }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ formatDuration(device.flyTime) }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ formatTime(device.lastOnlineTime) }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ formatTime(device.createTime) }}</td>
                <td class="py-3 px-4">
                  <span class="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full"
                        :class="isDeviceOnline(device.sn)
                          ? 'bg-green-100 text-green-700'
                          : 'bg-gray-100 text-gray-500'">
                    <span class="w-2 h-2 rounded-full"
                          :class="isDeviceOnline(device.sn) ? 'bg-green-500' : 'bg-gray-400'"></span>
                    {{ isDeviceOnline(device.sn) ? '在线' : '离线' }}
                  </span>
                </td>
                <td class="py-3 px-4">
                  <button @click="openEditDialog(device)" class="text-blue-600 hover:text-blue-700 mr-3 cursor-pointer" title="编辑">
                    <i class="pi pi-pencil"></i>
                  </button>
                  <button @click="handleDelete(device)" class="text-red-600 hover:text-red-700 cursor-pointer" title="删除">
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
              @click="currentPage--, fetchDevices()"
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
              @click="currentPage++, fetchDevices()"
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
    
    <!-- 添加设备对话框 -->
    <Dialog
      v-model:visible="showAddDialog"
      modal
      header="添加设备"
      :style="{ width: '500px' }"
    >
      <div class="space-y-4">
        <!-- 添加方式切换 -->
        <div class="flex gap-3 mb-4">
          <Button
            label="手动输入SN码"
            :class="addMode === 'sn' 
              ? '!bg-blue-600 !border-0 !text-white !rounded-lg' 
              : '!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg'"
            @click="addMode = 'sn'"
            size="small"
          />
          <Button
            label="上传二维码"
            :class="addMode === 'qrcode' 
              ? '!bg-blue-600 !border-0 !text-white !rounded-lg' 
              : '!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg'"
            @click="addMode = 'qrcode'"
            size="small"
          />
        </div>
        
        <!-- SN码输入 -->
        <div v-if="addMode === 'sn'">
          <label class="block text-sm font-medium text-gray-700 mb-2">SN码</label>
          <Textarea
            v-model="addForm.snCode"
            placeholder="输入SN码，多个SN码用逗号或换行分隔"
            rows="4"
            class="w-full"
          />
          <small class="text-gray-500">支持批量添加，多个SN码用逗号或换行分隔</small>
        </div>
        
        <!-- 二维码上传 -->
        <div v-else>
          <label class="block text-sm font-medium text-gray-700 mb-2">二维码图片</label>
          <div class="border-2 border-dashed border-gray-300 rounded-lg p-6 text-center">
            <input
              ref="fileInputRef"
              type="file"
              accept="image/*"
              class="hidden"
              @change="handleQrFileChange"
            />
            <div v-if="qrCodeFile" class="text-sm text-gray-700">
              <i class="pi pi-image text-2xl text-blue-600 mb-2"></i>
              <p>{{ qrCodeFile.name }}</p>
            </div>
            <div v-else>
              <i class="pi pi-upload text-2xl text-gray-400 mb-2"></i>
              <p class="text-sm text-gray-500">点击选择二维码图片</p>
            </div>
            <Button
              :label="qrCodeFile ? '重新选择' : '选择文件'"
              @click="fileInputRef?.click()"
              outlined
              size="small"
              class="mt-3 !border-2 !border-blue-600 !text-blue-600 hover:!bg-blue-50 !rounded-lg"
            />
          </div>
        </div>
        
        <!-- 目标用户ID（仅管理员可见） -->
        <div v-if="isAdmin">
          <label class="block text-sm font-medium text-gray-700 mb-2">
            目标用户ID <span class="text-gray-500 text-xs">(管理员为他人添加时填写，留空则添加到自己账户)</span>
          </label>
          <InputText
            v-model="addForm.targetUserId"
            placeholder="留空则添加到当前账户"
            class="w-full"
          />
        </div>
      </div>
      
      <template #footer>
        <Button
          label="取消"
          outlined
          @click="showAddDialog = false"
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
        <Button
          label="确认添加"
          @click="handleAdd"
          :loading="addLoading"
          class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
        />
      </template>
    </Dialog>
    
    <!-- 编辑设备对话框 -->
    <Dialog
      v-model:visible="showEditDialog"
      modal
      header="编辑设备"
      :style="{ width: '500px' }"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">SN码</label>
          <InputText
            v-model="editForm.sn"
            disabled
            class="w-full !bg-gray-100"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">型号</label>
          <InputText
            v-model="editForm.model"
            placeholder="请输入设备型号"
            class="w-full"
          />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">飞控序列号</label>
          <InputText
            v-model="editForm.flightControllerSerialNumber"
            placeholder="请输入飞控序列号"
            class="w-full"
          />
        </div>
      </div>
      
      <template #footer>
        <Button
          label="取消"
          outlined
          @click="showEditDialog = false"
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
        <Button
          label="确认修改"
          @click="handleEdit"
          :loading="editLoading"
          class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
/* 修复对话框背景色 */
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

:deep(.p-dialog label) {
  color: #374151 !important;
}

/* 复选框样式 */
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

/* 下拉框样式 */
:deep(.p-dropdown) {
  background-color: #ffffff !important;
}

/* Textarea 样式 */
:deep(.p-textarea) {
  background-color: #ffffff !important;
}
</style>