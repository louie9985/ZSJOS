<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePageList } from '@/composables/usePageList'
import { getMessagePage, markRead, getUnreadCount, type MessageItem } from '@/api/message'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'Messages' })

const router = useRouter()
const unreadCount = ref(0)

const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList(
  (params) => getMessagePage(params),
  undefined,
  { immediate: true }
)

onMounted(async () => {
  try {
    unreadCount.value = await getUnreadCount()
  } catch {
    unreadCount.value = 0
  }
})

function goDetail(item: MessageItem) {
  // 标记已读
  if (!item.readStatus) {
    markRead([item.id]).catch(() => {})
    item.readStatus = true
    if (unreadCount.value > 0) unreadCount.value--
  }

  // 根据 bizType 跳转到对应业务页
  if (item.bizType === 'lead' && item.bizId) {
    router.push(`/lead/${item.bizId}`)
  } else if (item.bizType === 'cashback' && item.bizId) {
    router.push('/earnings')
  } else if (item.bizType === 'withdrawal' && item.bizId) {
    router.push(`/withdrawal/${item.bizId}`)
  }
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
