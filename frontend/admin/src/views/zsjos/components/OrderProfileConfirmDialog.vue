<template>
  <el-dialog v-model="visible" title="确认提交订单" :width="$slots.default ? 'min(980px, calc(100vw - 24px))' : 'min(640px, calc(100vw - 24px))'" append-to-body :close-on-click-modal="false" @closed="onClosed">
    <slot />
    <el-divider v-if="$slots.default" />
    <p>{{ differences.length ? '以下信息与客户档案不同，请核对要更新的项目。' : profile?.personId ? '本次学员信息与客户档案一致，无需更新。' : '将使用本次填写的信息建立客户档案。' }}</p>
    <div v-for="field in differences" :key="field.key" class="profile-difference">
      <strong>{{ field.label }}</strong>
      <p>档案现有：{{ profile?.[field.key] || '未填写' }}</p><p>本次订单：{{ incoming[field.key] }}</p>
      <el-checkbox v-if="profile?.[field.key]?.trim()" :model-value="selected.includes(field.key)" @change="value => selected = value ? [...selected, field.key] : selected.filter(key => key !== field.key)">用本次订单的{{ field.label }}更新档案</el-checkbox>
      <p v-else>空项将在提交成功后自动补齐</p>
    </div>
    <div v-if="replacements.length" class="mb-12px"><el-button @click="selected = replacements.map(f => f.key)">全部更新档案</el-button><el-button @click="selected = []">保留已有值</el-button></div>
    <p>订单保存本次填写的信息；原客资及历史订单保持原样。</p>
    <el-alert :closable="false" type="info" :title="!profile?.personId ? '提交时建立客户档案' : updates.length ? `提交成功后，档案将更新：${updates.map(f => f.label).join('、')}。` : '提交成功后，档案保持原样。'" />
    <template #footer><el-button @click="cancel">返回修改</el-button><el-button type="primary" :disabled="confirmDisabled" @click="finish">确认提交审批</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import type { OrderProfile, OrderProfileField, OrderProfileUpdate } from '@/api/zsjos/workbenchMenus'
const props = defineProps<{ confirmDisabled?: boolean }>()
const visible = ref(false)
const profile = ref<OrderProfile>()
const incoming = ref<Record<OrderProfileField, string>>({ name: '', mobile: '', wechatId: '' })
const selected = ref<OrderProfileField[]>([])
const fields: Array<{ key: OrderProfileField; label: string }> = [{ key: 'name', label: '姓名' }, { key: 'mobile', label: '手机号' }, { key: 'wechatId', label: '微信号' }]
const differences = computed(() => profile.value?.personId ? fields.filter(f => (profile.value?.[f.key]?.trim() || '') !== incoming.value[f.key].trim()) : [])
const replacements = computed(() => differences.value.filter(f => profile.value?.[f.key]?.trim()))
const updates = computed(() => differences.value.filter(f => !profile.value?.[f.key]?.trim() || selected.value.includes(f.key)))
let resolve: ((choice: OrderProfileUpdate | undefined) => void) | undefined
const cancel = () => { visible.value = false; resolve?.(undefined); resolve = undefined }
const onClosed = () => { if (!visible.value) cancel() }
const finish = () => { if (props.confirmDisabled) return; resolve?.({ fingerprint: profile.value!.fingerprint, fields: [...selected.value] }); resolve = undefined; visible.value = false }
const confirm = (current: OrderProfile, values: Record<OrderProfileField, string>) => {
  cancel(); profile.value = current; incoming.value = values; selected.value = differences.value.map(f => f.key); visible.value = true
  return new Promise<OrderProfileUpdate | undefined>(done => { resolve = done })
}
onBeforeUnmount(cancel)
defineExpose({ confirm })
</script>
<style scoped>
:deep(.el-dialog__body) { max-height: calc(100dvh - 220px); overflow-y: auto; }
.profile-difference { padding: 12px 0; border-bottom: 1px solid var(--el-border-color); margin-bottom: 12px; overflow-wrap: anywhere; }
</style>
