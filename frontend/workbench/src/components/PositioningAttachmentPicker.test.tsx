import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import PositioningAttachmentPicker, { POSITIONING_ATTACHMENT_ACCEPT, type AttachmentItem } from './PositioningAttachmentPicker'

const download = async () => ({ url: 'https://files.test/a' })

describe('positioning attachment picker', () => {
  it('accepts documents, images, audio and video in one control', () => {
    const accept = POSITIONING_ATTACHMENT_ACCEPT.split(',')
    for (const extension of ['.pdf', '.docx', '.txt', '.png', '.mp3', '.wav', '.mp4', '.mov']) {
      expect(accept).toContain(extension)
    }
    // 可执行类扩展名永远不在白名单里。
    for (const extension of ['.sh', '.exe', '.html', '.svg', '.js']) expect(accept).not.toContain(extension)
  })

  it('renders uploaded attachments with a type icon, name and download action', () => {
    const items: AttachmentItem[] = [
      { key: 'file-1', name: '访谈稿.pdf', type: 'application/pdf', size: 2048, uploaded: { id: 1, name: '访谈稿.pdf' } },
      { key: 'file-2', name: '口播.mp3', type: 'audio/mpeg', size: 4096, uploaded: { id: 2, name: '口播.mp3' } },
    ]
    const html = renderToStaticMarkup(<PositioningAttachmentPicker items={items} onChange={() => undefined} onDownload={download} />)
    expect(html.match(/class="positioning-attachment-item"/g)).toHaveLength(2)
    expect(html).toContain('访谈稿.pdf')
    expect(html).toContain('口播.mp3')
    expect(html).toContain('已上传')
    expect(html).toContain('kind-pdf')
    expect(html).toContain('kind-audio')
    expect(html).toContain('继续添加附件')
  })

  it('labels not-yet-persisted files as pending and blocks downloading them', () => {
    const items: AttachmentItem[] = [{ key: 'pending-1', name: '新稿.png', type: 'image/png', size: 1024,
      pending: { uid: '1', file: new File(['x'], '新稿.png', { type: 'image/png' }) } }]
    const html = renderToStaticMarkup(<PositioningAttachmentPicker items={items} onChange={() => undefined} onDownload={download} />)
    expect(html).toContain('待保存')
    expect(html).toMatch(/aria-label="下载 新稿\.png"[^>]*disabled=""/)
  })

  it('renders an empty state and disables the trigger while busy', () => {
    const html = renderToStaticMarkup(<PositioningAttachmentPicker items={[]} onChange={() => undefined} onDownload={download} busy />)
    expect(html).toContain('暂无附件')
    expect(html).toContain('上传附件')
    expect(html).toContain('disabled')
  })
})
