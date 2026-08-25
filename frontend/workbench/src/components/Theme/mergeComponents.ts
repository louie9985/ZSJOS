import type { ConfigProviderProps, ThemeConfig } from 'antd'

/**
 * 两级浅合并：`components` → 组件名 → 字段。
 *
 * 由 glassSurface（玻璃态配色）与 scaleTokens（密度/字号尺度）共用。
 * 两者都要写 `components.Table` 与 `components.Card`，若任一方整体替换组件对象，
 * 后执行的会把先执行的字段冲掉 —— 玻璃态表头会退回实色，或单元格内边距丢失。
 *
 * 同时 cartoon / illustration / serene 等 preset 各自在 components.Card /
 * components.Table 里存着自己的字段（如 serene 的 headerColor），也必须保留。
 */
export function mergeComponentTokens(
  config: ConfigProviderProps,
  incoming: NonNullable<ThemeConfig['components']>
): ConfigProviderProps {
  const base = config.theme ?? {}
  const baseComponents = (base.components ?? {}) as Record<string, Record<string, unknown>>
  const merged: Record<string, Record<string, unknown>> = { ...baseComponents }

  for (const [name, fields] of Object.entries(incoming)) {
    merged[name] = { ...(baseComponents[name] ?? {}), ...(fields as Record<string, unknown>) }
  }

  return {
    ...config,
    theme: { ...base, components: merged as ThemeConfig['components'] }
  }
}
