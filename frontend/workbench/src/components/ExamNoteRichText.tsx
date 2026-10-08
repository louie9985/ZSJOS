import { useMemo } from 'react'

const TAGS = new Set('p br h1 h2 h3 h4 h5 h6 strong b em i u s span ul ol li a blockquote table thead tbody tr td th img'.split(' '))
export function safeExamNoteHtml(html: string) {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll('script,style,iframe,object,embed,svg,math,form').forEach(node => node.remove())
  doc.body.querySelectorAll<HTMLElement>('*').forEach(node => {
    if (!TAGS.has(node.tagName.toLowerCase())) { node.replaceWith(...node.childNodes); return }
    for (const attribute of Array.from(node.attributes)) {
      if (!['style', 'href', 'src', 'alt', 'colspan', 'rowspan'].includes(attribute.name)) node.removeAttribute(attribute.name)
    }
    const style = node.style
    const color = style.color, background = style.backgroundColor, align = style.textAlign
    node.removeAttribute('style')
    if (color) node.style.color = color
    if (background) node.style.backgroundColor = background
    if (['left', 'right', 'center', 'justify'].includes(align)) node.style.textAlign = align
  })
  doc.querySelectorAll<HTMLAnchorElement>('a').forEach(link => {
    try {
      if (!['http:', 'https:', 'mailto:'].includes(new URL(link.href, window.location.origin).protocol)) link.removeAttribute('href')
      else { link.target = '_blank'; link.rel = 'noopener noreferrer' }
    } catch { link.removeAttribute('href') }
  })
  doc.querySelectorAll<HTMLImageElement>('img').forEach(image => {
    try {
      if (!['http:', 'https:'].includes(new URL(image.src, window.location.origin).protocol)) image.remove()
      else image.loading = 'lazy'
    } catch { image.remove() }
  })
  doc.querySelectorAll('table').forEach(table => {
    const wrapper = doc.createElement('div'); wrapper.className = 'exam-note-table-scroll'
    table.replaceWith(wrapper); wrapper.append(table)
  })
  return doc.body.innerHTML
}

export default function ExamNoteRichText({ html }: { html: string }) {
  const safe = useMemo(() => safeExamNoteHtml(html), [html])
  return <div className="exam-note-rich-text" dangerouslySetInnerHTML={{ __html: safe }} />
}
