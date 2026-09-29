import { Icon, iconLoaded, loadIcon } from '@iconify/react'
import { useEffect, useState } from 'react'

/**
 * 消息分类图标。
 *
 * 图标名与后端 `NotifyMessageCategory` 的分类编码一一对应；未收录的编码回退为通用图标，
 * 使后端先上线新分类时不会渲染空白。
 */
const CATEGORY_ICONS: Record<string, string> = {
  all: 'ep:menu',
  appeal: 'ep:warning-filled',
  withdrawal: 'ep:wallet',
  lead: 'ep:user-filled',
  system: 'ep:bell-filled'
}

const FALLBACK_ICON = 'ep:bell'

/**
 * `@iconify/react` 默认按需从远端拉取图标数据。列表里每行都渲染图标会把一次请求放大成多次，
 * 未就绪时还会逐个闪烁，因此在模块加载时就预热全部用到的图标；期间先用降级图标占位，
 * 尺寸与最终图标一致，避免加载完成时发生布局位移。
 */
const ICON_NAMES = [...new Set([...Object.values(CATEGORY_ICONS), FALLBACK_ICON])]

Object.values(CATEGORY_ICONS).forEach(icon => {
  // loadIcon 内部缓存 Promise，重复调用不会重复请求；失败时保持降级图标。
  if (!iconLoaded(icon)) void loadIcon(icon).catch(() => undefined)
})

export default function MessageCategoryIcon({
  category,
  className,
  size = 16
}: {
  category?: string | null
  className?: string
  size?: number
}) {
  const icon = (category && CATEGORY_ICONS[category]) || FALLBACK_ICON
  const [ready, setReady] = useState(() => iconLoaded(icon))

  useEffect(() => {
    if (iconLoaded(icon)) {
      setReady(true)
      return
    }
    let active = true
    setReady(false)
    void loadIcon(icon).then(() => { if (active) setReady(true) }).catch(() => undefined)
    return () => { active = false }
  }, [icon])

  return <Icon
    className={className}
    icon={ready ? icon : FALLBACK_ICON}
    width={size}
    height={size}
    aria-hidden
  />
}

/** 供 Segmented 等需要图标节点的场景复用，避免每个调用点重复拼装。 */
export const messageCategoryIconName = (category?: string | null) =>
  (category && CATEGORY_ICONS[category]) || FALLBACK_ICON

export const MESSAGE_CATEGORY_ICON_NAMES = ICON_NAMES
