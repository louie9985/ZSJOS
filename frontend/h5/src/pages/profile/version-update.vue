<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showConfirmDialog } from 'vant'
import { checkVersion, currentVersion, useVersion } from '@/services/version'

defineOptions({ name: 'VersionUpdate' })
const { latest, loading, error, checked, refreshError, hasUpdate, showNotice, dismissNotice, refresh } = useVersion()
const expanded = ref<number | null>(null)
const detailsVisible = ref(false)
const confirming = ref(false)
const status = computed(() => loading.value ? '正在检查版本'
  : error.value ? '版本检查失败' : !checked.value ? '尚未检查版本'
    : hasUpdate.value ? '发现新版本' : '已是最新版本')
const releases = computed(() => (latest.value || currentVersion).releases)
const notes = computed(() => releases.value[0]?.notes || [])

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

async function update() {
  if (confirming.value) return
  confirming.value = true
  try {
    await showConfirmDialog({ title: '更新页面', message: '将刷新当前页面以加载已发布的版本，是否继续？', confirmButtonText: '立即更新' })
    refresh()
  } catch { /* 用户取消刷新。 */ }
  finally { confirming.value = false }
}

onMounted(() => { void checkVersion(true) })
</script>

<template>
  <div class="page-container my-subpage-page version-page">
    <van-nav-bar class="my-subpage__nav" title="版本控制与更新" left-arrow @click-left="$router.back()" />
    <main class="version-content">
      <section class="my-subpage-card version-intro">
        <div class="version-symbol"><van-icon name="upgrade" size="30" /></div>
        <h1>中世健 · 兼职端</h1>
        <p>每一次更新，让工作更顺手</p>
        <span class="version-status" :class="{ 'version-status--error': error }" role="status" aria-live="polite">{{ status }}</span>
      </section>

      <section v-if="showNotice && !error" class="version-notice" aria-label="更新提示">
        <div><strong>有更新可用</strong><p>查看更新说明后，可在页面底部立即更新。</p></div>
        <button class="text-button" aria-label="关闭本次更新提示" @click="dismissNotice">关闭</button>
      </section>

      <section v-if="error" class="version-error" role="alert">
        <p>{{ error }}</p>
        <p v-if="latest" class="version-muted">下方保留上次成功获取的版本信息。</p>
        <van-button size="small" round :loading="loading" @click="checkVersion(true)">重试</van-button>
      </section>
      <section v-if="refreshError" class="version-error" role="alert">{{ refreshError }}</section>

      <section class="my-subpage-card version-card" aria-label="版本信息">
        <dl>
          <div><dt>当前版本</dt><dd>v{{ currentVersion.version }}</dd></div>
          <div><dt>最新版本</dt><dd>{{ latest ? `v${latest.version}` : '—' }}</dd></div>
          <div><dt>发布时间</dt><dd>{{ formatTime(latest?.publishedAt) }}</dd></div>
        </dl>
        <button class="text-button details-toggle" :aria-expanded="detailsVisible" @click="detailsVisible = !detailsVisible">
          版本详情 <van-icon :name="detailsVisible ? 'arrow-up' : 'arrow-down'" />
        </button>
        <dl v-if="detailsVisible" class="build-details">
          <div><dt>当前构建</dt><dd>{{ currentVersion.buildId }}</dd></div>
          <div><dt>最新构建</dt><dd>{{ latest?.buildId || '—' }}</dd></div>
        </dl>
      </section>

      <section class="my-subpage-card version-card">
        <h2>本次更新说明</h2>
        <p v-if="!latest" class="version-muted">当前安装版本的更新说明</p>
        <ul v-if="notes.length" class="version-notes"><li v-for="(note, index) in notes" :key="index">{{ note }}</li></ul>
        <p v-else class="version-muted">暂无更新说明</p>
      </section>

      <section class="my-subpage-card version-card">
        <h2>历史更新记录</h2>
        <p v-if="!releases.length" class="version-muted">暂无更新记录</p>
        <article v-for="(release, index) in releases" :key="index" class="release">
          <button class="release-toggle" :aria-expanded="expanded === index" @click="expanded = expanded === index ? null : index">
            <span><strong>v{{ release.version }}</strong><span class="release-date">{{ release.date }}</span></span>
            <span class="release-action">{{ expanded === index ? '收起' : '查看' }} <van-icon :name="expanded === index ? 'arrow-up' : 'arrow-down'" /></span>
          </button>
          <p class="release-summary">{{ release.summary }}</p>
          <ul v-if="expanded === index" class="version-notes"><li v-for="(note, noteIndex) in release.notes" :key="noteIndex">{{ note }}</li></ul>
        </article>
      </section>

      <div class="version-actions">
        <van-button block round :loading="loading" @click="checkVersion(true)">{{ loading ? '检查中' : '检查更新' }}</van-button>
        <van-button v-if="hasUpdate" block round type="primary" :disabled="loading || Boolean(error)" :loading="confirming" @click="update">立即更新</van-button>
      </div>
      <p class="version-footnote">更新通过刷新页面完成，无需下载安装。</p>
    </main>
  </div>
