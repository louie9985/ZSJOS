import { useMemo, useState, type MouseEvent, type KeyboardEvent } from 'react'
import { Image } from 'antd'

const SAFE_PROTOCOLS = new Set(['http:', 'https:', 'mailto:'])

export function sanitizeRichText(html: string, announcementTables = false) {
  const document = new DOMParser().parseFromString(html, 'text/html')
  document.querySelectorAll('script,iframe,object,embed,form').forEach(node => node.remove())
  document.querySelectorAll<HTMLElement>('*').forEach(node => {
    for (const attribute of Array.from(node.attributes)) {
      if (attribute.name.toLowerCase().startsWith('on')) node.removeAttribute(attribute.name)
    }
  })
  document.querySelectorAll<HTMLAnchorElement>('a[href]').forEach(link => {
    try {
      const url = new URL(link.href, window.location.origin)
      if (!SAFE_PROTOCOLS.has(url.protocol)) link.removeAttribute('href')
      else { link.target = '_blank'; link.rel = 'noopener noreferrer' }
    } catch { link.removeAttribute('href') }
  })
  document.querySelectorAll<HTMLImageElement>('img[src]').forEach(image => {
    try {
      const url = new URL(image.src, window.location.origin)
      if (!SAFE_PROTOCOLS.has(url.protocol) && url.protocol !== 'data:') image.removeAttribute('src')
      image.loading = 'lazy'
      if (announcementTables && image.getAttribute('src')) {
        image.tabIndex = 0
        image.setAttribute('role', 'button')
        image.setAttribute('aria-label', `预览图片：${image.alt || '公告图片'}`)
        image.style.cursor = 'zoom-in'
      }
    } catch { image.removeAttribute('src') }
  })
  // Keep wide tables inside the reading surface without squeezing their columns.
  // This wrapper is a display projection; the saved editor HTML stays unchanged.
  if (announcementTables) document.querySelectorAll('table').forEach(table => {
    const pixelWidth = (node: HTMLElement) => {
      const width = node.style.width || node.getAttribute('width') || ''
      return /^\d+(?:\.\d+)?(?:px)?$/.test(width) ? Number.parseFloat(width) : undefined
    }
    // CSS width alone is only a preference in automatic table layout. Keep authored
    // pixel columns as a lower bound, including wangEditor's exported colgroup.
    const columns = Array.from(table.querySelectorAll<HTMLTableColElement>(':scope > colgroup > col, :scope > col'))
    let minimumWidth = pixelWidth(table) || 0
    if (columns.length) {
      minimumWidth = Math.max(minimumWidth, columns.reduce((sum, col) => sum + (pixelWidth(col) ?? 80) * col.span, 0))
    }
    Array.from(table.rows).forEach(row => {
      let rowWidth = 0
      Array.from(row.cells).forEach(cell => {
        const width = pixelWidth(cell)
        if (width != null && !cell.style.minWidth) cell.style.minWidth = `${width}px`
        rowWidth += width ?? 80 * cell.colSpan
      })
      if (!columns.length) minimumWidth = Math.max(minimumWidth, rowWidth)
    })
    if (minimumWidth && !table.style.minWidth) table.style.minWidth = `${minimumWidth}px`
    const wrapper = document.createElement('div')
    wrapper.className = 'announcement-table-scroll'
    wrapper.tabIndex = 0
    wrapper.setAttribute('role', 'region')
    wrapper.setAttribute('aria-label', '正文表格，可横向滚动')
    table.replaceWith(wrapper)
    wrapper.append(table)
  })
  return document.body.innerHTML
}

export default function SafeRichText({ html, announcementTables = false }: { html: string; announcementTables?: boolean }) {
  const safeHtml = useMemo(() => sanitizeRichText(html, announcementTables), [html, announcementTables])
  return <RichTextContent key={safeHtml} safeHtml={safeHtml} announcementTables={announcementTables} />
}

function RichTextContent({ safeHtml, announcementTables }: { safeHtml: string; announcementTables: boolean }) {
  const [preview, setPreview] = useState<{ items: string[]; current: number }>()
  const openImage = (event: MouseEvent<HTMLDivElement> | KeyboardEvent<HTMLDivElement>) => {
    if (!announcementTables || !(event.target instanceof HTMLImageElement) || !event.target.getAttribute('src')) return
    if ('key' in event && event.key !== 'Enter' && event.key !== ' ') return
    const images = Array.from(event.currentTarget.querySelectorAll<HTMLImageElement>('img[src]')).filter(image => image.getAttribute('src'))
    event.preventDefault()
    event.stopPropagation()
    setPreview({ items: images.map(image => image.currentSrc || image.src), current: images.indexOf(event.target) })
  }
  return <>
    <div className={announcementTables ? 'announcement-rich-text announcement-rich-text--tables' : 'announcement-rich-text'} onClick={openImage} onKeyDown={openImage} dangerouslySetInnerHTML={{ __html: safeHtml }} />
    {preview && <Image.PreviewGroup items={preview.items} preview={{ open: true, current: preview.current, onChange: current => setPreview({ ...preview, current }), onOpenChange: open => { if (!open) setPreview(undefined) } }} />}
  </>
}
