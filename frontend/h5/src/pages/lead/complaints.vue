<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showImagePreview } from 'vant'
import { usePageList } from '@/composables/usePageList'
import { getMyComplaints, type LeadAppealEvidence, type LeadComplaintItem } from '@/api/lead'
import { formatDateTime, formatLeadNo } from '@/utils/format'

defineOptions({ name: 'ComplaintHistory' })

const router = useRouter()
const activeStatus = ref('all')
const params = computed(() => activeStatus.value === 'all' ? {} : { status: activeStatus.value })
const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  (query) => getMyComplaints(query as Parameters<typeof getMyComplaints>[0]),
  params
)
const resultText = (item: LeadComplaintItem) =>
  item.status === 'pending' ? '待处理' : item.result === 'founded' ? '投诉成立' : '投诉不成立'
const resultType = (item: LeadComplaintItem): 'warning' | 'danger' | 'success' =>
  item.status === 'pending' ? 'warning' : item.result === 'founded' ? 'danger' : 'success'
const openLead = (leadId: number) => router.push(`/lead/${leadId}`)

function isImage(file: LeadAppealEvidence) {
  return file.contentType?.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp)$/i.test(file.originalName || file.fileUrl || '')
}

function previewEvidence(files: LeadAppealEvidence[], index: number) {
  const selected = files[index]
  if (!selected?.fileUrl) return
  if (!isImage(selected)) {
    window.open(selected.fileUrl, '_blank')
    return
  }
  const images = files.filter(item => item.fileUrl && isImage(item)).map(item => item.fileUrl!)
  showImagePreview({
    images,
    startPosition: Math.max(0, images.indexOf(selected.fileUrl)),
    closeable: true
  })
}
</script>

<template>
  <div class="page-container complaint-history-page">
    <van-nav-bar class="complaint-top-nav" title="投诉记录" left-arrow @click-left="$router.back()" />
    <van-tabs class="complaint-top-tabs" v-model:active="activeStatus" shrink sticky @change="refresh">
      <van-tab name="all" title="全部" />
      <van-tab name="pending" title="待处理" />
      <van-tab name="handled" title="已处理" />
    </van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <article v-for="item in list" :key="item.id" class="card complaint-card">
          <div class="complaint-card__header">
            <button type="button" class="lead-link" @click="openLead(item.leadId)">
              {{ formatLeadNo(item.leadNo) }}
            </button>
            <van-tag class="complaint-card__status" :type="resultType(item)">{{ resultText(item) }}</van-tag>
          </div>
          <div class="complaint-card__section">
            <span class="complaint-card__label">投诉内容</span>
            <p class="complaint-card__reason">{{ item.reason }}</p>
          </div>
          <div v-if="item.salesUserName || item.handlerUserName" class="complaint-card__meta">
            <span v-if="item.salesUserName">被投诉销售：{{ item.salesUserName }}</span>
            <span v-if="item.handlerUserName">处理人：{{ item.handlerUserName }}</span>
          </div>
          <div v-if="item.evidence?.length" class="complaint-evidence">
            <span class="complaint-card__label">投诉证据（{{ item.evidence.length }}）</span>
            <div class="complaint-evidence__list">
              <button
                v-for="(file, index) in item.evidence"
                :key="file.infraFileId"
                type="button"
                class="complaint-evidence__item"
                :disabled="!file.fileUrl"
                @click="previewEvidence(item.evidence || [], index)"
              >
                <img v-if="file.fileUrl && isImage(file)" :src="file.fileUrl" :alt="file.originalName || `投诉证据 ${index + 1}`">
                <van-icon v-else name="description-o" size="22" />
                <span>{{ file.originalName || `投诉证据 ${index + 1}` }}</span>
              </button>
            </div>
          </div>
          <div v-if="item.handlerOpinion" class="complaint-card__result">
            <span>处理意见</span>
            <p>{{ item.handlerOpinion }}</p>
          </div>
          <div v-if="item.handlerEvidence?.length" class="complaint-evidence">
            <span class="complaint-card__label">处理证据（{{ item.handlerEvidence.length }}）</span>
            <div class="complaint-evidence__list">
              <button
                v-for="(file, index) in item.handlerEvidence"
                :key="file.infraFileId"
                type="button"
                class="complaint-evidence__item"
                :disabled="!file.fileUrl"
                @click="previewEvidence(item.handlerEvidence || [], index)"
              >
                <img v-if="file.fileUrl && isImage(file)" :src="file.fileUrl" :alt="file.originalName || `处理证据 ${index + 1}`">
                <van-icon v-else name="description-o" size="22" />
                <span>{{ file.originalName || `处理证据 ${index + 1}` }}</span>
              </button>
            </div>
          </div>
          <div class="complaint-card__time">
            <time>提交于 {{ formatDateTime(item.createTime) }}</time>
            <time v-if="item.handledAt">处理于 {{ formatDateTime(item.handledAt) }}</time>
          </div>
        </article>
        <van-empty v-if="!loading && error" :description="error" image="error">
          <van-button type="primary" round size="small" @click="refresh">重新加载</van-button>
        </van-empty>
        <van-empty v-if="!loading && !error && !list.length" description="暂无投诉记录" />
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.complaint-card {
  background: var(--h5-content-surface);
}

