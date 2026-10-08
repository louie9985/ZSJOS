import type { CSSProperties } from 'react'

/** Only opaque RGB values from the exam contract become CSS; text stays readable in either theme. */
export function examBackgroundStyle(value?: string | null): CSSProperties | undefined {
  if (!value || !/^#[\da-f]{6}$/i.test(value)) return undefined
  const rgb = [1, 3, 5].map(offset => parseInt(value.slice(offset, offset + 2), 16) / 255)
  const [r, g, b] = rgb.map(channel => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4)
  const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
  const foreground = luminance > 0.179 ? '#000000' : '#ffffff'
  return { backgroundColor: value, color: foreground, borderColor: value }
}
