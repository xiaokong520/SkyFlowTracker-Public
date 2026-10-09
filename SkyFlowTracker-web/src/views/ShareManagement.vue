<script setup>
import { ref, onMounted } from 'vue'
import Toast from 'primevue/toast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import { useToast } from 'primevue/usetoast'
import { getMyShares, deleteShare } from '../api/share'
import { checkToken } from '../api/auth'

const toast = useToast()
const isAdmin = ref(false)
const loading = ref(true)
const shares = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const pageSizeOptions = [
  { label: '10 条/页', value: 10 },
  { label: '20 条/页', value: 20 },
  { label: '50 条/页', value: 50 }
]

// 删除确认对话框
const showDeleteDialog = ref(false)
const deleteTarget = ref(null)
const deleteLoading = ref(false)

const fetchShares = async () => {
  loading.value = true
  try {
    const res = await getMyShares(currentPage.value, pageSize.value)
    if (res.code === 1) {
      shares.value = res.data.sharesList || []
      total.value = res.data.pagination?.total || 0
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '获取分享列表失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取分享列表失败', life: 3000 })
  } finally {
    loading.value = false
  }
}

const getShareLink = (shareCode) => {
  return `${window.location.origin}/share/${shareCode}`
}

const copyLink = async (shareCode) => {
  try {
    const link = getShareLink(shareCode)
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(link)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = link
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    toast.add({ severity: 'success', summary: '成功', detail: '链接已复制到剪贴板', life: 2000 })
  } catch {
    toast.add({ severity: 'error', summary: '错误', detail: '复制失败，请手动复制', life: 3000 })
  }
}

const confirmDelete = (share) => {
  deleteTarget.value = share
  showDeleteDialog.value = true
}

const handleDelete = async () => {
  if (!deleteTarget.value) return
  deleteLoading.value = true
  try {
    const res = await deleteShare(deleteTarget.value.id)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      showDeleteDialog.value = false
      fetchShares()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

const handlePageSizeChange = () => {
  currentPage.value = 1
  fetchShares()
}

const formatTime = (time) => {
  if (!time) return '-'
  if (typeof time === 'string') return time.replace('T', ' ')
  return time
}

onMounted(async () => {
  try {
    const res = await checkToken()
    if (res.code === 1) {
      isAdmin.value = res.data.role === 1
    }
  } catch (error) {}
  fetchShares()
})
</script>

<template>
  <div>
    <Toast />

    <div class="bg-white rounded-2xl shadow-lg p-8">
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-2xl font-bold text-gray-900">视频分享管理</h2>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="flex justify-center py-12">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
      </div>

      <!-- 空状态 -->
      <div v-else-if="shares.length === 0" class="text-center py-12">
        <i class="pi pi-share-alt text-6xl text-gray-400 mb-4"></i>
        <p class="text-gray-600">暂无分享记录</p>
        <p class="text-sm text-gray-400 mt-2">在推理任务详情中可以生成分享链接</p>
      </div>

      <!-- 分享列表表格 -->
      <div v-else>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="border-b border-gray-200">
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700 w-16">序号</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">分享码</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">任务ID</th>
                <th v-if="isAdmin" class="text-left py-3 px-4 text-sm font-semibold text-gray-700">创建者</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">模型</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">检测总数</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">访问次数</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">状态</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">创建时间</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">过期时间</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(share, index) in shares" :key="share.id" class="border-b border-gray-100 hover:bg-gray-50">
                <td class="py-3 px-4 text-sm text-gray-900">{{ (currentPage - 1) * pageSize + index + 1 }}</td>
                <td class="py-3 px-4 text-sm text-gray-900 font-mono">{{ share.shareCode }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ share.taskId }}</td>
                <td v-if="isAdmin" class="py-3 px-4 text-sm text-gray-600">{{ share.username || share.userId }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ share.modelName || '-' }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ share.totalDetections ?? '-' }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ share.viewCount }}</td>
                <td class="py-3 px-4">
                  <span :class="[
                    'px-2 py-1 text-xs font-semibold rounded-full',
                    share.isActive === 1 ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                  ]">
                    {{ share.isActive === 1 ? '有效' : '已过期' }}
                  </span>
                </td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ formatTime(share.createTime) }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ formatTime(share.expireTime) }}</td>
                <td class="py-3 px-4">
                  <button
                    v-if="share.isActive === 1"
                    @click="copyLink(share.shareCode)"
                    class="text-blue-600 hover:text-blue-700 mr-3 cursor-pointer"
                    title="复制链接"
                  >
                    <i class="pi pi-copy"></i>
                  </button>
                  <button
                    @click="confirmDelete(share)"
                    class="text-red-600 hover:text-red-700 cursor-pointer"
                    title="删除"
                  >
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
              @click="currentPage--, fetchShares()"
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
              @click="currentPage++, fetchShares()"
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

    <!-- 删除确认对话框 -->
    <Dialog v-model:visible="showDeleteDialog" modal header="确认删除" :style="{ width: '400px' }">
      <div class="flex items-start gap-3">
        <i class="pi pi-exclamation-triangle text-2xl text-amber-500 mt-0.5"></i>
        <div>
          <p class="text-gray-800">确定要删除这条分享记录吗？</p>
          <p class="text-sm text-gray-500 mt-1">删除后分享链接将立即失效，外部访问者将无法查看。</p>
        </div>
      </div>
      <template #footer>
        <Button
          label="取消"
          @click="showDeleteDialog = false"
          outlined
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
        <Button
          label="确认删除"
          icon="pi pi-trash"
          @click="handleDelete"
          :loading="deleteLoading"
          class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg"
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
:deep(.p-dropdown) {
  background-color: #ffffff !important;
}
</style>
