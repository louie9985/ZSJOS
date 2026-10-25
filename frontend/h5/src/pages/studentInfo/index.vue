<template>
  <main class="student-collection norem">
    <van-nav-bar title="学员信息收集" />
    <header class="collection-hero norem"><p>中世健 · 学员服务</p><h1>学员信息收集表</h1><p>请填写本次报名资料，方便老师为您办理后续服务。</p><span class="collection-chip norem">{{ runtime?.productName || '报名资料' }} · 仅可提交一次</span></header>
    <van-loading v-if="loading" class="collection-loading" />
    <template v-else>
      <van-notice-bar v-if="error" :text="error" color="#b42318" background="#fff1f0" wrapable />
      <van-button v-if="error && !runtime" block @click="load">重新加载</van-button>
      <van-empty v-if="runtime && runtime.status !== 'DRAFT'" :description="statusText" />
      <form v-else-if="runtime" novalidate @submit.prevent="submit">
        <div class="collection-draft norem" role="status" aria-live="polite"><span>{{ draftError || (draftBusy ? '草稿保存中…' : draftChanged ? '有修改待保存' : draftSaved ? '草稿已保存，可稍后继续填写' : '填写后自动保存草稿') }}</span><button v-if="draftConflict" type="button" :disabled="draftBusy || saving" @click="reloadConflictedDraft">重新加载最新草稿</button><button v-else-if="draftError" type="button" :disabled="draftBusy" @click="saveDraftNow">重试保存</button></div>
        <details v-for="group in visibleGroups" :key="group.key" :open="openGroups[group.key] !== false" class="collection-group norem" :data-group="group.key" @toggle="openGroups[group.key]=($event.target as HTMLDetailsElement).open">
          <summary class="norem" @click.prevent="openGroups[group.key]=openGroups[group.key] === false"><span class="collection-group-icon norem"><svg class="collection-group-svg norem" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path :d="group.iconPath" /></svg></span><span class="collection-group-title norem"><strong>{{ group.label }}</strong><small>{{ group.note }}</small></span><van-icon name="arrow-down" /></summary>
          <div class="collection-grid norem">
        <div v-for="field in group.fields" :key="field.key" class="collection-field norem" :class="{ 'collection-field-wide': field.type==='images' || field.type==='textarea' }">
          <label :for="field.key"><span :class="{ 'h5-required-label': field.required }">{{ field.label }}</span></label>
          <CollectionImages v-if="field.type==='images'" :key="token + field.key" :token="token" :field="field" :images="(runtime.images || []).filter(i=>i.fieldKey===field.key)" :disabled="saving" @change="updateImages(field.key,$event)" @busy="uploadBusy[field.key]=$event" @failed="uploadFailed[field.key]=$event" />
          <input v-else-if="['gender','age','household_area'].includes(field.key)" :id="field.key" :value="field.key === 'household_area' ? areaLabel : field.key === 'gender' ? genderLabel : answers.age" readonly placeholder="填写身份证后自动识别" />
          <CollectionSelect v-else-if="field.type === 'dict'" :id="field.key" v-model="answers[field.key]" :label="field.label" :options="runtime.options[field.key] || []" :disabled="saving" />
          <textarea v-else-if="field.type === 'textarea'" :id="field.key" v-model="answers[field.key]" rows="3" maxlength="2000" :required="field.required" :disabled="saving" />
          <input v-else :id="field.key" v-model="answers[field.key]" :required="field.required" :disabled="saving"
            :type="field.key === 'mobile' ? 'tel' : 'text'" :maxlength="field.key === 'mobile' ? 11 : field.key === 'id_card' ? 18 : 200"
            :pattern="field.key === 'mobile' ? '1[3-9][0-9]{9}' : undefined" autocomplete="off" />
          <p v-if="field.note">{{ field.note }}</p>
          <p v-if="field.key === 'household_area'">{{ areaError || '按身份证地址码自动识别' }}</p>
        </div>
          </div>
        </details>
        <div class="collection-submit norem"><van-button type="primary" block native-type="submit" :loading="saving" :disabled="saving || hasUploads || hasUploadErrors">提交信息</van-button><p v-if="hasUploads">附件正在上传，请稍候</p><p v-else-if="hasUploadErrors">请重试或移除上传失败的附件后提交</p><p>资料仅用于报名与履约服务，请放心填写。</p></div>
      </form>
    </template>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { collectionApi, CollectionError, type CollectionArea, type CollectionRuntime, type CollectionImage } from '@/api/studentInfo'
import CollectionImages from './CollectionImages.vue'
import CollectionSelect from './CollectionSelect.vue'
import { validCollectionIdentity, deriveCollectionIdentity, findCollectionIdentityArea } from '@/utils/studentInfoValidation'

