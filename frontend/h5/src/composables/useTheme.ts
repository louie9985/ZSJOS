import { useAppStore, type ThemeKey } from '@/stores/app'

export const THEMES: { key: ThemeKey; label: string; emoji: string; color: string }[] = [
  { key: 'coral', label: '珊瑚粉', emoji: '🌸', color: '#FF6B81' },
  { key: 'lavender', label: '薰衣草', emoji: '💜', color: '#9B7DFF' },
  { key: 'sky', label: '天空蓝', emoji: '🌊', color: '#4A90D9' }
]

export function useTheme() {
  const appStore = useAppStore()

  function currentTheme() {
    return appStore.theme
  }

  function switchTheme(key: ThemeKey) {
    appStore.setTheme(key)
  }

  return {
    currentTheme,
    switchTheme,
    themes: THEMES
  }
}
