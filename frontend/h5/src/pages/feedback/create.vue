<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast, type UploaderFileListItem } from 'vant'
import {
  createFeedback,
  getFeedbackForm,
  getFeedbackPortal,
  uploadFeedbackAttachment,
  type FeedbackAttachment,
  type FeedbackField,
  type FeedbackForm,
  type FeedbackPortal,
  type FeedbackType
} from '@/api/feedback'
import { createIdempotencyKey } from '@/utils/idempotency'

defineOptions({ name: 'FeedbackCreate' })

type FeedbackUploadItem = UploaderFileListItem & { result?: FeedbackAttachment }

const FEEDBACK_TYPES: FeedbackType[] = ['REQUIREMENT', 'BUG', 'SUPPORT']
const route = useRoute()
const router = useRouter()
const portal = ref<FeedbackPortal>()
const form = ref<FeedbackForm>()
const selectedType = ref<FeedbackType>(normalizeFeedbackType(route.query.type) || 'BUG')
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)
const formValues = reactive<Record<string, string | number | undefined>>({})
const uploadFiles = reactive<Record<string, FeedbackUploadItem[]>>({})

const selectedEntry = computed(() => portal.value?.entries.find(item => item.feedbackType === selectedType.value))
const visibleEntries = computed(() => portal.value?.entries || [])

function normalizeFeedbackType(value: unknown): FeedbackType | undefined {
  const text = Array.isArray(value) ? String(value[0] || '') : String(value || '')
  const normalized = text.trim().replace('-', '_').toUpperCase()
  return FEEDBACK_TYPES.includes(normalized as FeedbackType) ? normalized as FeedbackType : undefined
}

function resetValues(fields: FeedbackField[]) {
  Object.keys(formValues).forEach(key => delete formValues[key])
  Object.keys(uploadFiles).forEach(key => delete uploadFiles[key])
  fields.forEach((field) => {
    if (field.type === 'upload' || field.type === 'image') {
      uploadFiles[field.key] = []
    } else {
      formValues[field.key] = undefined
    }
  })
}

async function loadData(type = selectedType.value) {
  loading.value = true
  loadError.value = ''
  form.value = undefined
  try {
    portal.value = await getFeedbackPortal()
    const requested = portal.value.entries.find(item => item.feedbackType === type)
    const fallback = portal.value.entries.find(item => item.open) || portal.value.entries[0]
    selectedType.value = requested?.feedbackType || fallback?.feedbackType || type
    if (!selectedEntry.value?.open) {
      resetValues([])
      return
    }
    const result = await getFeedbackForm(selectedType.value)
    form.value = result
    resetValues(result.fields || [])
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '反馈表单加载失败'
  } finally {
    loading.value = false
  }
}

async function selectType(type: FeedbackType | string) {
  const nextType = normalizeFeedbackType(type)
  if (!nextType || selectedType.value === nextType) return
  selectedType.value = nextType
  await loadData(nextType)
}

async function afterRead(fieldKey: string, input: unknown) {
  const items = Array.isArray(input) ? input : [input]
  for (const raw of items) {
    const item = raw as FeedbackUploadItem
    if (!item.file) continue
    item.status = 'uploading'
    item.message = '上传中'
    try {
      const result = await uploadFeedbackAttachment(item.file)
      item.result = result
      item.url = result.url
      item.status = 'done'
      item.message = ''
    } catch (cause) {
      item.status = 'failed'
      item.message = cause instanceof Error ? cause.message : '上传失败'
    }
  }
  uploadFiles[fieldKey] ||= []
}

function validateField(field: FeedbackField): boolean {
  if (field.type === 'upload' || field.type === 'image') {
    const files = uploadFiles[field.key] || []
    if (files.some(item => item.status === 'uploading')) {
      showToast(`${field.label}正在上传`)
      return false
    }
    if (files.some(item => item.status === 'failed')) {
      showToast(`请删除或重新上传失败的${field.label}`)
      return false
    }
    if (field.required && !files.some(item => item.result?.id)) {
      showToast(`请上传${field.label}`)
      return false
    }
    return true
  }
  const value = formValues[field.key]
  const text = value == null ? '' : String(value).trim()
  if (field.required && !text) {
    showToast(`请填写${field.label}`)
    return false
  }
  if (field.maxLength && text.length > field.maxLength) {
    showToast(`${field.label}超过长度限制`)
    return false
  }
  return true
}

function serializeValues(fields: FeedbackField[]) {
  const result: Record<string, unknown> = {}
  for (const field of fields) {
    if (field.type === 'upload' || field.type === 'image') {
      const ids = (uploadFiles[field.key] || []).flatMap(item => item.result?.id ? [item.result.id] : [])
      if (ids.length || field.required) result[field.key] = ids
      continue
    }
    const value = formValues[field.key]
    const text = value == null ? '' : String(value).trim()
    if (!text && !field.required) continue
    result[field.key] = field.type === 'rating' ? Number(value) : text
  }
  return result
}