const route = useRoute()
const token = computed(() => new URLSearchParams(route.hash.slice(1)).get('token') || '')
let loadSequence = 0
const runtime = ref<CollectionRuntime>()
const answers = ref<Record<string,string>>({})
const areas = ref<CollectionArea[]>([])
const areaPath = ref<number[]>([])
const areaLabel = ref('')
const areaError = ref('')
const loading = ref(false)
const saving = ref(false)
const draftRevision = ref(0)
const draftSaved = ref(false)
const draftBusy = ref(false)
const draftError = ref('')
const draftConflict = ref(false)
const savedDraftText = ref('{}')
let draftTimer: ReturnType<typeof setTimeout> | undefined
let draftRequest: Promise<boolean> | undefined
let draftRequestSequence = 0
const draftValues = computed(() => Object.fromEntries((runtime.value?.fields || [])
  .filter(f => ['text','textarea','dict'].includes(f.type) && !['gender','age','household_area'].includes(f.key))
  .map(f => [f.key, answers.value[f.key] || ''])))
const draftText = computed(() => JSON.stringify(draftValues.value))
const draftChanged = computed(() => runtime.value?.status === 'DRAFT' && draftText.value !== savedDraftText.value)
async function saveDraftNow(): Promise<boolean> {
  clearTimeout(draftTimer)
  if (saving.value) return false
  if (draftConflict.value) return false
  if (draftRequest) {
    const pendingSequence = draftRequestSequence
    if (!await draftRequest && pendingSequence === loadSequence) return false
    return saveDraftNow()
  }
  if (!draftChanged.value || runtime.value?.status !== 'DRAFT') return true
  const requestToken = token.value, sequence = loadSequence, text = draftText.value
  draftRequestSequence = sequence
  draftBusy.value = true; draftError.value = ''
  draftRequest = (async () => {
    try {
      const saved = await collectionApi.saveDraft(requestToken, draftRevision.value, JSON.parse(text))
      if (sequence !== loadSequence || requestToken !== token.value) return false
      if (draftConflict.value) error.value = ''
      draftRevision.value = saved.revision; savedDraftText.value = text; draftSaved.value = true; draftConflict.value = false
      return true
    } catch (e) {
      if (sequence === loadSequence && requestToken === token.value) {
        draftError.value = e instanceof Error ? e.message : '草稿保存失败，请重试'
        draftConflict.value = e instanceof CollectionError && e.code === 1900090011
      }
      return false
    } finally {
      draftRequest = undefined
      if (sequence === loadSequence) draftBusy.value = false
    }
  })()
  return draftRequest
}
watch(draftText, () => {
  clearTimeout(draftTimer)
  if (!loading.value && draftChanged.value && !saving.value) draftTimer = setTimeout(() => { void saveDraftNow() }, 700)
})
const error = ref('')
const openGroups = ref<Record<string,boolean>>({})
const uploadBusy = ref<Record<string,boolean>>({})
const uploadFailed = ref<Record<string,boolean>>({})
const hasUploads = computed(()=>Object.values(uploadBusy.value).some(Boolean))
const hasUploadErrors = computed(()=>Object.values(uploadFailed.value).some(Boolean))
const genderLabel = computed(() => runtime.value?.options.gender?.find(option => option.value === answers.value.gender)?.label || '')
const groupDefinitions = [
  {key:'basic',label:'基本信息',iconPath:'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v2Z',note:'请与本人证件信息保持一致'},
  {key:'education',label:'学历与报考',iconPath:'m2 9 10-5 10 5-10 5L2 9Zm4 2v6c4 3 8 3 12 0v-6m4-2v7',note:'填写学历信息与本次报名意向'},
  {key:'career',label:'职业与学习目的',iconPath:'M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 7h18v14H3V7Zm0 6c6 3 12 3 18 0m-9 0v4',note:'帮助老师了解您的学习需求'},
  {key:'materials',label:'证明材料',iconPath:'M14 3H5v18h14V8l-5-5Zm0 0v5h5M8 12h8m-8 4h6',note:'请按各字段要求上传清晰材料'}
]
const visibleGroups = computed(()=>groupDefinitions.map(g=>({...g,fields:runtime.value?.fields.filter(f=>f.group===g.key)||[]})).filter(g=>g.fields.length))
const updateImages = (key:string,list:CollectionImage[])=>{if(runtime.value)runtime.value.images=[...(runtime.value.images||[]).filter(i=>i.fieldKey!==key),...list]}
const dirty = computed(()=>runtime.value?.status==='DRAFT' && (draftChanged.value || draftBusy.value || hasUploads.value || hasUploadErrors.value))
const beforeUnload = (e:BeforeUnloadEvent)=>{if(dirty.value){e.preventDefault();e.returnValue=''}}
const saveWhenHidden = ()=>{if(document.visibilityState === 'hidden' && draftChanged.value)void saveDraftNow()}
window.addEventListener('beforeunload',beforeUnload)
document.addEventListener('visibilitychange',saveWhenHidden)
onBeforeUnmount(()=>{loadSequence++;clearTimeout(draftTimer);window.removeEventListener('beforeunload',beforeUnload);document.removeEventListener('visibilitychange',saveWhenHidden)})
const confirmLeave = async()=>{if(!dirty.value)return true;if(!hasUploads.value && !hasUploadErrors.value && await saveDraftNow())return true;try{await showConfirmDialog({title:'离开填写页？',message:'仍有内容未保存，离开后这些修改可能丢失。'});return true}catch{return false}}
onBeforeRouteLeave(confirmLeave)
onBeforeRouteUpdate((to,from)=>to.hash===from.hash ? true : confirmLeave())
watch([() => answers.value.id_card, areas], () => {
  if (/身份证|户籍所在地/.test(error.value)) error.value = ''
  const identity = deriveCollectionIdentity(answers.value.id_card || '')
  answers.value.age = identity?.age || ''
  answers.value.gender = identity?.gender || ''
  const path = identity ? findCollectionIdentityArea(areas.value, identity.areaCode) : []
  areaPath.value = path.map(node => node.id)
  areaLabel.value = path.map(node => node.name).join(' / ')
  areaError.value = identity && !path.length ? '身份证地址码未匹配到地区，请联系报名老师维护地区配置' : ''
})
const statusText = computed(() => ({ SUBMITTED: '信息已提交，不可修改', EXPIRED: '链接已失效', REVOKED: '链接已撤销' }[runtime.value?.status || ''] || '链接不可用'))
const load = async () => {
  const sequence = ++loadSequence
  draftBusy.value = false; draftConflict.value = false
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
    if (data.status === 'DRAFT') {
      Object.assign(answers.value, data.draft?.values || {})
      draftRevision.value = data.draft?.revision || 0
      draftSaved.value = !!data.draft?.savedAt
      savedDraftText.value = JSON.stringify(draftValues.value)
      draftError.value = ''
    }
  } catch (e) { if (sequence === loadSequence) error.value = e instanceof Error ? e.message : '加载失败' }
  finally { if (sequence === loadSequence) loading.value = false }
}
const reloadConflictedDraft = async () => {
  if (draftBusy.value || saving.value) return
  try {
    await showConfirmDialog({ title: '重新加载最新草稿', message: '当前未保存内容不会覆盖其他页面的草稿。请先另行保留需要的内容，确认后将读取最新草稿。' })
  } catch { return }
  clearTimeout(draftTimer)
  answers.value = {}; areaPath.value = []; areaLabel.value = ''
  await load()
}
const submit = async () => {
  if (!runtime.value || saving.value || hasUploads.value || hasUploadErrors.value) return
  if (draftConflict.value) { error.value = draftError.value; return }
  error.value = ''
  if (answers.value.id_card && !validCollectionIdentity(answers.value.id_card)) { error.value = '请检查身份证号码'; return }
  if (runtime.value.fields.some(f => f.key === 'household_area') && areaError.value) { error.value = areaError.value; return }
  for(const f of runtime.value.fields){const images=runtime.value.images?.filter(i=>i.fieldKey===f.key)||[];const missing=f.type==='images'?images.length<(f.key==='identity_images'?2:1):f.type==='area'?!areaPath.value.length:!(answers.value[f.key]||'').trim();if(f.required&&missing){error.value='请填写'+f.label;openGroups.value[f.group]=true;document.getElementById(f.key)?.focus();return}}
  const area = runtime.value.fields.find(f => f.type === 'area')
  if (area?.required && !areaPath.value.length) { error.value = '请填写有效身份证以识别户籍所在地'; return }
  const requestToken = token.value
  try { await showConfirmDialog({ title: '确认提交', message: '提交后信息将锁定，不能再次修改。' }) } catch { return }
  if (requestToken !== token.value || runtime.value?.status !== 'DRAFT') return
  clearTimeout(draftTimer)
  if (!await saveDraftNow()) { error.value = draftError.value; return }
  clearTimeout(draftTimer)
  if (requestToken !== token.value || runtime.value?.status !== 'DRAFT') return
  if (draftConflict.value) { error.value = draftError.value; return }
  saving.value = true
  try {
    const values: Record<string,string | number[]> = {}
    for (const f of runtime.value.fields) values[f.key] = f.type==='images' ? (runtime.value.images||[]).filter(i=>i.fieldKey===f.key).map(i=>i.id) : f.type === 'area' ? areaPath.value : (answers.value[f.key] || '')
    await collectionApi.submit(requestToken, values, draftRevision.value)
    if (requestToken !== token.value) return
    runtime.value = { ...runtime.value, status: 'SUBMITTED', fields: [], options: {}, images: [] }
    answers.value = {}; areaPath.value = []; areaLabel.value = ''
  } catch (e) {
    if (requestToken !== token.value) return
    if (e instanceof CollectionError && e.code === 1900090011) { draftConflict.value = true; draftError.value = e.message }
    if (e instanceof CollectionError && [1900090006, 1900090007].includes(e.code)) await load()
    else error.value = e instanceof Error ? e.message : '提交失败，请重试'
  }
  finally { saving.value = false }
}
watch(token, () => {
  clearTimeout(draftTimer); draftRevision.value = 0; draftSaved.value = false; draftBusy.value = false; draftError.value = ''; draftConflict.value = false; savedDraftText.value = '{}'
  openGroups.value = {}
  answers.value = {}; uploadBusy.value = {}; uploadFailed.value = {}; areaPath.value = []; areaLabel.value = ''; areaError.value = ''; areas.value = []
  void load()
}, { immediate: true })
</script>

