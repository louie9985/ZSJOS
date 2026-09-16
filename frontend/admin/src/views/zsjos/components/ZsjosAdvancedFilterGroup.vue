<template>
  <div class="filter-group" :class="`depth-${depth}`">
    <div class="filter-group__head">
      <el-select :model-value="modelValue.logic" class="!w-130px" @change="setLogic">
        <el-option label="满足全部" value="AND" />
        <el-option label="满足任一" value="OR" />
      </el-select>
      <div class="filter-group__actions">
        <el-button size="small" :disabled="!fields.length || total >= 20" @click="addCondition">
          <Icon icon="ep:plus" />条件
        </el-button>
        <el-button v-if="depth === 0" size="small" :disabled="modelValue.groups.length >= 5" @click="addGroup">
          <Icon icon="ep:folder-add" />条件组
        </el-button>
        <el-button v-if="removable" size="small" type="danger" plain @click="emit('remove')">删除组</el-button>
      </div>
    </div>

    <div v-for="(condition, index) in modelValue.conditions" :key="`${condition.fieldKey}-${index}`" class="filter-condition">
      <el-select :model-value="condition.fieldKey" class="filter-condition__field" filterable fit-input-width popper-class="advanced-filter-select-popper" :title="fieldOptionLabel(condition.fieldKey)" @change="(value) => setField(index, value)">
        <el-option-group v-for="optionGroup in fieldOptionGroups" :key="optionGroup.label" :label="optionGroup.label">
          <el-option v-for="field in optionGroup.options" :key="field.fieldKey" :label="`${field.group} · ${field.label}`" :value="field.fieldKey" />
        </el-option-group>
      </el-select>
      <el-select :model-value="condition.operator" class="filter-condition__operator" fit-input-width popper-class="advanced-filter-select-popper" @change="(value) => setOperator(index, value)">
        <el-option v-for="operator in fieldOf(condition)?.operators || []" :key="operator" :label="operatorLabels[operator] || operator" :value="operator" />
      </el-select>
      <div class="filter-condition__value">
        <template v-if="!isEmptyOperator(condition.operator)">
          <el-select v-if="condition.operator === 'relative'" :model-value="relativeValue(condition)" :placeholder="relativePlaceholder" popper-class="advanced-filter-select-popper" @change="(value) => setValue(index, 'value', value, true)">
            <el-option v-for="option in relativeDateOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <template v-else-if="fieldOf(condition)?.valueType === 'duration'">
            <div class="duration-control">
              <el-select :model-value="condition.startFieldKey" filterable fit-input-width placeholder="开始时间" popper-class="advanced-filter-select-popper" @change="(value) => setValue(index, 'startFieldKey', value, true)">
                <el-option v-for="option in durationDateOptions(condition)" :key="String(option.value)" :label="option.label" :value="option.value" />
              </el-select>
              <el-select :model-value="condition.endFieldKey" filterable fit-input-width placeholder="结束时间" popper-class="advanced-filter-select-popper" @change="(value) => setValue(index, 'endFieldKey', value, true)">
                <el-option v-for="option in durationDateOptions(condition)" :key="String(option.value)" :label="option.label" :value="option.value" />
              </el-select>
              <el-select :model-value="condition.unit || 'hour'" fit-input-width popper-class="advanced-filter-select-popper" @change="(value) => setValue(index, 'unit', value, true)">
                <el-option v-for="option in durationUnitOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <div v-if="condition.operator === 'between'" class="number-range"><el-input-number :model-value="numberValue(condition.valueFrom)" placeholder="最小值" @update:model-value="(value) => setValue(index, 'valueFrom', value)" /><span>至</span><el-input-number :model-value="numberValue(condition.valueTo)" placeholder="最大值" @update:model-value="(value) => setValue(index, 'valueTo', value)" /></div>
              <el-input-number v-else :model-value="numberValue(condition.value)" placeholder="比较值" @update:model-value="(value) => setValue(index, 'value', value)" />
            </div>
          </template>
          <template v-else-if="condition.operator === 'between'">
            <el-date-picker v-if="fieldOf(condition)?.valueType === 'date'" :model-value="dateRangeValue(condition.valueFrom, condition.valueTo)" type="datetimerange" value-format="x" start-placeholder="开始时间" end-placeholder="结束时间" @update:model-value="(value) => setRange(index, value)" />
            <div v-else class="number-range"><el-input-number :model-value="condition.valueFrom as number" @update:model-value="(value) => setValue(index, 'valueFrom', value)" /><span>至</span><el-input-number :model-value="condition.valueTo as number" @update:model-value="(value) => setValue(index, 'valueTo', value)" /></div>
          </template>
          <el-select v-else-if="fieldOf(condition)?.valueType === 'select'" :model-value="selectValue(condition)" multiple filterable fit-input-width popper-class="advanced-filter-select-popper" :loading="fieldOf(condition)?.optionsLoading" @change="(value) => setValue(index, 'value', value, true)">
            <el-option v-for="option in fieldOf(condition)?.options || []" :key="String(option.value)" :label="option.label" :value="option.value" />
            <template #empty><div class="filter-empty"><template v-if="fieldOf(condition)?.optionsError">选项加载失败 <el-button link type="primary" @click="emit('retryOptions', condition.fieldKey)">重试</el-button></template><template v-else>暂无可选项</template></div></template>
          </el-select>
          <el-input-number v-else-if="fieldOf(condition)?.valueType === 'number'" :model-value="numberValue(condition.value)" @update:model-value="(value) => setValue(index, 'value', value)" />
          <el-date-picker v-else-if="fieldOf(condition)?.valueType === 'date'" :model-value="dateInputValue(condition.value)" type="datetime" value-format="x" placeholder="选择时间" @update:model-value="(value) => setValue(index, 'value', value)" />
          <el-input v-else :model-value="textValue(condition.value)" clearable @update:model-value="(value) => setValue(index, 'value', value)" />
        </template>
      </div>
      <el-button circle text type="danger" aria-label="删除条件" @click="removeCondition(index)"><Icon icon="ep:delete" /></el-button>
    </div>

    <ZsjosAdvancedFilterGroup v-for="(group, index) in modelValue.groups" :key="`group-${index}`" :model-value="group" :fields="fields" :depth="1" :total="total" removable :relative-date-options="relativeDateOptions" @update:model-value="(value, immediate) => updateGroup(index, value, immediate)" @remove="removeGroup(index)" @retry-options="(fieldKey) => emit('retryOptions', fieldKey)" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AdvancedFilterCondition, AdvancedFilterField, AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'

