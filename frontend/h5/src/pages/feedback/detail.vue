<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showImagePreview, showSuccessToast, showToast, type UploaderFileListItem } from 'vant'
import { getFeedbackDetail, supplementFeedback, uploadFeedbackAttachment, type FeedbackAttachment, type FeedbackItem } from '@/api/feedback'
import { wasMockedEndpoint } from '@/api/mock'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'FeedbackDetail' })
const route = useRoute()
const id = Number(route.params.id)
const detail = ref<FeedbackItem>()
const loading = ref(true)
const loadError = ref('')
const supplement = ref('')
const submitting = ref(false)
type FeedbackUploadItem = UploaderFileListItem & { result?: FeedbackAttachment }
const files = ref<FeedbackUploadItem[]>([])
const usingMock = computed(() => wasMockedEndpoint('/zsjos/feedback'))
const canSupplement = computed(() => detail.value && !['resolved', 'closed'].includes(detail.value.status))

async function loadDetail() { loading.value = true; loadError.value = ''; try { detail.value = await getFeedbackDetail(id) } catch (cause) { loadError.value = cause instanceof Error ? cause.message : '反馈详情加载失败' } finally { loading.value = false } }
async function afterRead(input: unknown) {
  const items = Array.isArray(input) ? input : [input]
  for (const raw of items) {
    const item = raw as FeedbackUploadItem
    if (!item.file) continue
    item.status = 'uploading'; item.message = '上传中'
    try { item.result = await uploadFeedbackAttachment(item.file); item.url = item.result.fileUrl; item.status = 'done'; item.message = '' }
    catch (cause) { item.status = 'failed'; item.message = cause instanceof Error ? cause.message : '上传失败' }
  }
}
async function submitSupplement() {
  if (supplement.value.trim().length < 2) { showToast('补充说明至少输入 2 个字'); return }
  if (files.value.some(item => item.status !== 'done')) { showToast('请等待上传完成或删除失败图片'); return }
  submitting.value = true
  try {
    detail.value = await supplementFeedback(id, { content: supplement.value.trim(), attachmentFileIds: files.value.flatMap(item => item.result ? [item.result.infraFileId] : []), idempotencyKey: createIdempotencyKey() })
    supplement.value = ''; files.value = []; showSuccessToast('已补充')
  } finally { submitting.value = false }
}
function preview(index: number) { if (detail.value) showImagePreview({ images: detail.value.attachments.map(item => item.fileUrl), startPosition: index, closeable: true }) }
onMounted(loadDetail)
</script>

<template>
  <div class="page-container feedback-detail-page">
    <van-nav-bar title="反馈详情" left-arrow @click-left="$router.back()" />
    <van-notice-bar v-if="usingMock" color="#8a6100" background="#fff7df" left-icon="info-o">当前反馈为开发环境演示数据</van-notice-bar>
    <van-skeleton :loading="loading" :row="8" style="padding:16px">
      <van-empty v-if="loadError" :description="loadError" image="error"><van-button size="small" type="primary" @click="loadDetail">重新加载</van-button></van-empty>
      <template v-else-if="detail">
        <div class="card detail-heading"><div><small>{{ detail.feedbackNo }}</small><h1>{{ detail.title }}</h1></div><van-tag plain>{{ detail.statusText }}</van-tag></div>
        <div class="card"><div class="section-title">反馈信息</div><van-cell-group :border="false"><van-cell title="问题类型" :value="detail.categoryText" /><van-cell title="影响程度" :value="detail.severityText" /><van-cell title="提交时间" :value="formatDateTime(detail.createdAt)" /></van-cell-group><div class="content-block"><strong>问题描述</strong><p>{{ detail.description }}</p></div><div v-if="detail.reproduceSteps" class="content-block"><strong>复现步骤</strong><p>{{ detail.reproduceSteps }}</p></div><div v-if="detail.attachments.length" class="image-grid"><button v-for="(item,index) in detail.attachments" :key="item.infraFileId" type="button" @click="preview(index)"><img :src="item.fileUrl" :alt="item.originalName" /></button></div></div>
        <div v-if="detail.publicReply" class="card"><div class="section-title">处理回复</div><p class="reply">{{ detail.publicReply }}</p></div>
        <div v-if="detail.events.length" class="card"><div class="section-title">处理进度</div><van-steps direction="vertical" :active="-1"><van-step v-for="event in detail.events" :key="event.eventId"><h3>{{ event.eventTypeText }}</h3><p v-if="event.content">{{ event.content }}</p><p>{{ formatDateTime(event.createdAt) }}</p></van-step></van-steps></div>
        <div v-if="canSupplement" class="card"><div class="section-title">补充说明</div><van-field v-model="supplement" type="textarea" rows="3" maxlength="2000" show-word-limit placeholder="补充问题信息或处理结果" /><van-uploader v-model="files" multiple :max-count="6" :after-read="afterRead" /><van-button block round type="primary" :loading="submitting" class="supplement-button" @click="submitSupplement">提交补充</van-button></div>
      </template>
    </van-skeleton>
  </div>
</template>

<style scoped>
.feedback-detail-page{padding-bottom:20px}.detail-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:16px}.detail-heading small{color:var(--h5-text-secondary)}.detail-heading h1{margin:5px 0 0;font-size:18px;line-height:1.4;letter-spacing:0}.section-title{margin-bottom:8px;font-size:15px;font-weight:600}.content-block{padding:10px 16px}.content-block strong{font-size:13px}.content-block p,.reply{margin:6px 0 0;font-size:13px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.image-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;padding:10px 16px}.image-grid button{padding:0;border:0;background:transparent}.image-grid img{display:block;width:100%;aspect-ratio:1;object-fit:cover;border-radius:6px}.card :deep(.van-step h3){font-size:14px;letter-spacing:0}.card :deep(.van-step p){margin:4px 0;color:var(--h5-text-secondary);font-size:12px}.supplement-button{margin-top:12px}
</style>
