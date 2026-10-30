<template>
  <ContentWrap
    ><h2>首页指标配置</h2><p>按查看者的业务权限统计，发布后显示在员工首页。</p
    ><el-button
      v-hasPermi="['zsjos:home-metric:update']"
      type="primary"
      :disabled="!ready"
      @click="create"
      >新增指标</el-button
    ><el-button @click="initialize">刷新</el-button></ContentWrap
  >
  <ContentWrap
    ><el-alert v-if="error" :title="error" type="error" :closable="false" /><el-table
      :data="rows"
      v-loading="loading"
      ><el-table-column prop="spec.name" label="名称" /><el-table-column
        prop="spec.formula"
        label="公式"
      /><el-table-column prop="spec.sort" label="排序" /><el-table-column label="状态"
        ><template #default="{ row }">{{
          row.enabled ? '已发布' : '未启用'
        }}</template></el-table-column
      ><el-table-column label="操作" width="370"
        ><template #default="{ row }"
          ><el-button link :disabled="!ready" @click="edit(row)">编辑</el-button
          ><el-button
            link
            v-hasPermi="['zsjos:home-metric:publish']"
            @click="command('publish', row)"
            >发布</el-button
          ><el-button
            link
            v-hasPermi="['zsjos:home-metric:publish']"
            @click="command('disable', row)"
            >停用</el-button
          ><el-button link v-hasPermi="['zsjos:home-metric:update']" @click="command('copy', row)"
            >复制</el-button
          ><el-button link @click="history(row)">版本</el-button></template
        ></el-table-column
      ></el-table
    ></ContentWrap
  >
  <el-dialog v-model="open" title="指标配置" width="min(1000px,95vw)" :close-on-click-modal="false"
    ><el-alert v-if="error" :title="error" type="error" /><el-form label-width="100px" v-if="form">
      <el-form-item label="稳定编码"
        ><el-input v-model="form.code" :disabled="!!selected" /></el-form-item
      ><el-form-item label="显示名称"><el-input v-model="form.name" maxlength="100" /></el-form-item
      ><el-form-item label="口径说明"
        ><el-input v-model="form.description" type="textarea" /></el-form-item
      ><el-form-item label="显示单位"><el-input v-model="form.unit" /></el-form-item
      ><el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
      <el-form-item label="查看权限"
        ><el-select v-model="form.viewPermission" filterable
          ><el-option
            v-for="p in permissions"
            :key="p.permission"
            :value="p.permission"
            :label="p.label + ' · ' + p.permission" /></el-select
      ></el-form-item>
      <el-form-item label="指定人员"
        ><el-select v-model="form.userIds" multiple filterable clearable placeholder="请选择人员"
          ><el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.nickname" /></el-select
        ><small>留空不额外限制人员</small></el-form-item
      >
      <el-form-item label="指定部门"
        ><el-select v-model="form.deptIds" multiple filterable clearable placeholder="请选择部门"
          ><el-option
            v-for="d in departments"
            :key="d.id"
            :value="d.id"
            :label="d.name" /></el-select
      ></el-form-item>
      <el-form-item label="点击行为"
        ><el-select v-model="form.action"
          ><el-option v-for="o in options.actions" :key="o.value" v-bind="o" /></el-select
      ></el-form-item>
      <el-divider>操作数与筛选</el-divider
      ><el-button @click="addOperand" :disabled="form.operands.length >= 16">添加操作数</el-button>
      <el-card v-for="o in form.operands" :key="o.editorId" class="metric-operand"
        ><el-form-item label="变量名称"
          ><el-input v-model="o.calculation.name" /><el-button @click="removeOperand(o)"
            >移除</el-button
          ></el-form-item
        ><el-form-item label="数据来源"
          ><el-select v-model="o.calculation.source" @change="sourceChanged(o)"
            ><el-option
              v-for="s in sources"
              :key="s.key"
              :label="s.label"
              :value="s.key" /></el-select
        ></el-form-item>
        <el-form-item label="统计方式"
          ><el-select v-model="o.calculation.aggregate"
            ><el-option
              v-for="a in (options.aggregates || []).filter(
                (x) => source(o)?.fields.length || ['COUNT', 'DISTINCT_COUNT'].includes(x.value)
              )"
              :key="a.value"
              v-bind="a" /></el-select></el-form-item
        ><el-form-item
          v-if="!['COUNT', 'DISTINCT_COUNT'].includes(o.calculation.aggregate)"
          label="数值字段"
          ><el-select v-model="o.calculation.field"
            ><el-option
              v-for="f in source(o)?.fields"
              :key="f.key"
              :value="f.key"
              :label="f.label" /></el-select
        ></el-form-item>
        <el-form-item label="统计周期"
          ><el-select v-model="o.calculation.period" @change="periodChanged(o)"
            ><el-option
              v-for="p in (options.periods || []).filter(
                (x) => source(o)?.timeFields.length || x.value === 'ALL'
              )"
              :key="p.value"
              v-bind="p" /></el-select></el-form-item
        ><el-form-item label="比较周期"
          ><el-select v-model="o.calculation.comparison" :disabled="o.calculation.period === 'ALL'"
            ><el-option
              v-for="p in options.comparisons"
              :key="p.value"
              v-bind="p" /></el-select></el-form-item
        ><el-form-item v-if="o.calculation.period !== 'ALL'" label="时间字段"
          ><el-select v-model="o.timeField" placeholder="请选择时间字段"
            ><el-option
              v-for="f in source(o)?.timeFields"
              :key="f.key"
              :value="f.key"
              :label="f.label" /></el-select
          ><el-checkbox v-if="o.calculation.period !== 'CUSTOM'" v-model="o.calculation.toDate"
            >截至当前时刻</el-checkbox
          ></el-form-item
        >
        <el-form-item v-if="o.calculation.period === 'CUSTOM'" label="起止时间"
          ><el-date-picker
            v-model="editorState(o).range"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            @change="setRange(o)"
          />
          <div
            v-if="editorState(o).range?.length === 2"
            class="metric-range-summary"
            aria-label="完整北京时间区间"
          >
            <span>北京时间开始：{{ editorState(o).range[0].replace('T', ' ') }}</span>
            <span>北京时间结束：{{ editorState(o).range[1].replace('T', ' ') }}</span>
          </div>
        </el-form-item>
        <ZsjosAdvancedFilterGroup
          :model-value="o.filter"
          @update:model-value="updateFilter(o, $event)"
          :fields="displayFields(o)"
          :depth="0"
          :total="count(o.filter)"
          :relative-date-options="editorState(o).dates"
          @retry-options="catalog(o)"
        />
      </el-card>
      <el-form-item label="计算公式"
        ><el-input v-model="form.formula" placeholder="例如 (A - B) / B" /></el-form-item
      ><el-form-item label="百分比"><el-switch v-model="form.percentage" /></el-form-item
      ><el-form-item label="小数位"
        ><el-input-number v-model="form.decimals" :min="0" :max="8" /></el-form-item
      ><el-alert v-if="preview" :title="preview" type="info"
    /></el-form>
    <template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button :loading="busy" @click="doPreview">按本人权限预览</el-button
      ><el-button
        v-hasPermi="['zsjos:home-metric:update']"
        type="primary"
        :loading="busy"
        @click="save"
        >保存草稿</el-button
      ></template
    ></el-dialog
  >
  <el-dialog v-model="versionsOpen" title="发布版本"
    ><el-table :data="versions"
      ><el-table-column prop="version" label="版本" /><el-table-column
        prop="spec.name"
        label="名称"
      /><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button v-hasPermi="['zsjos:home-metric:publish']" @click="rollback(row.version)"
            >回退到此版本</el-button
          ></template
        ></el-table-column
      ></el-table
    ></el-dialog
  >
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { metricPickerDate } from '@/api/zsjos/metricTime'
import {
  metricApi,
  type MetricConfig,
  type MetricSpec,
  type Source,
  type Operand
} from '@/api/zsjos/homeMetric'
import {
  getCatalog,
  type AdvancedFilterField,
  type AdvancedFilterGroup,
  type AdvancedFilterScene
} from '@/api/zsjos/advancedFilter'

