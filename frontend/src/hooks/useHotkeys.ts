import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { timeApi } from '../api/time'
import type { TimeEntryResponse } from '../types'
import { useToast } from './useToast'
import { useCommandPaletteStore } from '../store/commandPaletteStore'
import { useShortcutsDialogStore } from '../store/shortcutsDialogStore'

const SEQUENCE_TIMEOUT_MS = 900
const CASE_ROUTE = /^\/cases\/([^/]+)$/

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
}

export function useHotkeys(): void {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const toggleCommandPalette = useCommandPaletteStore((state) => state.toggle)
  const toggleShortcutsDialog = useShortcutsDialogStore((state) => state.toggle)

  const locationRef = useRef(location)
  locationRef.current = location

  useEffect(() => {
    let pendingPrefix: string | null = null
    let pendingTimer: ReturnType<typeof setTimeout> | null = null
    let pendingToastId: string | null = null

    const clearPending = (): void => {
      pendingPrefix = null
      if (pendingTimer) {
        clearTimeout(pendingTimer)
        pendingTimer = null
      }
      if (pendingToastId) {
        toast.dismiss(pendingToastId)
        pendingToastId = null
      }
    }

    const toggleTimer = async (): Promise<void> => {
      const activeTimer = queryClient.getQueryData<TimeEntryResponse | null>(['active-timer'])
      if (activeTimer) {
        try {
          await timeApi.stopTimer(activeTimer.caseId)
          queryClient.invalidateQueries({ queryKey: ['active-timer'] })
          queryClient.invalidateQueries({ queryKey: ['case-time', activeTimer.caseId] })
        } catch {
          toast.error(t('globalTimer.stopError'))
        }
        return
      }

      const match = CASE_ROUTE.exec(locationRef.current.pathname)
      if (!match) {
        toast.info(t('hotkeys.timerNeedsCase'))
        return
      }
      const caseId = match[1]
      try {
        await timeApi.startTimer(caseId, {
          description: t('timeTracking.defaultDescription'),
          hourlyRate: 0,
          billable: true,
        })
        queryClient.invalidateQueries({ queryKey: ['active-timer'] })
      } catch {
        toast.error(t('timeTracking.startError'))
      }
    }

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.metaKey || event.ctrlKey || event.altKey) return
      if (event.repeat) return
      if (isTypingTarget(event.target)) return

      const key = event.key

      if (pendingPrefix === 'g') {
        clearPending()
        if (key === 'd') {
          event.preventDefault()
          navigate('/dashboard')
        } else if (key === 'c') {
          event.preventDefault()
          navigate('/cases')
        } else if (key === 'k') {
          event.preventDefault()
          navigate('/clients')
        }
        return
      }

      if (key === 'g') {
        event.preventDefault()
        pendingPrefix = 'g'
        pendingTimer = setTimeout(clearPending, SEQUENCE_TIMEOUT_MS)
        pendingToastId = toast.info(t('hotkeys.gPrefixActive'))
        return
      }

      if (key === 'n') {
        event.preventDefault()
        navigate('/cases?new=1')
      } else if (key === 't') {
        event.preventDefault()
        void toggleTimer()
      } else if (key === '/') {
        event.preventDefault()
        toggleCommandPalette()
      } else if (key === '?') {
        event.preventDefault()
        toggleShortcutsDialog()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      clearPending()
    }
  }, [navigate, queryClient, toast, toggleCommandPalette, toggleShortcutsDialog, t])
}
