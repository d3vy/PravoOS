const REDACTED = '[redacted]'

const JWT = /eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}/g
const BEARER = /bearer\s+[A-Za-z0-9._~+/=-]{8,}/gi
const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g
const RUSSIAN_PHONE = /(?<!\d)(?:\+7|8)[\s(-]?\d{3}[\s)-]?\d{3}[\s-]?\d{2}[\s-]?\d{2}(?!\d)/g

function maskEmail(email: string): string {
  const atIndex = email.indexOf('@')
  return `${email.slice(0, Math.min(2, atIndex))}***${email.slice(atIndex)}`
}

export function scrubPii(value: string): string
export function scrubPii(value: undefined): undefined
export function scrubPii(value: string | undefined): string | undefined
export function scrubPii(value: string | undefined): string | undefined {
  if (!value) {
    return value
  }
  return value
    .replace(JWT, REDACTED)
    .replace(BEARER, `Bearer ${REDACTED}`)
    .replace(EMAIL, maskEmail)
    .replace(RUSSIAN_PHONE, REDACTED)
}
