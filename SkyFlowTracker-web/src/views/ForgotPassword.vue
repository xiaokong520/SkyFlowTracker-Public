<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { forgotPassword, getVerificationCode } from '../api/auth'

const router = useRouter()
const toast = useToast()

const step = ref(1) // 1: 输入邮箱, 2: 重置密码
const formData = ref({
  email: '',
  code: '',
  password: '',
  confirmPassword: ''
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
      step.value = 2
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '发送失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '发送失败，请重试', life: 3000 })
  } finally {
    sendingCode.value = false
  }
}

// 重新发送验证码
const resendCode = async () => {
  sendingCode.value = true
  try {
    const res = await getVerificationCode(formData.value.email)
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '验证码已重新发送', life: 3000 })
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
  
  if (!formData.value.code) {
    errors.value.code = '请输入验证码'
  }
  
  if (!formData.value.password) {
    errors.value.password = '请输入新密码'
  } else if (formData.value.password.length < 8 || formData.value.password.length > 25) {
    errors.value.password = '密码长度为8-25位'
  }
  
  if (!formData.value.confirmPassword) {
    errors.value.confirmPassword = '请确认密码'
  } else if (formData.value.password !== formData.value.confirmPassword) {
    errors.value.confirmPassword = '两次密码不一致'
  }
  
  return Object.keys(errors.value).length === 0
}

// 提交重置密码
const handleReset = async () => {
  if (!validateForm()) return
  
  loading.value = true
  try {
    const resetData = {
      email: formData.value.email,
      code: formData.value.code,
      password: formData.value.password
    }
    
    const res = await forgotPassword(resetData)
    
    if (res.code === 1) {
      toast.add({ severity: 'success', summary: '成功', detail: '密码重置成功，即将跳转登录', life: 2000 })
      setTimeout(() => router.push('/login'), 2000)
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '重置失败', life: 3000 })
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '重置失败，请重试', life: 3000 })
  } finally {
    loading.value = false
  }
}

