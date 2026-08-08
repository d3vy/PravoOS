import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const sourceDir = join(scriptDir, '..', '..', 'docs', 'legal')
const targetDir = join(scriptDir, '..', 'src', 'legal')
const requisitesFile = join(sourceDir, 'rekvizity.json')

const PUBLIC_DOCUMENTS = [
  'politika-obrabotki-pdn.md',
  'soglasie-na-obrabotku-pdn.md',
  'soglasie-transgranichnaya-peredacha.md',
  'politika-cookie.md',
]

const PLACEHOLDER_PATTERN = /\{\{([A-ZА-Я_]+)\}\}/g
const LINK_PATTERN = /\[([^\]]+)\]\(([^)]+)\)/g

function readRequisites() {
  if (!existsSync(requisitesFile)) {
    throw new Error(`[sync-legal] missing ${requisitesFile}`)
  }
  const parsed = JSON.parse(readFileSync(requisitesFile, 'utf8'))
  return Object.fromEntries(Object.entries(parsed).filter(([key]) => !key.startsWith('_')))
}

function render(document, source, requisites) {
  const unresolved = new Set()
  const rendered = source.replace(PLACEHOLDER_PATTERN, (match, key) => {
    if (requisites[key] === undefined) {
      unresolved.add(key)
      return match
    }
    return requisites[key]
  })
  if (unresolved.size > 0) {
    throw new Error(
      `[sync-legal] ${document}: no value in rekvizity.json for ${[...unresolved].join(', ')}`
    )
  }
  return rendered
}

function assertLinksAreRenderable(document, source) {
  const dead = [...source.matchAll(LINK_PATTERN)]
    .map(([, , href]) => href)
    .filter((href) => !href.startsWith('http') && !PUBLIC_DOCUMENTS.includes(href))
  if (dead.length > 0) {
    throw new Error(
      `[sync-legal] ${document}: LegalMarkdown renders these links as plain text — ` +
        `use an absolute URL or a published document: ${[...new Set(dead)].join(', ')}`
    )
  }
}

if (!existsSync(sourceDir)) {
  console.log('[sync-legal] docs/legal is not available, keeping bundled copies')
  process.exit(0)
}

const requisites = readRequisites()

for (const document of readdirSync(sourceDir).filter((name) => name.endsWith('.md'))) {
  render(document, readFileSync(join(sourceDir, document), 'utf8'), requisites)
}

mkdirSync(targetDir, { recursive: true })

for (const document of PUBLIC_DOCUMENTS) {
  const source = join(sourceDir, document)
  if (!existsSync(source)) {
    throw new Error(`[sync-legal] missing source document: ${document}`)
  }
  const rendered = render(document, readFileSync(source, 'utf8'), requisites)
  assertLinksAreRenderable(document, rendered)
  writeFileSync(join(targetDir, document), rendered, 'utf8')
}

console.log(`[sync-legal] synced ${PUBLIC_DOCUMENTS.length} documents into src/legal`)
