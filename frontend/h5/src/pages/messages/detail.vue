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
  <div class="page-container message-detail-page">
    <van-nav-bar title="消息详情" left-arrow @click-left="$router.back()" />
    <van-skeleton :loading="loading" :row="8" class="message-detail-page__skeleton">
      <van-empty v-if="loadError" :description="loadError" image="error">
        <van-button type="primary" round size="small" @click="loadDetail">重新加载</van-button>
      </van-empty>
      <article v-else-if="detail" class="card message-detail">
        <div class="message-detail__head">
          <div class="message-detail__title-wrap">
            <h1>{{ detail.templateTitle }}</h1>
            <div class="message-detail__meta">
              <span>{{ detail.templateNickname || '中世健' }}</span>
              <span>{{ formatDateTime(detail.createTime) }}</span>
            </div>
          </div>
          <div class="message-detail__chips">
            <span class="message-detail__chip">消息</span>
            <span class="message-detail__chip message-detail__chip--muted">{{ detail.bizType || '系统' }}</span>
          </div>
        </div>

        <div class="message-detail__status">
          <span>消息类型：{{ detail.bizType || '系统' }}</span>
          <span v-if="detail.readTime">已读于 {{ formatDateTime(detail.readTime) }}</span>
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
  background: var(--h5-bg);
}

.message-detail-page__skeleton {
  padding: 16px;
}

.message-detail {
  margin-top: 12px;
  padding: 16px;
}

.message-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.message-detail__title-wrap {
  min-width: 0;
}

.message-detail h1 {
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 8px;
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.message-detail__chips {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.message-detail__chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 11px;
  font-weight: 600;
}

.message-detail__chip--muted {
  background: var(--h5-bg);
  color: var(--h5-text-secondary);
}

.message-detail__status {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--h5-primary) 6%, var(--h5-card-bg));
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.message-detail__section {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--h5-divider);
}

.message-detail__section--summary {
  margin-top: 16px;
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
  background: var(--h5-bg);
  color: var(--h5-text-secondary);
  font-size: 13px;
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
