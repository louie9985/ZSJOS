import { describe, expect, it } from 'vitest'
import { getAnnouncementAttachmentKind } from './AnnouncementAttachmentIcon'

describe('getAnnouncementAttachmentKind', () => {
  it('maps common attachments to native file kinds', () => {
    expect(getAnnouncementAttachmentKind('report.xlsx')).toBe('excel')
    expect(getAnnouncementAttachmentKind('proposal.docx')).toBe('word')
    expect(getAnnouncementAttachmentKind('slides.pptx')).toBe('powerpoint')
    expect(getAnnouncementAttachmentKind('manual.pdf')).toBe('pdf')
    expect(getAnnouncementAttachmentKind('bundle.zip')).toBe('archive')
  })

  it('uses mime type and filename fallbacks', () => {
    expect(getAnnouncementAttachmentKind('photo', 'image/png')).toBe('image')
    expect(getAnnouncementAttachmentKind('notes.md')).toBe('text')
    expect(getAnnouncementAttachmentKind('unknown.bin')).toBe('unknown')
  })
})