import request from '@/config/axios'
import { getDictDataByType } from '@/api/system/dict/dict.data'
import ZsjosAdvancedFilterGroup from '../components/ZsjosAdvancedFilterGroup.vue'
const rows = ref<MetricConfig[]>([]),
  sources = ref<Source[]>([]),
  options = ref<Record<string, { value: string; label: string }[]>>({}),
  permissions = ref<{ permission: string; label: string }[]>([]),
  users = ref<{ id: number; nickname: string }[]>([]),
  departments = ref<{ id: number; name: string }[]>([])
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  open = ref(false),
  preview = ref(''),
  selected = ref<MetricConfig>(),
  form = ref<MetricSpec>(),
  states = ref<Record<string, EditorState>>({}),
  ready = ref(false),
  versionsOpen = ref(false),
  versions = ref<{ version: number; spec: MetricSpec }[]>([])
type EditorState = {
  fields: AdvancedFilterField[]
  dates: { value: string; label: string }[]
  range: string[]
  sequence: number
  snapshots: Record<string, string>
  snapshotName: string
}
let nextEditorId = 0
let editorGeneration = 0
const editorState = (o: Operand) => states.value[o.editorId!]
function attach(o: Operand) {
  o.editorId = 'operand-' + (++nextEditorId)
  states.value[o.editorId] = {
    fields: [],
    dates: [],
    range: o.calculation.customRange
      ? [
          metricPickerDate(o.calculation.customRange.start),
          metricPickerDate(o.calculation.customRange.end)
        ]
      : [],
    sequence: 0,
    snapshots: { ...form.value?.dictionarySnapshots },
    snapshotName: o.calculation.name
  }
}
function removeOperand(o: Operand) {
  delete states.value[o.editorId!]
  form.value!.operands = form.value!.operands.filter((item) => item.editorId !== o.editorId)
}
function selectedValues(g: AdvancedFilterGroup, key: string): string[] {
  return [
    ...g.conditions
      .filter((c) => c.fieldKey === key)
      .flatMap((c) =>
        Array.isArray(c.value) ? c.value.map(String) : c.value == null ? [] : [String(c.value)]
      ),
    ...g.groups.flatMap((child) => selectedValues(child, key))
  ]
}
function updateFilter(o: Operand, g: AdvancedFilterGroup) {
  o.filter = g
  const state = editorState(o)
  for (const key of Object.keys(state.snapshots)) {
    const parts = key.split(':')
    if (
      parts[0] === state.snapshotName &&
      !selectedValues(g, parts[1]).includes(parts.slice(3).join(':'))
    )
      delete state.snapshots[key]
  }
}
function displayFields(o: Operand) {
  const state = editorState(o)
  return state.fields.map((f) => {
    if (!f.optionSource?.startsWith('dict:')) return f
    const prefix = state.snapshotName + ':' + f.fieldKey + ':' + f.optionSource.slice(5) + ':'
    const options = f.options.map((option) => ({
      ...option,
      label: state.snapshots[prefix + option.value] ?? option.label
    }))
    for (const [key, label] of Object.entries(state.snapshots)) {
      if (key.startsWith(prefix)) {
        const value = key.slice(prefix.length)
        if (
          selectedValues(o.filter, f.fieldKey).includes(value) &&
          !options.some((x) => String(x.value) === value)
        )
          options.push({ value, label })
      }
    }
    return { ...f, options }
  })
}
const count = (g: AdvancedFilterGroup): number =>
  g.conditions.length + g.groups.reduce((n, x) => n + count(x), 0)
