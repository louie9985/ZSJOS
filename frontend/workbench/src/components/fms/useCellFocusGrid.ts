import { useCallback, useRef } from 'react'

/**
 * 分录表格单元格焦点管理 hook。
 *
 * 维护一个 Map<`${rowIndex}:${fieldName}`, HTMLElement | null> 的注册表，
 * 提供 reg(row, field) 回调 ref、moveFocus(dr, dc) 移动焦点、focusCell(row, field) 直接定位。
 */
export function useCellFocusGrid(fields: string[]) {
  const cellMap = useRef<Map<string, HTMLElement | null>>(new Map())
  const currentRow = useRef(0)
  const currentCol = useRef(0)

  /** 生成 Map key */
  const key = (row: number, field: string) => `${row}:${field}`

  /**
   * 注册一个单元格的 ref callback。
   * 由于 antd 6 InputNumber/Select 的 ref 不直接暴露 input，
   * 这里接收包装 div 或原生元素，再通过 querySelector 找到可聚焦元素。
   */
  const reg = useCallback((row: number, field: string) => {
    return (el: HTMLElement | null) => {
      const k = key(row, field)
      if (el) {
        cellMap.current.set(k, el)
      } else {
        cellMap.current.delete(k)
      }
    }
  }, [])

  /** 在给定的 HTMLElement 中查找可聚焦的 input 或 .ant-select-selection-search-input */
  const findFocusable = (el: HTMLElement | null): HTMLElement | null => {
    if (!el) return null
    // 如果本身就是 input
    if (el.tagName === 'INPUT') return el
    // antd Select search input
    const selectInput = el.querySelector<HTMLElement>('.ant-select-selection-search-input')
    if (selectInput) return selectInput
    // antd InputNumber / Input 内的 input
    const input = el.querySelector<HTMLElement>('input')
    if (input) return input
    return el
  }

  /** 定位到指定单元格并聚焦 */
  const focusCell = useCallback((row: number, field: string) => {
    const k = key(row, field)
    const el = cellMap.current.get(k) ?? null
    const focusable = findFocusable(el)
    if (focusable && 'focus' in focusable) {
      ;(focusable as HTMLElement).focus()
      currentRow.current = row
      currentCol.current = fields.indexOf(field)
    }
  }, [fields])

  /** 从当前焦点格相对移动 dr 行 dc 列 */
  const moveFocus = useCallback((dr: number, dc: number, rowCount: number) => {
    let newRow = currentRow.current + dr
    let newCol = currentCol.current + dc

    // 边界约束
    if (newRow < 0) newRow = 0
    if (newRow >= rowCount) newRow = rowCount - 1
    if (newCol < 0) newCol = 0
    if (newCol >= fields.length) newCol = fields.length - 1

    const field = fields[newCol]
    if (field) {
      focusCell(newRow, field)
    }
  }, [fields, focusCell])

  /** 从当前位置跳到下一个有效输入格（按行优先顺序） */
  const focusNext = useCallback((rowCount: number) => {
    let row = currentRow.current
    let col = currentCol.current + 1

    // 先在同行后面的列找
    while (row < rowCount) {
      while (col < fields.length) {
        const k = key(row, fields[col])
        const el = cellMap.current.get(k) ?? null
        const focusable = findFocusable(el)
        if (focusable) {
          currentRow.current = row
          currentCol.current = col
          ;(focusable as HTMLElement).focus()
          return true
        }
        col++
      }
      col = 0
      row++
    }
    return false
  }, [fields])

  /**
   * 从 keydown 事件推断当前聚焦的单元格（row, col），
   * 更新内部 currentRow/currentCol 状态。
   */
  const syncCurrentFromEvent = useCallback((e: React.KeyboardEvent | KeyboardEvent, rowCount: number) => {
    const target = e.target as HTMLElement
    // 逆查 cellMap 找到当前聚焦的格
    for (const [k, el] of cellMap.current.entries()) {
      if (!el) continue
      if (el === target || el.contains(target)) {
        const [rowStr, field] = k.split(':')
        const row = Number(rowStr)
        const col = fields.indexOf(field)
        if (row >= 0 && row < rowCount && col >= 0) {
          currentRow.current = row
          currentCol.current = col
        }
        break
      }
    }
  }, [fields])

  /**
   * 统一的 keydown 事件处理器，在 table 外层 div 上挂载。
   * 返回 { handled, action? } 告知调用方是否需要额外处理。
   */
  const handleKeyDown = useCallback((
    e: React.KeyboardEvent,
    rowCount: number
  ): { handled: boolean; action?: 'balance' | 'save' } => {
    // 不处理中文输入期间的按键
    if (e.nativeEvent.isComposing) return { handled: false }

    const target = e.target as HTMLElement
    // Select 下拉展开中不拦截方向键/Enter
    if (target.getAttribute('aria-expanded') === 'true') return { handled: false }

    // 先同步当前焦点位置
    syncCurrentFromEvent(e, rowCount)

    switch (e.key) {
      case 'ArrowUp':
        e.preventDefault()
        moveFocus(-1, 0, rowCount)
        return { handled: true }
      case 'ArrowDown':
        e.preventDefault()
        moveFocus(1, 0, rowCount)
        return { handled: true }
      case 'ArrowLeft':
        e.preventDefault()
        moveFocus(0, -1, rowCount)
        return { handled: true }
      case 'ArrowRight':
        e.preventDefault()
        moveFocus(0, 1, rowCount)
        return { handled: true }
      case 'Tab':
        e.preventDefault()
        if (e.shiftKey) {
          moveFocus(0, -1, rowCount)
        } else {
          // Tab 跳到下一个有效格
          if (!focusNext(rowCount)) {
            moveFocus(0, 1, rowCount)
          }
        }
        return { handled: true }
      case 'Enter':
        e.preventDefault()
        focusNext(rowCount)
        return { handled: true }
      case '=': {
        // 只在金额列触发配平
        const field = fields[currentCol.current]
        if (field === 'debitAmount' || field === 'creditAmount') {
          e.preventDefault()
          return { handled: true, action: 'balance' }
        }
        return { handled: false }
      }
      case 'F12':
        e.preventDefault()
        return { handled: true, action: 'save' }
      default:
        return { handled: false }
    }
  }, [fields, moveFocus, focusNext, syncCurrentFromEvent])

  return { reg, focusCell, moveFocus, focusNext, handleKeyDown, currentRow, currentCol }
}
