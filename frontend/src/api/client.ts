import axios from 'axios'
import { useAuthStore } from '../store/authStore'
import type { AuthResponse } from '../types'

const baseURL = import.meta.env.VITE_API_URL || ''

const apiClient = axios.create({
  baseURL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshPromise: Promise<string> | null = null

export async function refreshSession(): Promise<string> {
  const response = await axios.post<AuthResponse>(
    '/api/auth/refresh',
    null,
    { baseURL, withCredentials: true }
  )
  const { accessToken, userId, email, role } = response.data
  useAuthStore.getState().setSession(accessToken, { userId, email, role })
  return accessToken
}

function runSingleFlightRefresh(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = refreshSession().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

function redirectToLogin(): void {
  useAuthStore.getState().clearAuth()
  const publicPaths = ['/', '/login', '/apply']
  if (!publicPaths.includes(window.location.pathname)) {
    window.location.href = '/login'
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    const isUnauthorized = error.response?.status === 401
    const isAuthEndpoint = typeof originalRequest?.url === 'string' && originalRequest.url.includes('/api/auth/')
    const canRetry = isUnauthorized && originalRequest && !originalRequest._retry && !isAuthEndpoint

    if (canRetry) {
      originalRequest._retry = true
      try {
        const newAccessToken = await runSingleFlightRefresh()
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return apiClient(originalRequest)
      } catch {
        redirectToLogin()
        return Promise.reject(error)
      }
    }

    if (isUnauthorized) {
      redirectToLogin()
    }
    return Promise.reject(error)
  }
)

export default apiClient