// 返回上一步
const goBack = () => {
  if (step.value === 2) {
    step.value = 1
    formData.value.code = ''
    formData.value.password = ''
    formData.value.confirmPassword = ''
    errors.value = {}
  } else {
    router.push('/login')
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 p-4">
    <Toast />
    
    <!-- 居中容器 -->
    <div class="w-full max-w-6xl flex bg-white rounded-3xl shadow-2xl overflow-hidden">
      <!-- 左侧 - 重置密码表单 -->
      <div class="w-full lg:w-1/2 flex items-center justify-center p-8">
      <div class="w-full max-w-md">
        <!-- 返回按钮 -->
        <button 
          type="button"
          @click="goBack"
          class="flex items-center gap-2 text-gray-600 hover:text-gray-900 mb-8 transition-colors cursor-pointer"
        >
          <i class="pi pi-arrow-left"></i>
          <span class="font-medium">{{ step === 1 ? '返回登录' : '返回上一步' }}</span>
        </button>

        <!-- 标题 -->
        <div class="mb-8">
          <h1 class="text-3xl font-bold text-gray-900 mb-2">重置密码</h1>
          <p class="text-gray-600">
            {{ step === 1 ? '输入邮箱获取验证码' : '设置新密码' }}
          </p>
        </div>

        <!-- 步骤指示器 -->
        <div class="flex items-center justify-center gap-3 mb-8">
          <div class="flex items-center gap-2">
            <div :class="[
              'w-10 h-10 rounded-full flex items-center justify-center font-semibold transition-all',
              step >= 1 ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-500'
            ]">
              1
            </div>
            <span :class="['text-sm font-medium', step >= 1 ? 'text-gray-900' : 'text-gray-500']">
              验证邮箱
            </span>
          </div>
          <div class="w-16 h-0.5 bg-gray-300"></div>
          <div class="flex items-center gap-2">
            <div :class="[
              'w-10 h-10 rounded-full flex items-center justify-center font-semibold transition-all',
              step >= 2 ? 'bg-blue-600 text-white' : 'bg-gray-200 text-gray-500'
            ]">
              2
            </div>
            <span :class="['text-sm font-medium', step >= 2 ? 'text-gray-900' : 'text-gray-500']">
              重置密码
            </span>
          </div>
        </div>

        <!-- 步骤 1: 输入邮箱 -->
        <form v-if="step === 1" @submit.prevent="sendCode" class="space-y-4">
          <div>
            <InputText
              v-model="formData.email"
              type="email"
              placeholder="请输入注册时的邮箱"
              class="w-full"
              :class="{ 'p-invalid': errors.email }"
            />
            <small v-if="errors.email" class="p-error block mt-1">{{ errors.email }}</small>
            <small class="text-gray-500 block mt-2">我们将向该邮箱发送验证码</small>
          </div>

          <Button
            type="submit"
            label="发送验证码"
            :loading="sendingCode"
            class="w-full !bg-blue-600 hover:!bg-blue-700 !border-0 !rounded-full !py-3 !font-semibold"
            size="large"
          />
        </form>

        <!-- 步骤 2: 重置密码 -->
        <form v-if="step === 2" @submit.prevent="handleReset" class="space-y-4">
          <!-- 邮箱显示 -->
          <div class="bg-blue-50 border border-blue-200 rounded-lg p-3 flex items-center gap-2">
            <i class="pi pi-info-circle text-blue-600"></i>
            <span class="text-sm text-blue-900">
              验证码已发送至：<span class="font-semibold">{{ formData.email }}</span>
            </span>
          </div>

          <!-- 验证码 -->
          <div>
            <div class="flex gap-3">
              <InputText
                v-model="formData.code"
                placeholder="请输入6位验证码"
                class="flex-1"
                maxlength="6"
                :class="{ 'p-invalid': errors.code }"
              />
              <Button
                type="button"
                :label="countdown > 0 ? `${countdown}秒` : '重新发送'"
                :disabled="countdown > 0 || sendingCode"
                :loading="sendingCode"
                @click="resendCode"
                outlined
                class="whitespace-nowrap !border-2 !border-blue-600 !text-blue-600 hover:!bg-blue-50 !rounded-lg"
              />
            </div>
            <small v-if="errors.code" class="p-error block mt-1">{{ errors.code }}</small>
          </div>

          <!-- 新密码 -->
          <div>
            <Password
              v-model="formData.password"
              placeholder="请输入新密码（8-25位）"
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
              placeholder="请再次输入新密码"
              :feedback="false"
              toggleMask
              class="w-full"
              inputClass="w-full"
              :class="{ 'p-invalid': errors.confirmPassword }"
            />
            <small v-if="errors.confirmPassword" class="p-error block mt-1">{{ errors.confirmPassword }}</small>
          </div>

          <!-- 提交按钮 -->
          <Button
            type="submit"
            label="重置密码"
            :loading="loading"
            class="w-full !bg-blue-600 hover:!bg-blue-700 !border-0 !rounded-full !py-3 !font-semibold"
            size="large"
          />
        </form>
        </div>
      </div>

      <!-- 右侧 - 装饰信息 -->
      <div class="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-blue-500 via-blue-600 to-indigo-600 items-center justify-center p-12 relative overflow-hidden">
      <!-- 装饰圆圈 -->
      <div class="absolute top-20 right-20 w-64 h-64 bg-white/10 rounded-full"></div>
      <div class="absolute bottom-20 left-20 w-96 h-96 bg-white/10 rounded-full"></div>
      
      <div class="relative z-10 text-center text-white">
        <div class="mb-8">
          <svg class="w-32 h-32 mx-auto mb-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
          </svg>
        </div>
        <h2 class="text-4xl font-bold mb-4">安全重置</h2>
        <p class="text-lg text-blue-100 mb-8 max-w-md mx-auto">
          忘记密码不用担心，我们会帮助您安全地重置密码，保护您的账户安全。
        </p>
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
