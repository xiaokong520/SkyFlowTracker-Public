<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import Toast from 'primevue/toast'
import Dialog from 'primevue/dialog'
import Password from 'primevue/password'
import InputText from 'primevue/inputtext'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import { getUserInfo, updatePassword, updateUserInfo, updateAvatar } from '../api/auth'
import { clearAuth } from '../utils/auth'

const router = useRouter()
const toast = useToast()
const loading = ref(true)
const userInfo = ref({})
const showPasswordDialog = ref(false)
const showNicknameDialog = ref(false)
const passwordForm = ref({
  newPassword: '',
  confirmPassword: ''
})
const nicknameForm = ref({
  nickName: ''
})
const passwordLoading = ref(false)
const nicknameLoading = ref(false)
const passwordErrors = ref({})
const nicknameErrors = ref({})
const avatarLoading = ref(false)
const fileInputRef = ref(null)

// 字段映射（中文显示名称）
const fieldLabels = {
  userId: '用户ID',
  userName: '用户名',
  email: '邮箱',
  role: '角色',
  status: '状态',
  ip: 'IP地址',
  nickName: '昵称',
  createTime: '创建时间',
  creationTime: '注册时间',
  modificationTime: '修改时间',
  lastLoginTime: '最后登录时间'
}

// 需要排除的字段（不在列表中显示）
const excludeFields = ['password', 'avatar', 'userName', 'email', 'role']

// 格式化字段值
const formatValue = (key, value) => {
  if (value === null || value === undefined) {
    return '-'
  }
  
  if (key === 'role') {
    return value === 1 ? '管理员' : '普通用户'
  }
  
  if (key === 'status') {
    if (value === 0) return '封禁'
    if (value === 1) return '正常'
    if (value === 2) return '待审核'
    return '未知'
  }
  
  return value
}

// 判断字段是否可点击编辑
const isFieldEditable = (key) => {
  return key === 'nickName'
}

// 处理字段点击
const handleFieldClick = (field) => {
  if (field.key === 'nickName') {
    openNicknameDialog()
  }
}

// 获取角色标签样式
const getRoleBadgeClass = computed(() => {
  if (!userInfo.value.role) return 'bg-green-100 text-green-800'
  return userInfo.value.role === 1 ? 'bg-blue-100 text-blue-800' : 'bg-green-100 text-green-800'
})

// 获取头像 URL
const avatarUrl = computed(() => {
  if (userInfo.value.avatar) {
    // 添加时间戳防止浏览器缓存
    const timestamp = new Date().getTime()
    // 如果是完整 URL，直接使用
    if (userInfo.value.avatar.startsWith('http')) {
      return `${userInfo.value.avatar}?t=${timestamp}`
    }
    // 如果是相对路径，拼接基础 URL
    return `http://127.0.0.1:8080${userInfo.value.avatar}?t=${timestamp}`
  }
  return null
})

// 过滤要显示的字段（排除密码、头像等特殊字段）
const displayFields = computed(() => {
  return Object.entries(userInfo.value)
    .filter(([key]) => !excludeFields.includes(key))
    .map(([key, value]) => ({
      key,
      label: fieldLabels[key] || key,
      value: formatValue(key, value)
    }))
})

// 获取用户信息
const fetchUserInfo = async () => {
  loading.value = true
  try {
    const res = await getUserInfo()
    if (res.code === 1) {
      userInfo.value = res.data || {}
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '获取用户信息失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取用户信息失败', life: 3000 })
  } finally {
    loading.value = false
  }
}

// 打开修改密码对话框
const openPasswordDialog = () => {
  passwordForm.value = {
    newPassword: '',
    confirmPassword: ''
  }
  passwordErrors.value = {}
  showPasswordDialog.value = true
}

// 验证密码表单
const validatePasswordForm = () => {
  passwordErrors.value = {}
  
  if (!passwordForm.value.newPassword) {
    passwordErrors.value.newPassword = '请输入新密码'
    return false
  }
  
  if (passwordForm.value.newPassword.length < 6) {
    passwordErrors.value.newPassword = '密码长度至少6位'
    return false
  }
  
  if (!passwordForm.value.confirmPassword) {
    passwordErrors.value.confirmPassword = '请确认新密码'
    return false
  }
  
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    passwordErrors.value.confirmPassword = '两次密码输入不一致'
    return false
  }
  
  return true
}

