import { useTranslation } from 'react-i18next'
import { Modal } from '../../../components/ui/Modal'
import { Button } from '../../../components/ui/Button'
import { DASHBOARD_WIDGET_IDS, useDashboardLayoutStore } from '../../../store/dashboardLayoutStore'
import { WIDGET_TITLE_KEYS } from './registry'

interface WidgetPickerModalProps {
  open: boolean
  onClose: () => void
}

export function WidgetPickerModal({ open, onClose }: WidgetPickerModalProps): JSX.Element {
  const { t } = useTranslation()
  const { hidden, toggleWidget, resetLayout } = useDashboardLayoutStore()

  return (
    <Modal open={open} onClose={onClose} title={t('dashboard.widgetsPickerTitle')} size="sm">
      <div className="space-y-1">
        {DASHBOARD_WIDGET_IDS.map((id) => (
          <label
            key={id}
            className="flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-bg cursor-pointer"
          >
            <input
              type="checkbox"
              checked={!hidden.includes(id)}
              onChange={() => toggleWidget(id)}
              className="w-4 h-4 rounded border-line accent-accent-solid"
            />
            <span className="text-sm text-fg">{t(WIDGET_TITLE_KEYS[id])}</span>
          </label>
        ))}
      </div>
      <div className="mt-4 flex justify-end">
        <Button variant="ghost" size="sm" onClick={resetLayout}>
          {t('dashboard.widgetsReset')}
        </Button>
      </div>
    </Modal>
  )
}
