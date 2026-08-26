<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showToast, type UploaderFileListItem } from 'vant'
import { createFeedback, getFeedbackOptions, uploadFeedbackAttachment, type FeedbackAttachment, type FeedbackOptions } from '@/api/feedback'
import { wasMockedEndpoint } from '@/api/mock'
import { createIdempotencyKey } from '@/utils/idempotency'

defineOptions({ name: 'FeedbackCreate' })
const router = useRouter()
const options = ref<FeedbackOptions>()
const optionLoading = ref(true)
const optionError = ref('')
const submitting = ref(false)
type FeedbackUploadItem = UploaderFileListItem & { result?: FeedbackAttachment }
const files = ref<FeedbackUploadItem[]>([])
const form = reactive({ category: '', severity: '', title: '', description: '', reproduceSteps: '' })
const usingMock = computed(() => wasMockedEndpoint('/zsjos/feedback'))

async function loadOptions() {
  optionLoading.value = true; optionError.value = ''
  try {
    options.value = await getFeedbackOptions()
    form.category ||= options.value.categories[0]?.value || ''
    form.severity ||= options.value.severities[0]?.value || ''
  } catch (cause) { optionError.value = cause instanceof Error ? cause.message : '反馈选项加载失败' }
  finally { optionLoading.value = false }
}

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

async function submit() {
  if (submitting.value) return
  if (!form.category || !form.severity) { showToast('请选择问题类型和影响程度'); return }
  if (form.title.trim().length < 2) { showToast('标题至少输入 2 个字'); return }
  if (form.description.trim().length < 5) { showToast('问题描述至少输入 5 个字'); return }
  if (files.value.some(item => item.status === 'uploading')) { showToast('图片正在上传'); return }
  if (files.value.some(item => item.status === 'failed')) { showToast('请删除或重新上传失败的图片'); return }
  submitting.value = true
  try {
    const result = await createFeedback({
      category: form.category, severity: form.severity, title: form.title.trim(), description: form.description.trim(),
      reproduceSteps: form.reproduceSteps.trim() || undefined,
      attachmentFileIds: files.value.flatMap(item => item.result ? [item.result.infraFileId] : []),
      clientContext: { route: router.currentRoute.value.fullPath, url: location.href, userAgent: navigator.userAgent, viewport: `${innerWidth}x${innerHeight}`, appVersion: 'zsjos-partner-h5' },
      idempotencyKey: createIdempotencyKey()
    })
    showSuccessToast('反馈已提交')
    await router.replace(`/feedback/${result.id}`)
  } finally { submitting.value = false }
}

onMounted(loadOptions)
</script>

<template>
  <div class="page-container feedback-form-page">
    <van-nav-bar title="新建反馈" left-arrow @click-left="$router.back()" />
    <van-notice-bar v-if="usingMock" color="#8a6100" background="#fff7df" left-icon="info-o">提交只保存为开发环境演示数据</van-notice-bar>
    <van-skeleton :loading="optionLoading" :row="8" style="padding:16px">
      <van-empty v-if="optionError" :description="optionError" image="error"><van-button size="small" type="primary" @click="loadOptions">重新加载</van-button></van-empty>
      <van-form v-else @submit="submit">
        <van-cell-group inset>
          <van-field label="问题类型" required><template #input><select v-model="form.category"><option v-for="item in options?.categories || []" :key="item.value" :value="item.value">{{ item.label }}</option></select></template></van-field>
          <van-field label="影响程度" required><template #input><select v-model="form.severity"><option v-for="item in options?.severities || []" :key="item.value" :value="item.value">{{ item.label }}</option></select></template></van-field>
          <van-field v-model="form.title" label="标题" maxlength="80" show-word-limit required placeholder="例如：客资详情打不开" />
          <van-field v-model="form.description" label="问题描述" type="textarea" rows="4" maxlength="2000" show-word-limit required placeholder="请描述遇到的问题" />
          <van-field v-model="form.reproduceSteps" label="复现步骤" type="textarea" rows="3" maxlength="2000" show-word-limit placeholder="从哪个页面进入，进行了什么操作" />
          <van-field label="问题截图"><template #input><van-uploader v-model="files" multiple :max-count="6" :after-read="afterRead" /></template></van-field>
        </van-cell-group>
        <div class="form-submit"><van-button block round type="primary" native-type="submit" :loading="submitting">提交反馈</van-button></div>
      </van-form>
    </van-skeleton>
  </div>
</template>

<style scoped>
.feedback-form-page select{width:100%;padding:6px 0;border:0;background:transparent;color:var(--h5-text-primary)}.form-submit{padding:20px 16px}
</style>
