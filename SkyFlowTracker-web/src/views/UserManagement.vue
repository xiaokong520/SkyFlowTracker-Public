<script setup>
import { ref, onMounted, computed } from 'vue'
import Toast from 'primevue/toast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Dropdown from 'primevue/dropdown'
import Checkbox from 'primevue/checkbox'
import { useToast } from 'primevue/usetoast'
import { getUsersList, adminUpdateUser, adminUpdateAvatar, deleteUsers, adminUpdatePassword } from '../api/auth'

const toast = useToast()
const loading = ref(true)
const users = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedUsers = ref([])

// 对话框状态
const showEditDialog = ref(false)
const showAvatarDialog = ref(false)
const editLoading = ref(false)
const avatarLoading = ref(false)
const deleteLoading = ref(false)

// 编辑表单
const editForm = ref({
  userId: '',
  userName: '',
  nickName: '',
  password: '',
  role: null,
  status: null
})
const editErrors = ref({})

// 头像上传
const currentEditUser = ref(null)
const fileInputRef = ref(null)

// 每页条数选项
const pageSizeOptions = [
  { label: '10 条/页', value: 10 },
  { label: '20 条/页', value: 20 },
  { label: '50 条/页', value: 50 },
  { label: '100 条/页', value: 100 }
]

// 角色选项
const roleOptions = [
  { label: '普通用户', value: 0 },
  { label: '管理员', value: 1 }
]

// 状态选项
const statusOptions = [
  { label: '待审核', value: 2 },
  { label: '正常', value: 1 },
  { label: '封禁', value: 0 }
]

// 是否全选（改为可写的计算属性）
const isAllSelected = computed({
  get: () => users.value.length > 0 && selectedUsers.value.length === users.value.length,
  set: (value) => {
    if (value) {
      selectedUsers.value = users.value.map(u => u.userId)
    } else {
      selectedUsers.value = []
    }
  }
})

// 获取用户列表
const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await getUsersList(currentPage.value, pageSize.value)
    if (res.code === 1) {
      // 数据结构：data.userInfoList 和 data.pagination
      users.value = res.data.userInfoList || []
      total.value = res.data.pagination?.total || 0
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '获取用户列表失败', life: 3000 })
    }
  } catch (error) {
    console.error('获取用户列表失败:', error)
    toast.add({ severity: 'error', summary: '错误', detail: '获取用户列表失败', life: 3000 })
  } finally {
    loading.value = false
  }
}

// 格式化角色
const formatRole = (role) => {
  return role === 1 ? '管理员' : '普通用户'
}

// 格式化状态
const formatStatus = (status) => {
  if (status === 0) return '封禁'
  if (status === 1) return '正常'
  if (status === 2) return '待审核'
  return '未知'
}

// 获取头像 URL
const getAvatarUrl = (avatar) => {
  if (!avatar) return null
  const timestamp = new Date().getTime()
  if (avatar.startsWith('http')) {
    return `${avatar}?t=${timestamp}`
  }
  return `http://127.0.0.1:8080${avatar}?t=${timestamp}`
}

// 切换全选（不需要了，使用可写计算属性）

// 切换单个选择（不需要了，直接用 v-model）

// 打开编辑对话框
const openEditDialog = (user) => {
  editForm.value = {
    userId: user.userId,
    userName: user.userName,
    nickName: user.nickName || '',
    password: '',
    role: user.role,
    status: user.status
  }
  editErrors.value = {}
  showEditDialog.value = true
}

// 验证编辑表单
const validateEditForm = () => {
  editErrors.value = {}
  
  if (!editForm.value.nickName || editForm.value.nickName.trim() === '') {
    editErrors.value.nickName = '昵称不能为空'
    return false
  }
  
  if (editForm.value.nickName.length > 20) {
    editErrors.value.nickName = '昵称长度不能超过20个字符'
    return false
  }
  
  if (editForm.value.password && editForm.value.password.length < 6) {
    editErrors.value.password = '密码长度至少6位'
    return false
  }
  
  if (editForm.value.role === null) {
    editErrors.value.role = '请选择角色'
    return false
  }
  
  if (editForm.value.status === null) {
    editErrors.value.status = '请选择状态'
    return false
  }
  
  return true
}

