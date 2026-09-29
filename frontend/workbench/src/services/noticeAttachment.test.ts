import { afterEach, describe, expect, it, vi } from 'vitest'
import { noticeAttachmentResource } from './noticeAttachment'

const file = { infraFileId: 4, fileName: '公告.png', fileSize: 1200, mimeType: 'image/png', sort: 0 }
afterEach(() => vi.unstubAllGlobals())
describe('notice attachment resource', () => {
  it('preserves metadata and signed URL without exposing an internal ID', () => {
    vi.stubGlobal('window', { location: { origin: 'https://work.example' } })
    expect(noticeAttachmentResource({ ...file, downloadUrl: 'https://files.example/a?signature=test' })).toEqual({
      name: '公告.png', size: 1200, type: 'image/png', url: 'https://files.example/a?signature=test',
    })
    expect(noticeAttachmentResource({ ...file, downloadUrl: '/files/a' }).url).toBe('https://work.example/files/a')
  })
  it('does not fall back to expired or removed files', () => {
    expect(() => noticeAttachmentResource()).toThrow('文件不可用')
    expect(() => noticeAttachmentResource(file)).toThrow('文件不可用')
  })
  it.each(['javascript:alert(1)', 'data:text/html,test', 'file:///tmp/a'])('rejects %s', downloadUrl => {
    vi.stubGlobal('window', { location: { origin: 'https://work.example' } })
    expect(() => noticeAttachmentResource({ ...file, downloadUrl })).toThrow('附件地址不可用')
  })
})
