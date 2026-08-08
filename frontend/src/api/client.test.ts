import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const requestUse = vi.fn()
const responseUse = vi.fn()
const apiClientFn = Object.assign(vi.fn(), {
  interceptors: {
    request: { use: requestUse },
    response: { use: responseUse },
  },
})
const postMock = vi.fn()
const axiosCreateMock = vi.fn(() => apiClientFn)

vi.mock('axios', () => ({
  default: {
    create: axiosCreateMock,
    post: postMock,
  },
}))

const authState = {
  accessToken: null as string | null,
  setSession: vi.fn(),
  clearAuth: vi.fn(),
}

vi.mock('../store/authStore', () => ({
  useAuthStore: {
    getState: () => authState,
  },
}))

function setLocation(pathname: string): void {
  Object.defineProperty(window, 'location', {
    value: { pathname, href: '' },
    writable: true,
    configurable: true,
  })
}

interface RetryableRequestConfig {
  url?: string
  headers: Record<string, string>
  _retry?: boolean
}

interface InterceptedError {
  response?: { status: number }
  config: RetryableRequestConfig
}

describe('apiClient', () => {
  let requestInterceptor: (config: RetryableRequestConfig) => RetryableRequestConfig
  let responseSuccess: (response: unknown) => unknown
  let responseError: (error: InterceptedError) => Promise<unknown>
  let refreshSession: () => Promise<string>

  beforeEach(async () => {
    vi.resetModules()
    requestUse.mockClear()
    responseUse.mockClear()
    apiClientFn.mockClear()
    axiosCreateMock.mockClear()
    postMock.mockReset()
    authState.accessToken = null
    authState.setSession.mockClear()
    authState.clearAuth.mockClear()
    setLocation('/dashboard')

    const clientModule = await import('./client')
    refreshSession = clientModule.refreshSession
    requestInterceptor = requestUse.mock.calls[0][0]
    responseSuccess = responseUse.mock.calls[0][0]
    responseError = responseUse.mock.calls[0][1]
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('creates an axios instance with credentials and timeout', () => {
    expect(axiosCreateMock).toHaveBeenCalledWith(
      expect.objectContaining({ withCredentials: true, timeout: 30000 })
    )
  })

  it('attaches the Authorization header when a token is present', () => {
    authState.accessToken = 'token-123'
    const config = requestInterceptor({ headers: {} })
    expect(config.headers.Authorization).toBe('Bearer token-123')
  })

  it('leaves the Authorization header untouched when there is no token', () => {
    authState.accessToken = null
    const config = requestInterceptor({ headers: {} })
    expect(config.headers.Authorization).toBeUndefined()
  })

  it('passes through successful responses unchanged', () => {
    const response = { status: 200, data: {} }
    expect(responseSuccess(response)).toBe(response)
  })

  describe('refreshSession', () => {
    it('stores the refreshed session and returns the new access token', async () => {
      postMock.mockResolvedValue({
        data: { accessToken: 'new-token', userId: 'u1', email: 'a@b.com', role: 'LAWYER' },
      })

      const token = await refreshSession()

      expect(token).toBe('new-token')
      expect(postMock).toHaveBeenCalledWith(
        '/api/auth/refresh',
        null,
        expect.objectContaining({ withCredentials: true })
      )
      expect(authState.setSession).toHaveBeenCalledWith('new-token', {
        userId: 'u1',
        email: 'a@b.com',
        role: 'LAWYER',
      })
    })
  })

  describe('response error interceptor', () => {
    it('retries the original request with a refreshed token on 401', async () => {
      postMock.mockResolvedValue({
        data: { accessToken: 'refreshed-token', userId: 'u1', email: 'a@b.com', role: 'LAWYER' },
      })
      apiClientFn.mockResolvedValue({ status: 200, data: 'retried' })

      const originalRequest: RetryableRequestConfig = { url: '/api/cases', headers: {}, _retry: undefined }
      const error = { response: { status: 401 }, config: originalRequest }

      const result = await responseError(error)

      expect(originalRequest._retry).toBe(true)
      expect(originalRequest.headers.Authorization).toBe('Bearer refreshed-token')
      expect(apiClientFn).toHaveBeenCalledWith(originalRequest)
      expect(result).toEqual({ status: 200, data: 'retried' })
    })

    it('does not retry auth endpoints and redirects to login', async () => {
      const originalRequest: RetryableRequestConfig = { url: '/api/auth/login', headers: {} }
      const error = { response: { status: 401 }, config: originalRequest }

      await expect(responseError(error)).rejects.toBe(error)

      expect(authState.clearAuth).toHaveBeenCalled()
      expect(postMock).not.toHaveBeenCalled()
      expect(window.location.href).toBe('/login')
    })

    it('does not redirect when already on a public path', async () => {
      setLocation('/login')
      const originalRequest: RetryableRequestConfig = { url: '/api/auth/login', headers: {} }
      const error = { response: { status: 401 }, config: originalRequest }

      await expect(responseError(error)).rejects.toBe(error)

      expect(window.location.href).toBe('')
    })

    it('does not retry a request that already retried once', async () => {
      const originalRequest: RetryableRequestConfig = { url: '/api/cases', headers: {}, _retry: true }
      const error = { response: { status: 401 }, config: originalRequest }

      await expect(responseError(error)).rejects.toBe(error)

      expect(postMock).not.toHaveBeenCalled()
      expect(authState.clearAuth).toHaveBeenCalled()
    })

    it('clears auth and redirects when the refresh call itself fails', async () => {
      postMock.mockRejectedValue(new Error('refresh failed'))
      const originalRequest: RetryableRequestConfig = { url: '/api/cases', headers: {} }
      const error = { response: { status: 401 }, config: originalRequest }

      await expect(responseError(error)).rejects.toBe(error)

      expect(authState.clearAuth).toHaveBeenCalled()
      expect(window.location.href).toBe('/login')
      expect(apiClientFn).not.toHaveBeenCalled()
    })

    it('passes through non-401 errors without touching auth state', async () => {
      const originalRequest: RetryableRequestConfig = { url: '/api/cases', headers: {} }
      const error = { response: { status: 500 }, config: originalRequest }

      await expect(responseError(error)).rejects.toBe(error)

      expect(authState.clearAuth).not.toHaveBeenCalled()
      expect(postMock).not.toHaveBeenCalled()
    })

    it('coalesces concurrent refresh calls into a single request', async () => {
      let resolvePost: (value: unknown) => void = () => undefined
      postMock.mockReturnValue(
        new Promise((resolve) => {
          resolvePost = resolve
        })
      )
      apiClientFn.mockResolvedValue({ status: 200, data: 'ok' })

      const requestA: RetryableRequestConfig = { url: '/api/cases', headers: {} }
      const requestB: RetryableRequestConfig = { url: '/api/clients', headers: {} }
      const errorA = { response: { status: 401 }, config: requestA }
      const errorB = { response: { status: 401 }, config: requestB }

      const promiseA = responseError(errorA)
      const promiseB = responseError(errorB)

      resolvePost({
        data: { accessToken: 'shared-token', userId: 'u1', email: 'a@b.com', role: 'LAWYER' },
      })

      await Promise.all([promiseA, promiseB])

      expect(postMock).toHaveBeenCalledTimes(1)
      expect(requestA.headers.Authorization).toBe('Bearer shared-token')
      expect(requestB.headers.Authorization).toBe('Bearer shared-token')
    })
  })
})
