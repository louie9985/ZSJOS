<template>
  <el-alert v-if="order.status === 'voided'" type="warning" :closable="false" title="订单已作废，不计入业绩" :description="String(order.voidReason || '原报名及服务保持不变')" />
  <el-descriptions :column="1" border class="mt-12px">
    <el-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">
      {{ order[field.key] || '历史未记录' }}
    </el-descriptions-item>
    <el-descriptions-item v-if="leadProfile?.sourceType === 'partner'" label="兼职归属新媒体员工">
      {{ orderPartnerOwnerText(leadProfile.sourceType, leadProfile.partnerOwnerSnapshot) }}
    </el-descriptions-item>
  </el-descriptions>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { orderPartnerOwnerText, type OrderPartnerOwnerSnapshot } from '@/api/zsjos/orderPartnerOwner'
const props = defineProps<{ order: Record<string, unknown> }>()
const leadProfile = computed(() => props.order.leadProfile as { sourceType?: string; partnerOwnerSnapshot?: OrderPartnerOwnerSnapshot | null } | undefined)
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