</template>

<style scoped>
.version-content { max-width: 640px; margin: 0 auto; padding: 16px 16px calc(24px + env(safe-area-inset-bottom)); }
.version-intro { padding: 24px 16px; text-align: center; }
.version-symbol { display: inline-flex; align-items: center; justify-content: center; width: 60px; height: 60px; border-radius: 20px; color: var(--h5-primary); background: var(--h5-primary-opacity); }
h1 { margin: 14px 0 6px; font-size: 20px; color: var(--h5-text-primary); }
.version-intro p, .version-muted, .version-footnote { color: var(--h5-text-secondary); font-size: 12px; line-height: 1.7; }
.version-intro p { margin: 0 0 16px; }
.version-status { display: inline-block; padding: 6px 14px; border-radius: 999px; color: var(--h5-primary); background: var(--h5-primary-opacity); font-size: 13px; }
.version-status--error { color: var(--h5-text-primary); }
.version-card { padding: 18px 16px; margin-top: 14px; }
h2 { margin: 0 0 14px; color: var(--h5-text-primary); font-size: 15px; }
dl { margin: 0; font-size: 13px; }
dl > div { display: flex; justify-content: space-between; gap: 16px; padding: 9px 0; }
dt { flex-shrink: 0; color: var(--h5-text-secondary); }
dd { margin: 0; color: var(--h5-text-primary); text-align: right; overflow-wrap: anywhere; }
.text-button, .release-toggle { border: 0; background: transparent; font: inherit; cursor: pointer; }
.text-button { color: var(--h5-primary); padding: 10px 0; font-size: 13px; }
.details-toggle { display: flex; align-items: center; justify-content: space-between; width: 100%; margin-top: 6px; border-top: 1px solid var(--h5-divider); }
.build-details { font-size: 12px; }
.version-notice, .version-error { display: flex; align-items: center; gap: 12px; margin-top: 14px; padding: 14px 16px; border: 1px solid var(--h5-glass-border); border-radius: 16px; background: var(--h5-primary-opacity); color: var(--h5-text-primary); font-size: 13px; line-height: 1.7; }
.version-notice > div { flex: 1; }
.version-notice .text-button { flex-shrink: 0; }
.version-notice p { margin: 4px 0 0; font-size: 12px; }
.version-error { display: block; }
.version-error p { margin: 0 0 8px; }
.version-notes { margin: 0; padding-left: 20px; color: var(--h5-text-primary); font-size: 13px; line-height: 1.85; }
.version-notes li + li { margin-top: 8px; }
.release + .release { margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--h5-divider); }
.release-toggle { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; padding: 4px 0; text-align: left; color: var(--h5-text-primary); font-size: 14px; }
.release-date { display: block; color: var(--h5-text-secondary); font-size: 12px; margin-top: 4px; }
.release-action { flex-shrink: 0; color: var(--h5-primary); font-size: 12px; }
.release-summary { color: var(--h5-text-secondary); font-size: 13px; line-height: 1.7; }
.version-actions { display: flex; gap: 12px; margin-top: 24px; }
.version-actions > * { flex: 1; min-width: 0; }
.version-footnote { text-align: center; margin: 12px 0 0; }
</style>
