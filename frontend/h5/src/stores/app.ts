import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeKey = 'coral' | 'lavender' | 'sky'

export interface DictItem {
  label: string
  value: string
  colorType?: string
}

export const useAppStore = defineStore('app', () => {
  // --- 主题 ---
  const theme = ref<ThemeKey>((localStorage.getItem('h5-theme') as ThemeKey) || 'coral')

  function setTheme(key: ThemeKey) {
    theme.value = key
    document.documentElement.setAttribute('data-theme', key)
    localStorage.setItem('h5-theme', key)
  }

  // 初始化时应用主题
  function initTheme() {
    document.documentElement.setAttribute('data-theme', theme.value)
  }

  // --- 字典缓存 ---
  const dictCache = ref<Record<string, DictItem[]>>({})

  function setDict(type: string, items: DictItem[]) {
    dictCache.value[type] = items
  }

  function getDict(type: string): DictItem[] {
    return dictCache.value[type] || []
  }

  return {
    theme,
    setTheme,
    initTheme,
    dictCache,
    setDict,
    getDict
  }
})
