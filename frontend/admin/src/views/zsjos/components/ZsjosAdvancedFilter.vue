<template>
  <div class="advanced-toolbar">
    <el-input v-model="searchText" clearable :placeholder="placeholder" @clear="submitSearch" @keyup.enter="submitSearch">
      <template #suffix><Icon icon="ep:search" class="search-icon" tabindex="0" @click="submitSearch" @keyup.enter="submitSearch" /></template>
    </el-input>
    <el-badge :value="count || ''"><el-button :icon="Filter" @click="visible = true">筛选</el-button></el-badge>
  </div>
  <div v-if="templates.length" class="advanced-presets">
    <span class="advanced-presets__label">快捷</span>
    <el-tag
      v-for="template in templates"
      :key="template.id"
      :type="template.id === activeTemplateId ? 'primary' : 'info'"
      :effect="template.id === activeTemplateId ? 'dark' : 'plain'"
      class="advanced-presets__tag"
      @click="applyTemplate(template)"
      >{{ template.name }}<span v-if="template.defaultTemplate" class="advanced-presets__default">默认</span></el-tag
    >
  </div>
  <div v-if="pageKey" class="advanced-presets__manage">
    <el-button link type="primary" :disabled="!count" @click="openSaveDialog()">存为快捷筛选</el-button>
    <el-button v-if="personalTemplates.length" link type="primary" @click="openManageDialog">管理我的快捷筛选</el-button>
  </div>
  <div v-if="count" class="advanced-tags">
    <el-tag v-for="item in flatConditions" :key="item.key" closable @close="removeTag(item)">{{ summarize(item.condition) }}</el-tag>
    <el-button link type="primary" @click="clear">清空全部</el-button>
  </div>
  <el-drawer v-model="visible" class="advanced-filter-drawer" title="高级筛选 · 修改后自动生效" size="min(560px, 100%)">
    <div v-if="catalogLoading" class="catalog-state"><el-icon class="is-loading"><Loading /></el-icon><span>正在加载可筛选字段</span></div>
    <el-alert v-else-if="catalogError" type="error" title="筛选字段加载失败" show-icon :closable="false"><template #default><el-button link type="primary" @click="loadCatalog">重试</el-button></template></el-alert>
    <el-empty v-else-if="!fields.length" description="当前场景没有可用筛选字段" />
    <ZsjosAdvancedFilterGroup v-else :model-value="draft" :fields="fields" :relative-date-options="relativeDateOptions" :depth="0" :total="draftCount" @update:model-value="updateDraft" @retry-options="retryOptions" />
    <template #footer>
      <el-button @click="clear">清空全部</el-button>
      <el-button type="primary" :disabled="!count" @click="openSaveDialog()">存为快捷筛选</el-button>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-drawer>
  <Dialog v-model="saveOpen" :title="saveForm.id ? '重命名快捷筛选' : '存为快捷筛选'" width="460px">
    <el-form label-width="88px" @submit.prevent>
      <el-form-item label="名称" required>
        <el-input v-model="saveForm.name" maxlength="30" show-word-limit placeholder="例如：本月高意向" />
      </el-form-item>
      <el-form-item label="设为默认">
        <el-switch v-model="saveForm.defaultTemplate" />
        <span class="advanced-presets__hint">设为默认后，打开页面时自动套用（优先于系统预置）</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="saveOpen = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveTemplate">保存</el-button>
    </template>
  </Dialog>
  <Dialog v-model="manageOpen" title="管理我的快捷筛选" width="560px">
    <el-table :data="personalTemplates" empty-text="暂无个人快捷筛选" row-key="id">
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="默认" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.defaultTemplate" type="warning" effect="plain">默认</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openSaveDialog(row)">重命名</el-button>
          <el-button link type="primary" @click="setDefault(row)" :disabled="row.defaultTemplate">设为默认</el-button>
          <el-button link type="danger" @click="removeTemplate(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import { Filter, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Dialog } from '@/components/Dialog'
import * as Api from '@/api/zsjos/advancedFilter'
import * as TemplateApi from '@/api/zsjos/advancedFilterTemplate'
import * as DictDataApi from '@/api/system/dict/dict.data'
import * as UserApi from '@/api/system/user'
import ZsjosAdvancedFilterGroup from './ZsjosAdvancedFilterGroup.vue'

