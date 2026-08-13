<template>
  <div class="advanced-toolbar">
    <el-input v-model="searchText" clearable :placeholder="placeholder" @keyup.enter="emit('search', searchText.trim())">
      <template #suffix><Icon icon="ep:search" class="cursor-pointer" @click="emit('search', searchText.trim())" /></template>
    </el-input>
    <el-badge :value="count || ''"><el-button :icon="Filter" @click="visible = true">筛选</el-button></el-badge>
  </div>
  <div v-if="count" class="advanced-tags">
    <el-tag v-for="(item, index) in flatConditions" :key="index">{{ fieldMap[item.fieldKey]?.group }} · {{ fieldMap[item.fieldKey]?.label }} {{ operatorLabels[item.operator] }}</el-tag>
    <el-button link type="primary" @click="clear">清空全部</el-button>
  </div>
  <el-drawer v-model="visible" title="高级筛选 · 修改后自动生效" size="560px">
    <GroupEditor :model-value="draft" :fields="fields" :depth="0" @update:model-value="draft = $event" />
    <template #footer><el-button @click="clear">清空全部</el-button><el-button type="primary" @click="visible = false">关闭</el-button></template>
  </el-drawer>
</template>

<script setup lang="ts">
import { Filter } from '@element-plus/icons-vue'
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue'
import * as Api from '@/api/zsjos/advancedFilter'

const props = defineProps<{ scene: 'lead' | 'order'; placeholder: string; keyword?: string }>()
const emit = defineEmits<{ change: [value?: Api.AdvancedFilterGroup]; search: [value: string] }>()
const visible = ref(false), fields = ref<Api.AdvancedFilterField[]>([]), searchText = ref(props.keyword || '')
const blank = (): Api.AdvancedFilterGroup => ({ logic: 'AND', conditions: [], groups: [] })
const draft = ref(blank())
const countGroup = (group: Api.AdvancedFilterGroup): number => group.conditions.length + group.groups.reduce((sum, item) => sum + countGroup(item), 0)
const count = computed(() => countGroup(draft.value))
const flatConditions = computed(() => [...draft.value.conditions, ...draft.value.groups.flatMap(group => group.conditions)])
const fieldMap = computed(() => Object.fromEntries(fields.value.map(field => [field.fieldKey, field])))
const operatorLabels: Record<string, string> = { contains: '包含', eq: '等于', ne: '不等于', in: '属于', not_in: '不属于', gt: '大于', lt: '小于', between: '区间', is_empty: '为空', is_not_empty: '不为空' }
let timer: number | undefined
watch(draft, () => { window.clearTimeout(timer); timer = window.setTimeout(() => emit('change', count.value ? structuredClone(draft.value) : undefined), 500) }, { deep: true })
onMounted(async () => { try { fields.value = (await Api.getCatalog(props.scene)).fields || [] } catch { fields.value = [] } })
const clear = () => { draft.value = blank() }

const GroupEditor = defineComponent({
  name: 'GroupEditor', props: { modelValue: { type: Object, required: true }, fields: { type: Array, required: true }, depth: { type: Number, required: true } }, emits: ['update:modelValue'],
  setup(p, { emit: update }) {
    const group = computed(() => p.modelValue as Api.AdvancedFilterGroup), list = computed(() => p.fields as Api.AdvancedFilterField[])
    const change = (next: Api.AdvancedFilterGroup) => update('update:modelValue', next)
    return () => h('div', { class: ['advanced-group', `depth-${p.depth}`] }, [
      h('div', { class: 'advanced-group-head' }, [h('select', { value: group.value.logic, onChange: (e: Event) => change({ ...group.value, logic: (e.target as HTMLSelectElement).value as 'AND' | 'OR' }) }, [h('option', { value: 'AND' }, '满足全部'), h('option', { value: 'OR' }, '满足任一')]),
        h('button', { disabled: !list.value.length, onClick: () => { const f = list.value[0]; change({ ...group.value, conditions: [...group.value.conditions, { fieldKey: f.fieldKey, operator: f.operators[0] }] }) } }, '添加条件'),
        p.depth === 0 ? h('button', { onClick: () => change({ ...group.value, groups: [...group.value.groups, blank()] }) }, '添加条件组') : null]),
      ...group.value.conditions.map((condition, index) => h('div', { class: 'advanced-condition' }, [
        h('select', { value: condition.fieldKey, onChange: (e: Event) => { const f = list.value.find(item => item.fieldKey === (e.target as HTMLSelectElement).value)!; const conditions = [...group.value.conditions]; conditions[index] = { fieldKey: f.fieldKey, operator: f.operators[0] }; change({ ...group.value, conditions }) } }, list.value.map(field => h('option', { value: field.fieldKey }, `${field.group} · ${field.label}`))),
        h('select', { value: condition.operator, onChange: (e: Event) => { const conditions = [...group.value.conditions]; conditions[index] = { fieldKey: condition.fieldKey, operator: (e.target as HTMLSelectElement).value }; change({ ...group.value, conditions }) } }, (list.value.find(f => f.fieldKey === condition.fieldKey)?.operators || []).map(op => h('option', { value: op }, operatorLabels[op]))),
        !['is_empty','is_not_empty'].includes(condition.operator) ? h('input', { value: String(condition.value ?? ''), onInput: (e: Event) => { const conditions = [...group.value.conditions]; conditions[index] = { ...condition, value: (e.target as HTMLInputElement).value }; change({ ...group.value, conditions }) } }) : null,
        h('button', { onClick: () => change({ ...group.value, conditions: group.value.conditions.filter((_, i) => i !== index) }) }, '删除')
      ])),
      ...group.value.groups.map((child, index) => h(GroupEditor, { modelValue: child, fields: list.value, depth: 1, 'onUpdate:modelValue': (next: Api.AdvancedFilterGroup) => { const groups = [...group.value.groups]; groups[index] = next; change({ ...group.value, groups }) } }))
    ])
  }
})
</script>

<style scoped>.advanced-toolbar{display:flex;gap:8px;max-width:520px}.advanced-tags{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}.advanced-group{display:flex;flex-direction:column;gap:10px;padding:12px;border-radius:10px;background:var(--el-fill-color-light)}.advanced-group.depth-1{margin-left:16px;background:var(--el-fill-color)}.advanced-group-head,.advanced-condition{display:flex;gap:8px;align-items:center}.advanced-condition select,.advanced-condition input{min-width:0;flex:1;height:32px;border:1px solid var(--el-border-color);border-radius:4px;background:var(--el-bg-color);padding:0 8px}.advanced-group button{height:32px;border:1px solid var(--el-border-color);border-radius:4px;background:var(--el-bg-color);padding:0 10px;cursor:pointer}@media(max-width:768px){.advanced-toolbar{max-width:none}.advanced-condition{flex-wrap:wrap}.advanced-condition select,.advanced-condition input{flex-basis:40%}}</style>
