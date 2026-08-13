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
      <el-select :model-value="condition.fieldKey" filterable @change="(value) => setField(index, value)">
        <el-option v-for="field in fields" :key="field.fieldKey" :label="`${field.group} · ${field.label}`" :value="field.fieldKey" />
      </el-select>
      <el-select :model-value="condition.operator" @change="(value) => setOperator(index, value)">
        <el-option v-for="operator in fieldOf(condition)?.operators || []" :key="operator" :label="operatorLabels[operator] || operator" :value="operator" />
      </el-select>
      <div class="filter-condition__value">
        <template v-if="!isEmptyOperator(condition.operator)">
          <template v-if="condition.operator === 'between'">
            <el-date-picker v-if="fieldOf(condition)?.valueType === 'date'" :model-value="[condition.valueFrom, condition.valueTo]" type="datetimerange" value-format="x" start-placeholder="开始时间" end-placeholder="结束时间" @update:model-value="(value) => setRange(index, value)" />
            <div v-else class="number-range"><el-input-number :model-value="condition.valueFrom as number" @update:model-value="(value) => setValue(index, 'valueFrom', value)" /><span>至</span><el-input-number :model-value="condition.valueTo as number" @update:model-value="(value) => setValue(index, 'valueTo', value)" /></div>
          </template>
          <el-select v-else-if="fieldOf(condition)?.valueType === 'select'" :model-value="selectValue(condition)" multiple filterable :loading="fieldOf(condition)?.optionsLoading" @change="(value) => setValue(index, 'value', value, true)">
            <el-option v-for="option in fieldOf(condition)?.options || []" :key="String(option.value)" :label="option.label" :value="option.value" />
            <template #empty><div class="filter-empty"><template v-if="fieldOf(condition)?.optionsError">选项加载失败 <el-button link type="primary" @click="emit('retryOptions', condition.fieldKey)">重试</el-button></template><template v-else>暂无可选项</template></div></template>
          </el-select>
          <el-input-number v-else-if="fieldOf(condition)?.valueType === 'number'" :model-value="numberValue(condition.value)" @update:model-value="(value) => setValue(index, 'value', value)" />
          <el-date-picker v-else-if="fieldOf(condition)?.valueType === 'date'" :model-value="condition.value" type="datetime" value-format="x" placeholder="选择时间" @update:model-value="(value) => setValue(index, 'value', value)" />
          <el-input v-else :model-value="textValue(condition.value)" clearable @update:model-value="(value) => setValue(index, 'value', value)" />
        </template>
      </div>
      <el-button circle text type="danger" aria-label="删除条件" @click="removeCondition(index)"><Icon icon="ep:delete" /></el-button>
    </div>

    <ZsjosAdvancedFilterGroup v-for="(group, index) in modelValue.groups" :key="`group-${index}`" :model-value="group" :fields="fields" :depth="1" :total="total" removable @update:model-value="(value, immediate) => updateGroup(index, value, immediate)" @remove="removeGroup(index)" @retry-options="(fieldKey) => emit('retryOptions', fieldKey)" />
  </div>
</template>

