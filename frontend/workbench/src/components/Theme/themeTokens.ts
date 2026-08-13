import type { GlobalToken } from 'antd'

/**
 * Tier 1 桥接层：把当前 antd token 映射成 --crm-* CSS 变量。
 *
 * 为什么需要这一层：13 套 preset 各自定义了完整的 antd token，但自有 CSS
 * 取不到 JS 里的 token 值。把 token 桥接成 CSS 变量后，自有样式即可跟随 preset
 * 切换，不必再硬编码颜色。
 *
 * 变量最终写在 :root（见 ThemeVars.tsx），而非 .crm-shell 元素上 ——
 * Modal / Drawer 走 portal 挂到 body，在 .crm-shell 之外，挂 shell 会导致
 * 弹层内 var(--crm-*) 未定义、属性在 computed-value 阶段失效。
 *
 * 纯函数，便于单测断言 preset 切换后的取值。
 */

export interface CrmVarOptions {
  /** 是否启用了自定义渐变背景（BACKGROUND_METAS 中非 'theme' 的项） */
  hasBackground: boolean
  /** 玻璃不透明度 0–100（默认 60），仅 hasBackground 时生效 */
  glassOpacity?: number
}

/**
 * color-mix 工具：将颜色与透明按比例混合。
 * 浏览器基线已满足（项目内 attachment.css 已使用）。
 */
const glass = (color: string, pct: number) =>
  `color-mix(in srgb, ${color} ${pct}%, transparent)`

export function buildCrmVars(token: GlobalToken, options: CrmVarOptions): Record<string, string> {
  const { hasBackground, glassOpacity = 60 } = options
  // 从用户设置的基础不透明度推导各层级
  const containerPct = glassOpacity
  const chromePct = Math.min(100, glassOpacity + 18)
  const elevatedPct = Math.min(100, glassOpacity + 25)
  const borderPct = Math.max(0, glassOpacity - 5)

  return {
    // ---- 主色族 ----
    '--crm-color-primary': token.colorPrimary,
    '--crm-color-primary-hover': token.colorPrimaryHover,
    '--crm-color-primary-bg': token.colorPrimaryBg,
    '--crm-color-primary-border': token.colorPrimaryBorder,

    // ---- 状态色 ----
    '--crm-color-success': token.colorSuccess,
    '--crm-color-warning': token.colorWarning,
    '--crm-color-error': token.colorError,

    // ---- 背景 ----
    // 玻璃态：基于用户设定的 glassOpacity 推导各层级
    '--crm-bg-container': hasBackground ? glass(token.colorBgContainer, containerPct) : token.colorBgContainer,
    '--crm-bg-chrome': hasBackground ? glass(token.colorBgContainer, chromePct) : token.colorBgContainer,
    '--crm-bg-layout': hasBackground ? 'transparent' : token.colorBgLayout,
    '--crm-bg-elevated': hasBackground ? glass(token.colorBgElevated, elevatedPct) : token.colorBgElevated,
    '--crm-bg-mask': token.colorBgMask,

    // ---- 描边 ----
    '--crm-border': hasBackground ? glass(token.colorBorder, borderPct) : token.colorBorderSecondary,
    '--crm-border-strong': token.colorBorder,

    // ---- 文字 ----
    '--crm-text': token.colorText,
    '--crm-text-secondary': token.colorTextSecondary,
    '--crm-text-tertiary': token.colorTextTertiary,

    // ---- 填充（弱背景块：hero、卡片头、区域底色）----
    // 迁移前这一族是 rgba(127,127,127,.035~.08)，按深浅归到 tertiary / secondary
    '--crm-fill-secondary': token.colorFillSecondary,
    '--crm-fill-tertiary': token.colorFillTertiary,
    '--crm-fill-quaternary': token.colorFillQuaternary,

    // ---- 阴影 ----
    '--crm-shadow': token.boxShadowTertiary,
    '--crm-shadow-raised': token.boxShadowSecondary,

    // ---- 控件尺寸 ----
    // 供自有 CSS 与 antd 控件对齐（如顶栏接单状态 Tag 与按钮同高）。
    // 密度切档时 antd 会改这些值，跟着它走即可自动对齐。
    '--crm-control-h': `${token.controlHeight}px`,
    '--crm-control-h-sm': `${token.controlHeightSM}px`
  }
}
