import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from './types'
import { useUserStore } from '@/stores/user'

/**
 * axios 封装。
 *
 * <p>三件事必须在这里统一做掉，否则每个页面都要重复一遍：</p>
 * <ol>
 *   <li>自动带 token；</li>
 *   <li>HTTP 200 但业务 code 非 200 时<b>也要当成失败抛出去</b>
 *       （后端是「HTTP 一律 200 + code 区分业务结果」，不拆一层会让 Promise 永远 resolve）；</li>
 *   <li>40101 统一跳登录，40301/40302 统一提示。</li>
 * </ol>
 */

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20_000,
})

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

/** 鉴权失败时只弹一次提示，避免同一屏刷出五条一样的错误 */
let lastAuthMessageAt = 0
function notifyOnce(message: string) {
  const now = Date.now()
  if (now - lastAuthMessageAt > 1500) {
    lastAuthMessageAt = now
    ElMessage.error(message)
  }
}

request.interceptors.response.use(
  (response) => {
    const body = response.data as Result<unknown>
    // 网关/健康检查等非标准响应直接透传
    if (body && typeof body.code === 'number') {
      if (body.code === 200) {
        return response
      }
      handleBusinessError(body.code, body.msg)
      // 抛出去，让调用方的 .catch / try-catch 能感知
      return Promise.reject(new Error(body.msg || `业务失败(${body.code})`))
    }
    return response
  },
  (error: AxiosError<Result<unknown>>) => {
    const status = error.response?.status
    if (status === 401) {
      forceLogout('登录状态已失效，请重新登录')
    } else if (status === 403) {
      notifyOnce('没有访问该资源的权限')
    } else if (status && status >= 500) {
      notifyOnce('服务暂时不可用，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      notifyOnce('请求超时，请检查网络')
    } else {
      notifyOnce(error.message || '请求失败')
    }
    return Promise.reject(error)
  },
)

function handleBusinessError(code: number, msg: string) {
  // 40101 令牌失效
  if (code === 40101) {
    forceLogout(msg || '登录状态已失效，请重新登录')
    return
  }
  if (code === 40301 || code === 40302) {
    notifyOnce(msg || '无操作权限')
    return
  }
  notifyOnce(msg || `操作失败(${code})`)
}

function forceLogout(message: string) {
  const store = useUserStore()
  store.logout()
  notifyOnce(message)
  // 不能用 router.push：request 层不持有 router，且这里可能发生在路由守卫之外
  if (!location.hash.startsWith('#/login')) {
    location.hash = '#/login'
  }
}

/**
 * 业务成功时返回 data，失败直接 reject。
 *
 * <p>{@code config} 留给少数需要额外 axios 配置的接口（例如 POST 但参数走
 * query string 的报表预览）。绝大多数调用不需要它。</p>
 */
export async function get<T>(url: string, params?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.get<Result<T>>(url, { params, ...config })
  return res.data.data
}

export async function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.post<Result<T>>(url, data, config)
  return res.data.data
}

export async function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.put<Result<T>>(url, data, config)
  return res.data.data
}

export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.delete<Result<T>>(url, config)
  return res.data.data
}

export default request
