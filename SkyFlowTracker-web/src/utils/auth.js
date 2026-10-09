// 认证工具函数

/**
 * 保存 token
 * @param {string} token - JWT token
 * @param {boolean} remember - 是否记住我
 */
export const saveToken = (token, remember = false) => {
  if (remember) {
    // 记住我：保存到 localStorage（持久化）
    localStorage.setItem('token', token)
    localStorage.setItem('rememberMe', 'true')
  } else {
    // 不记住：保存到 sessionStorage（关闭浏览器后清除）
    sessionStorage.setItem('token', token)
    localStorage.removeItem('rememberMe')
  }
}

/**
 * 获取 token
 * @returns {string|null} token
 */
export const getToken = () => {
  // 优先从 localStorage 获取（记住我的情况）
  const localToken = localStorage.getItem('token')
  if (localToken) {
    return localToken
  }
  // 其次从 sessionStorage 获取（未记住的情况）
  return sessionStorage.getItem('token')
}

/**
 * 清除认证信息
 */
export const clearAuth = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('rememberMe')
  sessionStorage.removeItem('token')
}

/**
 * 检查是否已登录
 * @returns {boolean}
 */
export const isAuthenticated = () => {
  return !!getToken()
}
