import axios from 'axios'
import { useAuthStore } from '../store/authStore'
import type { LoginResponse } from '../types'

const baseURL = import.meta.env.VITE_API_URL || ''

const apiClient = axios.create({
  baseURL,
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

async function refreshAccessToken(): Promise<string> {
  const refreshToken = useAuthStore.getState().refreshToken
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }
  const response = await axios.post<LoginResponse>(
    '/api/auth/refresh',
    { refreshToken },
    { baseURL, headers: { 'Content-Type': 'application/json' } }
  )
  const { accessToken, refreshToken: rotatedToken } = response.data
  useAuthStore.getState().setTokens(accessToken, rotatedToken)
  return accessToken
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
    const canRetry = isUnauthorized && originalRequest && !originalRequest._retry
    const hasRefreshToken = useAuthStore.getState().refreshToken !== null

    if (canRetry && hasRefreshToken) {
      originalRequest._retry = true
      try {
        if (!refreshPromise) {
          refreshPromise = refreshAccessToken().finally(() => {
            refreshPromise = null
          })
        }
        const newAccessToken = await refreshPromise
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
