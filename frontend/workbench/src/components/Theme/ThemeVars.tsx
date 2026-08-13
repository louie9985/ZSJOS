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
  const { backgroundValue, density, fontScale, borderRadius, animation, glassOpacity } = useTheme()
  const hasBackground = Boolean(backgroundValue)

  useLayoutEffect(() => {
    const root = document.documentElement
    const vars = buildCrmVars(token, { hasBackground, glassOpacity })
    Object.entries(vars).forEach(([name, value]) => root.style.setProperty(name, value))
    // 卸载时移除，避免残留覆盖 tokens.css 里的静态兜底值
    return () => Object.keys(vars).forEach(name => root.style.removeProperty(name))
  }, [token, hasBackground, glassOpacity])

  // 密度、字号、圆角、动画、背景状态以 data 属性驱动
  useLayoutEffect(() => {
    const root = document.documentElement
    root.dataset.crmDensity = density
    root.dataset.crmFont = fontScale
    root.dataset.crmRadius = borderRadius
    root.dataset.crmBg = hasBackground ? 'custom' : 'theme'
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
      delete root.dataset.crmNoMotion
    }
  }, [density, fontScale, borderRadius, animation, hasBackground])

  return null
}

export default ThemeVars
