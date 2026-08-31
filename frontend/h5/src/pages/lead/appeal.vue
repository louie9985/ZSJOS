<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { getLeadAppeals, submitAppeal, uploadAppealAttachment, type LeadAppealItem } from '@/api/lead'
import { formatDateTime } from '@/utils/format'
import { createIdempotencyKey } from '@/utils/idempotency'

defineOptions({ name: 'LeadAppeal' })

const route = useRoute()
const router = useRouter()
const leadId = Number(route.params.id)

const reason = ref('')
const submitting = ref(false)
const loading = ref(true)
const loadError = ref('')
const appeals = ref<LeadAppealItem[]>([])

// 图片上传状态
const fileList = ref<{ id: string; url: string; status: string; infraFileId?: number }[]>([])
const uploading = ref(false)

onMounted(async () => {
  loadError.value = ''
  try {
    appeals.value = await getLeadAppeals(leadId)
  } catch (cause) {
    appeals.value = []
    loadError.value = cause instanceof Error ? cause.message : '申诉记录加载失败'
  } finally {
    loading.value = false
  }
})

async function loadAppeals() {
  loading.value = true
  loadError.value = ''
  try {
    appeals.value = await getLeadAppeals(leadId)
  } catch (cause) {
    appeals.value = []
    loadError.value = cause instanceof Error ? cause.message : '申诉记录加载失败'
  } finally {
    loading.value = false
  }
}

const lastAppeal = () => appeals.value[appeals.value.length - 1]
const canAppeal = () => appeals.value.length === 0 || lastAppeal()?.canSubmitNextRound === true

const stageMap: Record<string, string> = {
  sales_manager: '销售主管复核', quality: '质控仲裁', chairman: '最终裁定',
  sales_manager_reviewing: '销售主管复核', quality_reviewing: '质控仲裁', chairman_reviewing: '最终裁定'
}

const statusMap: Record<string, { text: string; color: string }> = {
  submitted: { text: '审核中', color: 'var(--h5-info)' },
  sales_manager_reviewing: { text: '主管复核中', color: 'var(--h5-warning)' },
  quality_reviewing: { text: '质控仲裁中', color: 'var(--h5-warning)' },
  chairman_reviewing: { text: '最终裁定中', color: 'var(--h5-warning)' },
  overturned: { text: '已改判', color: 'var(--h5-success)' },
  upheld: { text: '已维持', color: 'var(--h5-danger)' },
  withdrawn: { text: '已撤回', color: 'var(--h5-text-secondary)' }
}

// 简易图片上传（使用申诉专用接口）
async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files) return
  for (let i = 0; i < files.length; i++) {
    if (fileList.value.length >= 9) break
    const file = files[i]
    const id = createIdempotencyKey()
    const url = URL.createObjectURL(file)
    fileList.value.push({ id, url, status: 'uploading' })
    try {
      const result = await uploadAppealAttachment(file)
      const item = fileList.value.find(f => f.id === id)
      if (item) { item.status = 'done'; item.infraFileId = result.infraFileId; item.url = result.fileUrl }
    } catch {
      const item = fileList.value.find(f => f.id === id)
      if (item) item.status = 'error'
    }
  }
  input.value = ''
}

function removeFile(id: string) {
  fileList.value = fileList.value.filter(f => f.id !== id)
}

