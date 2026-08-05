import { copyFileSync, existsSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const sourceDir = join(scriptDir, '..', '..', 'docs', 'legal')
const targetDir = join(scriptDir, '..', 'src', 'legal')

const PUBLIC_DOCUMENTS = [
  'politika-obrabotki-pdn.md',
  'soglasie-na-obrabotku-pdn.md',
  'soglasie-transgranichnaya-peredacha.md',
  'politika-cookie.md',
]

if (!existsSync(sourceDir)) {
  console.log('[sync-legal] docs/legal is not available, keeping bundled copies')
  process.exit(0)
}

mkdirSync(targetDir, { recursive: true })

for (const document of PUBLIC_DOCUMENTS) {
  const source = join(sourceDir, document)
  if (!existsSync(source)) {
    console.warn(`[sync-legal] missing source document: ${document}`)
    continue
  }
  copyFileSync(source, join(targetDir, document))
}

console.log(`[sync-legal] synced ${PUBLIC_DOCUMENTS.length} documents into src/legal`)
