<template>
  <el-descriptions :column="1" border class="mt-12px">
    <el-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">
      {{ order[field.key] || '历史未记录' }}
    </el-descriptions-item>
  </el-descriptions>
</template>
<script setup lang="ts">
defineProps<{ order: Record<string, unknown> }>()
const fields = [
  { key: 'submitterUserName', label: '实际提交人' },
  { key: 'formalSalesUserName', label: '成交负责人' },
  { key: 'studentNatureLabelSnapshot', label: '学员性质' },
  { key: 'servicePeriodLabelSnapshot', label: '服务周期' },
  { key: 'studentSourceLabelSnapshot', label: '学员来源' },
  { key: 'feeModeLabelSnapshot', label: '收费方式' },
  { key: 'paymentMethodLabelSnapshot', label: '支付方式' }
]
</script>
