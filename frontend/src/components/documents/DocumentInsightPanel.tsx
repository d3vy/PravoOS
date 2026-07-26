import { ScopedChatPanel } from '../chat/ScopedChatPanel'
import { DocumentSummaryCard } from './DocumentSummaryCard'

export function DocumentInsightPanel({ documentId }: { documentId: string }): JSX.Element {
  return (
    <div className="mt-3">
      <DocumentSummaryCard documentId={documentId} />
      <ScopedChatPanel
        scope={{ documentId }}
        i18nPrefix="documentChat"
        conversationsQueryKey={['document-conversations', documentId]}
      />
    </div>
  )
}
