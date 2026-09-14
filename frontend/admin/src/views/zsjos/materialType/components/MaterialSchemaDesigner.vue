<template>
  <div class="material-schema-designer">
    <div class="material-schema-designer__toolbar">
      <div>
        <strong>字段定义</strong>
        <span>一级字段最多 100 个，重复字段组只支持一层</span>
      </div>
      <el-button type="primary" plain @click="addField">
        <Icon icon="ep:plus" class="mr-4px" />新增字段
      </el-button>
    </div>
    <el-empty v-if="!modelValue.length" description="请先添加字段" />
    <MaterialSchemaFieldCard
      v-for="(field, index) in modelValue"
      :key="field.key || index"
      :field="field"
      :index="index"
      :total="modelValue.length"
      :dict-types="dictTypes"
      @move="move(index, $event)"
      @remove="remove(index)"
    />
  </div>
</template>

<script setup lang="ts">
import type { MaterialFieldDefinition } from '@/api/zsjos/material'
import { RECOMMENDATION_DICT_DIMENSIONS } from '@/api/zsjos/material'
import MaterialSchemaFieldCard from './MaterialSchemaFieldCard.vue'

defineOptions({ name: 'MaterialSchemaDesigner' })

const props = defineProps<{
  modelValue: MaterialFieldDefinition[]
  dictTypes: Array<{ name: string; type: string }>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: MaterialFieldDefinition[]] }>()

const update = (fields: MaterialFieldDefinition[]) => emit('update:modelValue', fields)
const addField = () =>
  update([
    ...props.modelValue,
    {
      key: `field_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`,
      label: '新字段',
      type: 'text',
      required: false,
      searchable: false
    }
  ])
const move = (index: number, offset: number) => {
  const target = index + offset
  if (target < 0 || target >= props.modelValue.length) return
  const fields = [...props.modelValue]
  ;[fields[index], fields[target]] = [fields[target], fields[index]]
  update(fields)
}
const remove = (index: number) => update(props.modelValue.filter((_, current) => current !== index))

const validateFields = (fields: MaterialFieldDefinition[]): string => {
  if (!fields.length) return '至少配置一个字段'
  if (fields.length > 100) return '一级字段不能超过 100 个'
  const dimensions = new Set<string>()
  const validateLevel = (items: MaterialFieldDefinition[], nested: boolean): string => {
    const keys = new Set<string>()
    for (const field of items) {
      if (!/^[a-z][a-z0-9_]{0,63}$/.test(field.key || '')) {
        return `字段“${field.label || field.key || '未命名'}”编码格式不正确`
      }
      if (keys.has(field.key)) return `同一层字段编码不能重复：${field.key}`
      keys.add(field.key)
      if (!field.label?.trim()) return `字段 ${field.key} 缺少名称`
      if (nested && field.type === 'repeat-group') return '重复字段组不能嵌套'
      if (['dict-single', 'dict-multi'].includes(field.type) && !field.dictType) {
        return `字典字段“${field.label}”必须选择系统字典`
      }
      const dimension = field.dictType ? RECOMMENDATION_DICT_DIMENSIONS[field.dictType] : undefined
      if (dimension) {
        if (nested) return `推荐维度字段“${field.label}”不能放在重复字段组中`
        if (dimensions.has(dimension)) {
          return `推荐维度不能重复：${dimension}`
        }
        dimensions.add(dimension)
      }
      if (field.min != null && field.max != null && field.min > field.max) {
        return `字段“${field.label}”的最小值不能大于最大值`
      }
      if (field.minCount != null && field.maxCount != null && field.minCount > field.maxCount) {
        return `字段“${field.label}”的最少数量不能大于最多数量`
      }
      if (field.type === 'repeat-group') {
        if (!field.children?.length) return `重复字段组“${field.label}”至少需要一个子字段`
        if (field.children.length > 50) return `重复字段组“${field.label}”的子字段不能超过 50 个`
        const childProblem = validateLevel(field.children, true)
        if (childProblem) return childProblem
      }
    }
    return ''
  }
  return validateLevel(fields, false)
}

const normalizedFields = (): MaterialFieldDefinition[] => {
  const normalize = (fields: MaterialFieldDefinition[]) =>
    fields.map((field, index) => {
      const supportsCount =
        field.type === 'dict-multi' ||
        field.type === 'repeat-group' ||
        ['image', 'video', 'attachment'].includes(field.type) ||
        (['employee', 'department'].includes(field.type) && field.multiple)
      return {
        ...field,
        key: field.key.trim(),
        label: field.label.trim(),
        minCount: supportsCount ? field.minCount : undefined,
        maxCount: supportsCount ? field.maxCount : undefined,
        allowedExtensions: field.allowedExtensions?.map((item) => item.trim().replace(/^\./, '')),
        children: field.children ? normalize(field.children) : undefined,
        sort: (index + 1) * 10
      }
    })
  return normalize(props.modelValue)
}

defineExpose({ validate: () => validateFields(props.modelValue), normalizedFields })
</script>

<style scoped>
.material-schema-designer__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.material-schema-designer__toolbar > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.material-schema-designer__toolbar span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
