import { DomEditor, SlateEditor, SlateTransforms, type IDomEditor } from '@wangeditor-next/editor'

/** Native row handles use authored heights, which differ from rows expanded by wrapped text. */
export function installNoticeTableResize(editor: IDomEditor, root: HTMLElement) {
  const apply = editor.apply
  // wangEditor 5.7 exposes Slate history on the instance, but not HistoryEditor helpers.
  // Its resize observer and hover handlers save transient table metadata as document edits.
  // A disposable history for those operations keeps both undo and redo intact; row heights
  // and columnWidths remain real edits. Do not mutate nodes outside the Slate pipeline.
  const historyEditor = editor as IDomEditor & { history?: { undos: unknown[]; redos: unknown[] } }
  const transient = new Set(['scrollWidth', 'isHoverCellBorder', 'resizingIndex', 'isResizing', 'isHoverRowBorder', 'resizingRowIndex', 'isResizingRow'])
  editor.apply = operation => {
    const history = historyEditor.history
    if (history && operation.type === 'set_node') {
      const node = SlateEditor.node(editor, operation.path)[0]
      const keys = [...Object.keys(operation.properties), ...Object.keys(operation.newProperties)]
      const onlyMeasurement = keys.every(key => transient.has(key) || (key === 'height' && DomEditor.getNodeType(node) === 'table'))
      if (onlyMeasurement) {
        historyEditor.history = { undos: [], redos: [] }
        try { apply(operation) } finally { historyEditor.history = history }
        return
      }
    }
    apply(operation)
  }
  const guide = document.createElement('div')
  guide.className = 'notice-table-row-guide'
  guide.hidden = true
  root.append(guide)
  let cancelDrag: (() => void) | undefined

  const boundary = (event: MouseEvent) => {
    if (editor.isDisabled() || !(event.target instanceof Element)) return undefined
    const table = event.target.closest('.table-container')?.querySelector('table')
    if (!table || !root.contains(table)) return undefined
    const rect = table.getBoundingClientRect()
    if (event.clientX < rect.left || event.clientX > rect.right) return undefined
    return Array.from(table.rows).find(row => Math.abs(event.clientY - row.getBoundingClientRect().bottom) <= 4)
  }
  const showGuide = (row?: HTMLTableRowElement) => {
    guide.hidden = !row
    root.classList.toggle('notice-table-row-hover', Boolean(row))
    if (!row) return
    const rect = row.getBoundingClientRect()
    const parent = root.getBoundingClientRect()
    const viewport = row.closest('.table-container')!.getBoundingClientRect()
    const left = Math.max(rect.left, viewport.left, parent.left)
    const right = Math.min(rect.right, viewport.right, parent.right)
    guide.style.left = `${left - parent.left}px`
    guide.style.top = `${rect.bottom - parent.top}px`
    guide.style.width = `${Math.max(0, right - left)}px`
  }
  const hover = (event: MouseEvent) => {
    if (cancelDrag || event.buttons) return
    const row = boundary(event)
    showGuide(row)
    if (row) event.stopPropagation()
  }
  const leave = () => { if (!cancelDrag) showGuide() }
  const down = (event: MouseEvent) => {
    if (event.button !== 0 || cancelDrag) return
    const row = boundary(event)
    if (!row) return
    event.preventDefault()
    event.stopPropagation()
    const node = DomEditor.toSlateNode(editor, row)
    const path = DomEditor.findPath(editor, node)
    const ref = SlateEditor.pathRef(editor, path)
    editor.select(SlateEditor.start(editor, path))
    const initialY = event.clientY
    const initialHeight = row.getBoundingClientRect().height
    const originalStyle = row.style.height
    const cursor = document.body.style.cursor
    let height = initialHeight
    document.body.style.cursor = 'row-resize'
    const move = (next: MouseEvent) => {
      next.preventDefault()
      next.stopPropagation()
      height = Math.max(30, Math.round(initialHeight + next.clientY - initialY))
      // Preview only; one Slate operation on release preserves a useful undo step.
      row.style.height = `${height}px`
      showGuide(row)
    }
    const finish = (commit: boolean) => {
      window.removeEventListener('mousemove', move, true)
      window.removeEventListener('mouseup', up, true)
      window.removeEventListener('keydown', key, true)
      window.removeEventListener('blur', blur)
      row.style.height = originalStyle
      document.body.style.cursor = cursor
      cancelDrag = undefined
      showGuide()
      const currentPath = ref.unref()
      if (commit && currentPath && Math.abs(height - initialHeight) >= 1 && !editor.isDisabled()) {
        SlateTransforms.setNodes(editor, { height } as Partial<typeof node>, { at: currentPath })
      }
    }
    const up = (next: MouseEvent) => { next.preventDefault(); next.stopPropagation(); finish(true) }
    const key = (next: KeyboardEvent) => {
      if (next.key === 'Escape') { next.preventDefault(); next.stopPropagation(); finish(false) }
    }
    const blur = () => finish(false)
    cancelDrag = () => finish(false)
    window.addEventListener('mousemove', move, true)
    window.addEventListener('mouseup', up, true)
    window.addEventListener('keydown', key, true)
    window.addEventListener('blur', blur)
  }
  root.addEventListener('mousemove', hover, true)
  root.addEventListener('mousedown', down, true)
  root.addEventListener('mouseleave', leave)
  root.addEventListener('scroll', leave, true)
  return () => {
    cancelDrag?.()
    editor.apply = apply
    root.removeEventListener('mousemove', hover, true)
    root.removeEventListener('mousedown', down, true)
    root.removeEventListener('mouseleave', leave)
    root.removeEventListener('scroll', leave, true)
    root.classList.remove('notice-table-row-hover')
    guide.remove()
  }
}