<style scoped>
.collection-draft.norem { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 0 4px 12px; font-size: 12px; color: var(--h5-text-secondary); }
.collection-draft.norem button { border: 0; background: transparent; color: var(--h5-primary); font: inherit; cursor: pointer; }
.collection-group-svg.norem { display: block; width: 22px; height: 22px; }
.student-collection.norem { max-width: 940px; margin: 0 auto; padding: 0 20px 40px; background: var(--h5-app-background); color: var(--h5-text-primary); min-height: 100vh; box-sizing: border-box; }
:global(#app:has(.student-collection.norem)){max-width:none}
.collection-hero.norem{padding:24px 4px}.collection-hero p,.collection-submit p{font-size:12px;color:var(--h5-text-secondary);margin:6px 0}.collection-chip.norem{display:inline-block;padding:4px 10px;margin-top:10px;border-radius:999px;background:var(--h5-primary-opacity);color:var(--h5-primary);font-size:12px}
.collection-group.norem{border:1px solid var(--h5-content-border);border-radius:12px;background:var(--h5-content-surface-strong-fallback);box-shadow:var(--h5-content-shadow);margin-bottom:14px;overflow:hidden}.collection-group summary.norem{display:flex;align-items:center;gap:12px;min-height:72px;padding:16px 18px;list-style:none;cursor:pointer}.collection-group summary::-webkit-details-marker{display:none}.collection-group-icon.norem{display:flex;align-items:center;justify-content:center;width:36px;height:36px;flex-shrink:0;border-radius:12px;color:var(--h5-primary);background:var(--h5-primary-opacity)}.collection-group-title.norem{flex:1;min-width:0}.collection-group-title strong{display:block;font-size:15px}.collection-group-title small{font-size:12px;color:var(--h5-text-secondary)}.collection-grid.norem{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 28px;padding:0 18px 14px}.collection-field.norem{display:flex;align-items:center;gap:12px;min-width:0;padding:14px 0;margin:0;border-top:1px solid var(--h5-border);flex-wrap:wrap}.collection-field.norem>label{width:108px;flex-shrink:0;font-size:13px;margin:0}.collection-field.norem input,.collection-field.norem select,.collection-field.norem textarea,.collection-field.norem .area-control{flex:1;min-width:0;width:100%;border:0;border-bottom:1px solid var(--h5-border);border-radius:0;padding:8px 0;min-height:40px;background:transparent;color:var(--h5-text-primary);font:inherit}.collection-field-wide.norem{grid-column:1/-1;align-items:flex-start}.collection-field-wide:has(.collection-images){display:block}.collection-field.norem p{width:100%;font-size:12px;color:var(--h5-text-secondary)}.collection-submit.norem{max-width:420px;margin:24px auto 0;text-align:center}
@media(max-width:600px){.student-collection.norem{padding:0 14px 32px}.collection-grid.norem{grid-template-columns:1fr;gap:0}.collection-field.norem input,.collection-field.norem select,.collection-field.norem textarea{font-size:16px}}
h1 { margin: 0 0 24px; font-size: 22px; font-weight: 600; letter-spacing: 0; }
.collection-field { margin-bottom: 20px; }
.collection-field label { display: block; margin-bottom: 8px; font-size: 15px; }
.collection-field input, .collection-field textarea, .collection-field select, .area-control { box-sizing: border-box; font: inherit; }
.area-control { text-align: left; }
.collection-field p { margin: 6px 0 0; color: #666; font-size: 13px; overflow-wrap: anywhere; white-space: pre-wrap; }
.collection-loading { padding: 24px; text-align: center; }
</style>