// 提交修改密码
const handleUpdatePassword = async () => {
  if (!validatePasswordForm()) {
    return
  }
  
  passwordLoading.value = true
  try {
    const res = await updatePassword(passwordForm.value.newPassword)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '密码修改成功，请重新登录', life: 3000 })
      showPasswordDialog.value = false
      // 清除登录状态
      clearAuth()
      // 延迟跳转到登录页面
      setTimeout(() => {
        router.push('/login')
      }, 2000)
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '密码修改失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '密码修改失败', life: 3000 })
  } finally {
    passwordLoading.value = false
  }
}

// 打开修改昵称对话框
const openNicknameDialog = () => {
  nicknameForm.value = {
    nickName: userInfo.value.nickName || ''
  }
  nicknameErrors.value = {}
  showNicknameDialog.value = true
}

// 验证昵称表单
const validateNicknameForm = () => {
  nicknameErrors.value = {}
  
  if (!nicknameForm.value.nickName) {
    nicknameErrors.value.nickName = '请输入昵称'
    return false
  }
  
  if (nicknameForm.value.nickName.length > 20) {
    nicknameErrors.value.nickName = '昵称长度不能超过20个字符'
    return false
  }
  
  return true
}

// 提交修改昵称
const handleUpdateNickname = async () => {
  if (!validateNicknameForm()) {
    return
  }
  
  nicknameLoading.value = true
  try {
    const res = await updateUserInfo({ nickName: nicknameForm.value.nickName })
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '昵称修改成功', life: 3000 })
      showNicknameDialog.value = false
      // 重新获取用户信息
      fetchUserInfo()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '昵称修改失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '昵称修改失败', life: 3000 })
  } finally {
    nicknameLoading.value = false
  }
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
    
    const res = await updateAvatar(formData)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '头像修改成功', life: 3000 })
      // 重新获取用户信息
      fetchUserInfo()
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '头像修改失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '头像修改失败', life: 3000 })
  } finally {
    avatarLoading.value = false
    // 清空文件选择
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

onMounted(() => {
  fetchUserInfo()
})
</script>

<template>
  <div class="max-w-4xl">
    <Toast />
    
    <div class="bg-white rounded-2xl shadow-lg p-8">
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-2xl font-bold text-gray-900">个人中心</h2>
        <Button
          label="修改密码"
          icon="pi pi-lock"
          @click="openPasswordDialog"
          class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
        />
      </div>
      
      <div v-if="loading" class="flex justify-center py-12">
        <i class="pi pi-spin pi-spinner text-4xl text-blue-600"></i>
      </div>
      
      <div v-else class="space-y-6">
        <div class="flex items-center gap-6">
          <!-- 头像显示 -->
          <div 
            class="relative w-24 h-24 rounded-full overflow-hidden border-2 border-blue-200 cursor-pointer group"
            @click="triggerFileInput"
            :title="avatarLoading ? '上传中...' : '点击修改头像'"
          >
            <div v-if="avatarUrl" class="w-full h-full">
              <img :src="avatarUrl" alt="用户头像" class="w-full h-full object-cover" />
            </div>
            <div v-else class="w-full h-full bg-blue-100 flex items-center justify-center">
              <i class="pi pi-user text-4xl text-blue-600"></i>
            </div>
            
            <!-- 悬停遮罩 -->
            <div class="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
              <i v-if="avatarLoading" class="pi pi-spin pi-spinner text-2xl text-white"></i>
              <i v-else class="pi pi-camera text-2xl text-white"></i>
            </div>
          </div>
          
          <!-- 隐藏的文件输入框 -->
          <input
            ref="fileInputRef"
            type="file"
            accept="image/jpeg,image/jpg,image/png,image/gif"
            class="hidden"
            @change="handleFileChange"
          />
          
          <div>
            <h3 class="text-xl font-semibold text-gray-900">{{ userInfo.userName || '未设置' }}</h3>
            <p class="text-gray-600">{{ userInfo.email || '未设置' }}</p>
            <span :class="[
              'inline-block mt-2 px-3 py-1 text-sm font-semibold rounded-full',
              getRoleBadgeClass
            ]">
              {{ formatValue('role', userInfo.role) }}
            </span>
          </div>
        </div>
        
        <div class="border-t border-gray-200 pt-6">
          <h4 class="text-lg font-semibold text-gray-900 mb-4">账户信息</h4>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div 
              v-for="field in displayFields" 
              :key="field.key" 
              :class="[
                'p-4 bg-gray-50 rounded-lg',
                isFieldEditable(field.key) ? 'cursor-pointer hover:bg-gray-100 transition-colors' : ''
              ]"
              @click="handleFieldClick(field)"
              :title="isFieldEditable(field.key) ? '点击修改' : ''"
            >
              <p class="text-sm text-gray-600 mb-1">
                {{ field.label }}
                <span v-if="isFieldEditable(field.key)" class="text-xs text-gray-600">（点我可修改昵称）</span>
              </p>
              <p :class="[
                'font-medium break-all text-gray-900'
              ]">
                {{ field.value }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 修改密码对话框 -->
    <Dialog
      v-model:visible="showPasswordDialog"
      modal
      header="修改密码"
      :style="{ width: '450px' }"
    >
      <div class="space-y-4">
        <div>
          <label for="newPassword" class="block text-sm font-medium text-gray-700 mb-2">新密码</label>
          <Password
            id="newPassword"
            v-model="passwordForm.newPassword"
            placeholder="请输入新密码"
            :feedback="false"
            toggleMask
            class="w-full"
            inputClass="w-full"
            :class="{ 'p-invalid': passwordErrors.newPassword }"
          />
          <small v-if="passwordErrors.newPassword" class="p-error">{{ passwordErrors.newPassword }}</small>
        </div>
        
        <div>
          <label for="confirmPassword" class="block text-sm font-medium text-gray-700 mb-2">确认新密码</label>
          <Password
            id="confirmPassword"
            v-model="passwordForm.confirmPassword"
            placeholder="请再次输入新密码"
            :feedback="false"
            toggleMask
            class="w-full"
            inputClass="w-full"
            :class="{ 'p-invalid': passwordErrors.confirmPassword }"
          />
          <small v-if="passwordErrors.confirmPassword" class="p-error">{{ passwordErrors.confirmPassword }}</small>
        </div>
      </div>
      
      <template #footer>
        <Button
          label="取消"
          outlined
          @click="showPasswordDialog = false"
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
        <Button
          label="确认修改"
          @click="handleUpdatePassword"
          :loading="passwordLoading"
          class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
        />
      </template>
    </Dialog>
    
    <!-- 修改昵称对话框 -->
    <Dialog
      v-model:visible="showNicknameDialog"
      modal
      header="修改昵称"
      :style="{ width: '450px' }"
    >
      <div class="space-y-4">
        <div>
          <label for="nickName" class="block text-sm font-medium text-gray-700 mb-2">昵称</label>
          <InputText
            id="nickName"
            v-model="nicknameForm.nickName"
            placeholder="请输入昵称"
            class="w-full"
            :class="{ 'p-invalid': nicknameErrors.nickName }"
          />
          <small v-if="nicknameErrors.nickName" class="p-error">{{ nicknameErrors.nickName }}</small>
        </div>
      </div>
      
      <template #footer>
        <Button
          label="取消"
          outlined
          @click="showNicknameDialog = false"
          class="!border-2 !border-gray-300 !text-gray-700 hover:!bg-gray-50 !rounded-lg"
        />
        <Button
          label="确认修改"
          @click="handleUpdateNickname"
          :loading="nicknameLoading"
          class="!bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-lg"
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

:deep(.p-password input.p-invalid::placeholder) {
  color: #ef4444 !important;
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

:deep(.p-dialog .p-dialog-header .p-dialog-title) {
  color: #1F2937 !important;
}

:deep(.p-dialog label) {
  color: #374151 !important;
}
</style>
