import { describe, expect, it } from 'vitest'
import en from './locales/en'
import ru from './locales/ru'

type TranslationTree = { [key: string]: string | TranslationTree }

function flatten(tree: TranslationTree, prefix = ''): Map<string, string> {
  const result = new Map<string, string>()
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') {
      result.set(path, value)
    } else {
      for (const [nestedPath, nestedValue] of flatten(value, path)) {
        result.set(nestedPath, nestedValue)
      }
    }
  }
  return result
}

const INTERPOLATION_PATTERN = /\{\{\s*([\w.]+)\s*\}\}/g
const PLURAL_SUFFIX_PATTERN = /_(zero|one|two|few|many|other)$/

function interpolationVars(value: string): string[] {
  return [...value.matchAll(INTERPOLATION_PATTERN)].map((match) => match[1]).sort()
}

function basePluralKey(key: string): string {
  return key.replace(PLURAL_SUFFIX_PATTERN, '')
}

describe('i18n locale parity', () => {
  const enFlat = flatten(en as TranslationTree)
  const ruFlat = flatten(ru as TranslationTree)
  const enBaseKeys = new Set([...enFlat.keys()].map(basePluralKey))
  const ruBaseKeys = new Set([...ruFlat.keys()].map(basePluralKey))

  it('en has no keys missing from ru', () => {
    const missingInRu = [...enBaseKeys].filter((key) => !ruBaseKeys.has(key))
    expect(missingInRu).toEqual([])
  })

  it('ru has no keys missing from en', () => {
    const missingInEn = [...ruBaseKeys].filter((key) => !enBaseKeys.has(key))
    expect(missingInEn).toEqual([])
  })

  it('has no empty translation values', () => {
    const emptyEn = [...enFlat.entries()].filter(([, value]) => value.trim() === '').map(([key]) => key)
    const emptyRu = [...ruFlat.entries()].filter(([, value]) => value.trim() === '').map(([key]) => key)
    expect(emptyEn).toEqual([])
    expect(emptyRu).toEqual([])
  })

  it('interpolation placeholders match between locales', () => {
    const mismatches: string[] = []
    for (const [key, enValue] of enFlat) {
      const ruValue = ruFlat.get(key)
      if (ruValue === undefined) continue
      const enVars = interpolationVars(enValue)
      const ruVars = interpolationVars(ruValue)
      if (enVars.join(',') !== ruVars.join(',')) {
        mismatches.push(`${key}: en=[${enVars.join(',')}] ru=[${ruVars.join(',')}]`)
      }
    }
    expect(mismatches).toEqual([])
  })

  it('has no duplicate values suggesting a copy-paste placeholder key', () => {
    const suspiciousKeys = [...enFlat.entries()]
      .filter(([key, value]) => value === key)
      .map(([key]) => key)
    expect(suspiciousKeys).toEqual([])
  })
})
