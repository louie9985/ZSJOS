<script setup lang="ts">
import { useRouter } from 'vue-router'
import { usePageList } from '@/composables/usePageList'
import { getMessagePage, markRead, type MessageItem } from '@/api/message'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'Messages' })

const router = useRouter()

const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  (params) => getMessagePage(params),
  undefined,
  { immediate: true }
)

function goDetail(item: MessageItem) {
  if (!item.readStatus) markRead([item.id]).catch(() => {})
  router.push(`/messages/${item.id}`)
}

const typeIcon: Record<string, string> = {
  lead: 'orders-o',
  cashback: 'gold-coin-o',
  withdrawal: 'balance-list-o',
  appeal: 'info-o'
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="消息" />

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <van-cell
          v-for="item in list"
          :key="item.id"
          :title="item.templateTitle"
          :label="formatDateTime(item.createTime)"
          clickable
          @click="goDetail(item)"
        >
          <template #icon>
            <div class="msg-icon-wrap">
              <van-icon :name="typeIcon[item.bizType || ''] || 'bell'" size="20" color="var(--h5-primary)" />
              <div v-if="!item.readStatus" class="msg-dot" />
            </div>
          </template>
          <template #value>
            <span class="msg-preview">{{ item.templateSummary || item.templateContent }}</span>
          </template>
        </van-cell>

        <van-empty v-if="!loading && !error && list.length === 0" description="暂无消息" image="default" />
        <van-empty v-if="!loading && error" :description="error" image="error">
          <van-button size="small" type="primary" @click="refresh">重新加载</van-button>
        </van-empty>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.msg-icon-wrap {
  position: relative;
  margin-right: 12px;
  display: flex;
  align-items: center;
}
.msg-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--h5-danger);
}
.msg-preview {
  font-size: 12px;
  color: var(--h5-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
