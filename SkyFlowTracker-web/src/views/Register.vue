<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { register, getVerificationCode } from '../api/auth'

const router = useRouter()
const toast = useToast()

const formData = ref({
  userName: '',
  nickName: '',
  email: '',
  password: '',
  confirmPassword: '',
  code: ''
})

const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const errors = ref({})

// 发送验证码
const sendCode = async () => {
  if (!formData.value.email) {
    errors.value.email = '请输入邮箱'
    return
  }
  
  const emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailReg.test(formData.value.email)) {
    errors.value.email = '邮箱格式不正确'
    return
  }
  
  errors.value.email = ''
  sendingCode.value = true
  
  try {
    const res = await getVerificationCode(formData.value.email)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '验证码已发送至邮箱', life: 3000 })
      countdown.value = 60
      const timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0) clearInterval(timer)
      }, 1000)
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '发送失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '发送失败，请重试', life: 3000 })
  } finally {
    sendingCode.value = false
  }
}

// 表单验证
const validateForm = () => {
  errors.value = {}
  
  if (!formData.value.userName) {
    errors.value.userName = '请输入用户名'
  } else if (formData.value.userName.length < 3) {
    errors.value.userName = '用户名长度不能少于3位'
  }
  
  if (!formData.value.nickName) {
    errors.value.nickName = '请输入昵称'
  }
  
  if (!formData.value.email) {
    errors.value.email = '请输入邮箱'
  } else {
    const emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailReg.test(formData.value.email)) {
      errors.value.email = '邮箱格式不正确'
    }
  }
  
  if (!formData.value.password) {
    errors.value.password = '请输入密码'
  } else if (formData.value.password.length < 8 || formData.value.password.length > 25) {
    errors.value.password = '密码长度为8-25位'
  }
  
  if (!formData.value.confirmPassword) {
    errors.value.confirmPassword = '请确认密码'
  } else if (formData.value.password !== formData.value.confirmPassword) {
    errors.value.confirmPassword = '两次密码不一致'
  }
  
  if (!formData.value.code) {
    errors.value.code = '请输入验证码'
  }
  
  return Object.keys(errors.value).length === 0
}

