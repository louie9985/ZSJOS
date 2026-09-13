import { computed, ref } from 'vue'
import { buildRefreshUrl, hasDifferentBuild, parseVersionManifest, type VersionManifest } from '../utils/version'

export const currentVersion: VersionManifest = __H5_VERSION__
const latest = ref<VersionManifest | null>(null)
const loading = ref(false)
const error = ref('')
const checked = ref(false)
const refreshError = ref('')
const dismissedBuild = ref(readLocal('h5-version-dismissed'))
const hasUpdate = computed(() => hasDifferentBuild(currentVersion, latest.value))
let lastCheck = 0
let pending: Promise<void> | undefined
const refreshKey = 'h5-version-refresh-target'

function readLocal(key: string) {
  try { return localStorage.getItem(key) || '' } catch { return '' }
}

try {
  const target = sessionStorage.getItem(refreshKey)
  if (target && target !== currentVersion.buildId) {
    refreshError.value = '刷新后仍未加载目标版本，可能是页面缓存或发布尚未完成。请稍后检查更新，或关闭页面后重新进入。'
  }
  sessionStorage.removeItem(refreshKey)
} catch { /* 浏览器禁用存储时仍允许检查与刷新。 */ }

export function checkVersion(force = false): Promise<void> {
  if (pending) return pending
  if (!force && Date.now() - lastCheck < 5 * 60 * 1000) return Promise.resolve()
  loading.value = true
  error.value = ''
  pending = (async () => {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 10000)
    try {
      const url = new URL(`${import.meta.env.BASE_URL}version.json`, window.location.origin)
      url.searchParams.set('t', String(Date.now()))
      const response = await fetch(url, { cache: 'no-store', signal: controller.signal })
      if (!response.ok) throw new Error(`版本检查失败（${response.status}），请重试`)
      latest.value = parseVersionManifest(await response.json())
      if (!hasUpdate.value) refreshError.value = ''
    } catch (cause) {
      error.value = controller.signal.aborted ? '版本检查超时，请重试'
        : cause instanceof SyntaxError ? '版本信息格式不正确，请稍后重试'
        : cause instanceof TypeError ? '无法连接版本服务，请检查网络后重试'
        : cause instanceof Error ? cause.message : '版本检查失败，请重试'
    } finally {
      clearTimeout(timer)
      lastCheck = Date.now()
      checked.value = true
      loading.value = false
      pending = undefined
    }
  })()
  return pending
}

export function useVersion() {
  return { latest, loading, error, checked, refreshError, hasUpdate,
    showNotice: computed(() => hasUpdate.value && dismissedBuild.value !== latest.value?.buildId),
    dismissNotice() {
      if (!latest.value) return
      dismissedBuild.value = latest.value.buildId
      try { localStorage.setItem('h5-version-dismissed', dismissedBuild.value) } catch { /* 仅当前页面关闭。 */ }
    },
    refresh() {
      if (!latest.value || !hasUpdate.value || error.value || loading.value) return
      try { sessionStorage.setItem(refreshKey, latest.value.buildId) } catch { /* 存储不可用不阻止主动刷新。 */ }
      window.location.replace(buildRefreshUrl(window.location.href, latest.value.buildId))
    }
  }
}
