<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { clearAuth, isAuthenticated } from '../utils/auth'
import { logout, checkToken } from '../api/auth'

const router = useRouter()
const toast = useToast()
const loading = ref(false)

// 检查登录状态
onMounted(async () => {
  if (!isAuthenticated()) {
    router.push('/login')
    return
  }
  
  try {
    const res = await checkToken()
    if (res.code !== 1) {
      // Token 无效，跳转到登录页
      clearAuth()
      router.push('/login')
    }
  } catch (error) {
    clearAuth()
    router.push('/login')
  }
})

// 退出登录
const handleLogout = async () => {
  loading.value = true
  try {
    const res = await logout()
    if (res.code === 1) {
      clearAuth()
      toast.add({ severity: 'success', summary: '成功', detail: '退出登录成功', life: 2000 })
      setTimeout(() => router.push('/login'), 2000)
    } else {
      toast.add({ severity: 'error', summary: '错误', detail: res.message || '退出登录失败', life: 3000 })
    }
  } catch (error) {
    // 即使接口失败也清除本地认证信息
    clearAuth()
    toast.add({ severity: 'warn', summary: '提示', detail: '已清除本地登录信息', life: 2000 })
    setTimeout(() => router.push('/login'), 2000)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <Toast />
    
    <!-- 顶部导航栏 -->
    <nav class="bg-white shadow-sm">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <div class="flex items-center">
            <h1 class="text-2xl font-bold text-blue-600">SkyFlowTracker</h1>
            <span class="ml-4 px-3 py-1 bg-green-100 text-green-800 text-sm font-semibold rounded-full">普通用户</span>
          </div>
          <Button
            label="退出登录"
            @click="handleLogout"
            :loading="loading"
            outlined
            class="!border-2 !border-red-500 !text-red-500 hover:!bg-red-50 !rounded-lg"
          />
        </div>
      </div>
    </nav>

    <!-- 主要内容 -->
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="bg-white rounded-2xl shadow-lg p-8">
        <div class="text-center">
          <div class="mb-6">
            <svg class="w-24 h-24 mx-auto text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
            </svg>
          </div>
          <h2 class="text-3xl font-bold text-gray-900 mb-4">欢迎来到无人机监测系统</h2>
          <p class="text-lg text-gray-600 mb-8">开始您的无人机追踪之旅</p>
          
          <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-12">
            <div class="p-6 bg-blue-50 rounded-xl">
              <i class="pi pi-map-marker text-4xl text-blue-600 mb-4"></i>
              <h3 class="text-xl font-semibold text-gray-900 mb-2">实时追踪</h3>
              <p class="text-gray-600">查看无人机实时位置</p>
            </div>
            
            <div class="p-6 bg-indigo-50 rounded-xl">
              <i class="pi pi-chart-bar text-4xl text-indigo-600 mb-4"></i>
              <h3 class="text-xl font-semibold text-gray-900 mb-2">飞行记录</h3>
              <p class="text-gray-600">查看历史飞行数据</p>
            </div>
            
            <div class="p-6 bg-purple-50 rounded-xl">
              <i class="pi pi-bell text-4xl text-purple-600 mb-4"></i>
              <h3 class="text-xl font-semibold text-gray-900 mb-2">告警通知</h3>
              <p class="text-gray-600">接收重要提醒</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
