<script setup lang="ts">
import '../../../../shared/exam-collection-body.css'
import { examCollectionBodyHtml } from '../../../../shared/examCollectionBody'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import CollectionImages from '../studentInfo/CollectionImages.vue'
import { CollectionError, type CollectionImage } from '@/api/studentInfo'
import { examCollectionApi, type ExamCollectionRuntime, type ExamCollectionSession, type ExamSubmissionCommand } from '@/api/examCollection'
import { createIdempotencyKey } from '@/utils/idempotency'
import { effectiveExamFields, validateExamCollection } from '@/utils/examCollectionValidation'

const route = useRoute()
const token = computed(() => new URLSearchParams(route.hash.slice(1)).get('token') || '')
const runtime = ref<ExamCollectionRuntime>()
const session = ref<ExamCollectionSession>()
const answers = ref<Record<string, string>>({})
const images = ref<CollectionImage[]>([])
const fieldErrors = ref<Record<string, string>>({})
const uploadBusy = ref<Record<string, boolean>>({}), uploadFailed = ref<Record<string, boolean>>({})
const loading = ref(false), saving = ref(false), success = ref(false), expired = ref(false)
const loadError = ref(''), submitError = ref(''), sessionGeneration = ref(0)
const command = shallowRef<ExamSubmissionCommand>()
let generation = 0
const hasUploads = computed(() => Object.values(uploadBusy.value).some(Boolean))
const hasUploadErrors = computed(() => Object.values(uploadFailed.value).some(Boolean))
const dirty = computed(() => !success.value && (Object.values(answers.value).some(v => v.trim()) || images.value.length > 0 || hasUploads.value || hasUploadErrors.value))
const locked = computed(() => saving.value || Boolean(command.value) || expired.value)
const groups = computed(() => {
  const fields = effectiveExamFields(runtime.value?.fields || [])
  return [
    { key: 'basic', title: '基本信息', fields: fields.filter(f => f.type !== 'images') },
    { key: 'materials', title: '本次收集材料', fields: fields.filter(f => f.type === 'images') },
  ].filter(group => group.fields.length)
})
const imageClient = computed(() => {
  const sessionToken = session.value?.sessionToken || ''
  const epoch = sessionGeneration.value
  return {
    upload: (taskToken: string, field: string, side: string, file: File, progress: (value: number) => void, requestKey?: string) => {
      if (!requestKey) return Promise.reject(new Error('上传请求无效，请重新选择文件'))
      return attachmentRequest(examCollectionApi.upload(taskToken, sessionToken, field, side, file, progress, requestKey), epoch, taskToken)
    },
    removeImage: (taskToken: string, id: number) => attachmentRequest(examCollectionApi.remove(taskToken, sessionToken, id), epoch, taskToken),
    image: (taskToken: string, image: CollectionImage) => attachmentRequest(examCollectionApi.image(taskToken, sessionToken, image), epoch, taskToken),
  }
})
async function attachmentRequest<T>(request: Promise<T>, epoch: number, taskToken: string): Promise<T> {
  try { return await request }
  catch (error) {
    if (epoch === sessionGeneration.value && taskToken === token.value && error instanceof CollectionError) {
      if (error.code === 1900170003 && runtime.value) { runtime.value.status = 'COMPLETED'; expired.value = true; uploadBusy.value = {} }
      if ([1900170004, 1900170005].includes(error.code)) { expired.value = true; submitError.value = error.message }
    }
    throw error
  }
}