.complaint-history-page :deep(.complaint-top-nav),
.complaint-history-page :deep(.complaint-top-tabs) {
  background: var(--h5-glass-surface);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
}

.complaint-history-page :deep(.complaint-top-nav) {
  border-bottom: 0;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.complaint-history-page :deep(.complaint-top-nav)::after {
  display: none;
}

.complaint-history-page :deep(.complaint-top-tabs .van-tabs__wrap),
.complaint-history-page :deep(.complaint-top-tabs .van-tabs__nav) {
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.complaint-history-page :deep(.complaint-top-tabs) {
  border-bottom: 1px solid var(--h5-glass-divider);
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .complaint-history-page :deep(.complaint-top-nav),
  .complaint-history-page :deep(.complaint-top-tabs) {
    background: var(--h5-glass-surface-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .complaint-history-page :deep(.complaint-top-nav),
  .complaint-history-page :deep(.complaint-top-tabs) {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}

.complaint-card{overflow:hidden}.complaint-card__header{display:flex;align-items:center;justify-content:space-between;gap:12px}.complaint-card__status{flex:0 0 auto;white-space:nowrap}.lead-link{min-width:0;overflow-wrap:anywhere;padding:0;border:0;background:none;color:var(--h5-primary);font-size:14px;font-weight:600;text-align:left}.complaint-card__section{margin-top:14px}.complaint-card__label{display:block;color:var(--h5-text-secondary);font-size:12px;line-height:1.5}.complaint-card__reason{margin:5px 0 0;color:var(--h5-text-primary);font-size:14px;line-height:1.65;white-space:pre-wrap;overflow-wrap:anywhere}.complaint-card__meta{display:flex;flex-wrap:wrap;gap:4px 12px;margin-top:10px;color:var(--h5-text-secondary);font-size:12px;line-height:1.6}.complaint-evidence{margin-top:12px}.complaint-evidence__list{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:7px}.complaint-evidence__item{min-width:0;padding:0;border:0;background:transparent;color:inherit;text-align:left}.complaint-evidence__item:disabled{opacity:.55}.complaint-evidence__item img{display:block;width:100%;aspect-ratio:1;object-fit:cover;border-radius:6px}.complaint-evidence__item .van-icon{display:flex;width:100%;aspect-ratio:1;align-items:center;justify-content:center;border-radius:6px;background:var(--h5-glass-sunken);color:var(--h5-text-secondary)}.complaint-evidence__item span{display:block;overflow:hidden;margin-top:4px;color:var(--h5-text-secondary);font-size:11px;line-height:16px;text-overflow:ellipsis;white-space:nowrap}.complaint-card__result{margin:12px 0 0;padding:12px;border-radius:8px;background:var(--h5-glass-sunken);font-size:13px}.complaint-card__result>span{color:var(--h5-text-secondary);font-size:12px}.complaint-card__result p{margin-top:6px;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.complaint-card__time{display:flex;flex-wrap:wrap;justify-content:space-between;gap:4px 12px;margin-top:12px}.complaint-card__time time{color:var(--h5-text-secondary);font-size:11px;line-height:1.5}
</style>
