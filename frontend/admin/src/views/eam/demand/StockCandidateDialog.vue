<template>
  <Dialog v-model="visible" title="检查并使用现有库存" width="760px">
    <el-alert type="info" :closable="false" class="mb-12px">
      系统仅按分类、单位和自定义字段推荐候选；确认后将在同一事务内预留并分配。
    </el-alert>
    <el-result v-if="error" icon="error" title="库存候选加载失败" :sub-title="error">
      <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
    </el-result>
    <el-table v-else v-loading="loading" :data="candidates" empty-text="没有匹配的可用库存">
      <el-table-column label="候选" min-width="200">
        <template #default="{ row }">
          <div>{{ row.name }}</div>
          <div class="text-xs text-gray-500">{{
            row.candidateType === 'SERIALIZED' ? row.assetCode : '批量库存品项'
          }}</div>
        </template>
      </el-table-column>
      <el-table-column label="可用" width="110">
        <template #default="{ row }">{{ row.availableQuantity }} {{ row.unit }}</template>
      </el-table-column>
      <el-table-column label="本次分配" width="150">
        <template #default="{ row }">
          <el-input-number
            v-model="quantities[candidateKey(row)]"
            :min="1"
            :max="Math.min(row.availableQuantity, remaining)"
            :disabled="row.candidateType === 'SERIALIZED'"
            size="small"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            :loading="allocating === candidateKey(row)"
            @click="allocate(row)"
          >
            确认分配
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import * as ProcurementApi from '@/api/eam/procurement'

defineOptions({ name: 'EamStockCandidateDialog' })
const emit = defineEmits(['success'])
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const error = ref('')
const allocating = ref('')
const demandItem = ref<ProcurementApi.DemandItemVO>()
const candidates = ref<ProcurementApi.StockCandidateVO[]>([])
const quantities = reactive<Record<string, number>>({})
const remaining = computed(() =>
  demandItem.value
    ? demandItem.value.quantity -
      (demandItem.value.reservedQuantity || 0) -
      (demandItem.value.purchasedQuantity || 0)
    : 0
)
const candidateKey = (row: ProcurementApi.StockCandidateVO) =>
  `${row.candidateType}-${row.assetId || row.stockBalanceId}`

const load = async () => {
  if (!demandItem.value?.id) return
  loading.value = true
  error.value = ''
  try {
    candidates.value = await ProcurementApi.getStockCandidates(demandItem.value.id)
    candidates.value.forEach((row) => {
      quantities[candidateKey(row)] =
        row.candidateType === 'SERIALIZED'
          ? 1
          : Math.max(1, Math.min(row.availableQuantity, remaining.value))
    })
  } catch (e: any) {
    error.value = e?.msg || e?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

const open = async (item: ProcurementApi.DemandItemVO) => {
  demandItem.value = item
  visible.value = true
  await load()
}
defineExpose({ open })

const allocate = async (candidate: ProcurementApi.StockCandidateVO) => {
  if (!demandItem.value?.id) return
  const key = candidateKey(candidate)
  allocating.value = key
  try {
    await ProcurementApi.reserveAndAllocateStock({
      demandItemId: demandItem.value.id,
      assetId: candidate.assetId,
      stockBalanceId: candidate.stockBalanceId,
      quantity: quantities[key]
    })
    message.success('库存已原子预留并分配')
    visible.value = false
    emit('success')
  } finally {
    allocating.value = ''
  }
}
</script>
