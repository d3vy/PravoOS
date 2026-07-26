import { ScopedChatPanel } from '../chat/ScopedChatPanel'

export function CaseChatSection({ caseId }: { caseId: string }): JSX.Element {
  return (
    <section className="mb-8">
      <ScopedChatPanel
        scope={{ caseId }}
        i18nPrefix="caseChat"
        conversationsQueryKey={['case-conversations', caseId]}
      />
    </section>
  )
}
