import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
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

export const createShare = (taskId, expireHours) =>
  api.post('/share/create', { taskId, expireHours })

export const getShareInfo = (shareCode) =>
  axios.get(`/api/v1/share/info/${shareCode}`).then(res => res.data)

export const getMyShares = (page = 1, pageSize = 10) =>
  api.get('/share/myShares', { params: { page, pageSize } })

export const deleteShare = (shareId) =>
  api.delete('/share/delete', { params: { shareId } })
