import { describe, expect, it, vi } from 'vitest'
vi.mock('./api', () => ({ http: { post: vi.fn() }, unwrap: (value: unknown) => value }))
import { http } from './api'
import { examAttachmentError, examAttachmentItems, examScheduleAttachments } from './examScheduleAttachments'

describe('exam attachments', () => {
  it('accepts supported names and rejects empty, oversized and executable files', () => {
    for (const name of ['官方通知.PNG', '通知.pdf', '公告.docx', '安排.xlsx', '课件.ppt'])
      expect(examAttachmentError({ name, size: 10 })).toBeUndefined()
    expect(examAttachmentError({ name: 'boundary.zip.pdf', size: 100 * 1024 * 1024 })).toBeUndefined()
    for (const file of [{ name: 'x.html', size: 1 }, { name: 'x.pdf', size: 0 }, { name: 'x.jpg', size: 100 * 1024 * 1024 + 1 }])
      expect(examAttachmentError(file)).toBeTruthy()
  })
  it('retains stable IDs and display metadata for editing without reupload', () => {
    const file = { fileId: 12, name: '通知.pdf', type: 'application/pdf', size: 8, url: 'https://example.test/a.pdf' }
    expect(examAttachmentItems([file])).toEqual([{ uid: '12', name: file.name, type: file.type,
      size: 8, url: file.url, status: 'done', uploaded: file }])
  })
  it('uploads through the exam service and rejects invalid files before transport', async () => {
    const file = new File(['notice'], '通知.pdf', { type: 'application/pdf' })
    await examScheduleAttachments.upload(file)
    expect(http.post).toHaveBeenCalledWith('/zsjos/exam-calendar/attachment/upload', expect.any(FormData))
    await expect(examScheduleAttachments.upload(new File(['x'], 'bad.exe'))).rejects.toThrow()
    expect(http.post).toHaveBeenCalledTimes(1)
  })
})
