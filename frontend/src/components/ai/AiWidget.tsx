import { useCallback, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import { AnimatePresence } from 'framer-motion'
import { useAiChatStore } from '../../store/aiChatStore'
import { AiWidgetLauncher } from './AiWidgetLauncher'
import { AiWidgetPanel } from './AiWidgetPanel'

const HIDDEN_ROUTES = ['/ai']

export function AiWidget(): JSX.Element | null {
  const location = useLocation()
  const open = useAiChatStore((state) => state.open)
  const close = useAiChatStore((state) => state.close)
  const launcherRef = useRef<HTMLButtonElement>(null)

  const handleClose = useCallback((): void => {
    close()
    requestAnimationFrame(() => launcherRef.current?.focus())
  }, [close])

  if (HIDDEN_ROUTES.some((route) => location.pathname.startsWith(route))) return null

  return (
    <AnimatePresence initial={false}>
      {open ? (
        <AiWidgetPanel key="panel" onClose={handleClose} />
      ) : (
        <AiWidgetLauncher key="launcher" ref={launcherRef} />
      )}
    </AnimatePresence>
  )
}
