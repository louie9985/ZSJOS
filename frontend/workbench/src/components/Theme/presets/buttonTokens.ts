/**
 * 按钮 token 结构统一工具。
 *
 * 13 套 preset 的按钮应保持一致的结构性属性（无阴影、fontWeight 500、default 六件套），
 * 但颜色由各 preset 的中性色面定义。需要保留特色的（mui 的 Material 阴影、bootstrap
 * 的渐变等）通过 override 参数覆写回去。
 *
 * 不碰 `muiTheme` / `illustrationTheme` / `cartoonTheme` / `glassTheme`——
 * 它们有 antd-style 的 className 层，token 层改动会与之打架。
 */

export interface NeutralSurface {
  /** default 按钮底色 */
  bg: string
  /** default 按钮边框色 */
  border: string
  /** default 按钮文字色 */
  color: string
  /** hover 底色 */
  hoverBg: string
  /** hover 边框色 */
  hoverBorder: string
  /** hover 文字色（省略则与 color 相同） */
  hoverColor?: string
  /** active 底色 */
  activeBg: string
  /** active 边框色 */
  activeBorder: string
}

export interface ButtonTokens {
  primaryShadow: string
  defaultShadow: string
  dangerShadow: string
  fontWeight: number
  defaultBg: string
  defaultBorderColor: string
  defaultColor: string
  defaultHoverBg: string
  defaultHoverBorderColor: string
  defaultHoverColor: string
  defaultActiveBg: string
  defaultActiveBorderColor: string
  [key: string]: unknown
}

/**
 * 从中性色面构建统一结构的 Button component token。
 * @param neutral 该 preset 的中性色面（bg/border/color/hover/active）
 * @param override 需要保留的特色字段（如 mui 的阴影）
 */
export function buildButtonTokens(neutral: NeutralSurface, override?: Partial<ButtonTokens>): ButtonTokens {
  return {
    primaryShadow: '0 2px 0 rgba(0, 0, 0, 0.04)',
    defaultShadow: 'none',
    dangerShadow: 'none',
    fontWeight: 500,
    defaultBg: neutral.bg,
    defaultBorderColor: neutral.border,
    defaultColor: neutral.color,
    defaultHoverBg: neutral.hoverBg,
    defaultHoverBorderColor: neutral.hoverBorder,
    defaultHoverColor: neutral.hoverColor ?? neutral.color,
    defaultActiveBg: neutral.activeBg,
    defaultActiveBorderColor: neutral.activeBorder,
    ...override
  }
}