const props = withDefaults(defineProps<{ modelValue: AdvancedFilterGroup; fields: AdvancedFilterField[]; depth: number; total: number; removable?: boolean; relativeDateOptions?: Array<{ value: string; label: string }> }>(), { relativeDateOptions: () => [] })
const emit = defineEmits<{ 'update:modelValue': [value: AdvancedFilterGroup, immediate?: boolean]; remove: []; retryOptions: [fieldKey: string] }>()
const operatorLabels: Record<string, string> = { contains: '包含', not_contains: '不包含', eq: '等于', ne: '不等于', in: '属于', not_in: '不属于', gt: '大于', gte: '大于等于', lt: '小于', lte: '小于等于', between: '区间', relative: '相对时间', is_empty: '为空', is_not_empty: '不为空' }
const durationUnitOptions: Array<{ value: 'minute' | 'hour' | 'day'; label: string }> = [{ value: 'minute', label: '分钟' }, { value: 'hour', label: '小时' }, { value: 'day', label: '天' }]
const blank = (): AdvancedFilterGroup => ({ logic: 'AND', conditions: [], groups: [] })
const fieldOf = (condition: AdvancedFilterCondition) => props.fields.find((field) => field.fieldKey === condition.fieldKey)
const durationDateOptions = (condition: AdvancedFilterCondition) => {
  const field = fieldOf(condition)
  return field?.valueType === 'duration' && field.options.length
    ? field.options
    : props.fields.filter((item) => item.valueType === 'date').map((item) => ({ value: item.fieldKey, label: item.label }))
}
const isEmptyOperator = (operator: string) => operator === 'is_empty' || operator === 'is_not_empty'
const fieldOptionLabel = (fieldKey: string) => { const field = props.fields.find((item) => item.fieldKey === fieldKey); return field ? `${field.group} · ${field.label}` : '' }
const fieldOptionGroups = computed(() => {
  const groups = new Map<string, AdvancedFilterField[]>()
  props.fields.forEach((field) => { const list = groups.get(field.group); if (list) list.push(field); else groups.set(field.group, [field]) })
  return Array.from(groups, ([label, options]) => ({ label, options }))
})
const relativeValue = (condition: AdvancedFilterCondition) => typeof condition.value === 'string' && condition.value !== '' ? condition.value : undefined
const relativePlaceholder = computed(() => (props.relativeDateOptions.length ? '选择相对时间' : '暂无可选相对时间'))
const selectValue = (condition: AdvancedFilterCondition) => Array.isArray(condition.value) ? condition.value as Array<string | number> : []
const numberValue = (value: unknown) => typeof value === 'number' ? value : typeof value === 'string' && value !== '' ? Number(value) : undefined
const textValue = (value: unknown) => typeof value === 'string' ? value : ''
const dateInputValue = (value: unknown): Date | undefined => value instanceof Date ? value : typeof value === 'string' || typeof value === 'number' ? new Date(Number(value)) : undefined
const dateRangeValue = (from: unknown, to: unknown): [Date, Date] | undefined => { const start = dateInputValue(from), end = dateInputValue(to); return start && end ? [start, end] : undefined }
const update = (next: AdvancedFilterGroup, immediate = false) => emit('update:modelValue', next, immediate)
const setLogic = (logic: 'AND' | 'OR') => update({ ...props.modelValue, logic }, true)
const addCondition = () => { const field = props.fields[0]; if (field && props.total < 20) update({ ...props.modelValue, conditions: [...props.modelValue.conditions, { fieldKey: field.fieldKey, operator: field.operators[0] }] }, true) }
const addGroup = () => update({ ...props.modelValue, groups: [...props.modelValue.groups, blank()] }, true)
const setField = (index: number, fieldKey: string) => { const field = props.fields.find((item) => item.fieldKey === fieldKey); if (!field) return; const conditions = [...props.modelValue.conditions]; conditions[index] = field.valueType === 'duration' ? { fieldKey, operator: field.operators[0], unit: 'hour' } : { fieldKey, operator: field.operators[0] }; update({ ...props.modelValue, conditions }, true) }
const setOperator = (index: number, operator: string) => { const conditions = [...props.modelValue.conditions]; const current = conditions[index]; const field = fieldOf(current); conditions[index] = field?.valueType === 'duration' ? { fieldKey: current.fieldKey, operator, startFieldKey: current.startFieldKey, endFieldKey: current.endFieldKey, unit: current.unit || 'hour' } : { fieldKey: current.fieldKey, operator }; update({ ...props.modelValue, conditions }, true) }
const setValue = (index: number, key: 'value' | 'valueFrom' | 'valueTo' | 'startFieldKey' | 'endFieldKey' | 'unit', value: unknown, immediate = false) => { const conditions = [...props.modelValue.conditions]; const normalized = fieldOf(conditions[index])?.valueType === 'date' && value !== undefined && value !== null ? Number(value) : value; conditions[index] = { ...conditions[index], [key]: normalized }; update({ ...props.modelValue, conditions }, immediate) }
const setRange = (index: number, value?: [string | number, string | number]) => { const conditions = [...props.modelValue.conditions]; conditions[index] = { ...conditions[index], valueFrom: value?.[0] === undefined ? undefined : Number(value[0]), valueTo: value?.[1] === undefined ? undefined : Number(value[1]) }; update({ ...props.modelValue, conditions }) }
const removeCondition = (index: number) => update({ ...props.modelValue, conditions: props.modelValue.conditions.filter((_, item) => item !== index) }, true)
const updateGroup = (index: number, group: AdvancedFilterGroup, immediate = false) => update({ ...props.modelValue, groups: props.modelValue.groups.map((item, itemIndex) => itemIndex === index ? group : item) }, immediate)
const removeGroup = (index: number) => update({ ...props.modelValue, groups: props.modelValue.groups.filter((_, item) => item !== index) }, true)
</script>

