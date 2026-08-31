<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePageList } from '@/composables/usePageList'
import { getMyComplaints, type LeadComplaintItem } from '@/api/lead'
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
  item.status === 'pending' ? '处理中' : item.result === 'founded' ? '投诉成立' : '投诉不成立'
const resultType = (item: LeadComplaintItem): 'warning' | 'danger' | 'success' =>
  item.status === 'pending' ? 'warning' : item.result === 'founded' ? 'danger' : 'success'
const openLead = (leadId: number) => router.push(`/lead/${leadId}`)
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="投诉记录" left-arrow @click-left="$router.back()" />
    <van-tabs v-model:active="activeStatus" shrink sticky @change="refresh">
      <van-tab name="all" title="全部" />
      <van-tab name="pending" title="处理中" />
      <van-tab name="handled" title="已处理" />
    </van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <article v-for="item in list" :key="item.id" class="card complaint-card">
          <div class="complaint-card__header">
            <button type="button" class="lead-link" @click="openLead(item.leadId)">
              {{ formatLeadNo(item.leadNo) }}
            </button>
            <van-tag :type="resultType(item)">{{ resultText(item) }}</van-tag>
          </div>
          <p class="complaint-card__reason">{{ item.reason }}</p>
          <div v-if="item.salesUserName || item.handlerUserName" class="complaint-card__meta">销售：{{ item.salesUserName || '--' }} · 处理人：{{ item.handlerUserName || '待处理' }}</div>
          <div v-if="item.evidence?.length" class="complaint-card__meta">投诉证据：{{ item.evidence.length }} 个</div>
          <div v-if="item.handlerOpinion" class="complaint-card__result">
            <span>处理意见</span>
            <p>{{ item.handlerOpinion }}</p>
          </div>
          <div v-if="item.handlerEvidence?.length" class="complaint-card__meta">处理证据：{{ item.handlerEvidence.length }} 个</div>
          <time>{{ item.handledAt ? `处理于 ${formatDateTime(item.handledAt)}` : formatDateTime(item.createTime) }}</time>
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
.complaint-card__header{display:flex;align-items:center;justify-content:space-between;gap:12px}.lead-link{padding:0;border:0;background:none;color:var(--h5-primary);font-size:14px;font-weight:600}.complaint-card__reason{margin:14px 0;color:var(--h5-text-primary);font-size:14px;line-height:1.65;white-space:pre-wrap}.complaint-card__result{margin:12px 0;padding:12px;border-radius:8px;background:var(--h5-bg);font-size:13px}.complaint-card__result span,time{color:var(--h5-text-secondary);font-size:12px}.complaint-card__result p{margin-top:6px;line-height:1.6;white-space:pre-wrap}
</style>
