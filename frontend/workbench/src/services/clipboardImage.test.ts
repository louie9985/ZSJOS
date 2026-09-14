import { afterEach, describe, expect, it, vi } from 'vitest'
import { ClipboardImageReadError, clipboardImageFiles, clipboardImageName, readClipboardImageFiles } from './clipboardImage'

const clipboardItem = (types: string[]) => ({
  types,
  getType: vi.fn(async (type: string) => new Blob([type], { type })),
}) as unknown as ClipboardItem

const stubClipboardRead = (items: ClipboardItem[]) => {
  const read = vi.fn(async () => items as ClipboardItems)
  vi.stubGlobal('isSecureContext', true)
  vi.stubGlobal('navigator', { clipboard: { read } })
  return read
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('clipboard image helpers', () => {
  it('creates a named image file from clipboard items', () => {
    const source = new File(['image'], 'image.png', { type: 'image/png' })
    const event = { clipboardData: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => source }] } } as unknown as ClipboardEvent
    const files = clipboardImageFiles(event)
    expect(files).toHaveLength(1)
    expect(files[0].type).toBe('image/png')
    expect(files[0].name).toMatch(/^截图-\d{8}-\d{6}\.png$/)
  })

  it('ignores non-image clipboard content', () => {
    const event = { clipboardData: { items: [{ kind: 'string', type: 'text/plain', getAsFile: () => null }] } } as unknown as ClipboardEvent
    expect(clipboardImageFiles(event)).toEqual([])
  })

  it('uses jpg for jpeg screenshots', () => {
    expect(clipboardImageName('image/jpeg', new Date(2026, 8, 9, 1, 2, 3))).toBe('截图-20260909-010203.jpg')
  })

  it('reads PNG, JPEG and WebP images from the clipboard API', async () => {
    const read = stubClipboardRead([
      clipboardItem(['image/png']),
      clipboardItem(['image/jpeg']),
      clipboardItem(['image/webp']),
    ])

    const files = await readClipboardImageFiles()

    expect(read).toHaveBeenCalledOnce()
    expect(files.map(file => file.type)).toEqual(['image/png', 'image/jpeg', 'image/webp'])
    expect(files.map(file => file.name)).toEqual([
      expect.stringMatching(/^截图-\d{8}-\d{6}\.png$/),
      expect.stringMatching(/^截图-\d{8}-\d{6}\.jpg$/),
      expect.stringMatching(/^截图-\d{8}-\d{6}\.webp$/),
    ])
  })

  it('returns no files for empty, text or HTML clipboard content', async () => {
    stubClipboardRead([
      clipboardItem(['text/plain']),
      clipboardItem(['text/html']),
    ])
    await expect(readClipboardImageFiles()).resolves.toEqual([])
  })

  it('reports unsupported clipboard reads outside a supported secure context', async () => {
    vi.stubGlobal('isSecureContext', false)
    vi.stubGlobal('navigator', {})
    await expect(readClipboardImageFiles()).rejects.toMatchObject({ code: 'unsupported' } satisfies Partial<ClipboardImageReadError>)
  })

  it('reports clipboard permission denial separately', async () => {
    vi.stubGlobal('isSecureContext', true)
    vi.stubGlobal('navigator', { clipboard: { read: vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError')) } })
    await expect(readClipboardImageFiles()).rejects.toMatchObject({ code: 'permission-denied' } satisfies Partial<ClipboardImageReadError>)
  })

  it('reports other clipboard read failures', async () => {
    vi.stubGlobal('isSecureContext', true)
    vi.stubGlobal('navigator', { clipboard: { read: vi.fn().mockRejectedValue(new Error('failed')) } })
    await expect(readClipboardImageFiles()).rejects.toMatchObject({ code: 'read-failed' } satisfies Partial<ClipboardImageReadError>)
  })
})
