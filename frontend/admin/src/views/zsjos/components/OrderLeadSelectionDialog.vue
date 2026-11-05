<template>
  <section>
    <el-alert :closable="false" type="info" :title="candidates.length === 1 ? '已自动关联唯一匹配客资' : '该客户有多条客资，请选择本次订单关联的客资'" description="订单归属当前录单人，关联不会改变客资状态或归属。" />
    <div class="association-options" role="radiogroup" aria-label="候选客资">
      <section v-for="item in candidates" :key="item.id" class="association-option" :class="{ 'is-selected': modelValue === item.id }"
        @click="emit('update:modelValue', item.id)">
        <div class="association-heading">
          <el-tag>{{ item.statusLabel }}</el-tag>
          <label class="association-choice" @click.stop>
            <input type="radio" :name="groupName" :value="item.id" :checked="modelValue === item.id"
              :aria-label="`选择客资 ${item.profile.leadNo || '未记录'}`" @change="emit('update:modelValue', item.id)" />
            {{ modelValue === item.id ? '已选中' : '选择' }}
          </label>
        </div>
        <h3>{{ item.profile.submittedName || '姓名未记录' }}</h3>
        <el-descriptions :column="1">
          <el-descriptions-item v-for="field in fields(item)" :key="field.label" :label="field.label">{{ field.value || '历史未记录' }}</el-descriptions-item>
        </el-descriptions>
      </section>
    </div>
  </section>
</template>
<script setup lang="ts">
import { useId } from 'vue'
import type { OrderLeadCandidate } from '@/api/zsjos/workbenchMenus'
import { orderPartnerOwnerText } from '@/api/zsjos/orderPartnerOwner'
defineProps<{ candidates: OrderLeadCandidate[]; modelValue?: number }>()
const groupName = useId()
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()
const fields = (item: OrderLeadCandidate) => {
  const p = item.profile
  const source = p.selfSourcedWithoutProvider ? p.sourceType === 'sales_self_sourced' ? '销售本人自拓（未关联新媒体）' : '教务本人自拓（未关联新媒体）' : p.sourceUserName
  return [
    { label: '姓名', value: p.submittedName }, { label: '客资编号', value: p.leadNo },
    { label: '手机号', value: p.submittedMobile }, { label: '微信号', value: p.submittedWechatId },
    { label: '客资提交时间', value: p.submittedAt ? new Date(p.submittedAt).toLocaleString('zh-CN') : undefined },
    { label: '成交归属身份', value: '按当前录单人确定' },
    { label: '业务来源', value: [p.sourceLabel, p.sourceType === 'internal_new_media' ? p.dispatchMode === 'auto' ? '自动分配' : p.dispatchMode === 'specified' ? '指定派单' : '' : ''].filter(Boolean).join(' · ') },
    { label: '来源人员', value: source },
    ...(p.sourceType === 'partner' ? [{ label: '兼职归属新媒体员工', value: orderPartnerOwnerText(p.sourceType, p.partnerOwnerSnapshot) }] : []),
    { label: '负责人', value: [p.ownerUserName, p.ownerIdentityLabel].filter(Boolean).join(' · ') },
    { label: '分类', value: p.leadCategoryLabelSnapshot }, { label: '渠道', value: p.sourceChannelLabelSnapshot },
    { label: '地区', value: [p.provinceName, p.cityName].filter(Boolean).join(' / ') }
  ]
}
</script>
<style scoped>
.association-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 28px; align-items: start; padding: 28px 14px 20px; }
.association-option { min-width: 0; padding: 20px; border: 1px solid var(--el-border-color); border-radius: 16px; background: var(--el-bg-color); cursor: pointer; overflow-wrap: anywhere; transition: transform 220ms ease, box-shadow 220ms ease, border-color 220ms ease; }
.association-option.is-selected { border-color: var(--el-color-primary); transform: translateY(-6px) scale(1.025); box-shadow: 0 0 0 3px color-mix(in srgb, var(--el-color-primary) 12%, transparent), 0 0 24px color-mix(in srgb, var(--el-color-primary) 25%, transparent), 0 10px 24px color-mix(in srgb, var(--el-color-primary) 12%, transparent); }
.association-option:focus-within { outline: 2px solid var(--el-color-primary); outline-offset: 4px; }
.association-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.association-choice { display: flex; align-items: center; gap: 6px; color: var(--el-text-color-secondary); cursor: inherit; }
.association-choice input { accent-color: var(--el-color-primary); width: 18px; height: 18px; }
.is-selected .association-choice { color: var(--el-color-primary); }
@media (max-width: 640px) { .association-options { grid-template-columns: minmax(0, 1fr); padding-inline: 10px; } .association-option { padding: 16px; } }
@media (prefers-reduced-motion: reduce) { .association-option { transition: none; } }
</style>
