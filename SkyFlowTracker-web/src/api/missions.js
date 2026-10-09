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

// 创建航线任务
export const createMission = (data) => api.post('/missions/create', data)

// 编辑航线任务
export const updateMission = (data) => api.put('/missions/update', data)

// 批量删除航线任务
export const deleteMissions = (ids) => api.delete('/missions/delete', { data: { ids } })

// 获取航线任务列表
export const getMissionsList = (page = 1, pageSize = 10, keyword = '') =>
  api.get('/missions/getMissionsList', { params: { page, pageSize, ...(keyword ? { keyword } : {}) } })

// 获取航线任务详情
export const getMissionDetail = (missionId) =>
  api.get('/missions/getMissionDetail', { params: { missionId } })

// 分派航线任务
export const assignMission = (missionId, assigneeId) =>
  api.put('/missions/assign', { missionId, assigneeId })
