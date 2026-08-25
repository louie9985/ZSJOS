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
  /** 玻璃背景模糊半径 0–40px（默认 20），仅 hasBackground 时生效 */
  glassBlur?: number
}

/**
 * color-mix 工具：将颜色与透明按比例混合。
 * 浏览器基线已满足（项目内 attachment.css 已使用）。
 */
const glass = (color: string, pct: number) =>
  `color-mix(in srgb, ${color} ${pct}%, transparent)`

/**
 * 玻璃边缘高光的「无」值。
 *
 * 不能用 `none`：该变量会被拼进 `box-shadow: var(--crm-glass-edge), var(--crm-shadow-card)`
 * 这样的阴影列表，而 `none` 不是合法的列表成员，整条声明会被丢弃。
 * 用一个零尺寸透明阴影占位，语义等价且始终合法。
 */
const NO_EDGE = 'inset 0 0 0 0 transparent'

/**
 * 导航栏 / 侧栏底色在容器色里掺入的布局色比例（%）。
 *
 * 表面色阶的设计意图是 layout → sunken → chrome → container → elevated 五级，
 * 但 chrome 此前直接取 colorBgContainer，与卡片同为纯白，两级塌成一个色。
 * 掺一点布局灰把这一级拉开：浅色主题下 #ffffff → 约 #fafafb，暗色主题下同理
 * 略微压暗（colorBgLayout 比 container 深），方向一致。
 *
 * 调大 = 导航栏更灰，调小 = 更接近纯白。
 */
const CHROME_TINT = 30

export function buildCrmVars(token: GlobalToken, options: CrmVarOptions): Record<string, string> {
  const { hasBackground, glassOpacity = 60, glassBlur = 20 } = options
  // 从用户设置的基础不透明度推导各层级
  const containerPct = glassOpacity
  const chromePct = Math.min(100, glassOpacity + 18)
  const elevatedPct = Math.min(100, glassOpacity + 25)
  // overlay（modal / drawer）背后有遮罩兜底，可以更透，让 backdrop-filter 看得见
  const overlayPct = Math.min(100, glassOpacity + 5)
  const borderPct = Math.max(0, glassOpacity - 5)

  // 背景模糊：面板用基础半径，浮层（modal / dropdown 等）加深 4px 以拉开层次。
  // 非玻璃态一律 0 —— 纯色背景下模糊没有可透视的内容，只会白白多出合成层。
  const blur = hasBackground ? Math.max(0, glassBlur) : 0
  // 边缘高光透明度随模糊增强：模糊越强，玻璃「片」越厚，轮廓也该越明显。
  const edgeAlpha = Math.round((0.12 + (blur / 40) * 0.28) * 100) / 100

  // 导航栏底色：掺灰后的容器色。玻璃态下再按 chromePct 兑透明（color-mix 可嵌套）。
  const chromeBase = `color-mix(in srgb, ${token.colorBgContainer} ${100 - CHROME_TINT}%, ${token.colorBgLayout})`

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

    // ---- 状态色的淡背景与描边 ----
    // 取 antd 派生的 Bg / Border 色阶，而非在业务侧用 color-mix 手搓百分比：
    // 手搓的比例是按浅色底调的，暗色 preset 下 8% 的红压在深色底上几乎不可见。
    '--crm-color-success-bg': token.colorSuccessBg,
    '--crm-color-success-border': token.colorSuccessBorder,
    '--crm-color-warning-bg': token.colorWarningBg,
    '--crm-color-warning-border': token.colorWarningBorder,
    '--crm-color-error-bg': token.colorErrorBg,
    '--crm-color-error-border': token.colorErrorBorder,

    // ---- 表面色阶 ----
    '--crm-bg-container': hasBackground ? glass(token.colorBgContainer, containerPct) : token.colorBgContainer,
    '--crm-bg-chrome': hasBackground ? glass(chromeBase, chromePct) : chromeBase,
    '--crm-bg-layout': hasBackground ? 'transparent' : token.colorBgLayout,
    '--crm-bg-elevated': hasBackground ? glass(token.colorBgElevated, elevatedPct) : token.colorBgElevated,
    // overlay：有遮罩隔离的大面板（modal / drawer），比 elevated 更透以配合 backdrop-filter
    '--crm-bg-overlay': hasBackground ? glass(token.colorBgElevated, overlayPct) : token.colorBgElevated,
    '--crm-bg-sunken': hasBackground ? glass(token.colorFillQuaternary, containerPct) : token.colorFillQuaternary,
    '--crm-bg-mask': hasBackground ? glass(token.colorBgMask, 55) : token.colorBgMask,

    // ---- 描边（结构分隔线用）----
    '--crm-border': hasBackground ? glass(token.colorBorder, borderPct) : token.colorBorderSecondary,
    '--crm-border-strong': token.colorBorder,

    // ---- 文字 ----
    '--crm-text': token.colorText,
    '--crm-text-secondary': token.colorTextSecondary,
    '--crm-text-tertiary': token.colorTextTertiary,

    // ---- 填充 ----
    '--crm-fill-secondary': token.colorFillSecondary,
    '--crm-fill-tertiary': token.colorFillTertiary,
    '--crm-fill-quaternary': token.colorFillQuaternary,

    // ---- 阴影：elevation 体系 ----
    '--crm-shadow': token.boxShadowTertiary,
    '--crm-shadow-card': token.boxShadow,
    '--crm-shadow-raised': token.boxShadowSecondary,
    '--crm-shadow-float': token.boxShadow,
    '--crm-shadow-inset': `inset 0 2px 4px 0 rgba(0, 0, 0, ${hasBackground ? '0.08' : '0.05'})`,

    // ---- 玻璃：模糊半径与边缘高光 ----
    // 供 CSS 的 backdrop-filter / box-shadow 取用；blur 为 0 时 ThemeVars 会额外
    // 打上 data-crm-glass="flat"，由选择器整条跳过 backdrop-filter，而不是下发 blur(0px)
    // —— 后者仍会让浏览器为每个命中元素创建合成层。
    '--crm-glass-blur': `${blur}px`,
    '--crm-glass-blur-strong': `${blur > 0 ? blur + 4 : 0}px`,
    '--crm-glass-edge': blur > 0 ? `inset 0 1px 0 0 rgba(255, 255, 255, ${edgeAlpha})` : NO_EDGE,

    // ---- 控件尺寸 ----
    '--crm-control-h': `${token.controlHeight}px`,
    '--crm-control-h-sm': `${token.controlHeightSM}px`
  }
}
