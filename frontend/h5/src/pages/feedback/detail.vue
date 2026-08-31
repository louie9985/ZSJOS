<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showImagePreview, showSuccessToast, showToast, type UploaderFileListItem } from 'vant'
import {
  getFeedbackDetail,
  markFeedbackRead,
  replyFeedback,
  uploadFeedbackAttachment,
  type FeedbackAttachment,
  type FeedbackField,
  type FeedbackItem,
  type FeedbackStatus,
  type FeedbackType
} from '@/api/feedback'
import { createIdempotencyKey } from '@/utils/idempotency'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'FeedbackDetail' })

type FeedbackUploadItem = UploaderFileListItem & { result?: FeedbackAttachment }

const route = useRoute()
const id = Number(route.params.id)
const detail = ref<FeedbackItem>()
const loading = ref(true)
const loadError = ref('')
const replyContent = ref('')
const submitting = ref(false)
const files = ref<FeedbackUploadItem[]>([])

const canReply = computed(() => Boolean(detail.value?.canReply))

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
const authorLabel: Record<string, string> = {
  ADMIN: '平台处理人',
  EMPLOYEE: '提交人',
  PARTNER_ACCOUNT: '我'
}

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await getFeedbackDetail(id)
    detail.value = result
    if (result.unread) void markRead(result)
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '反馈详情加载失败'
  } finally {
    loading.value = false
  }
}

async function markRead(item: FeedbackItem) {
  try {
    await markFeedbackRead(item.id, { version: item.version, idempotencyKey: createIdempotencyKey() })
    if (detail.value?.id === item.id) {
      detail.value = { ...detail.value, unread: false, version: detail.value.version + 1 }
    }
  } catch {
    // 已读失败不影响详情浏览，后续刷新仍会按服务端状态显示。
  }
}

async function afterRead(input: unknown) {
  const items = Array.isArray(input) ? input : [input]
  for (const raw of items) {
    const item = raw as FeedbackUploadItem
    if (!item.file) continue
    item.status = 'uploading'
    item.message = '上传中'
    try {
      item.result = await uploadFeedbackAttachment(item.file)
      item.url = item.result.url
      item.status = 'done'
      item.message = ''
    } catch (cause) {
      item.status = 'failed'
      item.message = cause instanceof Error ? cause.message : '上传失败'
    }
  }
}

async function submitReply() {
  if (!detail.value || submitting.value) return
  if (replyContent.value.trim().length < 2) {
    showToast('回复内容至少输入 2 个字')
    return
  }
  if (files.value.some(item => item.status !== 'done')) {
    showToast('请等待上传完成或删除失败附件')
    return
  }
  submitting.value = true
  try {
    await replyFeedback(id, {
      version: detail.value.version,
      content: replyContent.value.trim(),
      attachmentIds: files.value.flatMap(item => item.result?.id ? [item.result.id] : []),
      idempotencyKey: createIdempotencyKey()
    })
    replyContent.value = ''
    files.value = []
    showSuccessToast('已回复')
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

function normalizeAttachments(value: unknown): FeedbackAttachment[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item): FeedbackAttachment[] => {
    if (!item || typeof item !== 'object') return []
    const raw = item as Record<string, unknown>
    const rawId = raw.id
    const id = typeof rawId === 'number' ? rawId : Number(rawId)
    if (!Number.isFinite(id)) return []
    return [{
      id,
      name: typeof raw.name === 'string' ? raw.name : undefined,
      type: typeof raw.type === 'string' ? raw.type : undefined,
      size: typeof raw.size === 'number' ? raw.size : undefined,
      url: typeof raw.url === 'string' ? raw.url : undefined
    }]
  })
}

function fieldAttachments(field: FeedbackField) {
  return normalizeAttachments(detail.value?.values?.[field.key])
}

function isImage(file: FeedbackAttachment) {
  return file.type?.startsWith('image/') || /\.(png|jpe?g|gif|webp|bmp)$/i.test(file.name || file.url || '')
}

