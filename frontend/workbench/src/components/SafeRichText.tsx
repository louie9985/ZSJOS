import { useMemo } from 'react'

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
  return <div className={announcementTables ? 'announcement-rich-text announcement-rich-text--tables' : 'announcement-rich-text'} dangerouslySetInnerHTML={{ __html: safeHtml }}/>
}