<style scoped>
.filter-group{display:flex;flex-direction:column;gap:12px;padding:14px;border:1px solid var(--el-border-color-light);border-radius:8px;background:var(--el-fill-color-extra-light)}.filter-group.depth-1{margin-top:4px;background:var(--el-fill-color-light)}.filter-group__head,.filter-group__actions,.filter-condition,.number-range{display:flex;align-items:center;gap:8px}.filter-group__head{justify-content:space-between}.filter-condition{min-width:0}.filter-condition__field{flex:0 0 210px;width:210px}.filter-condition__operator{flex:0 0 112px;width:112px}.filter-condition__value{min-width:160px;flex:1 1 auto}.filter-condition__value :deep(.el-select),.filter-condition__value :deep(.el-date-editor),.filter-condition__value :deep(.el-input-number){width:100%}.filter-condition__value :deep(.el-select__selected-item),.filter-condition__field :deep(.el-select__selected-item){overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.duration-control{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;min-width:0}.number-range span{flex:none;color:var(--el-text-color-secondary)}.filter-empty{padding:8px;text-align:center;color:var(--el-text-color-secondary)}:global(.advanced-filter-select-popper){min-width:190px!important;max-width:min(600px,calc(100vw - 24px))}:global(.advanced-filter-select-popper .el-select-dropdown__item){height:auto;min-height:34px;white-space:normal;overflow-wrap:anywhere;line-height:20px;padding-top:7px;padding-bottom:7px}:global(.advanced-filter-select-popper .el-select-group__title){height:auto;line-height:20px;padding:7px 12px 4px}@media(max-width:768px){.filter-group__head,.filter-condition{align-items:stretch;flex-direction:column}.filter-group__actions{flex-wrap:wrap}.filter-condition__field,.filter-condition__operator,.filter-condition__value{width:100%;min-width:0}.filter-condition>button{align-self:flex-end}.filter-group.depth-1{margin-left:0}.duration-control{grid-template-columns:1fr}}
</style>
