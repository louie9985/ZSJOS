<template>
  <div v-loading="loading" class="p-20px">
    <el-empty v-if="!loading && !transfer" description="资产流转单不存在或无权查看" />
    <el-descriptions v-else-if="transfer" :column="2" border>
      <el-descriptions-item label="流转单号">{{ transfer.no || '-' }}</el-descriptions-item>
      <el-descriptions-item label="流转类型">{{ transfer.typeLabelSnapshot || '-' }}</el-descriptions-item>
      <el-descriptions-item label="资产编号">{{ transfer.assetCodeSnapshot || transfer.assetCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="资产名称">{{ transfer.assetNameSnapshot || transfer.assetName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="转出员工">{{ transfer.fromEmployeeName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="转出部门">{{ transfer.fromDeptName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="接收员工">{{ transfer.toEmployeeName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="接收部门">{{ transfer.toDeptName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="预计归还日期">{{ transfer.expectedReturnDate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请人">{{ transfer.applyUserName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ formatDate(transfer.applyTime) || '-' }}</el-descriptions-item>
      <el-descriptions-item label="申请事由" :span="2">{{ transfer.reason || '-' }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import * as TransferApi from '@/api/eam/transfer'

defineOptions({ name: 'EamTransferApprovalDetail' })
const props = defineProps<{ id?: string }>()
const loading = ref(false)
const transfer = ref<TransferApi.TransferVO>()
const resolveTransferId = (businessKey?: string) => {
  const match = businessKey?.match(/^asset-transfer:(\d+):round:\d+$/)
  return match ? Number(match[1]) : undefined
}
const load = async () => {
  const id = resolveTransferId(props.id)
  if (!id) return
  loading.value = true
  try {
    transfer.value = await TransferApi.getTransfer(id)
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
