<template>
  <el-descriptions :column="1" border class="mt-12px">
    <el-descriptions-item v-for="field in fields" :key="field.key" :label="field.label">
      {{ order[field.key] || '历史未记录' }}
    </el-descriptions-item>
    <el-descriptions-item v-if="leadProfile" label="来源人员">{{ sourcePersonText }}</el-descriptions-item>
    <el-descriptions-item v-if="leadProfile?.sourceType === 'partner'" label="兼职归属新媒体员工">
      {{ orderPartnerOwnerText(leadProfile.sourceType, leadProfile.partnerOwnerSnapshot) }}
    </el-descriptions-item>
  </el-descriptions>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { orderPartnerOwnerText, type OrderPartnerOwnerSnapshot } from '@/api/zsjos/orderPartnerOwner'
const props = defineProps<{ order: Record<string, unknown> }>()
const leadProfile = computed(() => props.order.leadProfile as { sourceType?: string; sourceUserName?: string; selfSourcedWithoutProvider?: boolean | null; partnerOwnerSnapshot?: OrderPartnerOwnerSnapshot | null } | undefined)
const sourcePersonText = computed(() => {
  const profile = leadProfile.value
  if (profile?.selfSourcedWithoutProvider === true) {
    if (profile.sourceType === 'sales_self_sourced') return '销售本人自拓（未关联新媒体）'
    if (profile.sourceType === 'education_self_sourced') return '教务本人自拓（未关联新媒体）'
  }
  return profile?.sourceUserName || '历史未记录'
})
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