// 提交注册
const handleRegister = async () => {
  if (!validateForm()) return
  
  loading.value = true
  try {
    const registerData = {
      userName: formData.value.userName,
      nickName: formData.value.nickName,
      email: formData.value.email,
      password: formData.value.password,
      code: formData.value.code
    }
    
    const res = await register(registerData)
    
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '注册成功，请等待管理员审核后登录', life: 5000 })
      setTimeout(() => router.push('/login'), 3000)
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '注册失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '注册失败，请重试', life: 3000 })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 p-4">
    <Toast />
    
    <!-- 居中容器 -->
    <div class="w-full max-w-6xl flex bg-white rounded-3xl shadow-2xl overflow-hidden">
      <!-- 左侧 - 欢迎信息 -->
      <div class="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-blue-500 via-blue-600 to-indigo-600 items-center justify-center p-12 relative overflow-hidden">
      <!-- 装饰圆圈 -->
      <div class="absolute top-20 left-20 w-64 h-64 bg-white/10 rounded-full"></div>
      <div class="absolute bottom-20 right-20 w-96 h-96 bg-white/10 rounded-full"></div>
      
      <div class="relative z-10 text-center text-white">
        <div class="mb-8">
          <svg class="w-32 h-32 mx-auto mb-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
          </svg>
        </div>
        <h2 class="text-4xl font-bold mb-4">欢迎回来!</h2>
        <p class="text-lg text-blue-100 mb-8 max-w-md mx-auto">
          已经有账号了？登录以继续使用无人机监测系统的所有功能！
        </p>
        <Button
          label="登录"
          @click="router.push('/login')"
          outlined
          class="!border-2 !border-white !text-white hover:!bg-white hover:!text-blue-600 !rounded-full !px-12 !py-3 !font-semibold !transition-all"
        />
        </div>
      </div>

      <!-- 右侧 - 注册表单 -->
      <div class="w-full lg:w-1/2 flex items-center justify-center p-8">
      <div class="w-full max-w-md">
        <!-- 标题 -->
        <div class="mb-8">
          <h1 class="text-3xl font-bold text-gray-900 mb-2">创建账号</h1>
          <p class="text-gray-600">加入 SkyFlowTracker 无人机监测系统</p>
        </div>

        <!-- 注册表单 -->
        <form @submit.prevent="handleRegister" class="space-y-4">
          <!-- 用户名 -->
          <div>
            <InputText
              v-model="formData.userName"
              placeholder="请输入用户名（至少3位）"
              class="w-full"
              :class="{ 'p-invalid': errors.userName }"
            />
            <small v-if="errors.userName" class="p-error block mt-1">{{ errors.userName }}</small>
          </div>

          <!-- 昵称 -->
          <div>
            <InputText
              v-model="formData.nickName"
              placeholder="请输入昵称"
              class="w-full"
              :class="{ 'p-invalid': errors.nickName }"
            />
            <small v-if="errors.nickName" class="p-error block mt-1">{{ errors.nickName }}</small>
          </div>

          <!-- 邮箱 -->
          <div>
            <InputText
              v-model="formData.email"
              type="email"
              placeholder="请输入邮箱地址"
              class="w-full"
              :class="{ 'p-invalid': errors.email }"
            />
            <small v-if="errors.email" class="p-error block mt-1">{{ errors.email }}</small>
          </div>

          <!-- 密码 -->
          <div>
            <Password
              v-model="formData.password"
              placeholder="请输入密码（8-25位）"
              toggleMask
              :feedback="false"
              class="w-full"
              inputClass="w-full"
              :class="{ 'p-invalid': errors.password }"
            />
            <small v-if="errors.password" class="p-error block mt-1">{{ errors.password }}</small>
          </div>

          <!-- 确认密码 -->
          <div>
            <Password
              v-model="formData.confirmPassword"
              placeholder="请输入确认密码"
              :feedback="false"
              toggleMask
              class="w-full"
              inputClass="w-full"
              :class="{ 'p-invalid': errors.confirmPassword }"
            />
            <small v-if="errors.confirmPassword" class="p-error block mt-1">{{ errors.confirmPassword }}</small>
          </div>

          <!-- 邮箱验证码 -->
          <div>
            <div class="flex gap-3">
              <InputText
                v-model="formData.code"
                placeholder="请输入邮箱验证码"
                class="flex-1"
                :class="{ 'p-invalid': errors.code }"
              />
              <Button
                type="button"
                :label="countdown > 0 ? `${countdown}秒` : '发送'"
                :disabled="countdown > 0 || sendingCode"
                :loading="sendingCode"
                @click="sendCode"
                outlined
                class="whitespace-nowrap !border-2 !border-blue-600 !text-blue-600 hover:!bg-blue-50 !rounded-lg"
              />
            </div>
            <small v-if="errors.code" class="p-error block mt-1">{{ errors.code }}</small>
          </div>

          <!-- 注册按钮 -->
          <Button
            type="submit"
            label="注册"
            :loading="loading"
            class="w-full !bg-gradient-to-r !from-blue-600 !to-indigo-600 hover:!from-blue-700 hover:!to-indigo-700 !border-0 !text-white !rounded-full !py-3 !font-semibold"
            size="large"
          />

          <!-- 登录链接 -->
          <div class="text-center text-sm text-gray-600 pt-2">
            已有账号？
            <button
              type="button"
              @click="router.push('/login')" 
              class="text-blue-600 hover:text-blue-700 font-semibold cursor-pointer transition-colors"
            >
              立即登录
            </button>
          </div>
        </form>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.p-password) {
  width: 100%;
}

:deep(.p-password-input) {
  width: 100%;
}

:deep(.p-inputtext) {
  border-radius: 0.5rem;
  padding: 0.75rem 1rem;
  border: 2px solid #e5e7eb;
  transition: all 0.2s;
}

:deep(.p-inputtext:focus) {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}
</style>
