import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useCommandPaletteStore } from '../../store/commandPaletteStore'
import { Modal } from '../ui/Modal'

const GRAFANA_URL = '/grafana/'

interface AdminCommand {
  id: string
  label: string
  run: () => void
}

export function AdminCommandPalette(): JSX.Element {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const open = useCommandPaletteStore((state) => state.open)
  const setOpen = useCommandPaletteStore((state) => state.setOpen)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  const commands = useMemo<AdminCommand[]>(
    () => [
      { id: 'applications', label: t('admin.applications'), run: () => navigate('/admin/applications') },
      { id: 'users', label: t('admin.lawyers'), run: () => navigate('/admin/users') },
      { id: 'documents', label: t('admin.documents'), run: () => navigate('/admin/documents') },
      { id: 'aiStats', label: t('admin.aiMetrics'), run: () => navigate('/admin/ai-stats') },
      {
        id: 'grafana',
        label: t('admin.metricsGrafana'),
        run: () => window.open(GRAFANA_URL, '_blank', 'noopener,noreferrer'),
      },
    ],
    [t, navigate]
  )

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return commands
    return commands.filter((command) => command.label.toLowerCase().includes(normalized))
  }, [commands, query])

  useEffect(() => {
    if (!open) {
      setQuery('')
      setActiveIndex(0)
      return
    }
    const timer = setTimeout(() => inputRef.current?.focus(), 30)
    return () => clearTimeout(timer)
  }, [open])

  useEffect(() => {
    setActiveIndex(0)
  }, [query])

  const runAndClose = (command: AdminCommand): void => {
    command.run()
    setOpen(false)
  }

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((index) => Math.min(index + 1, filtered.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((index) => Math.max(index - 1, 0))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      const command = filtered[activeIndex]
      if (command) runAndClose(command)
    }
  }

  return (
    <Modal open={open} onClose={() => setOpen(false)} title={t('command.dialog')}>
      <input
        ref={inputRef}
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={t('command.inputAria')}
        aria-label={t('command.inputAria')}
        className="w-full px-3 py-2.5 rounded-lg border border-line bg-bg text-fg text-sm placeholder:text-fg-muted/60 focus:outline-none focus:ring-2 focus:ring-accent mb-3"
      />
      {filtered.length === 0 ? (
        <p className="px-1 py-4 text-sm text-fg-muted">{t('command.nothingFound', { query })}</p>
      ) : (
        <ul role="listbox" className="flex flex-col gap-1">
          {filtered.map((command, index) => (
            <li key={command.id}>
              <button
                type="button"
                role="option"
                aria-selected={index === activeIndex}
                onClick={() => runAndClose(command)}
                onMouseEnter={() => setActiveIndex(index)}
                className={`w-full text-left px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  index === activeIndex ? 'bg-accent/10 text-accent' : 'text-fg hover:bg-bg'
                }`}
              >
                {command.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
