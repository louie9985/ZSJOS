<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { getPublicNotice, getPublicNoticeAttachmentUrl, NoticeShareError, type PublicNotice } from '@/api/noticeShare'
const route = useRoute()
const notice = ref<PublicNotice>()
const loading = ref(false)
const error = ref('')
const invalid = ref(false)
const fileErrors = ref<Record<number, string>>({})
const fileUrls = ref<Record<number, string>>({})
const fileBusy = ref<Record<number, boolean>>({})
let generation = 0
const token = () => new URLSearchParams(route.hash.replace(/^#/, '')).get('token') || ''
const load = async () => {
  const current = ++generation
  notice.value = undefined; loading.value = true; error.value = ''; invalid.value = false
  fileErrors.value = {}; fileUrls.value = {}; fileBusy.value = {}
  try {
    const result = await getPublicNotice(token())
    if (current === generation) { notice.value = result; document.title = result.title + ' · 中世健' }
  } catch (e) {
    if (current === generation) {
      invalid.value = e instanceof NoticeShareError && e.code === 1002008007
      error.value = e instanceof Error ? e.message : '加载失败，请重试'
    }
  } finally { if (current === generation) loading.value = false }
}
const prepareFile = async (id: number) => {
  const current = generation
  fileBusy.value[id] = true; delete fileErrors.value[id]; delete fileUrls.value[id]
  try {
    const url = await getPublicNoticeAttachmentUrl(token(), id)
    const parsed = new URL(url)
    if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password) throw new Error('文件地址不可用')
    if (current === generation) fileUrls.value[id] = url
  } catch (e) {
    if (current === generation) {
      if (e instanceof NoticeShareError && e.code === 1002008007) {
        notice.value = undefined; fileUrls.value = {}; invalid.value = true; error.value = e.message
      } else fileErrors.value[id] = e instanceof Error ? e.message : '附件暂时不可用'
    }
  } finally { if (current === generation) fileBusy.value[id] = false }
}
watch(() => route.hash, load, { immediate: true })
onBeforeUnmount(() => generation++)
</script>
<template>
  <main class="norem notice-public">
    <header class="notice-public-brand">中世健 <span>公告</span></header>
    <section v-if="loading" class="notice-public-state" aria-live="polite"><van-loading vertical>正在加载公告</van-loading></section>
    <section v-else-if="error" class="notice-public-state" role="alert">
      <h1>{{ invalid ? '分享内容已失效' : '暂时无法加载公告' }}</h1>
      <p>{{ invalid ? '该链接可能已关闭，或公告已下线。请联系分享人。' : error }}</p>
      <van-button v-if="!invalid" type="primary" @click="load">重试</van-button>
    </section>
    <article v-else-if="notice">
      <h1>{{ notice.title }}</h1>
      <p class="notice-public-date">{{ dayjs(notice.publishTime).format('YYYY-MM-DD HH:mm') }}</p>
      <!-- Only the anonymous System endpoint's allowlist-cleaned HTML is rendered here. -->
      <div class="notice-public-body" v-html="notice.content"></div>
      <section v-if="notice.attachments.length" class="notice-public-files">
        <h2>附件</h2>
        <div v-for="file in notice.attachments" :key="file.id" class="notice-public-file">
          <strong>{{ file.fileName }}</strong>
          <span>{{ Math.max(1, Math.ceil(file.fileSize / 1024)) }} KB</span>
          <p v-if="fileErrors[file.id]" role="alert">{{ fileErrors[file.id] }}</p>
          <van-button size="small" :loading="fileBusy[file.id]" @click="prepareFile(file.id)">{{ fileErrors[file.id] ? '重试' : fileUrls[file.id] ? '刷新文件地址' : '获取文件' }}</van-button>
          <a v-if="fileUrls[file.id]" :href="fileUrls[file.id]" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer">打开／下载附件</a>
        </div>
        <p class="notice-public-note">图片、PDF 可由浏览器查看；其他文件请下载后打开。文件地址失效时请刷新。</p>
      </section>
    </article>
  </main>
</template>
<style scoped>
/* Public documents use viewport space; the partner app keeps its mobile width elsewhere.
   Every document selector includes .norem to opt out of the H5 px-to-rem transform. */
:global(#app:has(.notice-public.norem)) { max-width: none; background: #f3f5f7; }
.notice-public.norem { box-sizing: border-box; width: 100%; max-width: 1120px; min-height: 100vh; margin: 0 auto; padding: clamp(20px, 4vw, 48px) clamp(16px, 4vw, 48px) 48px; background: #fff; color: #20242b; font: 16px/1.85 system-ui, sans-serif; overflow-wrap: anywhere; }
.notice-public.norem .notice-public-brand { border-bottom: 1px solid #ddd; padding-bottom: 20px; margin-bottom: 28px; font-size: 18px; font-weight: 600; }
.notice-public.norem .notice-public-brand span { margin-left: 10px; font-size: 14px; font-weight: normal; }
.notice-public.norem h1 { margin: 0 0 12px; font-size: 26px; line-height: 1.4; }
.notice-public.norem .notice-public-date, .notice-public.norem .notice-public-note { color: var(--h5-text-secondary, #666); font-size: 13px; }
.notice-public.norem .notice-public-body { font-size: 16px; line-height: 1.85; margin-top: 28px; }
.notice-public.norem .notice-public-body :deep(img), .notice-public.norem .notice-public-body :deep(video) { max-width: 100%; height: auto; }
.notice-public.norem .notice-public-body :deep(pre) { display: block; overflow-x: auto; max-width: 100%; }
.notice-public.norem .notice-public-body :deep(.notice-table-scroll) { width: 100%; max-width: 100%; overflow-x: auto; margin: 12px 0; }
.notice-public.norem .notice-public-body :deep(.notice-table-scroll:focus-visible) { outline: 2px solid var(--van-primary-color); outline-offset: 2px; }
.notice-public.norem .notice-public-body :deep(table) { width: 100%; border-collapse: collapse; }
/* Public document grids need visible lines on white, unlike the translucent card border. */
.notice-public.norem .notice-public-body :deep(td), .notice-public.norem .notice-public-body :deep(th) { box-sizing: border-box; border: 1px solid #ddd; padding: 8px; line-height: 1.5; white-space: pre-wrap; overflow-wrap: break-word; vertical-align: top; }
.notice-public.norem .notice-public-body :deep(th) { background-color: #f7f7f7; }
.notice-public.norem .notice-public-files { margin-top: 32px; border-top: 1px solid #ddd; }
.notice-public.norem .notice-public-files h2 { font-size: 20px; }
.notice-public.norem .notice-public-file { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 16px 0; border-bottom: 1px solid #ddd; }
.notice-public.norem .notice-public-file strong { flex: 1 1 100%; font-size: 15px; }
.notice-public.norem .notice-public-file p { flex-basis: 100%; color: #b42318; }
.notice-public.norem .notice-public-file a { color: var(--van-primary-color); text-decoration: underline; font-size: 14px; }
.notice-public.norem .notice-public-state { text-align: center; padding: 48px 0; }
@media (max-width: 480px) { .notice-public.norem h1 { font-size: 23px; } }
@media (min-width: 768px) { .notice-public.norem h1 { font-size: 32px; } }
</style>
