import { computed, ref, type Ref } from 'vue'
interface PageResult<T> {
  list: T[]
  total: number
}

interface UsePageListOptions {
  pageSize?: number
  immediate?: boolean
  mode?: 'append' | 'page'
}

/**
 * 通用分页列表 composable
 * 配合 Vant List + PullRefresh 使用
 */
export function usePageList<T, P extends Record<string, unknown> = Record<string, unknown>>(
  apiFn: (params: P & { pageNo: number; pageSize: number }) => Promise<PageResult<T>>,
  extraParams?: Ref<P> | (() => P),
  options: UsePageListOptions = {}
) {
  const { pageSize = 10, immediate = true, mode = 'append' } = options

  const list = ref<T[]>([]) as Ref<T[]>
  const loading = ref(false)
  const refreshing = ref(false)
  const finished = ref(false)
  const pageNo = ref(1)
  const total = ref(0)
  const error = ref('')
  let requestVersion = 0

  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

  function getParams(): P {
    if (!extraParams) return {} as P
    if (typeof extraParams === 'function') return extraParams()
    return extraParams.value
  }

  async function loadPage(targetPage = pageNo.value) {
    if (loading.value || targetPage < 1 || (total.value > 0 && targetPage > pageCount.value)) return
    const version = requestVersion
    loading.value = true
    error.value = ''
    try {
      const params = { ...getParams(), pageNo: targetPage, pageSize } as P & { pageNo: number; pageSize: number }
      const result = await apiFn(params)
      if (version !== requestVersion) return
      list.value = result.list
      total.value = result.total
      pageNo.value = targetPage
    } catch (cause) {
      if (version !== requestVersion) return
      error.value = cause instanceof Error ? cause.message : '加载失败'
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  async function loadMore() {
    if (mode === 'page') {
      await loadPage()
      return
    }
    if (loading.value || finished.value) return
    const version = requestVersion
    const currentPageNo = pageNo.value
    loading.value = true
    error.value = ''
    try {
      const params = { ...getParams(), pageNo: currentPageNo, pageSize } as P & { pageNo: number; pageSize: number }
      const result = await apiFn(params)
      if (version !== requestVersion) return
      if (currentPageNo === 1) {
        list.value = result.list
      } else {
        list.value.push(...result.list)
      }
      total.value = result.total
      finished.value = list.value.length >= result.total
      pageNo.value = currentPageNo + 1
    } catch (cause) {
      if (version !== requestVersion) return
      error.value = cause instanceof Error ? cause.message : '加载失败'
      finished.value = true
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  async function refresh() {
    const version = ++requestVersion
    refreshing.value = true
    loading.value = false
    pageNo.value = 1
    finished.value = false
    list.value = []
    await (mode === 'page' ? loadPage(1) : loadMore())
    if (version === requestVersion) refreshing.value = false
  }

  function reset() {
    requestVersion++
    loading.value = false
    refreshing.value = false
    pageNo.value = 1
    finished.value = false
    list.value = []
    total.value = 0
  }

  if (immediate) {
    loadMore()
  }

  return {
    list,
    loading,
    refreshing,
    finished,
    total,
    pageNo,
    pageCount,
    error,
    loadMore,
    loadPage,
    refresh,
    reset
  }
}
