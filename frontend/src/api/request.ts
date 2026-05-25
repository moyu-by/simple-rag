import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器：自动携带 JWT
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 从响应体提取错误消息
function extractMsg(data: any): string | null {
  if (!data) return null
  return data.message || null
}

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const { data } = response
    if (data.code && data.code !== 200) {
      const msg = data.message || '请求失败'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return data
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      const msg = extractMsg(data)

      // 401: 未登录 / token 过期 / 无权限
      if (status === 401) {
        const isOnAuthPage = router.currentRoute.value.meta.noAuth
        if (!isOnAuthPage) {
          localStorage.removeItem('token')
          localStorage.removeItem('userId')
          router.push('/login')
        }
        ElMessage.error(msg || '账号或密码错误')
      }
      // 400: 参数错误 / 业务校验失败
      else if (status === 400) {
        ElMessage.error(msg || '请求参数有误')
      }
      // 409: 数据冲突
      else if (status === 409) {
        ElMessage.error(msg || '数据已存在')
      }
      // 429: 限流
      else if (status === 429) {
        ElMessage.error('请求过于频繁，请稍后再试')
      }
      // 500: 服务器错误
      else if (status === 500) {
        ElMessage.error(msg || '服务器内部错误，请稍后重试')
      }
      // 其他
      else {
        ElMessage.error(msg || `请求错误 (${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络')
    } else {
      ElMessage.error('网络连接失败，请检查后端是否启动')
    }
    return Promise.reject(error)
  },
)

export default request