// 提交编辑
const handleEdit = async () => {
  if (!validateEditForm()) {
    return
  }
  
  editLoading.value = true
  try {
    // 先更新昵称、角色和状态
    const updateData = {
      userId: editForm.value.userId,
      nickName: editForm.value.nickName,
      role: editForm.value.role,
      status: editForm.value.status
    }
    
    const res = await adminUpdateUser(updateData)
    if (res.code !== 1) {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '修改失败', life: 3000 })
      editLoading.value = false
      return
    }
    
    // 如果填写了密码，单独调用修改密码接口
    if (editForm.value.password) {
      const passwordRes = await adminUpdatePassword(editForm.value.userId, editForm.value.password)
      if (passwordRes.code !== 1) {
        toast.add({ severity: 'error', summary: '错误', detail: passwordRes.message || '密码修改失败', life: 3000 })
        editLoading.value = false
        return
      }
    }
    
    toast.add({ severity: 'success', summary: '成功', detail: '用户信息修改成功', life: 3000 })
    showEditDialog.value = false
    fetchUsers()
    // 在对话框关闭后清空密码字段
    setTimeout(() => {
      editForm.value.password = ''
    }, 100)
  } catch (error) {
    console.error('修改失败:', error)
    toast.add({ severity: 'error', summary: '错误', detail: '修改失败', life: 3000 })
  } finally {
    editLoading.value = false
  }
}

// 打开头像上传对话框
const openAvatarDialog = (user) => {
  currentEditUser.value = user
  showAvatarDialog.value = true
}

// 触发文件选择
const triggerFileInput = () => {
  fileInputRef.value?.click()
}

// 处理文件选择
const handleFileChange = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  
  // 验证文件类型
  const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif']
  if (!allowedTypes.includes(file.type)) {
    toast.add({ severity: 'error', summary: '错误', detail: '只支持 JPG、PNG、GIF 格式的图片', life: 3000 })
    return
  }
  
  // 验证文件大小（2MB）
  const maxSize = 2 * 1024 * 1024
  if (file.size > maxSize) {
    toast.add({ severity: 'error', summary: '错误', detail: '图片大小不能超过 2MB', life: 3000 })
    return
  }
  
  // 上传头像
  avatarLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('userId', currentEditUser.value.userId)
    
    const res = await adminUpdateAvatar(currentEditUser.value.userId, formData)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '头像修改成功', life: 3000 })
      showAvatarDialog.value = false
      fetchUsers()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '头像修改失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '头像修改失败', life: 3000 })
  } finally {
    avatarLoading.value = false
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedUsers.value.length === 0) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请先选择要删除的用户', life: 3000 })
    return
  }
  
  if (!confirm(`确定要删除选中的 ${selectedUsers.value.length} 个用户吗？此操作不可恢复！`)) {
    return
  }
  
  deleteLoading.value = true
  try {
    const res = await deleteUsers(selectedUsers.value)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      selectedUsers.value = []
      fetchUsers()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

// 删除单个用户
const handleDelete = async (user) => {
  if (!confirm(`确定要删除用户 "${user.userName}" 吗？此操作不可恢复！`)) {
    return
  }
  
  deleteLoading.value = true
  try {
    const res = await deleteUsers([user.userId])
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '删除成功', life: 3000 })
      fetchUsers()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '删除失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '删除失败', life: 3000 })
  } finally {
    deleteLoading.value = false
  }
}

