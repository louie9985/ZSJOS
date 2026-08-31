import { Grid } from 'antd'
import { useTheme } from '../components/Theme/ThemeContext'

/** Shared predicate for inbox pages; table mode is intentionally desktop-only. */
export function useInboxTableLayout() {
  const screens = Grid.useBreakpoint()
  const { inboxLayoutMode } = useTheme()
  const isDesktop = typeof window === 'undefined'
    ? false
    : (screens.md ?? !window.matchMedia('(max-width: 768px)').matches)
  return { isDesktop, useTableLayout: inboxLayoutMode === 'table' && isDesktop }
}
