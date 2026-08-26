<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFeedbackPage, type FeedbackItem, type FeedbackStatus } from '@/api/feedback'
import { wasMockedEndpoint } from '@/api/mock'
import { usePageList } from '@/composables/usePageList'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'FeedbackList' })
const router = useRouter()
const activeStatus = ref('all')
const keywordInput = ref('')
const keyword = ref('')
const tabs: Array<{ key: 'all' | FeedbackStatus; label: string }> = [
  { key: 'all', label: '全部' }, { key: 'submitted', label: '待处理' }, { key: 'processing', label: '处理中' },
  { key: 'need_more_info', label: '待补充' }, { key: 'resolved', label: '已解决' }, { key: 'closed', label: '已关闭' }
]
const params = computed(() => ({
  ...(activeStatus.value !== 'all' ? { status: activeStatus.value as FeedbackStatus } : {}),
  ...(keyword.value ? { keyword: keyword.value } : {})
}))
const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList<FeedbackItem>(
  page => getFeedbackPage(page as Parameters<typeof getFeedbackPage>[0]), params
)
const usingMock = computed(() => wasMockedEndpoint('/zsjos/feedback'))
const statusType: Record<FeedbackStatus, 'primary' | 'warning' | 'success' | 'default'> = {
  submitted: 'warning', processing: 'primary', need_more_info: 'warning', resolved: 'success', closed: 'default'
}
function search() { keyword.value = keywordInput.value.trim(); refresh() }
function clearSearch() { keywordInput.value = ''; keyword.value = ''; refresh() }
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="系统反馈" left-arrow @click-left="$router.back()"><template #right><van-icon name="plus" size="20" @click="router.push('/feedback/create')" /></template></van-nav-bar>
    <van-notice-bar v-if="usingMock" color="#8a6100" background="#fff7df" left-icon="info-o">当前反馈为开发环境演示数据</van-notice-bar>
    <van-search v-model="keywordInput" placeholder="搜索反馈编号或标题" shape="round" @search="search" @clear="clearSearch" />
    <van-tabs v-model:active="activeStatus" shrink sticky @change="refresh"><van-tab v-for="tab in tabs" :key="tab.key" :name="tab.key" :title="tab.label" /></van-tabs>
    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div v-for="item in list" :key="item.id" class="card feedback-row" @click="router.push(`/feedback/${item.id}`)">
          <div class="feedback-row__head"><strong>{{ item.title }}</strong><van-tag :type="statusType[item.status]" plain>{{ item.statusText }}</van-tag></div>
          <p>{{ item.feedbackNo }} · {{ item.categoryText }} · {{ item.severityText }}</p>
          <p v-if="item.publicReply" class="feedback-row__reply">回复：{{ item.publicReply }}</p>
          <time>{{ formatDateTime(item.updatedAt || item.createdAt) }}</time>
        </div>
        <van-empty v-if="!loading && error" :description="error" image="error"><van-button size="small" type="primary" @click="refresh">重新加载</van-button></van-empty>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无反馈记录"><van-button size="small" type="primary" round @click="router.push('/feedback/create')">新建反馈</van-button></van-empty>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.feedback-row{margin:10px 16px;padding:14px;cursor:pointer}.feedback-row__head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.feedback-row__head strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:14px}.feedback-row p{margin:8px 0 0;color:var(--h5-text-secondary);font-size:12px;line-height:1.5}.feedback-row__reply{display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical}.feedback-row time{display:block;margin-top:8px;color:var(--h5-text-placeholder);font-size:11px}
</style>
