import { Fragment, type ReactNode } from 'react'

const INLINE_PATTERN = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g
const LINK_PATTERN = /^\[([^\]]+)\]\(([^)]+)\)$/

function renderInline(text: string): ReactNode {
  const parts = text.split(INLINE_PATTERN).filter((part) => part !== '')
  return parts.map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return (
        <strong key={index} className="font-semibold text-fg">
          {part.slice(2, -2)}
        </strong>
      )
    }
    if (part.startsWith('`') && part.endsWith('`')) {
      return (
        <code key={index} className="rounded bg-surface px-1.5 py-0.5 text-[0.85em] text-fg">
          {part.slice(1, -1)}
        </code>
      )
    }
    const link = LINK_PATTERN.exec(part)
    if (link) {
      const [, label, href] = link
      if (href.startsWith('http')) {
        return (
          <a
            key={index}
            href={href}
            target="_blank"
            rel="noreferrer noopener"
            className="text-accent hover:underline"
          >
            {label}
          </a>
        )
      }
      return <Fragment key={index}>{label}</Fragment>
    }
    return <Fragment key={index}>{part}</Fragment>
  })
}

function splitTableRow(line: string): string[] {
  return line
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => cell.trim())
}

function isTableDivider(line: string): boolean {
  return /^\|?[\s:-]+\|[\s|:-]*$/.test(line) && line.includes('-')
}

function Table({ rows }: { rows: string[] }): JSX.Element {
  const [headerLine, , ...bodyLines] = rows
  const headers = splitTableRow(headerLine)
  return (
    <div className="my-4 overflow-x-auto">
      <table className="w-full min-w-[32rem] border-collapse text-sm">
        <thead>
          <tr className="border-b border-line">
            {headers.map((header, index) => (
              <th key={index} className="px-3 py-2 text-left font-semibold text-fg">
                {renderInline(header)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {bodyLines.map((line, rowIndex) => (
            <tr key={rowIndex} className="border-b border-line/60 align-top">
              {splitTableRow(line).map((cell, cellIndex) => (
                <td key={cellIndex} className="px-3 py-2 text-fg-muted">
                  {renderInline(cell)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function LegalMarkdown({ source }: { source: string }): JSX.Element {
  const lines = source.split('\n')
  const blocks: ReactNode[] = []
  let paragraph: string[] = []
  let listItems: string[] = []
  let tableRows: string[] = []

  const flushParagraph = (): void => {
    if (paragraph.length === 0) return
    blocks.push(
      <p key={`p-${blocks.length}`} className="my-3 leading-relaxed text-fg-muted">
        {renderInline(paragraph.join(' '))}
      </p>,
    )
    paragraph = []
  }

  const flushList = (): void => {
    if (listItems.length === 0) return
    blocks.push(
      <ul key={`ul-${blocks.length}`} className="my-3 flex list-disc flex-col gap-1.5 pl-5">
        {listItems.map((item, index) => (
          <li key={index} className="leading-relaxed text-fg-muted">
            {renderInline(item)}
          </li>
        ))}
      </ul>,
    )
    listItems = []
  }

  const flushTable = (): void => {
    if (tableRows.length === 0) return
    blocks.push(<Table key={`table-${blocks.length}`} rows={tableRows} />)
    tableRows = []
  }

  const flushAll = (): void => {
    flushParagraph()
    flushList()
    flushTable()
  }

  for (const rawLine of lines) {
    const line = rawLine.trimEnd()

    if (line.trim() === '') {
      flushAll()
      continue
    }

    if (line.startsWith('|')) {
      flushParagraph()
      flushList()
      if (tableRows.length === 1 && !isTableDivider(line)) {
        tableRows.push('|---|')
      }
      tableRows.push(line)
      continue
    }
    flushTable()

    if (line.startsWith('#')) {
      flushParagraph()
      flushList()
      const level = line.match(/^#+/)?.[0].length ?? 1
      const text = line.replace(/^#+\s*/, '')
      const className =
        level === 1
          ? 'mt-0 mb-6 text-3xl font-bold tracking-tight text-fg'
          : level === 2
            ? 'mt-8 mb-3 text-xl font-semibold text-fg'
            : 'mt-6 mb-2 text-base font-semibold text-fg'
      const Heading = (level === 1 ? 'h1' : level === 2 ? 'h2' : 'h3') as 'h1' | 'h2' | 'h3'
      blocks.push(
        <Heading key={`h-${blocks.length}`} className={className}>
          {renderInline(text)}
        </Heading>,
      )
      continue
    }

    if (/^(---|\*\*\*)$/.test(line.trim())) {
      flushParagraph()
      flushList()
      blocks.push(<hr key={`hr-${blocks.length}`} className="my-8 border-line" />)
      continue
    }

    if (/^[-*]\s+/.test(line)) {
      flushParagraph()
      listItems.push(line.replace(/^[-*]\s+/, ''))
      continue
    }

    flushList()
    paragraph.push(line.trim())
  }

  flushAll()

  return <div className="text-[15px]">{blocks}</div>
}
