<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { login, getImgCode, checkToken } from '../api/auth'
import { saveToken } from '../utils/auth'

const router = useRouter()
const toast = useToast()

// 登录方式：username 或 email
const loginType = ref('username')
const formData = ref({
  userName: '',
  email: '',
  password: '',
  imgCode: '',
  imgId: ''
})
const rememberMe = ref(false)
const loading = ref(false)

// 图片验证码
const captchaImg = ref('')

// 表单验证错误
const errors = ref({})

// 获取图片验证码
const fetchCaptcha = async () => {
  try {
    const res = await getImgCode()
    if (res.code === 1) {
      captchaImg.value = `data:image/png;base64,${res.data.base64Image}`
      formData.value.imgId = res.data.imgId
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '获取验证码失败', life: 3000 })
  }
}

// 切换登录方式
const switchLoginType = (type) => {
  loginType.value = type
  errors.value = {}
  if (type === 'username') {
    formData.value.email = ''
  } else {
    formData.value.userName = ''
  }
}

// 表单验证
const validateForm = () => {
  errors.value = {}
  
  if (loginType.value === 'username') {
    if (!formData.value.userName) {
      errors.value.userName = '请输入用户名'
    }
  } else {
    if (!formData.value.email) {
      errors.value.email = '请输入邮箱'
    } else {
      const emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      if (!emailReg.test(formData.value.email)) {
        errors.value.email = '邮箱格式不正确'
      }
    }
  }
  
  if (!formData.value.password) {
    errors.value.password = '请输入密码'
  } else if (formData.value.password.length < 8) {
    errors.value.password = '密码长度不能少于8位'
  }
  
  if (!formData.value.imgCode) {
    errors.value.imgCode = '请输入验证码'
  }
  
  return Object.keys(errors.value).length === 0
}

// 提交登录
const handleLogin = async () => {
  if (!validateForm()) return
  
  loading.value = true
  try {
    const loginData = {
      password: formData.value.password,
      imgCode: formData.value.imgCode,
      imgId: formData.value.imgId
    }
    
    if (loginType.value === 'username') {
      loginData.userName = formData.value.userName
    } else {
      loginData.email = formData.value.email
    }
    
    const res = await login(loginData)
    
    if (res.code === 1) {
      // 保存 token（根据"记住我"选择存储方式）
      saveToken(res.data.token, rememberMe.value)
      
      // 调用 checkToken 接口获取用户角色
      const checkRes = await checkToken()
      if (checkRes.code === 1) {
        toast.add({ severity: 'success', summary: '成功', detail: '登录成功，欢迎回来！', life: 2000 })
        
        // 跳转到主页
        setTimeout(() => router.push('/profile'), 2000)
      } else {
        toast.add({ severity: 'error', summary: '错误', detail: '获取用户信息失败', life: 3000 })
      }
    } else {
      // 根据不同的错误信息显示不同的提示
      const errorMsg = res.message || '登录失败'
      
      // 账号待审核 - 使用警告级别
      if (errorMsg.includes('待审核') || errorMsg.includes('审核')) {
        toast.add({ 
          severity: 'warn', 
          summary: '提示', 
          detail: '您的账号正在审核中，请耐心等待管理员审核通过后再登录', 
          life: 5000 
        })
      } 
      // 账号被封禁 - 使用错误级别
      else if (errorMsg.includes('封禁')) {
        toast.add({ 
          severity: 'error', 
          summary: '错误', 
          detail: '您的账号已被封禁，如有疑问请联系管理员', 
          life: 5000 
        })
      } 
      // 其他错误 - 使用错误级别
      else {
        toast.add({ severity: 'error', summary: '错误', detail: errorMsg, life: 3000 })
      }
      
      fetchCaptcha()
    }
  } catch (error) {
    toast.add({ severity: 'error', summary: '错误', detail: '登录失败，请重试', life: 3000 })
    fetchCaptcha()
  } finally {
    loading.value = false
  }
}

