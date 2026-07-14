import { describe, expect, it } from 'vitest'
import { urlBase64ToUint8Array } from './vapid'

describe('urlBase64ToUint8Array', () => {
  it('decodes an unpadded url-safe VAPID key', () => {
    const decoded = urlBase64ToUint8Array('BOh0_-Wd')

    expect(Array.from(decoded)).toEqual([0x04, 0xe8, 0x74, 0xff, 0xe5, 0x9d])
  })

  it('restores padding for lengths that are not a multiple of four', () => {
    expect(urlBase64ToUint8Array('QQ').length).toBe(1)
    expect(urlBase64ToUint8Array('QUJD').length).toBe(3)
  })

  it('maps url-safe alphabet back to standard base64', () => {
    const urlSafe = urlBase64ToUint8Array('-_8')
    const standard = urlBase64ToUint8Array('+/8=')

    expect(Array.from(urlSafe)).toEqual(Array.from(standard))
  })

  it('produces a 65-byte application server key for a real VAPID public key', () => {
    const vapidPublicKey =
      'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U'

    expect(urlBase64ToUint8Array(vapidPublicKey).length).toBe(65)
  })
})
