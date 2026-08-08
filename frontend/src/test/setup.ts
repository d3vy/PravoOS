import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(cleanup)

try {
  window.localStorage.setItem('__pravoos_storage_probe__', '1')
  window.localStorage.removeItem('__pravoos_storage_probe__')
} catch {
  const memoryStorage = new Map<string, string>()
  const fallbackStorage: Storage = {
    get length() {
      return memoryStorage.size
    },
    clear: () => memoryStorage.clear(),
    getItem: (key) => memoryStorage.get(key) ?? null,
    key: (index) => Array.from(memoryStorage.keys())[index] ?? null,
    removeItem: (key) => {
      memoryStorage.delete(key)
    },
    setItem: (key, value) => {
      memoryStorage.set(key, value)
    },
  }
  Object.defineProperty(globalThis, 'localStorage', { value: fallbackStorage, configurable: true })
  Object.defineProperty(window, 'localStorage', { value: fallbackStorage, configurable: true })
}

if (!window.HTMLElement.prototype.scrollIntoView) {
  window.HTMLElement.prototype.scrollIntoView = (): void => undefined
}

if (!window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }) as MediaQueryList
}