// 改变每页条数
const handlePageSizeChange = () => {
  currentPage.value = 1
  fetchUsers()
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div>
    <Toast />
    
    <div class="bg-white rounded-2xl shadow-lg p-8">
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-2xl font-bold text-gray-900">用户管理</h2>
        <div class="flex gap-3">
          <Button
            label="批量删除"
            icon="pi pi-trash"
            severity="danger"
            @click="handleBatchDelete"
            :disabled="selectedUsers.length === 0 || deleteLoading"
            :loading="deleteLoading"
            class="!bg-red-600 hover:!bg-red-700 !border-0 !text-white !rounded-lg"
          />
        </div>
      </div>
      
      <div v-if="loading" class="flex justify-center py-12">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
      </div>
      
      <div v-else-if="users.length === 0" class="text-center py-12">
        <i class="pi pi-inbox text-6xl text-gray-400 mb-4"></i>
        <p class="text-gray-600">暂无用户数据</p>
      </div>
      
      <div v-else>
        <div class="overflow-x-auto">
          <table class="w-full">
            <thead>
              <tr class="border-b border-gray-200">
                <th class="text-left py-3 px-4 w-12">
                  <Checkbox 
                    v-model="isAllSelected" 
                    :binary="true"
                  />
                </th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700 w-16">序号</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">头像</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">用户名</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">昵称</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">邮箱</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">角色</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">状态</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">注册时间</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">最后登录时间</th>
                <th class="text-left py-3 px-4 text-sm font-semibold text-gray-700">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(user, index) in users" :key="user.userId" class="border-b border-gray-100 hover:bg-gray-50">
                <td class="py-3 px-4">
                  <Checkbox 
                    v-model="selectedUsers" 
                    :value="user.userId"
                    :binary="false"
                  />
                </td>
                <td class="py-3 px-4 text-sm text-gray-900">{{ (currentPage - 1) * pageSize + index + 1 }}</td>
                <td class="py-3 px-4">
                  <div 
                    class="w-10 h-10 rounded-full overflow-hidden border-2 border-gray-200 cursor-pointer hover:border-blue-400 transition-colors"
                    @click="openAvatarDialog(user)"
                    title="点击修改头像"
                  >
                    <img 
                      v-if="getAvatarUrl(user.avatar)" 
                      :src="getAvatarUrl(user.avatar)" 
                      alt="头像" 
                      class="w-full h-full object-cover"
                    />
                    <div v-else class="w-full h-full bg-blue-100 flex items-center justify-center">
                      <i class="pi pi-user text-blue-600"></i>
                    </div>
                  </div>
                </td>
                <td class="py-3 px-4 text-sm text-gray-900">{{ user.userName }}</td>
                <td class="py-3 px-4 text-sm text-gray-900">{{ user.nickName || '-' }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ user.email }}</td>
                <td class="py-3 px-4">
                  <span :class="[
                    'px-2 py-1 text-xs font-semibold rounded-full',
                    user.role === 1 ? 'bg-blue-100 text-blue-800' : 'bg-green-100 text-green-800'
                  ]">
                    {{ formatRole(user.role) }}
                  </span>
                </td>
                <td class="py-3 px-4">
                  <span :class="[
                    'px-2 py-1 text-xs font-semibold rounded-full',
                    user.status === 1 ? 'bg-green-100 text-green-800' : 
                    user.status === 0 ? 'bg-red-100 text-red-800' : 
                    'bg-yellow-100 text-yellow-800'
                  ]">
                    {{ formatStatus(user.status) }}
                  </span>
                </td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ user.createTime || '-' }}</td>
                <td class="py-3 px-4 text-sm text-gray-600">{{ user.lastLoginTime || '-' }}</td>
                <td class="py-3 px-4">
                  <button 
                    @click="openEditDialog(user)"
                    class="text-blue-600 hover:text-blue-700 mr-3 cursor-pointer"
                    title="编辑"
                  >
                    <i class="pi pi-pencil"></i>
                  </button>
                  <button 
                    @click="handleDelete(user)"
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
              @click="currentPage--, fetchUsers()"
              :disabled="currentPage === 1"
              class="w-10 h-10 flex items-center justify-center rounded-lg border-2 transition-colors"
              :class="currentPage === 1 
                ? 'border-gray-300 text-gray-400 cursor-not-allowed' 
                : 'border-blue-600 text-blue-600 hover:bg-blue-50 cursor-pointer'"
            >
              <i class="pi pi-chevron-left"></i>
            </button>
            <span class="text-sm text-gray-700">
              第 {{ currentPage }} 页 / 共 {{ Math.ceil(total / pageSize) }} 页
            </span>
            <button
              @click="currentPage++, fetchUsers()"
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
    
    <!-- 编辑用户对话框 -->
    <Dialog
      v-model:visible="showEditDialog"
      modal
      header="编辑用户"
      :style="{ width: '500px' }"
    >
      <div class="space-y-4">
        <div>
          <label for="nickName" class="block text-sm font-medium text-gray-700 mb-2">昵称</label>
          <InputText
            id="nickName"
            v-model="editForm.nickName"
            placeholder="请输入昵称"
            class="w-full"
            :class="{ 'p-invalid': editErrors.nickName }"
          />
          <small v-if="editErrors.nickName" class="p-error">{{ editErrors.nickName }}</small>
        </div>
        
        <div>
          <label for="password" class="block text-sm font-medium text-gray-700 mb-2">
            新密码 <span class="text-gray-500 text-xs">(留空则不修改)</span>
          </label>
          <Password
            id="password"
            v-model="editForm.password"
            placeholder="请输入新密码"
            :feedback="false"
            toggleMask
            class="w-full"
            inputClass="w-full"
            :class="{ 'p-invalid': editErrors.password }"
          />
          <small v-if="editErrors.password" class="p-error">{{ editErrors.password }}</small>
        </div>
        
        <div>
          <label for="role" class="block text-sm font-medium text-gray-700 mb-2">角色</label>
          <Dropdown
            id="role"
            v-model="editForm.role"
            :options="roleOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="请选择角色"
            class="w-full"
            :class="{ 'p-invalid': editErrors.role }"
          />
          <small v-if="editErrors.role" class="p-error">{{ editErrors.role }}</small>
        </div>
        
        <div>
          <label for="status" class="block text-sm font-medium text-gray-700 mb-2">状态</label>
          <Dropdown
            id="status"
            v-model="editForm.status"
            :options="statusOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="请选择状态"
            class="w-full"
            :class="{ 'p-invalid': editErrors.status }"
          />
          <small v-if="editErrors.status" class="p-error">{{ editErrors.status }}</small>
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
    
    <!-- 修改头像对话框 -->
    <Dialog
      v-model:visible="showAvatarDialog"
      modal
      header="修改用户头像"
      :style="{ width: '400px' }"
    >
      <div class="text-center py-6">
        <div 
          class="w-32 h-32 mx-auto rounded-full overflow-hidden border-4 border-blue-200 cursor-pointer hover:border-blue-400 transition-colors mb-4"
          @click="triggerFileInput"
        >
          <img 
            v-if="currentEditUser && getAvatarUrl(currentEditUser.avatar)" 
            :src="getAvatarUrl(currentEditUser.avatar)" 
            alt="头像" 
            class="w-full h-full object-cover"
          />
          <div v-else class="w-full h-full bg-blue-100 flex items-center justify-center">
            <i class="pi pi-user text-5xl text-blue-600"></i>
          </div>
        </div>
        
        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/jpg,image/png,image/gif"
          class="hidden"
          @change="handleFileChange"
        />
        
        <Button
          label="选择图片"
          icon="pi pi-upload"
          @click="triggerFileInput"
          :loading="avatarLoading"
          class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
        />
        
        <p class="text-sm text-gray-500 mt-3">支持 JPG、PNG、GIF 格式，大小不超过 2MB</p>
      </div>
      
      <template #footer>
        <Button
          label="关闭"
          outlined
          @click="showAvatarDialog = false"
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
/* 修复密码输入框背景色 */
:deep(.p-password input) {
  background-color: #ffffff !important;
}

:deep(.p-password input.p-invalid) {
  border-color: #ef4444 !important;
}

/* 修复下拉框样式 */
:deep(.p-dropdown) {
  background-color: #ffffff !important;
}

:deep(.p-dropdown.p-invalid) {
  border-color: #ef4444 !important;
}

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
  pointer-events: auto;
}

:deep(.p-checkbox .p-checkbox-box.p-highlight) {
  background-color: #ffffff;
  border-color: #2563eb;
}

:deep(.p-checkbox .p-checkbox-box .p-checkbox-icon) {
  color: #2563eb;
  font-size: 0.875rem;
}
</style>
