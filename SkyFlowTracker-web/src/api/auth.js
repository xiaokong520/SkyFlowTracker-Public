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
    // 优先从 localStorage 获取，其次从 sessionStorage 获取
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

// 获取图片验证码
export const getImgCode = () => api.get('/users/getImgCode')

// 获取邮箱验证码
export const getVerificationCode = (email) => 
  api.post('/users/getVerificationCode', { email })

// 用户登录
export const login = (loginData) => 
  api.post('/users/login', loginData)

// 用户注册
export const register = (registerData) => 
  api.post('/users/register', registerData)

// 忘记密码
export const forgotPassword = (data) => 
  api.put('/users/forgotPassword', data)

// 退出登录
export const logout = () => 
  api.post('/users/logout')

// 验证 Token 并获取用户信息
export const checkToken = () => 
  api.post('/users/checkToken')

// 获取当前用户信息
export const getUserInfo = () => 
  api.get('/users/getUserInfo')

// 获取用户列表
export const getUsersList = (page = 1, pageSize = 10) => 
  api.get('/users/getUsersInfoList', {
    params: { page, pageSize }
  })

// 修改密码
export const updatePassword = (password) => 
  api.put('/users/updatePassword', { password })

// 管理员修改用户密码
export const adminUpdatePassword = (userId, password) => 
  api.put('/users/updatePassword', { userId, password })

// 修改用户信息
export const updateUserInfo = (data) => 
  api.put('/users/updateUserInfo', data)
// 修改头像
export const updateAvatar = (formData) => 
  api.put('/users/updateAvatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })

// 批量删除用户
export const deleteUsers = (userIds) => 
  api.delete('/users/deleteUsers', {
    data: { userIds }
  })

// 管理员修改用户头像
export const adminUpdateAvatar = (userId, formData) => {
  formData.append('userId', userId)
  return api.put('/users/updateAvatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 管理员修改用户信息（密码、角色、状态等）
export const adminUpdateUser = (data) => 
  api.put('/users/updateUserInfo', data)
