// UTF-8. Normalize only the dimensions that wangEditor otherwise parses as integer pixels.
function dimension(value: string) {
  const match = value.trim().match(/^(\d+(?:\.\d+)?)(px|pt|pc|in|cm|mm|%)?$/i)
  if (!match) return undefined
  const unit = (match[2] || 'px').toLowerCase()
  const factors: Record<string, number> = { px: 1, pt: 96 / 72, pc: 16, in: 96, cm: 96 / 2.54, mm: 96 / 25.4, '%': 1 }
  const valueInPixels = Number(match[1]) * factors[unit]
  return valueInPixels > 0 ? { value: Math.max(1, Math.round(valueInPixels)), percent: unit === '%' } : undefined
}

export function normalizeNoticeTableHtml(html: string): string {
  if (!/<table[\s>]/i.test(html)) return html
  const doc = new DOMParser().parseFromString(html, 'text/html')
  doc.querySelectorAll<HTMLTableElement>('table').forEach(table => {
    const elements = [table, ...table.querySelectorAll<HTMLElement>('col, tr, td, th')]
    for (const element of elements) {
      for (const property of ['width', 'height'] as const) {
        const size = dimension(element.style[property] || element.getAttribute(property) || '')
        if (size && !size.percent) {
          element.style[property] = `${size.value}px`
          if (element.hasAttribute(property)) element.setAttribute(property, String(size.value))
        }
      }
    }
    const widthOf = (element?: HTMLElement) => dimension(element?.style.width || element?.getAttribute('width') || '')
    const tableWidth = widthOf(table)
    const columns = Array.from(table.querySelectorAll<HTMLTableColElement>(':scope > colgroup > col, :scope > col'))
      .flatMap(col => Array.from({ length: col.span }, () => col))
    const cells: { start: number; span: number; width: ReturnType<typeof dimension> }[] = []
    const occupiedUntil: number[] = []
    let count = columns.length
    // A merged heading is a constraint on several columns, not the width of column one.
    Array.from(table.rows).forEach((row, rowIndex) => {
      let column = 0
      Array.from(row.cells).forEach(cell => {
        if (cell.style.display === 'none') return
        while ((occupiedUntil[column] ?? 0) > rowIndex) column++
        cells.push({ start: column, span: cell.colSpan, width: widthOf(cell) })
        for (let offset = 0; offset < cell.colSpan; offset++) {
          occupiedUntil[column + offset] = cell.rowSpan === 0 ? table.rows.length : rowIndex + cell.rowSpan
        }
        column += cell.colSpan
        count = Math.max(count, column)
      })
    })
    if (!count) return
    const sizes = [...columns.map(widthOf), ...cells.map(cell => cell.width)].filter(size => size !== undefined)
    const percentageColumns = sizes.some(size => size.percent)
    // Keep mixed relative/absolute layouts with the native parser rather than inventing a viewport.
    if (percentageColumns && sizes.some(size => !size.percent) && (!tableWidth || tableWidth.percent)) return
    const toWidth = (size: ReturnType<typeof dimension>) => {
      if (!size) return undefined
      return size.percent && tableWidth && !tableWidth.percent ? size.value * tableWidth.value / 100 : size.value
    }
    const widths: (number | undefined)[] = Array.from({ length: count }, (_, index) => toWidth(widthOf(columns[index])))
    for (const cell of cells.filter(cell => cell.span === 1)) widths[cell.start] ??= toWidth(cell.width)
    for (const cell of cells.filter(cell => cell.span > 1).sort((a, b) => a.span - b.span)) {
      const total = toWidth(cell.width)
      if (total === undefined) continue
      const indices = Array.from({ length: cell.span }, (_, offset) => cell.start + offset)
      const missing = indices.filter(index => widths[index] === undefined)
      const remainder = total - indices.reduce((sum, index) => sum + (widths[index] ?? 0), 0)
      if (remainder > 0) missing.forEach(index => { widths[index] = remainder / missing.length })
    }
    const missing = widths.filter(width => width === undefined).length
    const total = percentageColumns && (!tableWidth || tableWidth.percent) ? 100 : tableWidth && !tableWidth.percent ? tableWidth.value : undefined
    const remainder = (total ?? 0) - widths.reduce<number>((sum, width) => sum + (width ?? 0), 0)
    const fallback = missing && remainder > 0 ? remainder / missing : 90
    const group = doc.createElement('colgroup')
    widths.forEach((width, index) => {
      const col = columns[index]?.cloneNode(false) as HTMLTableColElement | undefined || doc.createElement('col')
      col.removeAttribute('span')
      col.style.removeProperty('width')
      col.setAttribute('width', String(Math.max(1, Math.round(width ?? fallback))))
      group.append(col)
    })
    table.querySelectorAll(':scope > colgroup, :scope > col').forEach(col => col.remove())
    table.prepend(group)
  })
  return doc.body.innerHTML
}
