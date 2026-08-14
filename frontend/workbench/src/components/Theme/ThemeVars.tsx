import { useLayoutEffect } from 'react'
import { theme } from 'antd'
import { useTheme } from './ThemeContext'
import { buildCrmVars } from './themeTokens'

/**
 * 把当前 antd token 写成 :root 上的 --crm-* 变量。
 *
 * 必须挂在 ConfigProvider 内部（才能通过 useToken 读到合并后的 preset token），
 * 同时挂在 ThemeStateContext 内部（读 backgroundValue 判断玻璃态）。
 * 二者都由 ThemeProvider 满足，故本组件由 ThemeProvider 自动挂载，无需业务代码引入。
 *
 * 写 :root 而非 shell 元素，使 Modal / Drawer 等 portal 内容也能取到变量。
 */
const ThemeVars = () => {
  const { token } = theme.useToken()
  const { backgroundValue, density, fontScale, borderRadius, animation, glassOpacity, glassBlur } = useTheme()
  const hasBackground = Boolean(backgroundValue)
  // 背景模糊是否真正生效：需同时有自定义背景与非零半径
  const frosted = hasBackground && glassBlur > 0

  useLayoutEffect(() => {
    const root = document.documentElement
    const vars = buildCrmVars(token, { hasBackground, glassOpacity, glassBlur })
    Object.entries(vars).forEach(([name, value]) => root.style.setProperty(name, value))
    // 卸载时移除，避免残留覆盖 tokens.css 里的静态兜底值
    return () => Object.keys(vars).forEach(name => root.style.removeProperty(name))
  }, [token, hasBackground, glassOpacity, glassBlur])

  // 密度、字号、圆角、动画、背景状态以 data 属性驱动
  useLayoutEffect(() => {
    const root = document.documentElement
    root.dataset.crmDensity = density
    root.dataset.crmFont = fontScale
    root.dataset.crmRadius = borderRadius
    root.dataset.crmBg = hasBackground ? 'custom' : 'theme'
    // flat 时 CSS 整条跳过 backdrop-filter：blur(0px) 依然会为每个命中元素
    // 建合成层，列表页一屏几十个卡片时代价可观。这也是低配设备的关闭开关。
    root.dataset.crmGlass = frosted ? 'frosted' : 'flat'
    if (!animation) {
      root.dataset.crmNoMotion = ''
    } else {
      delete root.dataset.crmNoMotion
    }
    return () => {
      delete root.dataset.crmDensity
      delete root.dataset.crmFont
      delete root.dataset.crmRadius
      delete root.dataset.crmBg
      delete root.dataset.crmGlass
      delete root.dataset.crmNoMotion
    }
  }, [density, fontScale, borderRadius, animation, hasBackground, frosted])

  return null
}

export default ThemeVars