<script setup lang="ts">
import type { AdvancedFilterCondition, AdvancedFilterField, AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'

const props = defineProps<{ modelValue: AdvancedFilterGroup; fields: AdvancedFilterField[]; depth: number; total: number; removable?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: AdvancedFilterGroup, immediate?: boolean]; remove: []; retryOptions: [fieldKey: string] }>()
const operatorLabels: Record<string, string> = { contains: '包含', eq: '等于', ne: '不等于', in: '属于', not_in: '不属于', gt: '大于', lt: '小于', between: '区间', is_empty: '为空', is_not_empty: '不为空' }
const blank = (): AdvancedFilterGroup => ({ logic: 'AND', conditions: [], groups: [] })
const fieldOf = (condition: AdvancedFilterCondition) => props.fields.find((field) => field.fieldKey === condition.fieldKey)
const isEmptyOperator = (operator: string) => operator === 'is_empty' || operator === 'is_not_empty'
const selectValue = (condition: AdvancedFilterCondition) => Array.isArray(condition.value) ? condition.value as Array<string | number> : []
const numberValue = (value: unknown) => typeof value === 'number' ? value : undefined
const textValue = (value: unknown) => typeof value === 'string' ? value : ''
const update = (next: AdvancedFilterGroup, immediate = false) => emit('update:modelValue', next, immediate)
const setLogic = (logic: 'AND' | 'OR') => update({ ...props.modelValue, logic }, true)
const addCondition = () => { const field = props.fields[0]; if (field && props.total < 20) update({ ...props.modelValue, conditions: [...props.modelValue.conditions, { fieldKey: field.fieldKey, operator: field.operators[0] }] }, true) }
const addGroup = () => update({ ...props.modelValue, groups: [...props.modelValue.groups, blank()] }, true)
const setField = (index: number, fieldKey: string) => { const field = props.fields.find((item) => item.fieldKey === fieldKey); if (!field) return; const conditions = [...props.modelValue.conditions]; conditions[index] = { fieldKey, operator: field.operators[0] }; update({ ...props.modelValue, conditions }, true) }
const setOperator = (index: number, operator: string) => { const conditions = [...props.modelValue.conditions]; conditions[index] = { fieldKey: conditions[index].fieldKey, operator }; update({ ...props.modelValue, conditions }, true) }
const setValue = (index: number, key: 'value' | 'valueFrom' | 'valueTo', value: unknown, immediate = false) => { const conditions = [...props.modelValue.conditions]; const normalized = fieldOf(conditions[index])?.valueType === 'date' && value !== undefined && value !== null ? Number(value) : value; conditions[index] = { ...conditions[index], [key]: normalized }; update({ ...props.modelValue, conditions }, immediate) }
const setRange = (index: number, value?: [string | number, string | number]) => { const conditions = [...props.modelValue.conditions]; conditions[index] = { ...conditions[index], valueFrom: value?.[0] === undefined ? undefined : Number(value[0]), valueTo: value?.[1] === undefined ? undefined : Number(value[1]) }; update({ ...props.modelValue, conditions }) }
const removeCondition = (index: number) => update({ ...props.modelValue, conditions: props.modelValue.conditions.filter((_, item) => item !== index) }, true)
const updateGroup = (index: number, group: AdvancedFilterGroup, immediate = false) => update({ ...props.modelValue, groups: props.modelValue.groups.map((item, itemIndex) => itemIndex === index ? group : item) }, immediate)
const removeGroup = (index: number) => update({ ...props.modelValue, groups: props.modelValue.groups.filter((_, item) => item !== index) }, true)
</script>

<style scoped>
.filter-group{display:flex;flex-direction:column;gap:12px;padding:14px;border:1px solid var(--el-border-color-light);border-radius:8px;background:var(--el-fill-color-extra-light)}.filter-group.depth-1{margin-top:4px;background:var(--el-fill-color-light)}.filter-group__head,.filter-group__actions,.filter-condition,.number-range{display:flex;align-items:center;gap:8px}.filter-group__head{justify-content:space-between}.filter-condition> .el-select{width:170px}.filter-condition__value{min-width:0;flex:1}.filter-condition__value :deep(.el-select),.filter-condition__value :deep(.el-date-editor),.filter-condition__value :deep(.el-input-number){width:100%}.number-range span{flex:none;color:var(--el-text-color-secondary)}.filter-empty{padding:8px;text-align:center;color:var(--el-text-color-secondary)}@media(max-width:768px){.filter-group__head,.filter-condition{align-items:stretch;flex-direction:column}.filter-group__actions{flex-wrap:wrap}.filter-condition> .el-select{width:100%}.filter-condition>button{align-self:flex-end}.filter-group.depth-1{margin-left:0}}
</style>
