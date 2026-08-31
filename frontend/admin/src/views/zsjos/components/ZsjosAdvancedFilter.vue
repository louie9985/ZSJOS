<template>
  <div class="advanced-toolbar">
    <el-input v-model="searchText" clearable :placeholder="placeholder" @clear="submitSearch" @keyup.enter="submitSearch">
      <template #suffix><Icon icon="ep:search" class="search-icon" tabindex="0" @click="submitSearch" @keyup.enter="submitSearch" /></template>
    </el-input>
    <el-badge :value="count || ''"><el-button :icon="Filter" @click="visible = true">筛选</el-button></el-badge>
  </div>
  <div v-if="count" class="advanced-tags">
    <el-tag v-for="item in flatConditions" :key="item.key" closable @close="removeTag(item)">{{ summarize(item.condition) }}</el-tag>
    <el-button link type="primary" @click="clear">清空全部</el-button>
  </div>
  <el-drawer v-model="visible" class="advanced-filter-drawer" title="高级筛选 · 修改后自动生效" size="min(560px, 100%)">
    <div v-if="catalogLoading" class="catalog-state"><el-icon class="is-loading"><Loading /></el-icon><span>正在加载可筛选字段</span></div>
    <el-alert v-else-if="catalogError" type="error" title="筛选字段加载失败" show-icon :closable="false"><template #default><el-button link type="primary" @click="loadCatalog">重试</el-button></template></el-alert>
    <el-empty v-else-if="!fields.length" description="当前场景没有可用筛选字段" />
    <ZsjosAdvancedFilterGroup v-else :model-value="draft" :fields="fields" :depth="0" :total="draftCount" @update:model-value="updateDraft" @retry-options="retryOptions" />
    <template #footer><el-button @click="clear">清空全部</el-button><el-button type="primary" @click="visible = false">关闭</el-button></template>
  </el-drawer>
</template>

<script setup lang="ts">
import { Filter, Loading } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as Api from '@/api/zsjos/advancedFilter'
import * as DictDataApi from '@/api/system/dict/dict.data'
import * as UserApi from '@/api/system/user'
import ZsjosAdvancedFilterGroup from './ZsjosAdvancedFilterGroup.vue'

