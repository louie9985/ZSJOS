import { useEffect, useState } from 'react'
import { AppstoreOutlined } from '@ant-design/icons'
import { Icon, loadIcon } from '@iconify/react'

/** 后台菜单配置的 Iconify 图标名 → 渲染组件。无效或加载失败时降级为通用图标。 */
export default function BackendMenuIcon({ icon, className }: { icon?: string; className?: string }) {
  const iconName = icon?.trim()
  const isValidName = Boolean(iconName && /^[a-z0-9-]+:[a-z0-9-]+$/i.test(iconName))
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    let active = true
    setLoaded(false)
    if (!isValidName || !iconName) return () => { active = false }
    loadIcon(iconName)
      .then(() => { if (active) setLoaded(true) })
      .catch(() => { if (active) setLoaded(false) })
    return () => { active = false }
  }, [iconName, isValidName])

  return loaded && iconName
    ? <Icon className={className} icon={iconName} width="1em" height="1em"/>
    : <AppstoreOutlined className={className}/>
}
