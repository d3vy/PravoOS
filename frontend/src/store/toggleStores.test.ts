import { beforeEach, describe, expect, it } from 'vitest'
import { useCommandPaletteStore } from './commandPaletteStore'
import { useMobileTimerSheetStore } from './mobileTimerSheetStore'
import { useMoreSheetStore } from './moreSheetStore'
import { useShortcutsDialogStore } from './shortcutsDialogStore'

describe('useCommandPaletteStore / useShortcutsDialogStore (setOpen + toggle)', () => {
  for (const [name, store] of [
    ['commandPaletteStore', useCommandPaletteStore],
    ['shortcutsDialogStore', useShortcutsDialogStore],
  ] as const) {
    describe(name, () => {
      beforeEach(() => {
        store.setState({ open: false })
      })

      it('starts closed', () => {
        expect(store.getState().open).toBe(false)
      })

      it('setOpen(true) opens it and setOpen(false) closes it', () => {
        store.getState().setOpen(true)
        expect(store.getState().open).toBe(true)
        store.getState().setOpen(false)
        expect(store.getState().open).toBe(false)
      })

      it('toggle flips the open state', () => {
        store.getState().toggle()
        expect(store.getState().open).toBe(true)
        store.getState().toggle()
        expect(store.getState().open).toBe(false)
      })
    })
  }
})

describe('useMobileTimerSheetStore / useMoreSheetStore (toggle + close)', () => {
  for (const [name, store] of [
    ['mobileTimerSheetStore', useMobileTimerSheetStore],
    ['moreSheetStore', useMoreSheetStore],
  ] as const) {
    describe(name, () => {
      beforeEach(() => {
        store.setState({ open: false })
      })

      it('starts closed', () => {
        expect(store.getState().open).toBe(false)
      })

      it('toggle flips the open state', () => {
        store.getState().toggle()
        expect(store.getState().open).toBe(true)
        store.getState().toggle()
        expect(store.getState().open).toBe(false)
      })

      it('close forces the state closed even when already open', () => {
        store.getState().toggle()
        store.getState().close()
        expect(store.getState().open).toBe(false)
      })
    })
  }
})
