<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { createComplaint, uploadLeadAttachment } from '@/api/lead'
import ImageUploader from '@/components/ImageUploader.vue'

defineOptions({ name: 'LeadComplaint' })

const route = useRoute()
const router = useRouter()
const leadId = Number(route.params.id)

const reason = ref('')
const submitting = ref(false)
const uploaderRef = ref<InstanceType<typeof ImageUploader>>()

async function handleSubmit() {
  if (!reason.value.trim()) { showToast('请输入投诉原因'); return }
  if (uploaderRef.value?.isUploading()) { showToast('图片还在上传中'); return }
  if (uploaderRef.value?.hasError()) { showToast('有图片上传失败，请删除或重试'); return }

  submitting.value = true
  try {
    const evidenceFileIds = uploaderRef.value?.getUploadedIds() || []
    await createComplaint(leadId, {
      reason: reason.value.trim(),
      evidenceFileIds,
      idempotencyKey: crypto.randomUUID()
    })
    showSuccessToast('投诉已提交')
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
    <van-nav-bar title="投诉" left-arrow @click-left="$router.back()" />

    <div class="card">
      <p style="font-size: 13px; color: var(--h5-text-secondary); margin-bottom: 16px;">
        如果您对客资处理有异议，可以在此提交投诉。投诉将通知相关负责人处理。
      </p>

      <van-field
        v-model="reason"
        label="投诉原因"
        type="textarea"
        placeholder="请详细描述投诉原因"
        required
        maxlength="1000"
        show-word-limit
        rows="4"
        autosize
      />

      <div style="padding: 12px 16px;">
        <div style="font-size: 14px; margin-bottom: 8px; color: var(--h5-text-primary);">证据图片</div>
        <p style="font-size: 12px; color: var(--h5-text-secondary); margin-bottom: 10px;">可选，最多 9 张</p>
        <ImageUploader ref="uploaderRef" :max-count="9" />
      </div>
    </div>

    <div style="padding: 16px;">
      <van-button type="primary" block round :loading="submitting" @click="handleSubmit">
        提交投诉
      </van-button>
    </div>
  </div>
</template>