async function handleSubmit() {
  if (!reason.value.trim()) { showToast('请输入申诉理由'); return }
  if (fileList.value.some(f => f.status === 'uploading')) { showToast('图片还在上传中'); return }

  submitting.value = true
  try {
    const attachments = fileList.value
      .filter(f => f.status === 'done' && f.infraFileId)
      .map(f => ({ infraFileId: f.infraFileId! }))

    await submitAppeal(leadId, {
      reason: reason.value.trim(),
      idempotencyKey: createIdempotencyKey(),
      attachments
    })
    showSuccessToast('申诉已提交')
    router.back()
  } catch {
    // 拦截器已处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="申诉" left-arrow @click-left="$router.back()" />

    <van-skeleton :loading="loading" :row="4" style="padding: 16px;">
      <van-empty v-if="loadError" :description="loadError" image="error">
        <van-button size="small" type="primary" @click="loadAppeals">重新加载</van-button>
      </van-empty>
      <!-- 历史申诉记录 -->
      <div v-else-if="appeals.length > 0" class="card">
        <div class="section-title">申诉记录（{{ appeals.length }}）</div>
        <div v-for="appeal in appeals" :key="appeal.id" class="appeal-record">
          <div class="appeal-record__header">
            <span class="appeal-record__round">第 {{ appeal.roundNo }} 次<span v-if="appeal.reviewStage"> · {{ stageMap[appeal.reviewStage] || appeal.reviewStage }}</span></span>
            <span
              class="appeal-record__status"
              :style="{ color: statusMap[appeal.status]?.color }"
            >
              {{ statusMap[appeal.status]?.text || appeal.status }}
            </span>
          </div>
          <div class="appeal-record__reason">{{ appeal.reason }}</div>
          <div v-if="appeal.applicantUserName || appeal.reviewerUserName" class="appeal-record__meta">申请人：{{ appeal.applicantUserName || '--' }} · 处理人：{{ appeal.reviewerUserName || '待处理' }}</div>
          <div v-if="appeal.evidence?.length" class="appeal-record__meta">申诉证据：{{ appeal.evidence.length }} 个</div>
          <div v-if="appeal.invalidReasonSnapshot || appeal.invalidDescriptionSnapshot" class="appeal-record__meta">原判定：{{ appeal.invalidReasonSnapshot || '无原因' }}{{ appeal.invalidDescriptionSnapshot ? ` · ${appeal.invalidDescriptionSnapshot}` : '' }}</div>
          <div v-if="appeal.invalidEvidenceSnapshot?.length" class="appeal-record__meta">原判定证据：{{ appeal.invalidEvidenceSnapshot.length }} 个</div>
          <div v-if="appeal.decisionReason" class="appeal-record__decision">裁决：{{ appeal.decisionReason }}</div>
          <div v-if="appeal.decisionEvidence?.length" class="appeal-record__meta">裁决证据：{{ appeal.decisionEvidence.length }} 个</div>
          <div class="appeal-record__time">{{ formatDateTime(appeal.submittedAt) }}</div>
        </div>
      </div>

      <!-- 提交新申诉 -->
      <div v-else-if="canAppeal()" class="card">
        <div class="section-title">
          发起第 {{ (lastAppeal()?.roundNo || 0) + 1 }} 次申诉
        </div>

        <van-field
          v-model="reason"
          type="textarea"
          placeholder="请详细说明申诉理由"
          required
          maxlength="1000"
          show-word-limit
          rows="4"
          autosize
        />

        <div style="padding: 12px 16px;">
          <div style="font-size: 14px; margin-bottom: 8px;">证据图片</div>
          <div class="appeal-images">
            <div v-for="item in fileList" :key="item.id" class="appeal-images__item">
              <img :src="item.url" alt="" />
              <div v-if="item.status === 'uploading'" class="appeal-images__mask">
                <van-loading size="16" color="#fff" />
              </div>
              <van-icon name="clear" class="appeal-images__delete" size="16" @click="removeFile(item.id)" />
            </div>
            <label v-if="fileList.length < 9" class="appeal-images__add">
              <van-icon name="photograph" size="20" color="var(--h5-text-placeholder)" />
              <input type="file" accept="image/*" multiple hidden @change="onFileChange" />
            </label>
          </div>
        </div>

        <div style="padding: 12px 16px;">
          <van-button type="primary" block round :loading="submitting" @click="handleSubmit">
            提交申诉
          </van-button>
        </div>
      </div>

      <!-- 已用完申诉次数 -->
      <div v-if="!canAppeal() && !loading" class="card" style="text-align: center; padding: 32px;">
        <van-icon name="info-o" size="40" color="var(--h5-text-placeholder)" />
        <p style="margin-top: 8px; color: var(--h5-text-secondary);">当前没有可提交的下一轮申诉</p>
      </div>
    </van-skeleton>
  </div>
</template>

<style scoped>
.section-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--h5-text-primary);
  margin-bottom: 12px;
}

.appeal-record {
  padding: 10px 0;
  border-bottom: 1px solid var(--h5-divider);
}
.appeal-record:last-child {
  border-bottom: none;
}
.appeal-record__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.appeal-record__round {
  font-size: 13px;
  font-weight: 500;
  color: var(--h5-text-primary);
}
.appeal-record__status {
  font-size: 12px;
}
.appeal-record__reason {
  font-size: 13px;
  color: var(--h5-text-secondary);
  margin: 4px 0;
}
.appeal-record__time {
  font-size: 11px;
  color: var(--h5-text-placeholder);
}

.appeal-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.appeal-images__item {
  width: 70px;
  height: 70px;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
}
.appeal-images__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.appeal-images__mask {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}
.appeal-images__delete {
  position: absolute;
  top: 2px;
  right: 2px;
  color: #fff;
  background: rgba(0,0,0,0.4);
  border-radius: 50%;
}
.appeal-images__add {
  width: 70px;
  height: 70px;
  border-radius: 6px;
  border: 1px dashed var(--h5-border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
</style>
