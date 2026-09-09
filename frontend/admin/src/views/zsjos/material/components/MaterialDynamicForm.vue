<template>
  <el-form label-position="top" class="material-dynamic-form">
    <el-row :gutter="18">
      <el-col
        v-for="field in fields"
        :key="field.key"
        :xs="24"
        :lg="field.type === 'textarea' || field.type === 'rich-text' || field.type === 'repeat-group' ? 24 : 12"
      >
        <MaterialValueField
          :field="field"
          :model-value="modelValue[field.key]"
          :snapshot="snapshots[field.key]"
          :dict-data="dictData"
          :users="users"
          :departments="departments"
          :files="files"
          :field-path="field.key"
          :readonly="readonly"
          @update:model-value="updateField(field.key, $event)"
        />
      </el-col>
    </el-row>
  </el-form>
</template>

<script setup lang="ts">
import type { MaterialFieldDefinition, MaterialFile } from '@/api/zsjos/material'
import MaterialValueField from './MaterialValueField.vue'

defineOptions({ name: 'MaterialDynamicForm' })

const props = withDefaults(
  defineProps<{
    fields: MaterialFieldDefinition[]
    modelValue: Record<string, unknown>
    snapshots?: Record<string, unknown>
    dictData: Array<{ dictType: string; label: string; value: string | number }>
    users: Array<{ id: number; nickname: string }>
    departments: Array<{ id: number; name: string }>
    files?: MaterialFile[]
    readonly?: boolean
  }>(),
  { snapshots: () => ({}), files: () => [], readonly: false }
)
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const updateField = (key: string, value: unknown) =>
  emit('update:modelValue', { ...props.modelValue, [key]: value })

const plainRichText = (value: unknown) =>
  new DOMParser().parseFromString(String(value ?? ''), 'text/html').body.textContent?.trim() || ''

const empty = (value: unknown, field?: MaterialFieldDefinition) =>
  value == null || (typeof value === 'string' && value.trim() === '') ||
  (Array.isArray(value) && value.length === 0) ||
  (field?.type === 'rich-text' && plainRichText(value) === '')

const validateField = (field: MaterialFieldDefinition, value: unknown, label = field.label): string => {
  if (field.required && empty(value, field)) return `请填写${label}`
  if (empty(value, field)) return ''

  if (['text', 'textarea', 'rich-text'].includes(field.type) && field.maxLength != null &&
      String(value).trim().length > field.maxLength) {
    return `${label}不能超过 ${field.maxLength} 个字符`
  }
  if (field.type === 'number') {
    const number = Number(value)
    if (!Number.isFinite(number)) return `${label}必须是有效数字`
    if (field.min != null && number < field.min) return `${label}不能小于 ${field.min}`
    if (field.max != null && number > field.max) return `${label}不能大于 ${field.max}`
  }
  if (field.type === 'https-link') {
    try {
      const url = new URL(String(value).trim())
      if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || String(value).length > 2048) {
        return `${label}必须使用有效的 HTTPS 链接`
      }
    } catch {
      return `${label}链接格式不正确`
    }
  }

  const count = Array.isArray(value) ? value.length : 1
  if (field.minCount != null && count < field.minCount) return `${label}至少填写 ${field.minCount} 项`
  if (field.maxCount != null && count > field.maxCount) return `${label}最多填写 ${field.maxCount} 项`
  if (field.type !== 'repeat-group') return ''
  if (!Array.isArray(value)) return `${label}格式不正确`

  for (const [rowIndex, row] of value.entries()) {
    if (row == null || typeof row !== 'object' || Array.isArray(row)) return `${label}第 ${rowIndex + 1} 组格式不正确`
    for (const child of field.children || []) {
      const problem = validateField(
        child,
        (row as Record<string, unknown>)[child.key],
        `${label}第 ${rowIndex + 1} 组的${child.label}`
      )
      if (problem) return problem
    }
  }
  return ''
}

const validate = (): string => {
  for (const field of props.fields) {
    const problem = validateField(field, props.modelValue[field.key])
    if (problem) return problem
  }
  return ''
}

defineExpose({ validate })
</script>
