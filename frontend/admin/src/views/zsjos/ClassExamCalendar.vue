<template>
  <Dialog :model-value="modelValue" title="选择已有考期" width="min(720px, calc(100vw - 32px))" @update:model-value="emit('update:modelValue', $event)">
    <p>选择日期查看当天考期，点击考期填入；填入后仍可修改日期。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button @click="load">重试</el-button>
    </el-alert>
    <div v-else v-loading="loading">
      <el-calendar v-model="date">
        <template #date-cell="{ data }"><span>{{ Number(data.day.slice(-2)) }}{{ matches(data.day).length ? ' ●' : '' }}</span></template>
      </el-calendar>
      <el-empty v-if="!loading && !rows.length" description="暂无已发布且未结束的考期，可返回手动选择日期" />
      <template v-else>
        <p>{{ dayjs(date).format('YYYY年M月D日') }} 考期</p>
        <el-empty v-if="!loading && !daily.length" description="当天暂无考期，请选择有标记的日期" />
        <div v-for="row in daily" :key="row.id" class="class-exam-option"><el-button text type="primary" @click="select(row)">{{ row.displayName }}</el-button></div>
      </template>
    </div>
  </Dialog>
</template>
<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, ref, watch } from 'vue'
import { getExamOptions, type ExamOption } from '@/api/zsjos/deliveryClass'

const props = defineProps<{ modelValue: boolean; initialDate?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; select: [date: string] }>()
const date = ref(new Date())
const rows = ref<ExamOption[]>([])
const loading = ref(false)
const error = ref('')
let generation = 0
const dates = (row: ExamOption) => ({ start: row.scheduleType === 'EXACT' ? row.exactDate : row.startDate,
  end: row.scheduleType === 'EXACT' ? row.exactDate : row.endDate })
const matches = (key: string) => rows.value.filter(row => { const range = dates(row); return range.start && range.end && range.start <= key && key <= range.end })
const daily = computed(() => matches(dayjs(date.value).format('YYYY-MM-DD')))
const load = async () => {
  const current = ++generation
  loading.value = true; error.value = ''; rows.value = []
  try { const result = await getExamOptions(); if (current === generation) rows.value = result }
  catch (cause) { if (current === generation) error.value = cause instanceof Error ? cause.message : '考期加载失败，请重试' }
  finally { if (current === generation) loading.value = false }
}
watch(() => props.modelValue, open => {
  if (open) { date.value = props.initialDate ? dayjs(props.initialDate).toDate() : new Date(); void load() }
  else generation++
})
const select = (row: ExamOption) => { const range = dates(row); if (range.start && range.end) emit('select', dayjs(date.value).format('YYYY-MM-DD')) }
</script>
<style scoped>
:deep(.el-calendar-table .el-calendar-day) { height: 44px; }
.class-exam-option .el-button { height: auto; white-space: normal; text-align: left; }
</style>
