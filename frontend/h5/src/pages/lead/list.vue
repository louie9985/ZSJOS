<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePageList } from '@/composables/usePageList'
import { getMyLeadPage, type LeadListItem } from '@/api/lead'
import { formatDateTime, formatLeadNo, formatLeadStatus } from '@/utils/format'

defineOptions({ name: 'LeadList' })

const router = useRouter()
const activeTab = ref('all')

const statusTabs = [
  { key: 'all', label: '全部' },
  { key: 'submitted', label: '已提交' },
  { key: 'valid', label: '有效' },
  { key: 'invalid', label: '无效' },
  { key: 'won', label: '已成交' }
]

const filterParams = computed(() => {
  const params: Record<string, unknown> = {}
  if (activeTab.value !== 'all') {
    params.status = activeTab.value
  }
  return params
})

const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  (params) => getMyLeadPage(params as Parameters<typeof getMyLeadPage>[0]),
  filterParams
)

function onTabChange() {
  refresh()
}

function goDetail(id: number) {
  router.push(`/lead/${id}`)
}

function goSubmit() {
  router.push('/lead/submit')
}

const statusType: Record<string, string> = {
  submitted: 'primary',
  valid: 'success',
  invalid: 'danger',
  suspended: 'warning',
  won: 'success',
  converted: 'primary',
  closed: 'default'
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="我的客资" />

    <van-tabs v-model:active="activeTab" @change="onTabChange" shrink sticky>
      <van-tab v-for="tab in statusTabs" :key="tab.key" :name="tab.key" :title="tab.label" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="loadMore"
      >
        <van-cell
          v-for="item in list"
          :key="item.id"
          :title="item.submittedName"
          :label="`${formatLeadNo(item.leadNo)} · ${formatDateTime(item.submittedAt)}`"
          is-link
          @click="goDetail(item.id)"
        >
          <template #value>
            <van-tag :type="(statusType[item.status] as any) || 'default'" plain>
              {{ formatLeadStatus(item.status) }}
            </van-tag>
          </template>
        </van-cell>

        <van-empty v-if="!loading && error" :description="error" image="error">
          <van-button type="primary" round size="small" @click="refresh">重新加载</van-button>
        </van-empty>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无客资记录">
          <van-button type="primary" round size="small" @click="goSubmit">去提交客资</van-button>
        </van-empty>
      </van-list>
    </van-pull-refresh>

    <!-- 悬浮提交按钮 -->
    <div class="fab-btn" @click="goSubmit">
      <van-icon name="plus" size="24" color="#fff" />
    </div>
  </div>
</template>

<style scoped>
.fab-btn {
  position: fixed;
  right: 20px;
  bottom: 80px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--h5-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(255, 107, 129, 0.4);
  z-index: 100;
}
</style>
