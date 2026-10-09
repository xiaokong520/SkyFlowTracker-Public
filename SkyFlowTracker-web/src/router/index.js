import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import ForgotPassword from '../views/ForgotPassword.vue'
import MainLayout from '../layouts/MainLayout.vue'
import Profile from '../views/Profile.vue'
import UserManagement from '../views/UserManagement.vue'
import Tracking from '../views/Tracking.vue'
import DeviceManagement from '../views/DeviceManagement.vue'
import FlightManagement from '../views/FlightManagement.vue'
import InferenceManagement from '../views/InferenceManagement.vue'
import Dashboard from '../views/Dashboard.vue'
import AiChat from '../views/AiChat.vue'
import ShareView from '../views/ShareView.vue'
import ShareManagement from '../views/ShareManagement.vue'
import MissionManagement from '../views/MissionManagement.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/register',
    name: 'Register',
    component: Register
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: ForgotPassword
  },
  {
    path: '/share/:code',
    name: 'ShareView',
    component: ShareView,
    meta: { title: '视频分享', public: true }
  },
  {
    path: '/',
    component: MainLayout,
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: Dashboard,
        meta: { title: '数据看板' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: Profile,
        meta: { title: '个人中心' }
      },
      {
        path: 'user-management',
        name: 'UserManagement',
        component: UserManagement,
        meta: { title: '用户管理' }
      },
      {
        path: 'device-management',
        name: 'DeviceManagement',
        component: DeviceManagement,
        meta: { title: '设备管理' }
      },
      {
        path: 'flight-management',
        name: 'FlightManagement',
        component: FlightManagement,
        meta: { title: '飞行记录' }
      },
      {
        path: 'inference-management',
        name: 'InferenceManagement',
        component: InferenceManagement,
        meta: { title: '推理任务' }
      },
      {
        path: 'tracking',
        name: 'Tracking',
        component: Tracking,
        meta: { title: '追踪' }
      },
      {
        path: 'ai-chat',
        name: 'AiChat',
        component: AiChat,
        meta: { title: 'AI 助手' }
      },
      {
        path: 'share-management',
        name: 'ShareManagement',
        component: ShareManagement,
        meta: { title: '视频分享' }
      },
      {
        path: 'mission-management',
        name: 'MissionManagement',
        component: MissionManagement,
        meta: { title: '航线任务' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