const props = defineProps<{
  scene: Api.AdvancedFilterScene
  placeholder: string
  keyword?: string
  pageKey?: string
}>()
const emit = defineEmits<{ change: [value?: Api.AdvancedFilterGroup]; search: [value: string] }>()
const visible = ref(false), fields = ref<Api.AdvancedFilterField[]>([]), searchText = ref(props.keyword || '')
const templates = ref<TemplateApi.AdvancedFilterTemplate[]>([])
const activeTemplateId = ref<number>()
const personalTemplates = computed(() => templates.value.filter((item) => item.scope === 'personal'))
const saveOpen = ref(false), saving = ref(false), manageOpen = ref(false)
const saveForm = ref<{ id?: number; name: string; defaultTemplate: boolean; version?: number; sort?: number }>({ name: '', defaultTemplate: false })
const relativeDateOptions = ref<Array<{ value: string; label: string }>>([])
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
const applyTemplate = (template: TemplateApi.AdvancedFilterTemplate) => {
  // 再次点击已生效的预置表示取消套用，避免用户只能用「清空全部」退出。
  if (template.id === activeTemplateId.value) {
    activeTemplateId.value = undefined
    clear()
    return
  }
  updateDraft(structuredClone(template.filter), true)
  activeTemplateId.value = template.id
}
const loadTemplates = async () => {
  if (!props.pageKey) return
  try {
    templates.value = await TemplateApi.getVisibleTemplateList(props.scene, props.pageKey)
  } catch {
    // 预置是页面的附加能力，取不到时静默降级为「只能手动加条件」，不阻断高级筛选。
    templates.value = []
    return
  }
  // 默认模板由服务端标记（个人默认优先于系统默认），前端不再自行推断优先级，
  // 避免以后其他前端消费同一接口时判断出不同结果。
  const preset = templates.value.find((item) => item.effectiveDefault)
  // 预置是异步到达的，用户可能在等待期间已经手工加了条件，此时不覆盖他的输入。
  if (preset && !draftCount.value) applyTemplate(preset)
}
const openSaveDialog = (template?: TemplateApi.AdvancedFilterTemplate) => {
  // 无参数 = 把当前条件存成新的；带模板 = 仅重命名，filter 保持该模板原值不被当前条件覆盖。
  saveForm.value = template
    ? { id: template.id, name: template.name, defaultTemplate: template.defaultTemplate, version: template.version, sort: template.sort }
    : { name: '', defaultTemplate: false }
  saveOpen.value = true
}
const openManageDialog = async () => {
  await loadTemplates()
  manageOpen.value = true
}
const saveTemplate = async () => {
  const name = saveForm.value.name.trim()
  if (!name) return ElMessage.warning('请填写名称')
  const editing = saveForm.value.id
    ? templates.value.find((item) => item.id === saveForm.value.id)
    : undefined
  // 编辑态复用被编辑模板自己的条件与排序，保证「重命名」不会顺手把内容改成当前草稿。
  const filter = editing ? structuredClone(editing.filter) : structuredClone(effectiveGroup(draft.value))
  if (!countGroup(filter)) return ElMessage.warning('请先设置筛选条件')
  saving.value = true
  try {
    const payload: TemplateApi.AdvancedFilterTemplateSaveReq = {
      scene: props.scene,
      pageKey: props.pageKey as string,
      name,
      filter,
      sort: editing ? editing.sort : personalTemplates.value.length * 10 + 10,
      enabled: true,
      defaultTemplate: saveForm.value.defaultTemplate
    }
    if (editing) {
      await TemplateApi.updatePersonalTemplate({ ...payload, id: editing.id, version: editing.version })
      ElMessage.success('快捷筛选已更新')
    } else {
      await TemplateApi.createPersonalTemplate(payload)
      ElMessage.success('已存为我的快捷筛选')
    }
    saveOpen.value = false
    await loadTemplates()
  } catch (error: any) {
    ElMessage.error(error?.msg || error?.message || '保存失败，请重试')
  } finally {
    saving.value = false
  }
}
const setDefault = async (template: TemplateApi.AdvancedFilterTemplate) => {
  try {
    await TemplateApi.updatePersonalTemplate({
      id: template.id, scene: template.scene, pageKey: template.pageKey, name: template.name,
      filter: template.filter, sort: template.sort, enabled: template.enabled,
      defaultTemplate: true, version: template.version
    })
    ElMessage.success('已设为默认')
    await loadTemplates()
  } catch (error: any) {
    ElMessage.error(error?.msg || error?.message || '设置失败，请重试')
  }
}
const removeTemplate = async (template: TemplateApi.AdvancedFilterTemplate) => {
  try {
    await ElMessageBox.confirm(`删除后该快捷筛选不再出现，确定删除「${template.name}」？`, '删除快捷筛选')
  } catch {
    return
  }
  try {
    await TemplateApi.deletePersonalTemplate(template.id)
    if (activeTemplateId.value === template.id) activeTemplateId.value = undefined
    ElMessage.success('已删除')
    await loadTemplates()
  } catch (error: any) {
    ElMessage.error(error?.msg || error?.message || '删除失败，请重试')
  }
}
const removeTag = (item: { groupIndex: number; index: number }) => {
  if (item.groupIndex < 0) updateDraft({ ...draft.value, conditions: draft.value.conditions.filter((_, index) => index !== item.index) }, true)
  else updateDraft({ ...draft.value, groups: draft.value.groups.map((group, index) => index === item.groupIndex ? { ...group, conditions: group.conditions.filter((_, conditionIndex) => conditionIndex !== item.index) } : group) }, true)
}
const durationSummary = (condition: Api.AdvancedFilterCondition) => {
  const field = fieldMap.value[condition.fieldKey]
  const options = field?.options?.length ? field.options : fields.value.filter((item) => item.valueType === 'date').map((item) => ({ value: item.fieldKey, label: item.label }))
  const start = options.find((option) => option.value === condition.startFieldKey)?.label || '开始时间'
  const end = options.find((option) => option.value === condition.endFieldKey)?.label || '结束时间'
  const unit = durationUnitLabels[condition.unit || 'hour'] || condition.unit || ''
  if (condition.operator === 'between') return `${end} - ${start} ${durationOperatorLabels.between} ${condition.valueFrom ?? ''} - ${condition.valueTo ?? ''} ${unit}`
  return `${end} - ${start} ${durationOperatorLabels[condition.operator] || condition.operator} ${condition.value ?? ''} ${unit}`
}
const summarize = (condition: Api.AdvancedFilterCondition) => {
  const field = fieldMap.value[condition.fieldKey]
  if (condition.fieldKey === 'duration.diff') return durationSummary(condition)
  const value = condition.operator !== 'relative' ? '' : ` ${relativeDateOptions.value.find((item) => item.value === condition.value)?.label || condition.value || ''}`
  return `${field?.group || ''} · ${field?.label || '筛选字段'} ${operatorLabels[condition.operator] || condition.operator}${value}`
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
    relativeDateOptions.value = catalog.relativeDateOptions || []
    fields.value = catalog.fields.map((field) => field.optionSource && !field.options.length ? { ...field, optionsLoading: true } : field)
    await Promise.all(catalog.fields.map(async (field) => {
      if (!field.optionSource || field.options.length) return
      try { const options = await sourceOptions(field.optionSource); fields.value = fields.value.map((item) => item.fieldKey === field.fieldKey ? { ...item, options, optionsLoading: false } : item) }
      catch { fields.value = fields.value.map((item) => item.fieldKey === field.fieldKey ? { ...item, optionsLoading: false, optionsError: true } : item) }
    }))
  } catch { catalogError.value = true; fields.value = [] }
  finally { catalogLoading.value = false }
}
onMounted(() => { void loadCatalog(); void loadTemplates() })
onBeforeUnmount(() => window.clearTimeout(timer))
</script>

<style scoped>
.advanced-toolbar{display:flex;gap:8px;max-width:520px;margin-top:12px}.advanced-toolbar>.el-input{flex:1}.search-icon{cursor:pointer}.advanced-presets{display:flex;flex-wrap:wrap;align-items:center;gap:6px;margin-top:8px}.advanced-presets__label{color:var(--el-text-color-secondary);font-size:12px}.advanced-presets__tag{cursor:pointer}.advanced-presets__default{margin-left:4px;font-size:10px;opacity:.75}.advanced-presets__manage{display:flex;align-items:center;gap:12px;margin-top:6px}.advanced-presets__hint{margin-left:8px;color:var(--el-text-color-secondary);font-size:12px}.advanced-tags{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.catalog-state{display:flex;align-items:center;justify-content:center;gap:8px;min-height:160px;color:var(--el-text-color-secondary)}@media(max-width:768px){.advanced-toolbar{max-width:none}.advanced-toolbar>.el-input{min-width:0}:global(.advanced-filter-drawer){width:100%!important}}
</style>