function previewAttachments(attachments: FeedbackAttachment[], index: number) {
  const images = attachments.filter(item => item.url && isImage(item)).map(item => item.url!)
  const selected = attachments[index]
  if (!selected?.url) return
  if (!isImage(selected)) {
    window.open(selected.url, '_blank')
    return
  }
  const startPosition = Math.max(0, images.indexOf(selected.url))
  showImagePreview({ images, startPosition, closeable: true })
}

function formatFieldValue(field: FeedbackField) {
  const value = detail.value?.values?.[field.key]
  if (value == null || value === '') return '--'
  if (field.type === 'upload' || field.type === 'image') {
    const count = fieldAttachments(field).length
    return count ? `${count} 个附件` : '--'
  }
  if (field.type === 'dictionary') {
    if (typeof value === 'object' && value !== null && 'label' in value) {
      return String((value as { label?: unknown }).label || '--')
    }
    return field.options?.find(option => option.value === value)?.label || String(value)
  }
  if (field.type === 'rating') return `${value}/${field.maxRating || 5}`
  if (typeof value === 'object' && 'label' in value) return String((value as { label?: unknown }).label || '--')
  return String(value)
}

onMounted(loadDetail)
</script>

<template>
  <div class="page-container feedback-detail-page">
    <van-nav-bar title="反馈详情" left-arrow @click-left="$router.back()" />
    <van-skeleton :loading="loading" :row="8" style="padding:16px">
      <van-empty v-if="loadError" :description="loadError" image="error">
        <van-button size="small" type="primary" @click="loadDetail">重新加载</van-button>
      </van-empty>
      <template v-else-if="detail">
        <div class="card detail-heading">
          <div>
            <small>{{ detail.feedbackNo }} · {{ typeLabel[detail.feedbackType] || detail.feedbackType }}</small>
            <h1>{{ detail.title }}</h1>
          </div>
          <van-tag :type="statusType[detail.status]" plain>{{ statusLabel[detail.status] || detail.status }}</van-tag>
        </div>

        <div class="card">
          <div class="section-title">反馈信息</div>
          <van-cell-group :border="false">
            <van-cell title="提交时间" :value="formatDateTime(detail.createTime)" />
            <van-cell title="最近更新" :value="formatDateTime(detail.lastActivityAt)" />
            <van-cell v-if="detail.assigneeName" title="处理人" :value="detail.assigneeName" />
            <van-cell v-if="detail.supportTypeLabel" title="支持类型" :value="detail.supportTypeLabel" />
            <van-cell v-if="detail.rejectReason" title="驳回原因" :label="detail.rejectReason" />
          </van-cell-group>
        </div>

        <div v-if="detail.fields?.length" class="card">
          <div class="section-title">提交内容</div>
          <div v-for="field in detail.fields" :key="field.key" class="feedback-field">
            <div class="feedback-field__label">{{ field.label }}</div>
            <div v-if="field.type === 'upload' || field.type === 'image'" class="feedback-attachments">
              <button
                v-for="(file, index) in fieldAttachments(field)"
                :key="file.id"
                type="button"
                class="feedback-attachment"
                @click="previewAttachments(fieldAttachments(field), index)"
              >
                <img v-if="file.url && isImage(file)" :src="file.url" :alt="file.name || `附件 ${file.id}`">
                <van-icon v-else name="description-o" size="22" />
                <span>{{ file.name || `附件 ${file.id}` }}</span>
              </button>
              <span v-if="fieldAttachments(field).length === 0" class="feedback-field__empty">--</span>
            </div>
            <p v-else>{{ formatFieldValue(field) }}</p>
          </div>
        </div>

        <div v-if="detail.completedResult" class="card">
          <div class="section-title">处理结果</div>
          <p class="reply">{{ detail.completedResult }}</p>
          <div v-if="detail.resultAttachments?.length" class="feedback-attachments feedback-attachments--result">
            <button
              v-for="(file, index) in detail.resultAttachments"
              :key="file.id"
              type="button"
              class="feedback-attachment"
              @click="previewAttachments(detail.resultAttachments || [], index)"
            >
              <img v-if="file.url && isImage(file)" :src="file.url" :alt="file.name || `附件 ${file.id}`">
              <van-icon v-else name="description-o" size="22" />
              <span>{{ file.name || `附件 ${file.id}` }}</span>
            </button>
          </div>
        </div>

        <div v-if="detail.replies?.length" class="card">
          <div class="section-title">沟通记录</div>
          <div v-for="reply in detail.replies" :key="reply.id" class="reply-item">
            <div class="reply-item__head">
              <strong>{{ reply.authorName || authorLabel[reply.authorType] || '回复人' }}</strong>
              <time>{{ formatDateTime(reply.createTime) }}</time>
            </div>
            <p>{{ reply.content }}</p>
            <div v-if="reply.attachments?.length" class="feedback-attachments feedback-attachments--reply">
              <button
                v-for="(file, index) in reply.attachments"
                :key="file.id"
                type="button"
                class="feedback-attachment"
                @click="previewAttachments(reply.attachments, index)"
              >
                <img v-if="file.url && isImage(file)" :src="file.url" :alt="file.name || `附件 ${file.id}`">
                <van-icon v-else name="description-o" size="22" />
                <span>{{ file.name || `附件 ${file.id}` }}</span>
              </button>
            </div>
          </div>
        </div>

        <div v-if="canReply" class="card">
          <div class="section-title">回复反馈</div>
          <van-field v-model="replyContent" type="textarea" rows="3" maxlength="5000" show-word-limit placeholder="补充信息或回复处理结果" />
          <van-uploader v-model="files" multiple :max-count="20" :after-read="afterRead" />
          <van-button block round type="primary" :loading="submitting" class="reply-button" @click="submitReply">提交回复</van-button>
        </div>
      </template>
    </van-skeleton>
  </div>
