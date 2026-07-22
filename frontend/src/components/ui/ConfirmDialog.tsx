import { useTranslation } from 'react-i18next'
import { Modal } from './Modal'
import { Button } from './Button'
import { useConfirmStore } from '../../store/confirmStore'

export function ConfirmDialogHost(): JSX.Element {
  const { t } = useTranslation()
  const request = useConfirmStore((state) => state.request)
  const settle = useConfirmStore((state) => state.settle)

  return (
    <Modal
      open={request !== null}
      onClose={() => settle(false)}
      title={request?.title}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={() => settle(false)}>
            {request?.cancelLabel ?? t('common.cancel')}
          </Button>
          <Button variant={request?.danger ? 'danger' : 'primary'} onClick={() => settle(true)}>
            {request?.confirmLabel ?? t('common.confirm')}
          </Button>
        </>
      }
    >
      {request?.description && (
        <p className="text-sm text-fg-muted">{request.description}</p>
      )}
    </Modal>
  )
}
