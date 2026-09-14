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
  if (item.bizType === 'lead') return `/lead/${item.bizId}${item.sceneCode === 'zsjos.lead.submitter_feedback_created' ? '#submitter-feedback' : ''}`
  if (item.bizType === 'sales_order') return '/lead/list'
  if (item.bizType === 'cashback') return '/earnings'
  if (item.bizType === 'withdrawal') return `/withdrawal/${item.bizId}`
  if (item.bizType === 'feedback') return `/feedback/${item.bizId}`
  return undefined
})

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  try {
    const message = await getMessageDetail(id)
    if (!message?.id) throw new Error('消息不存在或当前账号无权查看')
    detail.value = message
    if (!message.readStatus) {
      markRead([message.id])
        .then(() => {
          if (detail.value?.id === message.id) {
            detail.value = { ...detail.value, readStatus: true, readTime: new Date().toISOString() }
          }
        })
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
  <div class="page-container message-detail-page">
    <van-nav-bar title="消息详情" left-arrow @click-left="$router.back()" />
    <van-skeleton :loading="loading" :row="8" class="message-detail-page__skeleton">
      <van-empty v-if="loadError" :description="loadError" image="error">
        <van-button type="primary" round size="small" @click="loadDetail">重新加载</van-button>
      </van-empty>
      <article v-else-if="detail" class="card message-detail">
        <div class="message-detail__head">
          <h1>{{ detail.templateTitle }}</h1>
          <div class="message-detail__meta">
            <span>{{ detail.templateNickname || '中世健' }}</span>
            <span>{{ formatDateTime(detail.createTime) }}</span>
            <span v-if="detail.readTime">已读于 {{ formatDateTime(detail.readTime) }}</span>
          </div>
        </div>

        <section v-if="detail.templateSummary" class="message-detail__section message-detail__section--summary">
          <div class="message-detail__section-title">摘要</div>
          <p>{{ detail.templateSummary }}</p>
        </section>

        <section class="message-detail__section">
          <div class="message-detail__section-title">内容</div>
          <div class="message-detail__content">{{ detail.templateContent }}</div>
        </section>

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
.message-detail-page {
  min-height: 100vh;
  padding-bottom: 88px;
  background: transparent;
}

.message-detail-page__skeleton {
  padding: 16px;
}

.message-detail {
  margin-top: 12px;
  padding: 16px;
}

.message-detail__head {
  min-width: 0;
}

.message-detail h1 {
  color: var(--h5-text-primary);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.message-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 8px;
  color: var(--h5-text-placeholder);
  font-size: 12px;
  line-height: 1.4;
}

.message-detail__section {
  margin-top: 20px;
}

.message-detail__section-title {
  margin-bottom: 8px;
  color: var(--h5-text-primary);
  font-size: 14px;
  font-weight: 650;
}

.message-detail__section--summary p {
  padding: 12px;
  border-radius: 14px;
  background: var(--h5-glass-sunken);
  color: var(--h5-text-primary);
  font-size: 14px;
  line-height: 1.7;
}

.message-detail__content {
  color: var(--h5-text-primary);
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.message-detail__action {
  margin-top: 22px;
}
</style>
