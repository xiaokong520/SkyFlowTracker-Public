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

// 检查权限
onMounted(async () => {
  if (!isAuthenticated()) {
    router.push('/login')
    return
  }
  
  try {
    const res = await checkToken()
    if (res.code === 1) {
      // 检查是否是管理员
      if (res.data.role !== 1) {
        router.push('/user')
      }
    } else {
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
            <span class="ml-4 px-3 py-1 bg-blue-100 text-blue-800 text-sm font-semibold rounded-full">管理员</span>
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
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
          </div>
          <h2 class="text-3xl font-bold text-gray-900 mb-4">欢迎来到管理员控制台</h2>
          <p class="text-lg text-gray-600 mb-8">您拥有系统的完整管理权限</p>
          
          <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mt-12">
            <div class="p-6 bg-blue-50 rounded-xl">
              <i class="pi pi-users text-4xl text-blue-600 mb-4"></i>
              <h3 class="text-xl font-semibold text-gray-900 mb-2">用户管理</h3>
              <p class="text-gray-600">管理系统用户和权限</p>
            </div>
            
            <div class="p-6 bg-indigo-50 rounded-xl">
              <i class="pi pi-chart-line text-4xl text-indigo-600 mb-4"></i>
              <h3 class="text-xl font-semibold text-gray-900 mb-2">数据统计</h3>
              <p class="text-gray-600">查看系统运行数据</p>
            </div>
            
            <div class="p-6 bg-purple-50 rounded-xl">
              <i class="pi pi-cog text-4xl text-purple-600 mb-4"></i>
              <h3 class="text-xl font-semibold text-gray-900 mb-2">系统设置</h3>
              <p class="text-gray-600">配置系统参数</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