function ratingValue(fieldKey: string): number | undefined {
  const value = formValues[fieldKey]
  return typeof value === 'number' ? value : undefined
}

function setRatingValue(fieldKey: string, value: number) {
  formValues[fieldKey] = value
}

async function submit() {
  if (submitting.value || !form.value) return
  if (!selectedEntry.value?.open || !form.value.open) {
    showToast(selectedEntry.value?.unavailableReason || form.value.unavailableReason || '该反馈入口暂未开放')
    return
  }
  if (!form.value.fields.every(validateField)) return
  submitting.value = true
  try {
    const id = await createFeedback(selectedType.value, {
      configVersion: form.value.configVersion,
      values: serializeValues(form.value.fields),
      idempotencyKey: createIdempotencyKey()
    })
    showSuccessToast('反馈已提交')
    await router.replace(`/feedback/${id}`)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadData(selectedType.value)
})
</script>

<template>
  <div class="page-container feedback-form-page">
    <van-nav-bar title="新建反馈" left-arrow @click-left="$router.back()" />
    <van-skeleton :loading="loading" :row="8" style="padding:16px">
      <van-empty v-if="loadError" :description="loadError" image="error">
        <van-button size="small" type="primary" @click="loadData(selectedType)">重新加载</van-button>
      </van-empty>
      <template v-else>
        <van-tabs
          v-if="visibleEntries.length > 1"
          v-model:active="selectedType"
          shrink
          sticky
          @change="selectType"
        >
          <van-tab v-for="entry in visibleEntries" :key="entry.feedbackType" :name="entry.feedbackType" :title="entry.title" />
        </van-tabs>

        <van-empty
          v-if="selectedEntry && !selectedEntry.open"
          :description="selectedEntry.unavailableReason || '该反馈入口暂未开放'"
          image="default"
        />
        <van-empty
          v-else-if="form && !form.open"
          :description="form.unavailableReason || '该反馈表单暂未开放'"
          image="default"
        />
        <van-form v-else-if="form" class="feedback-form" @submit="submit">
          <van-cell-group inset>
            <template v-for="field in form.fields" :key="field.key">
              <van-field
                v-if="field.type === 'textarea'"
                v-model="formValues[field.key]"
                :label="field.label"
                type="textarea"
                rows="4"
                :maxlength="field.maxLength"
                :show-word-limit="Boolean(field.maxLength)"
                :required="field.required"
              />
              <van-field
                v-else-if="field.type === 'text'"
                v-model="formValues[field.key]"
                :label="field.label"
                :maxlength="field.maxLength"
                :show-word-limit="Boolean(field.maxLength)"
                :required="field.required"
              />
              <van-field v-else-if="field.type === 'date'" :label="field.label" :required="field.required">
                <template #input>
                  <input v-model="formValues[field.key]" class="feedback-native-input" type="date">
                </template>
              </van-field>
              <van-field v-else-if="field.type === 'dictionary'" :label="field.label" :required="field.required">
                <template #input>
                  <select v-model="formValues[field.key]" class="feedback-native-input">
                    <option value="">请选择</option>
                    <option v-for="option in field.options || []" :key="option.value" :value="option.value">{{ option.label }}</option>
                  </select>
                </template>
              </van-field>
              <van-field v-else-if="field.type === 'rating'" :label="field.label" :required="field.required">
                <template #input>
                  <van-rate
                    :model-value="ratingValue(field.key)"
                    :count="field.maxRating || 5"
                    @update:model-value="value => setRatingValue(field.key, value)"
                  />
                </template>
              </van-field>
              <van-field v-else :label="field.label" :required="field.required">
                <template #input>
                  <van-uploader
                    v-model="uploadFiles[field.key]"
                    multiple
                    :accept="field.type === 'image' ? 'image/*' : undefined"
                    :max-count="20"
                    :after-read="input => afterRead(field.key, input)"
                  />
                </template>
              </van-field>
            </template>
          </van-cell-group>
          <div class="form-submit">
            <van-button block round type="primary" native-type="submit" :loading="submitting">提交反馈</van-button>
          </div>
        </van-form>
      </template>
    </van-skeleton>
  </div>
</template>

<style scoped>
.feedback-form-page { padding-bottom: 20px; }
.feedback-form { margin-top: 10px; }
.feedback-native-input {
  width: 100%;
  padding: 6px 0;
  border: 0;
  background: transparent;
  color: var(--h5-text-primary);
  font: inherit;
}
.form-submit { padding: 20px 16px; }
</style>
