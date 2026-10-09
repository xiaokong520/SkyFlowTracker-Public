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

export const sendChatMessageStream = (message, conversationId, { onChunk, onConversationId, onDone, onError }) => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  const controller = new AbortController()

  fetch('/api/v1/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      'Authorization': token || ''
    },
    body: JSON.stringify({ message, conversationId }),
    signal: controller.signal
  }).then(async response => {
    if (!response.ok) {
      onError?.('请求失败: ' + response.status)
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''
    let dataBuffer = ''
    let hasData = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        onDone?.()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line === '') {
          // 空行 = SSE 事件边界，分发累积的事件
          if (currentEvent === 'conversationId') {
            onConversationId?.(dataBuffer)
          } else if (currentEvent === 'chunk') {
            onChunk?.(dataBuffer)
          } else if (currentEvent === 'done') {
            onDone?.()
          } else if (currentEvent === 'error') {
            onError?.(dataBuffer)
          }
          currentEvent = ''
          dataBuffer = ''
          hasData = false
        } else if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const payload = line.slice(5)
          if (hasData) {
            dataBuffer += '\n' + payload
          } else {
            dataBuffer = payload
            hasData = true
          }
        }
      }
    }
  }).catch(err => {
    if (err.name !== 'AbortError') {
      onError?.(err.message)
    }
  })

  return controller
}

export const generateSummaryStream = (taskId, { onChunk, onDone, onError }) => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  const controller = new AbortController()

  fetch('/api/v1/ai/summary', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      'Authorization': token || ''
    },
    body: JSON.stringify({ taskId }),
    signal: controller.signal
  }).then(async response => {
    if (!response.ok) {
      onError?.('请求失败: ' + response.status)
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let currentEvent = ''
    let dataBuffer = ''
    let hasData = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        onDone?.()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line === '') {
          if (currentEvent === 'chunk') {
            onChunk?.(dataBuffer)
          } else if (currentEvent === 'done') {
            onDone?.()
          } else if (currentEvent === 'error') {
            onError?.(dataBuffer)
          }
          currentEvent = ''
          dataBuffer = ''
          hasData = false
        } else if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          const payload = line.slice(5)
          if (hasData) {
            dataBuffer += '\n' + payload
          } else {
            dataBuffer = payload
            hasData = true
          }
        }
      }
    }
  }).catch(err => {
    if (err.name !== 'AbortError') {
      onError?.(err.message)
    }
  })

  return controller
}

export const getConversations = () =>
  api.get('/ai/conversations')

export const getConversationHistory = (conversationId) =>
  api.get('/ai/conversation/history', { params: { conversationId } })

export const deleteConversation = (conversationId) =>
  api.delete('/ai/conversation', { params: { conversationId } })
