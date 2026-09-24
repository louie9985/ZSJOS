import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { getCatalog, type AdvancedFilterField } from '@/api/zsjos/advancedFilter'

export function useFinanceFilterOptions(scene: 'cashback' | 'withdrawal') {
  const fields = ref<AdvancedFilterField[]>([])
  const loading = ref(false)
  const error = ref('')
  let sequence = 0
  const reload = async () => {
    const request = ++sequence
    error.value = ''; loading.value = true
    try {
      const catalog = await getCatalog(scene)
      if (request === sequence) fields.value = catalog.fields
    } catch (cause) {
      const failure = cause as { code?: number; response?: { status?: number } } | null
      const code = failure?.response?.status ?? failure?.code
      if (request === sequence) error.value = code === 401 ? '登录已失效，请重新登录'
        : code === 403 ? '暂无该场景的筛选查询权限' : '筛选选项加载失败，请重试'
    } finally {
      if (request === sequence) loading.value = false
    }
  }
  const statuses = computed(() => fields.value.find(field => field.fieldKey === `${scene}.status`)?.options || [])
  const types = computed(() => fields.value.find(field => field.fieldKey === `${scene}.type`)?.options || [])
  onMounted(reload)
  onBeforeUnmount(() => { sequence++ })
  return { statuses, types, loading, error, reload }
}
