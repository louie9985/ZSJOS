import { ref } from 'vue'
import { useAppStore, type DictItem } from '@/stores/app'
import { getDictByType } from '@/api/lead'

const DICT_TYPES = {
  LEAD_SOURCE_CHANNEL: 'zsjos_lead_source_channel',
  LEAD_CATEGORY: 'zsjos_lead_category'
} as const

/**
 * 字典加载 composable — 带缓存
 */
export function useDict() {
  const appStore = useAppStore()
  const loading = ref(false)

  async function loadDict(type: string): Promise<DictItem[]> {
    // 命中缓存
    const cached = appStore.getDict(type)
    if (cached.length > 0) return cached

    loading.value = true
    try {
      const items = await getDictByType(type)
      appStore.setDict(type, items)
      return items
    } catch {
      return []
    } finally {
      loading.value = false
    }
  }

  async function loadSourceChannels() {
    return loadDict(DICT_TYPES.LEAD_SOURCE_CHANNEL)
  }

  async function loadLeadCategories() {
    return loadDict(DICT_TYPES.LEAD_CATEGORY)
  }

  function getDictLabel(type: string, value: string): string {
    const items = appStore.getDict(type)
    return items.find(i => i.value === value)?.label || value
  }

  return {
    loading,
    loadDict,
    loadSourceChannels,
    loadLeadCategories,
    getDictLabel,
    DICT_TYPES
  }
}
