/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string
  readonly VITE_SENTRY_DSN?: string
  readonly VITE_SENTRY_ENVIRONMENT?: string
  readonly VITE_APP_RELEASE?: string
  readonly VITE_ERROR_REPORT_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
