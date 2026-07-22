import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { useShortcutsDialogStore } from '../../store/shortcutsDialogStore'

interface ShortcutRow {
  keys: string[]
  labelKey: string
}

interface ShortcutGroup {
  titleKey: string
  rows: ShortcutRow[]
}

const GROUPS: ShortcutGroup[] = [
  {
    titleKey: 'hotkeys.groupNavigation',
    rows: [
      { keys: ['g', 'd'], labelKey: 'hotkeys.goDashboard' },
      { keys: ['g', 'c'], labelKey: 'hotkeys.goCases' },
      { keys: ['g', 'k'], labelKey: 'hotkeys.goClients' },
    ],
  },
  {
    titleKey: 'hotkeys.groupActions',
    rows: [
      { keys: ['n'], labelKey: 'hotkeys.newCase' },
      { keys: ['t'], labelKey: 'hotkeys.toggleTimer' },
      { keys: ['/'], labelKey: 'hotkeys.openSearch' },
      { keys: ['?'], labelKey: 'hotkeys.openCheatsheet' },
    ],
  },
  {
    titleKey: 'hotkeys.groupPalette',
    rows: [
      { keys: ['↑', '↓'], labelKey: 'command.hintNavigate' },
      { keys: ['↵'], labelKey: 'command.hintSelect' },
      { keys: ['esc'], labelKey: 'command.hintClose' },
    ],
  },
]

export function ShortcutsDialog(): JSX.Element {
  const { t } = useTranslation()
  const open = useShortcutsDialogStore((state) => state.open)
  const setOpen = useShortcutsDialogStore((state) => state.setOpen)

  return (
    <Modal open={open} onClose={() => setOpen(false)} title={t('hotkeys.dialogTitle')} size="sm">
      <div className="flex flex-col gap-5">
        {GROUPS.map((group) => (
          <div key={group.titleKey}>
            <p className="eyebrow mb-2">{t(group.titleKey)}</p>
            <div className="flex flex-col gap-2">
              {group.rows.map((row) => (
                <div key={row.labelKey} className="flex items-center justify-between gap-4">
                  <span className="text-sm text-fg">{t(row.labelKey)}</span>
                  <span className="flex items-center gap-1 shrink-0">
                    {row.keys.map((key) => (
                      <kbd
                        key={key}
                        className="inline-flex items-center rounded-md border border-line px-1.5 py-0.5 text-[11px] font-medium text-fg-muted"
                      >
                        {key}
                      </kbd>
                    ))}
                  </span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </Modal>
  )
}
