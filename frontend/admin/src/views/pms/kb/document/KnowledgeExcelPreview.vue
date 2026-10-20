<template>
  <section class="knowledge-excel" aria-label="Excel 表格预览" :aria-busy="loading">
    <p class="excel-note">只读预览 · 公式使用文件已保存的结果 · 不含图表及打印版式</p>
    <div v-if="loading" role="status">正在加载表格…</div>
    <template v-else-if="error">
      <el-alert :title="error" type="error" :closable="false" show-icon />
      <el-button class="mt-12px" @click="load(selectedSheet)">重试</el-button>
    </template>
    <template v-else-if="data">
      <el-tabs :model-value="String(data.sheetIndex)" @tab-change="changeSheet">
        <el-tab-pane v-for="sheet in data.sheets" :key="sheet.index" :name="String(sheet.index)" :label="sheet.name" />
      </el-tabs>
      <el-alert v-for="notice in data.notices" :key="notice" :title="notice" type="info" :closable="false" />
      <el-empty v-if="!data.rows.length || !data.columnCount" description="工作表为空" />
      <div v-else class="excel-scroll" tabindex="0" aria-label="工作表内容">
        <table>
          <thead><tr><th scope="col">行</th><th v-for="c in data.columnCount" :key="c" scope="col">{{ columnName(c) }}</th></tr></thead>
          <tbody><tr v-for="(row, r) in displayRows" :key="r">
            <th scope="row">{{ r + 1 }}</th>
            <td v-for="cell in row" :key="cell.column" :rowspan="cell.rowSpan" :colspan="cell.colSpan">{{ cell.text }}</td>
          </tr></tbody>
        </table>
      </div>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { getKnowledgeExcelPreview, type KnowledgeExcelSource, type KnowledgeExcelPreview } from '@/api/pms/kb/content/excel'
const props = defineProps<KnowledgeExcelSource>()
const data = ref<KnowledgeExcelPreview>()
const displayRows = computed(() => {
  const preview = data.value
  if (!preview) return []
  const covered = new Set<number>()
  const anchors = new Map<number, { rowSpan: number; colSpan: number }>()
  for (const merge of preview.merges || []) {
    anchors.set(merge.row * preview.columnCount + merge.column, merge)
    for (let r = merge.row; r < merge.row + merge.rowSpan; r++) {
      for (let c = merge.column; c < merge.column + merge.colSpan; c++) {
        if (r !== merge.row || c !== merge.column) covered.add(r * preview.columnCount + c)
      }
    }
  }
  return preview.rows.map((row, r) => Array.from({ length: preview.columnCount }, (_, c) => {
    const key = r * preview.columnCount + c
    const span = anchors.get(key)
    return { column: c, text: row[c] || '', rowSpan: span?.rowSpan || 1, colSpan: span?.colSpan || 1, covered: covered.has(key) }
  }).filter(cell => !cell.covered))
})
const loading = ref(false)
const error = ref('')
const selectedSheet = ref(-1)
let generation = 0
async function load(sheetIndex = -1) {
  const current = ++generation
  selectedSheet.value = sheetIndex
  loading.value = true
  data.value = undefined
  error.value = ''
  try {
    const result = await getKnowledgeExcelPreview({ ...props }, sheetIndex)
    if (current === generation) data.value = result
  } catch (cause) {
    if (current === generation) error.value = cause instanceof Error ? cause.message :
      typeof cause === 'string' ? cause : '表格暂时无法加载，请重试'
  } finally {
    if (current === generation) loading.value = false
  }
}
function changeSheet(value: string | number) { load(Number(value)) }
function columnName(column: number) {
  let name = ''
  while (column > 0) { column--; name = String.fromCharCode(65 + column % 26) + name; column = Math.floor(column / 26) }
  return name
}
watch(() => [props.documentId, props.fileId, props.recordId, props.shareToken], () => load(), { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>
<style scoped>
.knowledge-excel { min-width: 0; max-width: 100%; }
.excel-note { color: var(--el-text-color-secondary); font-size: 13px; }
.excel-scroll { max-height: 65vh; max-width: 100%; overflow: auto; border: 1px solid var(--el-border-color); }
table { border-collapse: separate; border-spacing: 0; font-size: 14px; }
th, td { min-width: 90px; max-width: 480px; padding: 8px; border-right: 1px solid var(--el-border-color); border-bottom: 1px solid var(--el-border-color); white-space: pre-wrap; overflow-wrap: anywhere; }
th { background: var(--el-fill-color-light); }
thead th { position: sticky; top: 0; z-index: 1; }
tbody th { min-width: 42px; }
</style>