async function initialize(preserve = false, forceSession = false) {
  const run = ++generation, taskToken = token.value
  loading.value = true; loadError.value = ''; fieldErrors.value = {}
  if (!preserve) {
    runtime.value = undefined; session.value = undefined; answers.value = {}; images.value = []
    uploadBusy.value = {}; uploadFailed.value = {}; command.value = undefined; success.value = false; expired.value = false; submitError.value = ''
  }
  try {
    if (!/^[A-Za-z0-9_-]{43}$/.test(taskToken)) throw new Error('收集链接无效，请向老师获取完整链接')
    const result = await examCollectionApi.detail(taskToken)
    if (run !== generation) return
    runtime.value = result
    if (result.status !== 'OPEN') return
    if (!result.fields.length || result.fields.some(f => !['text', 'textarea', 'images'].includes(f.type))) throw new Error('收集表配置暂不可用，请联系考务老师')
    if (!session.value || forceSession) {
      const created = await examCollectionApi.start(taskToken)
      if (run !== generation) return
      session.value = created; sessionGeneration.value++; images.value = []; uploadBusy.value = {}; uploadFailed.value = {}
      expired.value = false; command.value = undefined; submitError.value = ''
    }
  } catch (error) {
    if (run !== generation) return
    if (error instanceof CollectionError && error.code === 1900170003 && runtime.value) runtime.value.status = 'COMPLETED'
    else loadError.value = error instanceof Error ? error.message : '收集表加载失败，请重试'
  } finally { if (run === generation) loading.value = false }
}
watch(token, () => { void initialize() }, { immediate: true })
function updateImages(field: string, selected: CollectionImage[]) {
  images.value = [...images.value.filter(i => i.fieldKey !== field), ...selected]
  inputChanged(field)
}
function inputChanged(field: string) {
  delete fieldErrors.value[field]
  if (!command.value && !expired.value) submitError.value = ''
}
async function restartSession() {
  if (saving.value || hasUploads.value) return
  if (images.value.length) {
    try { await showConfirmDialog({ title: '重新建立填写会话', message: '文字内容会保留，附件需要重新上传。是否继续？', confirmButtonText: '继续', cancelButtonText: '取消' }) }
    catch { return }
  }
  await initialize(true, true)
}
async function submit() {
  if (!runtime.value || !session.value || saving.value || hasUploads.value || hasUploadErrors.value || expired.value) return
  if (!command.value) {
    const checked = validateExamCollection(runtime.value.fields, answers.value, images.value)
    fieldErrors.value = checked.errors
    const invalid = Object.keys(checked.errors)
    if (invalid.length) {
      submitError.value = `请检查 ${invalid.length} 项未完成或不正确的信息`
      await nextTick()
      const field = document.getElementById(`exam-field-${invalid[0]}`)
      field?.scrollIntoView({ block: 'center', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
      field?.focus({ preventScroll: true }); return
    }
    command.value = { idempotencyKey: createIdempotencyKey(), values: checked.values }
  }
  const run = generation, taskToken = token.value, sessionToken = session.value.sessionToken
  saving.value = true; submitError.value = ''
  try {
    await examCollectionApi.submit(taskToken, sessionToken, command.value)
    if (run !== generation) return
    success.value = true; answers.value = {}; images.value = []; session.value = undefined; command.value = undefined
    uploadBusy.value = {}; uploadFailed.value = {}
  } catch (error) {
    if (run !== generation) return
    if (error instanceof CollectionError && error.code !== 500 && error.code !== 503) {
      command.value = undefined
      submitError.value = error.message
      if (error.code === 1900170003) runtime.value.status = 'COMPLETED'
      if ([1900170004, 1900170005].includes(error.code)) expired.value = true
    } else {
      // Keep the exact request after an unknown outcome; editing then retrying could produce a different submission.
      submitError.value = '提交结果尚未确认，内容已保留在当前页面。请重试确认提交，不要关闭页面。'
    }
  } finally { if (run === generation) saving.value = false }
}
async function confirmLeave() {
  if (saving.value || hasUploads.value) { showToast('正在提交或上传，请稍候'); return false }
  if (!dirty.value) return true
  try { await showConfirmDialog({ title: '离开收集表？', message: command.value ? '提交结果尚未确认，建议先重试确认提交。离开后当前填写内容无法恢复。' : '尚未提交的内容仅保留在当前页面，离开后需要重新填写。', confirmButtonText: '离开', cancelButtonText: '继续填写' }); return true }
  catch { return false }
}
onBeforeRouteLeave(confirmLeave)
onBeforeRouteUpdate((to, from) => to.hash === from.hash && to.path === from.path ? true : confirmLeave())
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { generation++; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <main class="exam-public norem">
    <header class="exam-public__header norem"><p>中世健 · 考试资料收集</p><h1>{{ runtime?.title || '考试资料收集' }}</h1><p v-if="runtime?.description" class="exam-public__description norem">{{ runtime.description }}</p></header>
    <div v-if="runtime?.content" class="exam-collection-body norem" v-html="examCollectionBodyHtml(runtime.content)"></div>
    <div v-if="loading" class="exam-public__state norem" role="status"><van-loading vertical>正在加载收集表</van-loading></div>
    <section v-else-if="success" class="exam-public__state norem" aria-live="polite"><van-icon name="passed" size="44"/><h2>资料提交成功</h2><p>资料已收到，请以考务老师的通知为准。</p><p>如需补充或更正，可以重新填写；每次提交均会保留记录。</p><van-button type="primary" @click="initialize()">再填写一份</van-button></section>
    <section v-else-if="runtime?.status === 'COMPLETED'" class="exam-public__state norem"><van-icon name="clock-o" size="40"/><h2>收集已结束</h2><p>本任务暂不接收新的提交，如有疑问请联系考务老师。</p><van-button plain type="primary" @click="initialize(true)">刷新任务状态</van-button></section>
    <template v-else>
      <section v-if="loadError" class="exam-public__error norem" role="alert"><p>{{ loadError }}</p><van-button size="small" plain type="primary" @click="initialize(true)">重新加载</van-button></section>
      <form v-if="runtime && session && !loadError" novalidate @submit.prevent="submit">
        <p class="exam-public__hint norem">请填写本人资料。标有 * 的项目为必填项，未提交内容不会自动保存。</p>
        <section v-if="expired" class="exam-public__error norem" role="alert"><p>{{ submitError || '填写会话已失效，请重新建立会话' }}</p><van-button size="small" plain type="primary" :disabled="hasUploads" @click="restartSession">重新建立填写会话</van-button></section>
        <section v-for="group in groups" :key="group.key" class="exam-public__section norem"><h2>{{ group.title }}</h2><div class="exam-public__grid norem">
          <div v-for="field in group.fields" :key="field.key" :id="`exam-field-${field.key}`" tabindex="-1" class="exam-public__field norem" :class="{ 'is-wide': field.type !== 'text' }">
            <label :for="`exam-input-${field.key}`"><span v-if="field.required" class="exam-public__required norem" aria-hidden="true">*</span>{{ field.label }}<span v-if="field.type === 'images'" class="exam-public__hint norem" aria-live="polite">（{{ images.filter(image => image.fieldKey === field.key).length }}/{{ field.key === 'identity_images' ? 2 : field.maxImages || 9 }}）</span></label>
            <CollectionImages v-if="field.type === 'images'" :key="`${sessionGeneration}:${field.key}`" :token="token" :client="imageClient" :field="field" :images="images.filter(i => i.fieldKey === field.key)" :disabled="locked"
              @change="updateImages(field.key, $event)" @busy="uploadBusy[field.key] = $event" @failed="uploadFailed[field.key] = $event"/>
            <textarea v-else-if="field.type === 'textarea'" :id="`exam-input-${field.key}`" v-model="answers[field.key]" rows="3" maxlength="2000" :required="field.required" :disabled="locked" :aria-invalid="Boolean(fieldErrors[field.key])" :aria-describedby="fieldErrors[field.key] ? `exam-error-${field.key}` : undefined" @input="inputChanged(field.key)"/>
            <input v-else :id="`exam-input-${field.key}`" v-model="answers[field.key]" :type="field.key === 'mobile' ? 'tel' : 'text'" :maxlength="field.key === 'mobile' ? 11 : field.key === 'id_card' ? 18 : 200"
              :required="field.required" :disabled="locked" autocomplete="off" :aria-invalid="Boolean(fieldErrors[field.key])" :aria-describedby="fieldErrors[field.key] ? `exam-error-${field.key}` : undefined" @input="inputChanged(field.key)"/>
            <p v-if="fieldErrors[field.key]" :id="`exam-error-${field.key}`" class="exam-public__field-error norem" role="alert">{{ fieldErrors[field.key] }}</p><p v-if="field.note" class="exam-public__hint norem">{{ field.note }}</p>
          </div>
        </div></section>
        <section class="exam-public__submit norem"><p v-if="submitError && !expired" class="exam-public__field-error norem" role="alert">{{ submitError }}</p>
          <van-button type="primary" block native-type="submit" :loading="saving" :disabled="saving || hasUploads || hasUploadErrors || expired">{{ command && !saving ? '重试确认提交' : '提交考试资料' }}</van-button>
          <p v-if="hasUploads" role="status">附件正在上传，请稍候。</p><template v-else-if="hasUploadErrors"><p role="status">请重试或移除上传失败的附件后提交。</p><van-button size="small" plain type="primary" @click="restartSession">重新开始附件上传</van-button></template><p v-else>提交后可以再次填写，原始记录将保留。</p>
        </section>
      </form>
    </template>
  </main>
</template>

<style scoped src="../../../../shared/exam-public-form.css"></style>
