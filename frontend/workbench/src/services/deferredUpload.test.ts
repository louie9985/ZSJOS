import { describe, expect, it, vi } from 'vitest'
import { uploadDeferredFiles, type DeferredUploadItem } from './deferredUpload'

const item = (uid: string, status: DeferredUploadItem<string>['status'] = 'pending', uploaded?: string): DeferredUploadItem<string> => ({
  uid, name: `${uid}.png`, status, file: new File(['x'], `${uid}.png`, { type: 'image/png' }), uploaded
})

describe('deferred upload', () => {
  it('uploads pending files and keeps completed files out of retries', async () => {
    const upload = vi.fn(async (file: File) => file.name)
    const result = await uploadDeferredFiles([item('a', 'done', 'a-old'), item('b')], upload, vi.fn())
    expect(result.failed).toBe(false)
    expect(result.items.map(value => value.uploaded)).toEqual(['a-old', 'b.png'])
    expect(upload).toHaveBeenCalledTimes(1)
  })

  it('retains failed files for a later retry', async () => {
    const upload = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce('b.png')
    const updates: DeferredUploadItem<string>[][] = []
    const first = await uploadDeferredFiles([item('a'), item('b')], upload, next => updates.push(next))
    expect(first.failed).toBe(true)
    expect(first.items.map(value => value.status)).toEqual(['error', 'done'])
    const second = await uploadDeferredFiles(first.items, vi.fn(async (file: File) => file.name), vi.fn())
    expect(second.failed).toBe(false)
    expect(second.items.every(value => value.status === 'done')).toBe(true)
    expect(updates.length).toBeGreaterThan(0)
  })
})
