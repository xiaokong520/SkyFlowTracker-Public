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

// 获取飞行记录列表
export const getFlightsList = (page = 1, pageSize = 10, keyword = '') =>
  api.get('/flights/getFlightsList', { params: { page, pageSize, ...(keyword ? { keyword } : {}) } })

// 获取飞行详情
export const getFlightDetail = (flightId) =>
  api.get('/flights/getFlightDetail', { params: { flightId } })

// 批量删除飞行记录
export const deleteFlights = (ids) =>
  api.delete('/flights/deleteFlights', { data: { ids } })