</template>

<style scoped>
.feedback-detail-page{padding-bottom:20px}.detail-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding:16px}.detail-heading small{color:var(--h5-text-secondary)}.detail-heading h1{margin:5px 0 0;font-size:18px;line-height:1.4;letter-spacing:0}.section-title{margin-bottom:8px;font-size:15px;font-weight:600}.feedback-field{padding:10px 0;border-bottom:1px solid var(--h5-divider)}.feedback-field:last-child{border-bottom:0}.feedback-field__label{color:var(--h5-text-secondary);font-size:12px}.feedback-field p,.reply{margin:6px 0 0;font-size:13px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.feedback-field__empty{color:var(--h5-text-placeholder);font-size:13px}.feedback-attachments{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:8px}.feedback-attachments--result,.feedback-attachments--reply{margin-top:10px}.feedback-attachment{min-width:0;padding:0;border:0;background:transparent;color:inherit;text-align:left}.feedback-attachment img{display:block;width:100%;aspect-ratio:1;object-fit:cover;border-radius:6px}.feedback-attachment .van-icon{display:flex;width:100%;aspect-ratio:1;align-items:center;justify-content:center;border-radius:6px;background:var(--h5-bg);color:var(--h5-text-secondary)}.feedback-attachment span{display:block;overflow:hidden;margin-top:4px;color:var(--h5-text-secondary);font-size:11px;line-height:16px;text-overflow:ellipsis;white-space:nowrap}.reply-item{padding:10px 0;border-bottom:1px solid var(--h5-divider)}.reply-item:last-child{border-bottom:0}.reply-item__head{display:flex;align-items:center;justify-content:space-between;gap:10px}.reply-item__head strong{font-size:13px}.reply-item__head time{color:var(--h5-text-placeholder);font-size:11px}.reply-item p{margin:6px 0 0;color:var(--h5-text-secondary);font-size:13px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.reply-button{margin-top:12px}
</style>
