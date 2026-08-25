import type { ConfigProviderProps, ThemeConfig } from 'antd'
import { DENSITY_SCALE, FONT_SCALE_TABLE, type Density, type FontScale } from '../../constants'
import { mergeComponentTokens } from './mergeComponents'

/**
 * 尺度桥接层：把密度档与字号档注入 antd 组件的 token。
 *
 * 为什么需要这一层：自有 CSS 靠 `--crm-*` 变量跟随档位切换，但 antd 组件
 * 完全读不到 CSS 变量 —— 它们的间距走 JS 侧的 `theme.components`。
 * 没有这一层时，切「密度」「字号」只有自绘面板会动，antd 的表格和卡片纹丝不动，
 * 同一屏里呈现两种尺度语言。
 *
 * 只写 `theme.components`，不碰 `theme.token`（与 glassSurface 同样的约束：
 * antd 算法会对 seed token 做派生，改 token 层影响面不可控）。
 *
 * ⚠️ 必须与 withGlassSurface 共用 mergeComponentTokens 的浅合并，
 * 且在 ThemeProvider 里由 withGlassSurface 包在外层 —— 两者都写
 * components.Table / components.Card，整体替换会互相冲掉字段。
 */
export function withScale(
  config: ConfigProviderProps,
  density: Density,
  fontScale: FontScale
): ConfigProviderProps {
  const space = DENSITY_SCALE[density]
  const cell = FONT_SCALE_TABLE[fontScale]

  const scaleComponents: NonNullable<ThemeConfig['components']> = {
    // 卡片：与自绘卡片的 --crm-card-pad 取同一真值，消除 14px / 12px / 24px 三套并存。
    // header 与 body 用同一档，否则标题区与内容区的左右留白对不齐。
    Card: {
      bodyPadding: space.cardPad,
      bodyPaddingSM: space.cardPadSM,
      headerPadding: space.cardPad,
      headerPaddingSM: space.cardPadSM
    },
    // 表格：antd 把这两个值写死为 8 / 16，不随 fontSize 派生，
    // 于是切字号档时字变大而行高不动。这里按档位显式给出。
    Table: {
      cellPaddingBlock: cell.cellBlock,
      cellPaddingInline: cell.cellInline,
      cellPaddingBlockSM: Math.max(2, cell.cellBlock - 2),
      cellPaddingInlineSM: Math.max(4, cell.cellInline - 4)
    }
  }

  return mergeComponentTokens(config, scaleComponents)
}