// 初始化
onMounted(() => {
  fetchCaptcha()
})
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-50 p-4">
    <Toast />
    
    <!-- 居中容器 -->
    <div class="w-full max-w-6xl flex bg-white rounded-3xl shadow-2xl overflow-hidden">
      <!-- 左侧 - 登录表单 -->
      <div class="w-full lg:w-1/2 flex items-center justify-center p-8">
      <div class="w-full max-w-md">
        <!-- 标题 -->
        <div class="mb-8">
          <h1 class="text-3xl font-bold text-gray-900 mb-2">登录账号</h1>
        </div>

        <!-- 登录方式切换 -->
        <div class="flex gap-2 mb-6">
          <button
            type="button"
            @click="switchLoginType('username')"
            :class="[
              'flex-1 py-2 px-4 rounded-lg font-medium transition-all duration-200 cursor-pointer',
              loginType === 'username' 
                ? 'bg-blue-600 text-white' 
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            ]"
          >
            用户名登录
          </button>
          <button
            type="button"
            @click="switchLoginType('email')"
            :class="[
              'flex-1 py-2 px-4 rounded-lg font-medium transition-all duration-200 cursor-pointer',
              loginType === 'email' 
                ? 'bg-blue-600 text-white' 
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            ]"
          >
            邮箱登录
          </button>
        </div>

        <!-- 登录表单 -->
        <form @submit.prevent="handleLogin" class="space-y-4">
          <!-- 用户名输入 -->
          <div v-if="loginType === 'username'">
            <InputText
              v-model="formData.userName"
              placeholder="请输入用户名"
              class="w-full"
              :class="{ 'p-invalid': errors.userName }"
            />
            <small v-if="errors.userName" class="p-error block mt-1">{{ errors.userName }}</small>
          </div>

          <!-- 邮箱输入 -->
          <div v-if="loginType === 'email'">
            <InputText
              v-model="formData.email"
              type="email"
              placeholder="请输入邮箱地址"
              class="w-full"
              :class="{ 'p-invalid': errors.email }"
            />
            <small v-if="errors.email" class="p-error block mt-1">{{ errors.email }}</small>
          </div>

          <!-- 密码输入 -->
          <div>
            <Password
              v-model="formData.password"
              placeholder="请输入密码"
              :feedback="false"
              toggleMask
              class="w-full"
              inputClass="w-full"
              :class="{ 'p-invalid': errors.password }"
            />
            <small v-if="errors.password" class="p-error block mt-1">{{ errors.password }}</small>
          </div>

          <!-- 图片验证码 -->
          <div>
            <div class="flex gap-3">
              <InputText
                v-model="formData.imgCode"
                placeholder="请输入验证码"
                class="flex-1"
                :class="{ 'p-invalid': errors.imgCode }"
              />
              <img 
                v-if="captchaImg"
                :src="captchaImg"
                @click="fetchCaptcha"
                class="w-32 h-11 cursor-pointer rounded-lg border-2 border-gray-200 hover:border-blue-500 transition-all duration-200 object-cover"
                alt="验证码"
              />
            </div>
            <small v-if="errors.imgCode" class="p-error block mt-1">{{ errors.imgCode }}</small>
          </div>

          <!-- 记住我和忘记密码 -->
          <div class="flex items-center justify-between">
            <label for="remember" class="flex items-center gap-2 cursor-pointer" style="position: relative; z-index: 10;">
              <Checkbox 
                v-model="rememberMe" 
                :binary="true" 
                inputId="remember"
                style="position: relative; z-index: 10;"
              />
              <span class="text-sm text-gray-700">记住我</span>
            </label>
            <button
              type="button"
              @click="router.push('/forgot-password')" 
              class="text-sm text-blue-600 hover:text-blue-700 font-medium cursor-pointer transition-colors"
            >
              忘记密码?
            </button>
          </div>

          <!-- 登录按钮 -->
          <Button
            type="submit"
            label="登录"
            :loading="loading"
            class="w-full !bg-blue-600 hover:!bg-blue-700 !border-0 !text-white !rounded-full !py-3 !font-semibold"
            size="large"
          />

          <!-- 注册链接 -->
          <div class="text-center text-sm text-gray-600 pt-2">
            还没有账号？
            <button
              type="button"
              @click="router.push('/register')" 
              class="text-blue-600 hover:text-blue-700 font-semibold cursor-pointer transition-colors"
            >
              立即注册
            </button>
          </div>
        </form>
        </div>
      </div>

      <!-- 右侧 - 欢迎信息 -->
      <div class="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-blue-500 via-blue-600 to-indigo-600 items-center justify-center p-12 relative overflow-hidden">
      <!-- 装饰圆圈 -->
      <div class="absolute top-20 right-20 w-64 h-64 bg-white/10 rounded-full"></div>
      <div class="absolute bottom-20 left-20 w-96 h-96 bg-white/10 rounded-full"></div>
      
      <div class="relative z-10 text-center text-white">
        <div class="mb-8">
          <svg class="w-32 h-32 mx-auto mb-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
          </svg>
        </div>
        <h2 class="text-4xl font-bold mb-4">欢迎登录SkyFlowTracker - 无人机监测系统!</h2>
        <p class="text-lg text-blue-100 mb-8 max-w-md mx-auto">
          还没有账号？成为我们的会员，让我们一起开启无人机追踪的旅程！
        </p>
        <Button
          label="注册"
          @click="router.push('/register')"
          outlined
          class="!border-2 !border-white !text-white hover:!bg-white hover:!text-blue-600 !rounded-full !px-12 !py-3 !font-semibold !transition-all"
        />
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

:deep(.p-checkbox) {
  cursor: pointer !important;
  pointer-events: auto !important;
}

:deep(.p-checkbox .p-checkbox-box) {
  border-radius: 0.25rem;
  border: 2px solid #d1d5db;
  transition: all 0.2s;
  cursor: pointer !important;
  pointer-events: auto !important;
  width: 1.25rem !important;
  height: 1.25rem !important;
  min-width: 1.25rem !important;
  min-height: 1.25rem !important;
  max-width: 1.25rem !important;
  max-height: 1.25rem !important;
  background-color: #ffffff !important;
  position: relative !important;
  flex-shrink: 0 !important;
}

:deep(.p-checkbox .p-checkbox-box.p-highlight) {
  background: #ffffff !important;
  border-color: #2563eb !important;
  cursor: pointer !important;
  pointer-events: auto !important;
  width: 1.25rem !important;
  height: 1.25rem !important;
  min-width: 1.25rem !important;
  min-height: 1.25rem !important;
  max-width: 1.25rem !important;
  max-height: 1.25rem !important;
  flex-shrink: 0 !important;
}

/* 显示 PrimeVue 原生图标并设置颜色 */
:deep(.p-checkbox .p-checkbox-box .p-checkbox-icon) {
  color: #2563eb !important;
  display: block !important;
  visibility: visible !important;
  opacity: 1 !important;
  font-size: 0.75rem !important;
}

:deep(.p-checkbox input[type="checkbox"]) {
  cursor: pointer !important;
  pointer-events: auto !important;
}
</style>
