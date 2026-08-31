<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFeedbackPage, type FeedbackItem, type FeedbackStatus, type FeedbackType } from '@/api/feedback'
import { usePageList } from '@/composables/usePageList'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'FeedbackList' })

const router = useRouter()
const activeStatus = ref<'all' | FeedbackStatus>('all')
const activeType = ref<'all' | FeedbackType>('all')
const keywordInput = ref('')
const keyword = ref('')

const statusTabs: Array<{ key: 'all' | FeedbackStatus; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'APPROVING', label: '审批中' },
  { key: 'APPROVAL_REJECTED', label: '已驳回' },
  { key: 'WAITING', label: '待处理' },
  { key: 'IN_PROGRESS', label: '处理中' },
  { key: 'COMPLETED', label: '已完成' }
]
const typeTabs: Array<{ key: 'all' | FeedbackType; label: string }> = [
  { key: 'all', label: '全部类型' },
  { key: 'REQUIREMENT', label: '需求反馈' },
  { key: 'BUG', label: 'BUG 反馈' },
  { key: 'SUPPORT', label: '技术支持' }
]

const params = computed(() => ({
  ...(activeStatus.value !== 'all' ? { status: activeStatus.value } : {}),
  ...(activeType.value !== 'all' ? { feedbackType: activeType.value } : {}),
  ...(keyword.value ? { keyword: keyword.value } : {})
}))

const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList<FeedbackItem>(
  page => getFeedbackPage(page as Parameters<typeof getFeedbackPage>[0]),
  params
)

const statusType: Record<FeedbackStatus, 'primary' | 'warning' | 'success' | 'danger'> = {
  APPROVING: 'primary',
  APPROVAL_REJECTED: 'danger',
  WAITING: 'warning',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success'
}
const statusLabel: Record<FeedbackStatus, string> = {
  APPROVING: '审批中',
  APPROVAL_REJECTED: '已驳回',
  WAITING: '待处理',
  IN_PROGRESS: '处理中',
  COMPLETED: '已完成'
}
const typeLabel: Record<FeedbackType, string> = {
  REQUIREMENT: '需求反馈',
  BUG: 'BUG 反馈',
  SUPPORT: '技术支持'
}

function search() {
  keyword.value = keywordInput.value.trim()
  void refresh()
}

function clearSearch() {
  keywordInput.value = ''
  keyword.value = ''
  void refresh()
}

function changeFilter() {
  void refresh()
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="系统反馈" left-arrow @click-left="$router.back()">
      <template #right>
        <van-icon name="plus" size="20" @click="router.push('/feedback/create')" />
      </template>
    </van-nav-bar>
    <van-search v-model="keywordInput" placeholder="搜索反馈编号或标题" shape="round" @search="search" @clear="clearSearch" />
    <van-tabs v-model:active="activeType" shrink @change="changeFilter">
      <van-tab v-for="tab in typeTabs" :key="tab.key" :name="tab.key" :title="tab.label" />
    </van-tabs>
    <van-tabs v-model:active="activeStatus" shrink sticky @change="changeFilter">
      <van-tab v-for="tab in statusTabs" :key="tab.key" :name="tab.key" :title="tab.label" />
    </van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-for="item in list" :key="item.id" class="card feedback-row" @click="router.push(`/feedback/${item.id}`)">
          <div class="feedback-row__head">
            <strong>{{ item.title }}</strong>
            <van-tag :type="statusType[item.status]" plain>{{ statusLabel[item.status] || item.status }}</van-tag>
          </div>
          <p>{{ item.feedbackNo }} · {{ typeLabel[item.feedbackType] || item.feedbackType }}</p>
          <p v-if="item.latestReplySummary" class="feedback-row__reply">回复：{{ item.latestReplySummary }}</p>
          <time>{{ formatDateTime(item.lastActivityAt || item.createTime) }}</time>
        </div>
        <van-empty v-if="!loading && error" :description="error" image="error">
          <van-button size="small" type="primary" @click="refresh">重新加载</van-button>
        </van-empty>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无反馈记录">
          <van-button size="small" type="primary" round @click="router.push('/feedback/create')">新建反馈</van-button>
        </van-empty>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.feedback-row{margin:10px 16px;padding:14px;cursor:pointer}.feedback-row__head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.feedback-row__head strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:14px}.feedback-row p{margin:8px 0 0;color:var(--h5-text-secondary);font-size:12px;line-height:1.5}.feedback-row__reply{display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical}.feedback-row time{display:block;margin-top:8px;color:var(--h5-text-placeholder);font-size:11px}
</style>
