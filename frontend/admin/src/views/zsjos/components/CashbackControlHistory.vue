<template>
  <h3>操作记录</h3>
  <el-alert v-if="error" type="error" :title="error" :closable="false"><el-button link @click="load">重试</el-button></el-alert>
  <el-table v-loading="loading" :data="rows"><el-table-column label="操作" width="110"><template #default="{ row }">{{ row.action === 'block' ? '禁止提现' : '恢复提现' }}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="180" /><el-table-column prop="operatorName" label="操作人" width="110" /><el-table-column label="时间" min-width="170"><template #default="{ row }">{{ formatDate(row.occurredAt) }}</template></el-table-column></el-table>
  <Pagination v-model:page="page" v-model:limit="limit" :total="total" @pagination="load" />
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { getControlHistory, type CashbackControlLog } from '@/api/zsjos/cashback'
import { formatDate } from '@/utils/formatTime'
const props = defineProps<{ id: number; revision: number }>()
const rows = ref<CashbackControlLog[]>([]), total = ref(0), page = ref(1), limit = ref(10), loading = ref(false), error = ref('')
let sequence = 0
const load = async () => {
  const current = ++sequence; loading.value = true; error.value = ''; rows.value = []
  try { const result = await getControlHistory(props.id, page.value, limit.value); if (current === sequence) { rows.value = result.list; total.value = result.total } }
  catch (e) { if (current === sequence) error.value = e instanceof Error ? e.message : '操作记录加载失败' }
  finally { if (current === sequence) loading.value = false }
}
watch(() => [props.id, props.revision], () => { page.value = 1; void load() }, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>