const props = defineProps<{ scene: Api.AdvancedFilterScene; placeholder: string; keyword?: string }>()
const emit = defineEmits<{ change: [value?: Api.AdvancedFilterGroup]; search: [value: string] }>()
const visible = ref(false), fields = ref<Api.AdvancedFilterField[]>([]), searchText = ref(props.keyword || '')
const catalogLoading = ref(true), catalogError = ref(false)
const blank = (): Api.AdvancedFilterGroup => ({ logic: 'AND', conditions: [], groups: [] })
const draft = ref(blank())
const hasValue = (value: unknown) => Array.isArray(value) ? value.length > 0 : value !== undefined && value !== null && value !== ''
const isComplete = (condition: Api.AdvancedFilterCondition) => {
  if (condition.operator === 'is_empty' || condition.operator === 'is_not_empty') return true
  if (condition.fieldKey === 'duration.diff') return hasValue(condition.startFieldKey) && hasValue(condition.endFieldKey) && hasValue(condition.unit)
    && (condition.operator === 'between' ? hasValue(condition.valueFrom) && hasValue(condition.valueTo) : hasValue(condition.value))
  return condition.operator === 'between' ? hasValue(condition.valueFrom) && hasValue(condition.valueTo) : hasValue(condition.value)
}
const countGroup = (group: Api.AdvancedFilterGroup): number => group.conditions.length + group.groups.reduce((sum, item) => sum + countGroup(item), 0)
const effectiveGroup = (group: Api.AdvancedFilterGroup): Api.AdvancedFilterGroup => ({ logic: group.logic, conditions: group.conditions.filter(isComplete), groups: group.groups.map(effectiveGroup).filter((item) => item.conditions.length || item.groups.length) })
const count = computed(() => countGroup(effectiveGroup(draft.value)))
const draftCount = computed(() => countGroup(draft.value))
const flatConditions = computed(() => [
  ...draft.value.conditions.map((condition, index) => ({ key: `root-${index}`, condition, groupIndex: -1, index })),
  ...draft.value.groups.flatMap((group, groupIndex) => group.conditions.map((condition, index) => ({ key: `${groupIndex}-${index}`, condition, groupIndex, index })))
].filter((item) => isComplete(item.condition)))
const fieldMap = computed(() => Object.fromEntries(fields.value.map((field) => [field.fieldKey, field])))
const operatorLabels: Record<string, string> = { contains: '包含', not_contains: '不包含', eq: '等于', ne: '不等于', in: '属于', not_in: '不属于', gt: '大于', gte: '大于等于', lt: '小于', lte: '小于等于', between: '区间', relative: '相对时间', is_empty: '为空', is_not_empty: '不为空' }
const durationOperatorLabels: Record<string, string> = { gt: '大于', gte: '大于等于', lt: '小于', lte: '小于等于', between: '介于' }
const durationUnitLabels: Record<string, string> = { minute: '分钟', hour: '小时', day: '天' }
let timer: number | undefined
watch(() => props.keyword, (value) => { searchText.value = value || '' })
const submitSearch = () => emit('search', searchText.value.trim())
const deliver = (immediate = false) => { window.clearTimeout(timer); const run = () => { const value = effectiveGroup(draft.value); emit('change', countGroup(value) ? structuredClone(value) : undefined) }; if (immediate) run(); else timer = window.setTimeout(run, 500) }
const updateDraft = (value: Api.AdvancedFilterGroup, immediate = false) => { draft.value = value; deliver(immediate) }
const clear = () => updateDraft(blank(), true)
const removeTag = (item: { groupIndex: number; index: number }) => {
  if (item.groupIndex < 0) updateDraft({ ...draft.value, conditions: draft.value.conditions.filter((_, index) => index !== item.index) }, true)
  else updateDraft({ ...draft.value, groups: draft.value.groups.map((group, index) => index === item.groupIndex ? { ...group, conditions: group.conditions.filter((_, conditionIndex) => conditionIndex !== item.index) } : group) }, true)
}
const summarize = (condition: Api.AdvancedFilterCondition) => {
  const field = fieldMap.value[condition.fieldKey]
  if (condition.fieldKey !== 'duration.diff') return `${field?.group || ''} · ${field?.label || '筛选字段'} ${operatorLabels[condition.operator] || condition.operator}`
  const options = field?.options?.length ? field.options : fields.value.filter((item) => item.valueType === 'date').map((item) => ({ value: item.fieldKey, label: item.label }))
  const start = options.find((option) => option.value === condition.startFieldKey)?.label || '开始时间'
  const end = options.find((option) => option.value === condition.endFieldKey)?.label || '结束时间'
  const unit = durationUnitLabels[condition.unit || 'hour'] || condition.unit || ''
  if (condition.operator === 'between') return `${end} - ${start} ${durationOperatorLabels.between} ${condition.valueFrom ?? ''} - ${condition.valueTo ?? ''} ${unit}`
  return `${end} - ${start} ${durationOperatorLabels[condition.operator] || condition.operator} ${condition.value ?? ''} ${unit}`
}
const sourceOptions = async (source?: string): Promise<Api.AdvancedFilterOption[]> => {
  if (!source) return []
  if (source.startsWith('dict:')) return (await DictDataApi.getDictDataByType(source.slice(5))).map((item) => ({ value: item.value, label: item.label }))
  if (source === 'visible-users') return (await UserApi.getSimpleUserList()).map((item) => ({ value: item.id, label: item.nickname }))
  return []
}
const retryOptions = async (fieldKey: string) => {
  const field = fields.value.find((item) => item.fieldKey === fieldKey)
  if (!field?.optionSource) return
  fields.value = fields.value.map((item) => item.fieldKey === fieldKey ? { ...item, optionsLoading: true, optionsError: false } : item)
  try {
    const options = await sourceOptions(field.optionSource)
    fields.value = fields.value.map((item) => item.fieldKey === fieldKey ? { ...item, options, optionsLoading: false } : item)
  } catch {
    fields.value = fields.value.map((item) => item.fieldKey === fieldKey ? { ...item, optionsLoading: false, optionsError: true } : item)
  }
}
const loadCatalog = async () => {
  catalogLoading.value = true; catalogError.value = false
  try {
    const catalog = await Api.getCatalog(props.scene)
    fields.value = catalog.fields.map((field) => field.optionSource && !field.options.length ? { ...field, optionsLoading: true } : field)
    await Promise.all(catalog.fields.map(async (field) => {
      if (!field.optionSource || field.options.length) return
      try { const options = await sourceOptions(field.optionSource); fields.value = fields.value.map((item) => item.fieldKey === field.fieldKey ? { ...item, options, optionsLoading: false } : item) }
      catch { fields.value = fields.value.map((item) => item.fieldKey === field.fieldKey ? { ...item, optionsLoading: false, optionsError: true } : item) }
    }))
  } catch { catalogError.value = true; fields.value = [] }
  finally { catalogLoading.value = false }
}
onMounted(loadCatalog)
onBeforeUnmount(() => window.clearTimeout(timer))
</script>

<style scoped>
.advanced-toolbar{display:flex;gap:8px;max-width:520px;margin-top:12px}.advanced-toolbar>.el-input{flex:1}.search-icon{cursor:pointer}.advanced-tags{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.catalog-state{display:flex;align-items:center;justify-content:center;gap:8px;min-height:160px;color:var(--el-text-color-secondary)}@media(max-width:768px){.advanced-toolbar{max-width:none}.advanced-toolbar>.el-input{min-width:0}:global(.advanced-filter-drawer){width:100%!important}}
</style>
