import type { ConfigProviderProps, ThemeConfig } from 'antd'
import { mergeComponentTokens } from './mergeComponents'

/**
 * 玻璃覆盖层：当自定义背景启用时，向 antd ConfigProvider 的 theme.components
 * 注入半透明取值，使 Card / Modal / Drawer 等组件的背景跟随玻璃态。
 *
 * 约束：只写 `theme.components`，不碰 `theme.token`。antd 的算法会对 seed/map token
 * 做派生计算（colorBgContainerDisabled 等），塞 color-mix() 字符串进去可能解析失败；
 * components 层的值直接输出为 CSS，安全。
 *
 * 合并方式为两级浅合并（components → 组件名 → 字段），不整体替换——
 * cartoon / illustration / serene 各自在 components.Card / components.Table 里存着别的字段。
 */

/** color-mix 工具，与 themeTokens.ts 保持一致 */
const glass = (color: string, pct: number) =>
  `color-mix(in srgb, ${color} ${pct}%, transparent)`

/** 从 token 配置中取 colorBgContainer / colorBgElevated 的值；取不到就用 antd 默认白 */
function extractColors(config: ConfigProviderProps): { bgContainer: string; bgElevated: string } {
  const token = config.theme?.token as Record<string, unknown> | undefined
  return {
    bgContainer: (token?.colorBgContainer as string) || '#ffffff',
    bgElevated: (token?.colorBgElevated as string) || '#ffffff'
  }
}

export function withGlassSurface(
  config: ConfigProviderProps,
  hasBackground: boolean,
  glassOpacity = 60
): ConfigProviderProps {
  if (!hasBackground) return config

  const { bgContainer, bgElevated } = extractColors(config)
  const containerVal = glass(bgContainer, glassOpacity)
  // 弹层分两档，依据是「背后有没有遮罩兜底」：
  // - popup（dropdown / select / menu 等）直接浮在正文上，没有遮罩，必须更实才读得清
  // - overlay（modal / drawer）背后有遮罩做分隔，可以用基础不透明度，否则
  //   85% 的实底只透 15% 的光，backdrop-filter 再怎么调都看不出变化
  const elevatedVal = glass(bgElevated, Math.min(100, glassOpacity + 25))
  const overlayVal = glass(bgElevated, glassOpacity)
  const inputVal = glass(bgContainer, Math.min(100, glassOpacity + 8))
  const headerVal = glass(bgContainer, Math.max(0, glassOpacity - 10))

  const glassComponents: NonNullable<ThemeConfig['components']> = {
    Card: { colorBgContainer: containerVal },
    List: { colorBgContainer: containerVal },
    Collapse: { colorBgContainer: containerVal, headerBg: headerVal },
    Table: { colorBgContainer: containerVal, headerBg: headerVal },
    Modal: { contentBg: overlayVal, headerBg: 'transparent' },
    Drawer: { colorBgElevated: overlayVal },
    // mini-float / top-only 的二级浮层。popupBg 默认取 colorBgElevated 的实色，
    // 不覆盖就是玻璃背景上唯一一块不透明弹层。darkPopupBg 是 antd 写死的 #001529，
    // 同样要盖。
    Menu: { popupBg: elevatedVal, darkPopupBg: elevatedVal },
    Dropdown: { colorBgElevated: elevatedVal },
    Popover: { colorBgElevated: elevatedVal },
    Select: { colorBgElevated: elevatedVal },
    DatePicker: { colorBgElevated: elevatedVal },
    Notification: { colorBgElevated: elevatedVal },
    Message: { colorBgElevated: elevatedVal },
    Input: { colorBgContainer: inputVal },
    InputNumber: { colorBgContainer: inputVal },
    Segmented: { trackBg: inputVal }
  }

  // 两级浅合并：见 mergeComponentTokens 的说明
  return mergeComponentTokens(config, glassComponents)
}
