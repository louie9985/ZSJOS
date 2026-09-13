import { useAppStore, type ThemeKey } from '@/stores/app'

export const THEMES: { key: ThemeKey; label: string; accent: string; background: string }[] = [
  { key: 'coral', label: '珊瑚粉', accent: '#F04455', background: 'linear-gradient(160deg, #fff 0%, #FFFAFB 48%, #FFEFF2 100%)' },
  { key: 'lavender', label: '薰衣草', accent: '#9B7DFF', background: 'linear-gradient(160deg, #fff 0%, #FBFAFF 48%, #F1EDFF 100%)' },
  { key: 'sky', label: '天空蓝', accent: '#4A90D9', background: 'linear-gradient(160deg, #fff 0%, #F8FCFF 48%, #EBF6FF 100%)' }
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
