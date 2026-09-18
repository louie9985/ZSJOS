import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { attachmentKind, AttachmentTypeIcon } from './AttachmentCard'

describe('attachment visual types', () => {
  it.each([['访谈.MD', 'document'], ['照片.PNG', 'image'], ['记录.pdf', 'pdf'], ['合同.docx', 'document'], ['表格.xlsx', 'sheet'], ['方案.pptx', 'slides'], ['声音.mp3', 'audio'], ['访谈.mp4', 'video'], ['材料.7z', 'archive'], ['无扩展名', 'file']] as const)('%s uses %s', (name, kind) => {
    expect(attachmentKind(name)).toBe(kind)
    const html = renderToStaticMarkup(<AttachmentTypeIcon kind={kind} extension={name.split('.').pop()!} />)
    expect(html).toContain('<svg'); expect(html).toContain(`kind-${kind}`); expect(html).toContain('aria-label=')
  })
  it('recognizes a media MIME when the filename lacks an extension', () => {
    expect(attachmentKind('确认凭证', 'audio/wav')).toBe('audio')
  })
})
