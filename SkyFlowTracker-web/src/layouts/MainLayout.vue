<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { clearAuth, isAuthenticated } from '../utils/auth'
import { logout, checkToken } from '../api/auth'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const loading = ref(false)
const userRole = ref(null)
const sidebarCollapsed = ref(false)

// 菜单项配置
const menuItems = computed(() => {
  const items = [
    {
      label: '数据看板',
      icon: 'pi pi-chart-bar',
      route: '/dashboard'
    },
    {
      label: '个人中心',
      icon: 'pi pi-user',
      route: '/profile'
    },
    {
      label: '实时追踪',
      icon: 'pi pi-map-marker',
      route: '/tracking'
    }
  ]
  
  // 只有管理员才显示用户管理
  if (userRole.value === 1) {
    items.splice(1, 0, {
      label: '用户管理',
      icon: 'pi pi-users',
      route: '/user-management'
    })
    items.splice(2, 0, {
      label: '设备管理',
      icon: 'pi pi-box',
      route: '/device-management'
    })
    items.splice(3, 0, {
      label: '飞行记录',
      icon: 'pi pi-send',
      route: '/flight-management'
    })
    items.splice(4, 0, {
      label: '推理任务',
      icon: 'pi pi-microchip',
      route: '/inference-management'
    })
    items.splice(5, 0, {
      label: '航线任务',
      icon: 'pi pi-directions',
      route: '/mission-management'
    })
    items.splice(6, 0, {
      label: 'AI 助手',
      icon: 'pi pi-microchip-ai',
      route: '/ai-chat'
    })
    items.splice(7, 0, {
      label: '视频分享',
      icon: 'pi pi-share-alt',
      route: '/share-management'
    })
  } else {
    items.splice(1, 0, {
      label: '设备管理',
      icon: 'pi pi-box',
      route: '/device-management'
    })
    items.splice(2, 0, {
      label: '飞行记录',
      icon: 'pi pi-send',
      route: '/flight-management'
    })
    items.splice(3, 0, {
      label: '推理任务',
      icon: 'pi pi-microchip',
      route: '/inference-management'
    })
    items.splice(4, 0, {
      label: '航线任务',
      icon: 'pi pi-directions',
      route: '/mission-management'
    })
    items.splice(5, 0, {
      label: 'AI 助手',
      icon: 'pi pi-microchip-ai',
      route: '/ai-chat'
    })
    items.splice(6, 0, {
      label: '视频分享',
      icon: 'pi pi-share-alt',
      route: '/share-management'
    })
  }
  
  return items
})

// 检查登录状态和权限
onMounted(async () => {
  if (!isAuthenticated()) {
    router.push('/login')
    return
  }
  
  try {
    const res = await checkToken()
    if (res.code === 1) {
      userRole.value = res.data.role
    } else {
      clearAuth()
      router.push('/login')
    }
  } catch (error) {
    clearAuth()
    router.push('/login')
  }
})

// 切换侧边栏
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 导航到指定路由
const navigateTo = (routePath) => {
  router.push(routePath)
}

// 判断是否是当前路由
const isActiveRoute = (routePath) => {
  return route.path === routePath
}

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
    clearAuth()
    toast.add({ severity: 'warn', summary: '提示', detail: '已清除本地登录信息', life: 2000 })
    setTimeout(() => router.push('/login'), 2000)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="h-screen bg-gray-50 flex overflow-hidden">
    <Toast />
    
    <!-- 侧边栏 -->
    <aside 
      :class="[
        'bg-white shadow-lg transition-all duration-300 flex flex-col',
        sidebarCollapsed ? 'w-20' : 'w-64'
      ]"
    >
      <!-- Logo 区域 -->
      <div class="h-16 flex items-center justify-between px-4 border-b border-gray-200">
        <div v-if="!sidebarCollapsed" class="flex items-center">
          <h1 class="text-xl font-bold text-blue-600">SkyFlowTracker</h1>
        </div>
        <button
          @click="toggleSidebar"
          class="p-2 rounded-lg hover:bg-gray-100 transition-colors cursor-pointer"
        >
          <i :class="sidebarCollapsed ? 'pi pi-angle-right' : 'pi pi-angle-left'" class="text-gray-600"></i>
        </button>
      </div>
      
      <!-- 菜单列表 -->
      <nav class="flex-1 py-4 overflow-y-auto">
        <div
          v-for="item in menuItems"
          :key="item.route"
          @click="navigateTo(item.route)"
          :class="[
            'flex items-center px-4 py-3 mx-2 rounded-lg cursor-pointer transition-all duration-200',
            isActiveRoute(item.route)
              ? 'bg-blue-50 text-blue-600'
              : 'text-gray-700 hover:bg-gray-100'
          ]"
        >
          <i :class="item.icon" class="text-xl"></i>
          <span v-if="!sidebarCollapsed" class="ml-3 font-medium">{{ item.label }}</span>
        </div>
      </nav>
      
      <!-- 底部用户信息 -->
      <div class="border-t border-gray-200 p-4">
        <div
          v-if="userRole !== null"
          :class="[
            'flex items-center mb-3',
            sidebarCollapsed ? 'justify-center' : ''
          ]"
        >
          <span
            :class="[
              'px-3 py-1 text-xs font-semibold rounded-full',
              userRole === 1 
                ? 'bg-blue-100 text-blue-800' 
                : 'bg-green-100 text-green-800'
            ]"
          >
            {{ sidebarCollapsed ? (userRole === 1 ? '管' : '用') : (userRole === 1 ? '管理员' : '普通用户') }}
          </span>
        </div>
        <Button
          :label="sidebarCollapsed ? '' : '退出登录'"
          :icon="sidebarCollapsed ? 'pi pi-sign-out' : ''"
          @click="handleLogout"
          :loading="loading"
          outlined
          :class="[
            '!border-2 !border-red-500 !text-red-500 hover:!bg-red-50 !rounded-lg',
            sidebarCollapsed ? '!w-full !px-2' : '!w-full'
          ]"
          size="small"
        />
      </div>
    </aside>
    
    <!-- 主内容区域 -->
    <div class="flex-1 flex flex-col">
      <!-- 顶部导航栏 -->
      <header class="h-16 bg-white shadow-sm flex items-center justify-between px-6">
        <div class="flex items-center">
          <h2 class="text-xl font-semibold text-gray-800">{{ route.meta.title || '主页' }}</h2>
        </div>
        <div class="flex items-center gap-4">
          <i class="pi pi-bell text-xl text-gray-600 cursor-pointer hover:text-blue-600 transition-colors"></i>
          <i class="pi pi-cog text-xl text-gray-600 cursor-pointer hover:text-blue-600 transition-colors"></i>
        </div>
      </header>
      
      <!-- 页面内容 -->
      <main class="flex-1 p-6 overflow-y-auto min-h-0 relative">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
/* 自定义滚动条 */
nav::-webkit-scrollbar {
  width: 6px;
}

nav::-webkit-scrollbar-track {
  background: transparent;
}

nav::-webkit-scrollbar-thumb {
  background: #e5e7eb;
  border-radius: 3px;
}

nav::-webkit-scrollbar-thumb:hover {
  background: #d1d5db;
}
</style>
