import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Button, Dropdown } from 'antd'
import { MoreOutlined } from '@ant-design/icons'

/* ========== 类型 ========== */

export interface ToolbarAction {
  key: string
  icon: React.ReactNode
  label: string
  disabled?: boolean
  danger?: boolean
  onClick: () => void
}

interface OverflowToolbarProps {
  actions: ToolbarAction[]
  className?: string
}

/* ========== 组件 ========== */

/**
 * 自适应溢出工具条：默认展示所有按钮，宽度不足时末尾按钮自动收进「更多」下拉。
 * 使用 ResizeObserver 监听容器宽度变化，计算可见/溢出分界点。
 */
export default function OverflowToolbar({ actions, className }: OverflowToolbarProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const measureRef = useRef<HTMLDivElement>(null)
  const [visibleCount, setVisibleCount] = useState(actions.length)

  // 「更多」按钮固定预估宽度（icon + padding + border-left）
  const MORE_BTN_WIDTH = 52

  const recalc = useCallback(() => {
    const container = containerRef.current
    const measure = measureRef.current
    if (!container || !measure) return

    const available = container.offsetWidth
    const children = measure.children
    let sum = 0
    let fit = 0

    for (let i = 0; i < children.length; i++) {
      const w = (children[i] as HTMLElement).offsetWidth
      // 如果加上这个按钮后还能放下全部（或者这是最后一个按钮且不需要「更多」按钮），计入
      const needsMore = i < children.length - 1 || sum + w > available
      const threshold = needsMore && i < actions.length - 1
        ? available - MORE_BTN_WIDTH
        : available

      if (sum + w <= threshold) {
        sum += w
        fit++
      } else {
        break
      }
    }

    // 如果全部放得下就展示全部
    if (fit >= actions.length) fit = actions.length
    setVisibleCount(fit)
  }, [actions.length])

  // ResizeObserver
  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const ro = new ResizeObserver(recalc)
    ro.observe(el)
    return () => ro.disconnect()
  }, [recalc])

  // actions 变化时重新计算
  useLayoutEffect(recalc, [actions, recalc])

  const visible = actions.slice(0, visibleCount)
  const overflow = actions.slice(visibleCount)

  const overflowMenuItems = overflow.map(a => ({
    key: a.key,
    icon: a.icon,
    label: a.label,
    disabled: a.disabled,
    danger: a.danger,
    onClick: a.onClick,
  }))

  return (
    <div ref={containerRef} className={className || 'lead-action-toolbar'}>
      {/* 隐藏的测量行：渲染所有按钮但不可见，用于获取真实宽度 */}
      <div ref={measureRef} className="lead-toolbar-measure" aria-hidden>
        {actions.map(a => (
          <Button key={a.key} size="small" className={`lead-action-btn${a.danger ? ' lead-action-danger' : ''}`} icon={a.icon}>{a.label}</Button>
        ))}
      </div>

      {/* 实际可见按钮 */}
      {visible.map(a => (
        <Button
          key={a.key}
          size="small"
          className={`lead-action-btn${a.danger ? ' lead-action-danger' : ''}`}
          icon={a.icon}
          disabled={a.disabled}
          onClick={a.onClick}
        >
          {a.label}
        </Button>
      ))}

      {/* 溢出下拉 */}
      {overflow.length > 0 && (
        <Dropdown menu={{ items: overflowMenuItems }} trigger={['click']}>
          <Button size="small" className="lead-more-btn" icon={<MoreOutlined />}>更多</Button>
        </Dropdown>
      )}
    </div>
  )
}
