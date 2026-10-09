import axios from 'axios'

// 创建 axios 实例
const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

// 请求拦截器
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

// 响应拦截器
api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API 错误:', error)
    return Promise.reject(error)
  }
)

// 获取设备列表
export const getDevicesList = (page = 1, size = 10, snCode = '', userId = '') =>
  api.get('/devices/getDevicesList', {
    params: { page, size, ...(snCode ? { snCode } : {}), ...(userId ? { userId } : {}) }
  })

// 添加设备（手动输入SN码）
export const addDevicesBySn = (snCode, targetUserId = '') => {
  const formData = new FormData()
  formData.append('snCode', snCode)
  if (targetUserId) {
    formData.append('targetUserId', targetUserId)
  }
  return api.post('/devices/addDevices', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 添加设备（上传二维码）
export const addDevicesByQrCode = (file, targetUserId = '') => {
  const formData = new FormData()
  formData.append('file', file)
  if (targetUserId) {
    formData.append('targetUserId', targetUserId)
  }
  return api.post('/devices/addDevices', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 修改设备信息
export const updateDevice = (data) => 
  api.put('/devices/updateDevice', data)

// 删除设备
export const deleteDevice = (sns, userId = '') => 
  api.delete('/devices/deleteDevice', {
    data: { sns, ...(userId ? { userId } : {}) }
  })
