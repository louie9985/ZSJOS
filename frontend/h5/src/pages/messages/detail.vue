<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMessageDetail, markRead, type MessageItem } from '@/api/message'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'MessageDetail' })

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const detail = ref<MessageItem>()
const loading = ref(true)
const loadError = ref('')

const businessTarget = computed(() => {
  const item = detail.value
  if (!item?.bizId || item.actionType !== 'business_detail') return undefined
  if (item.bizType === 'lead') return `/lead/${item.bizId}`
  if (item.bizType === 'cashback') return '/earnings'
  if (item.bizType === 'withdrawal') return `/withdrawal/${item.bizId}`
  return undefined
})

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  try {
    const message = await getMessageDetail(id)
    detail.value = message
    if (!message.readStatus) {
      markRead([message.id])
        .then(() => { detail.value = { ...message, readStatus: true } })
        .catch(() => {})
    }
  } catch (cause) {
    detail.value = undefined
    loadError.value = cause instanceof Error ? cause.message : '消息详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="消息详情" left-arrow @click-left="$router.back()" />
    <van-skeleton :loading="loading" :row="8" style="padding: 16px">
      <van-empty v-if="loadError" :description="loadError" image="error">
        <van-button type="primary" round size="small" @click="loadDetail">重新加载</van-button>
      </van-empty>
      <article v-else-if="detail" class="card message-detail">
        <h1>{{ detail.templateTitle }}</h1>
        <div class="message-detail__meta">
          <span>{{ detail.templateNickname || '中世健' }}</span>
          <time>{{ formatDateTime(detail.createTime) }}</time>
        </div>
        <p v-if="detail.templateSummary" class="message-detail__summary">{{ detail.templateSummary }}</p>
        <div class="message-detail__content">{{ detail.templateContent }}</div>
        <van-button
          v-if="businessTarget"
          block
          round
          type="primary"
          class="message-detail__action"
          @click="router.push(businessTarget)"
        >查看相关业务</van-button>
      </article>
    </van-skeleton>
  </div>
</template>

<style scoped>
.message-detail{margin-top:16px;padding:20px}.message-detail h1{font-size:20px;line-height:1.4;letter-spacing:0}.message-detail__meta{display:flex;justify-content:space-between;gap:12px;margin-top:10px;color:var(--h5-text-secondary);font-size:12px}.message-detail__summary{margin-top:20px;padding:12px;border-left:3px solid var(--h5-primary);background:var(--h5-bg);color:var(--h5-text-secondary);font-size:13px;line-height:1.6}.message-detail__content{margin-top:20px;color:var(--h5-text-primary);font-size:15px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}.message-detail__action{margin-top:28px}
</style>
