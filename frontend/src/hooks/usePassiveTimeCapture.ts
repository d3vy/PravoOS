import { useEffect, useRef } from 'react'
import { matchPath, useLocation } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { timeApi } from '../api/time'
import type { CaseResponse, TimeEntryResponse } from '../types'
import {
  ACTIVITY_IDLE_TIMEOUT_MS,
  ACTIVITY_SUGGEST_THRESHOLD_SECONDS,
  useActivityTimeStore,
} from '../store/activityTimeStore'
import { useToast } from './useToast'
import { formatDuration } from '../utils/billing'

const ACTIVITY_EVENTS: (keyof DocumentEventMap)[] = ['mousemove', 'keydown', 'scroll', 'click']

function activeCaseIdFromPath(pathname: string): string | null {
  const match = matchPath('/cases/:caseId/*', pathname) ?? matchPath('/cases/:caseId', pathname)
  const caseId = match?.params.caseId
  if (!caseId || caseId === 'new') return null
  return caseId
}

export function usePassiveTimeCapture(): void {
  const location = useLocation()
  const queryClient = useQueryClient()
  const toast = useToast()
  const { t } = useTranslation()

  const lastActivityRef = useRef(Date.now())
  const pathnameRef = useRef(location.pathname)
  pathnameRef.current = location.pathname

  useEffect(() => {
    const markActive = (): void => {
      lastActivityRef.current = Date.now()
    }
    ACTIVITY_EVENTS.forEach((event) => document.addEventListener(event, markActive, { passive: true }))
    return () => {
      ACTIVITY_EVENTS.forEach((event) => document.removeEventListener(event, markActive))
    }
  }, [])

  useEffect(() => {
    const tick = window.setInterval(() => {
      const caseId = activeCaseIdFromPath(pathnameRef.current)
      if (!caseId) return
      if (document.visibilityState !== 'visible') return
      if (Date.now() - lastActivityRef.current > ACTIVITY_IDLE_TIMEOUT_MS) return

      const runningTimer = queryClient.getQueryData<TimeEntryResponse | null>(['active-timer'])
      if (runningTimer?.caseId === caseId) return

      const store = useActivityTimeStore.getState()
      store.addSeconds(caseId, 1)

      const accumulator = useActivityTimeStore.getState().accumulators[caseId]
      if (!accumulator || accumulator.suggested || accumulator.seconds < ACTIVITY_SUGGEST_THRESHOLD_SECONDS) return

      store.markSuggested(caseId)
      const caseTitle = queryClient.getQueryData<CaseResponse>(['case', caseId])?.title ?? ''
      const minutes = Math.round(accumulator.seconds / 60)

      toast.info(
        t('passiveTime.suggestion', { duration: formatDuration(minutes), caseTitle }),
        {
          label: t('passiveTime.logAction'),
          onClick: () => {
            timeApi
              .create(caseId, {
                description: t('passiveTime.entryDescription'),
                activityDate: new Date().toISOString().slice(0, 10),
                minutes,
                hourlyRate: 0,
                billable: true,
              })
              .then(() => {
                useActivityTimeStore.getState().reset(caseId)
                queryClient.invalidateQueries({ queryKey: ['case-time', caseId] })
                toast.success(t('passiveTime.logged'))
              })
              .catch(() => toast.error(t('passiveTime.logError')))
          },
        }
      )
    }, 1000)

    return () => window.clearInterval(tick)
  }, [queryClient, toast, t])
}
