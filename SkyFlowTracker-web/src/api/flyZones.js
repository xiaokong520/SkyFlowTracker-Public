import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token')
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  error => Promise.reject(error)
)

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API 错误:', error)
    return Promise.reject(error)
  }
)

// 管理员手动创建禁飞区
export const createFlyZone = (data) => api.post('/flyZones/create', data)

// 编辑禁飞区
export const updateFlyZone = (data) => api.put('/flyZones/update', data)

// 批量删除禁飞区
export const deleteFlyZones = (ids) => api.delete('/flyZones/delete', { data: { ids } })

// 获取禁飞区管理列表（管理员）
export const getFlyZonesList = (page = 1, pageSize = 10, keyword = '', category = null) =>
  api.get('/flyZones/getFlyZonesList', { params: { page, pageSize, ...(keyword ? { keyword } : {}), ...(category !== null ? { category } : {}) } })

// 获取所有启用的禁飞区（不需要token，地图显示用）
export const getAllEnabledFlyZones = () => api.get('/flyZones/getAllEnabled')
