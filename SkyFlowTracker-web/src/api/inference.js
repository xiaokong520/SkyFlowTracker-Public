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

export const getInferenceTasksList = (page = 1, pageSize = 10) =>
  api.get('/inference/getInferenceTasksList', { params: { page, pageSize } })

export const getInferenceTasksDetail = (taskId) =>
  api.get('/inference/getInferenceTasksDetail', { params: { taskId } })

export const deleteInferenceTasks = (ids) =>
  api.delete('/inference/deleteInferenceTasks', { data: { ids } })

export const startInference = (sn, modelName = 'yolov12m', flightId = null) =>
  api.post('/inference/start', { sn, modelName, ...(flightId ? { flightId } : {}) })

export const stopInference = (taskId) =>
  api.put('/inference/stop', { taskId })

export const startOfflineInference = (formData) =>
  api.post('/inference/startOffline', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })

export const startDemo = (sn) =>
  api.post('/inference/startDemo', { sn })

export const stopDemo = (sn) =>
  api.put('/inference/stopDemo', { sn })

export const startLatencyTest = (sn) =>
  api.post('/inference/startLatencyTest', { sn })

export const stopLatencyTest = (sn) =>
  api.put('/inference/stopLatencyTest', { sn })
