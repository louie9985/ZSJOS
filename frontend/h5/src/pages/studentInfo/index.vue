<template>
  <main class="student-collection">
    <header><h1>学员信息收集表</h1></header>
    <van-loading v-if="loading" class="collection-loading" />
    <template v-else>
      <van-notice-bar v-if="error" :text="error" color="#b42318" background="#fff1f0" wrapable />
      <van-button v-if="error && !runtime" block @click="load">重新加载</van-button>
      <van-empty v-if="runtime && runtime.status !== 'DRAFT'" :description="statusText" />
      <form v-else-if="runtime" @submit.prevent="submit">
        <div v-for="field in runtime.fields" :key="field.key" class="collection-field">
          <label :for="field.key">{{ field.label }}<span v-if="field.required" class="required"> *</span></label>
          <select v-if="field.type === 'dict'" :id="field.key" v-model="answers[field.key]" :required="field.required" :disabled="saving">
            <option value="">请选择</option>
            <option v-for="option in runtime.options[field.key] || []" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <template v-else-if="field.type === 'area'">
            <button :id="field.key" type="button" class="area-control" :disabled="saving" @click="areaOpen = true">{{ areaLabel || '请选择户籍所在地' }}</button>
            <van-popup v-model:show="areaOpen" position="bottom" round>
              <van-cascader v-model="areaValue" title="户籍所在地" :options="areas" :field-names="{ text: 'name', value: 'id', children: 'children' }" @close="areaOpen = false" @finish="finishArea" />
            </van-popup>
          </template>
          <textarea v-else-if="field.type === 'textarea'" :id="field.key" v-model="answers[field.key]" rows="3" maxlength="2000" :required="field.required" :disabled="saving" />
          <input v-else :id="field.key" v-model="answers[field.key]" :required="field.required" :disabled="saving"
            :type="field.key === 'mobile' ? 'tel' : 'text'" :maxlength="field.key === 'mobile' ? 11 : field.key === 'id_card' ? 18 : 200"
            :pattern="field.key === 'mobile' ? '1[3-9][0-9]{9}' : undefined" autocomplete="off" />
          <p v-if="field.note">{{ field.note }}</p>
        </div>
        <van-button type="primary" block native-type="submit" :loading="saving" :disabled="saving">提交信息</van-button>
      </form>
    </template>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { collectionApi, CollectionError, type CollectionArea, type CollectionRuntime } from '@/api/studentInfo'
import { validCollectionIdentity } from '@/utils/studentInfoValidation'

const route = useRoute()
const token = computed(() => new URLSearchParams(route.hash.slice(1)).get('token') || '')
let loadSequence = 0
const runtime = ref<CollectionRuntime>()
const answers = ref<Record<string,string>>({})
const areas = ref<CollectionArea[]>([])
const areaPath = ref<number[]>([])
const areaValue = ref<number>()
const areaLabel = ref('')
const areaOpen = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const statusText = computed(() => ({ SUBMITTED: '信息已提交，不可修改', EXPIRED: '链接已失效', REVOKED: '链接已撤销' }[runtime.value?.status || ''] || '链接不可用'))
const load = async () => {
  const sequence = ++loadSequence
  loading.value = true; error.value = ''; runtime.value = undefined
  try {
    if (!token.value) throw new Error('信息收集链接不完整')
    const data = await collectionApi.detail(token.value)
    if (sequence !== loadSequence) return
    if (data.status === 'DRAFT') {
      if (!data.fields.length) throw new Error('表单尚未配置')
      if (data.fields.some(f => f.type === 'area')) {
        if (!data.tenantId) throw new Error('表单信息不完整')
        const result = await collectionApi.areas(data.tenantId)
        if (sequence !== loadSequence) return
        areas.value = result
        if (!areas.value.length) throw new Error('地区配置为空，请联系报名老师')
      }
      for (const f of data.fields) if (!(f.key in answers.value)) answers.value[f.key] = ''
    }
    runtime.value = data
  } catch (e) { if (sequence === loadSequence) error.value = e instanceof Error ? e.message : '加载失败' }
  finally { if (sequence === loadSequence) loading.value = false }
}
const finishArea = ({ selectedOptions }: { selectedOptions: CollectionArea[] }) => {
  areaPath.value = selectedOptions.map(x => x.id)
  areaLabel.value = selectedOptions.map(x => x.name).join(' / ')
  areaOpen.value = false
}
const submit = async () => {
  if (!runtime.value || saving.value) return
  error.value = ''
  const area = runtime.value.fields.find(f => f.type === 'area')
  if (area?.required && !areaPath.value.length) { error.value = '请选择户籍所在地'; return }
  const card = answers.value.id_card
  if (card && !validCollectionIdentity(card)) { error.value = '请检查身份证号码'; return }
  const requestToken = token.value
  try { await showConfirmDialog({ title: '确认提交', message: '提交后信息将锁定，不能再次修改。' }) } catch { return }
  if (requestToken !== token.value || runtime.value?.status !== 'DRAFT') return
  saving.value = true
  try {
    const values: Record<string,string | number[]> = {}
    for (const f of runtime.value.fields) values[f.key] = f.type === 'area' ? areaPath.value : (answers.value[f.key] || '')
    await collectionApi.submit(requestToken, values)
    if (requestToken !== token.value) return
    runtime.value = { ...runtime.value, status: 'SUBMITTED', fields: [], options: {} }
    answers.value = {}; areaPath.value = []; areaLabel.value = ''
  } catch (e) {
    if (requestToken !== token.value) return
    if (e instanceof CollectionError && [1900090006, 1900090007].includes(e.code)) await load()
    else error.value = e instanceof Error ? e.message : '提交失败，请重试'
  }
  finally { saving.value = false }
}
watch(token, () => {
  answers.value = {}; areaPath.value = []; areaLabel.value = ''; areaValue.value = undefined; areaOpen.value = false
  void load()
}, { immediate: true })
</script>

<style scoped>
.student-collection { max-width: 640px; margin: 0 auto; padding: 20px 16px 40px; background: #fff; color: #202020; min-height: 100vh; box-sizing: border-box; }
h1 { margin: 0 0 24px; font-size: 22px; font-weight: 600; letter-spacing: 0; }
.collection-field { margin-bottom: 20px; }
.collection-field label { display: block; margin-bottom: 8px; font-size: 15px; }
.collection-field input, .collection-field textarea, .collection-field select, .area-control { width: 100%; box-sizing: border-box; border: 1px solid #c9cdd4; border-radius: 4px; padding: 10px; min-height: 44px; font: inherit; color: #202020; background: #fff; }
.area-control { text-align: left; }
.collection-field p { margin: 6px 0 0; color: #666; font-size: 13px; overflow-wrap: anywhere; white-space: pre-wrap; }
.required { color: #b42318; }
.collection-loading { padding: 24px; text-align: center; }
</style>