const source = (o: Operand) => sources.value.find((s) => s.key === o.calculation.source)
const fail = (e: unknown) => {
  error.value =
    e instanceof Error ? e.message : '操作失败，请刷新后重试；并发修改可能需要重新打开配置。'
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await metricApi.list()
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
}
function create() {
  if (!ready.value) return
  editorGeneration++
  selected.value = undefined
  form.value = {
    code: '',
    name: '',
    description: '',
    unit: '',
    sort: 0,
    viewPermission: '',
    userIds: [],
    deptIds: [],
    action: 'MODAL',
    formula: 'A',
    percentage: false,
    decimals: 0,
    operands: []
  }
  states.value = {}
  preview.value = ''
  open.value = true
  addOperand()
}
function addOperand() {
  const operand: Operand = {
    calculation: {
      name:
        Array.from({ length: 26 }, (_, i) => String.fromCharCode(65 + i)).find(
          (name) => !form.value!.operands.some((o) => o.calculation.name === name)
        ) || 'operand',
      source: '',
      aggregate: 'COUNT',
      period: 'ALL',
      toDate: false,
      comparison: 'CURRENT'
    },
    filter: { logic: 'AND', conditions: [], groups: [] }
  }
  attach(operand)
  form.value!.operands.push(operand)
}
async function sourceChanged(o: Operand) {
  o.filter = { logic: 'AND', conditions: [], groups: [] }
  o.calculation.aggregate = 'COUNT'
  o.calculation.field = undefined
  o.calculation.period = 'ALL'
  periodChanged(o)
  editorState(o).snapshots = {}
  await catalog(o)
}
async function catalog(o: Operand) {
  const state = editorState(o)
  const request = ++state.sequence
  const origin = o.calculation.source
  const sourceInfo = source(o)
  state.fields = []
  state.dates = []
  if (!sourceInfo?.scene) return
  try {
    const c = await getCatalog(sourceInfo.scene as AdvancedFilterScene)
    const fields = await Promise.all(
      c.fields.map(async (f) => {
        if (f.optionSource?.startsWith('dict:')) {
          try {
            return {
              ...f,
              options: (await getDictDataByType(f.optionSource.slice(5))).map((x) => ({
                value: x.value,
                label: x.label
              }))
            }
          } catch {
            return { ...f, optionsError: true }
          }
        }
        return f
      })
    )
    if (
      states.value[o.editorId!] !== state ||
      request !== state.sequence ||
      o.calculation.source !== origin
    )
      return
    state.fields = fields
    state.dates = c.relativeDateOptions || []
  } catch (e) {
    if (states.value[o.editorId!] === state && request === state.sequence) fail(e)
  }
}
async function edit(row: MetricConfig) {
  if (!ready.value) return
  const generation = ++editorGeneration
  try {
    const config = await metricApi.get(row.id)
    if(generation !== editorGeneration)return
    selected.value = config
    form.value = JSON.parse(JSON.stringify(selected.value.spec))
    states.value = {}
    form.value!.operands.forEach(attach)
    open.value = true
    preview.value = ''
    await Promise.all(form.value!.operands.map(catalog))
  } catch (e) {
    fail(e)
  }
}
function periodChanged(o: Operand) {
  editorState(o).range = []
  o.calculation.customRange = undefined
  if (o.calculation.period === 'ALL') {
    o.calculation.comparison = 'CURRENT'
    o.calculation.toDate = false
    o.timeField = undefined
  }
  if (o.calculation.period === 'CUSTOM') o.calculation.toDate = false
}
function setRange(o: Operand) {
  const range = editorState(o).range
  o.calculation.customRange = range?.length === 2 ? { start: range[0], end: range[1] } : undefined
}
function normalized(): MetricSpec {
  const s = JSON.parse(JSON.stringify(form.value!))
  for (const o of s.operands) {
    delete o.editorId
    if (['COUNT', 'DISTINCT_COUNT'].includes(o.calculation.aggregate)) {
      o.calculation.field = undefined
    }
    if (o.calculation.period === 'ALL') {
      o.timeField = undefined
      o.calculation.toDate = false
      o.calculation.customRange = undefined
      o.calculation.comparison = 'CURRENT'
    } else if (o.calculation.period === 'CUSTOM') o.calculation.toDate = false
    else o.calculation.customRange = undefined
  }
  return s
}
async function save() {
  busy.value = true
  try {
    await metricApi.save({
      id: selected.value?.id,
      version: selected.value?.version,
      spec: normalized()
    })
    open.value = false
    await load()
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}
async function doPreview() {
  busy.value = true
  try {
    const r = await metricApi.preview(normalized())
    preview.value =
      r.result.status === 'OK'
        ? '当前本人权限下结果：' + r.result.value
        : '无法计算：' + r.result.status
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}
async function command(c: 'publish' | 'disable' | 'copy', row: MetricConfig) {
  try {
    await metricApi.command(c, { id: row.id, version: row.version })
    await load()
  } catch (e) {
    fail(e)
  }
}
async function history(row: MetricConfig) {
  try {
    selected.value = row
    versions.value = await metricApi.versions(row.id)
    versionsOpen.value = true
  } catch (e) {
    fail(e)
  }
}
async function rollback(targetVersion: number) {
  try {
    await metricApi.command('rollback', {
      id: selected.value!.id,
      version: selected.value!.version,
      targetVersion
    })
    versionsOpen.value = false
    await load()
  } catch (e) {
    fail(e)
  }
}
async function initialize() {
  ready.value = false
  await load()
  try {
    const [s, o, p, u, d] = await Promise.all([
      metricApi.catalog(),
      metricApi.options(),
      metricApi.permissions(),
      request.get({ url: '/system/user/simple-list' }),
      request.get({ url: '/system/dept/simple-list' })
    ])
    sources.value = s
    options.value = o
    permissions.value = p
    users.value = u
    departments.value = d
    ready.value = true
  } catch (e) {
    fail(e)
  }
}
onMounted(initialize)
</script>
<style scoped>
.metric-operand {
  margin: 16px 0;
}
.metric-range-summary {
  display: flex;
  flex-direction: column;
  width: 100%;
  margin-top: 8px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  color: var(--el-text-color-secondary);
}
small {
  color: var(--el-text-color-secondary);
}
</style>
