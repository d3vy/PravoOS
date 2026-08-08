import { authApi } from '../api/auth'
import { clearLocalSession } from '../store/session'

export async function signOut(): Promise<void> {
  try {
    await authApi.logout()
  } catch {
    // best-effort revocation; the local session is cleared regardless
  }
  clearLocalSession()
}
