<template>
  <template v-if="allowed">
    <el-button link :type="action === 'block' ? 'danger' : 'primary'" @click="open">{{ title }}</el-button>
    <el-dialog v-model="visible" :title="title" width="min(480px, 95vw)" append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" :before-close="close">
      <el-alert type="warning" show-icon :closable="false" :title="action === 'block' ? '操作后，该笔返现不能发起提现，原返现金额保留。' : '恢复后按原结算条件判断是否可提现，不重新计算金额。'" />
      <el-descriptions :column="1"><el-descriptions-item label="返现编号">{{ row.cashbackNo }}</el-descriptions-item><el-descriptions-item label="受益人">{{ row.beneficiaryName || '历史归属信息缺失' }}</el-descriptions-item><el-descriptions-item label="返现金额">¥{{ Number(row.amount).toFixed(2) }}</el-descriptions-item><el-descriptions-item label="当前状态">{{ statusLabel || '状态暂不可用' }}</el-descriptions-item></el-descriptions>
      <el-alert v-if="error" type="error" :title="error" :closable="false" />
      <el-form label-position="top" @submit.prevent><el-form-item label="操作原因" required><el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit :disabled="saving" /><span v-if="action === 'block'">该原因将展示给兼职</span></el-form-item></el-form>
      <template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button :type="action === 'block' ? 'danger' : 'primary'" :loading="saving" @click="submit">确认{{ title }}</el-button></template>
    </el-dialog>
  </template>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import * as Api from '@/api/zsjos/cashback'
import { useUserStore } from '@/store/modules/user'
const props = defineProps<{ row: Api.CashbackVO; statusLabel?: string }>()
const emit = defineEmits<{ changed: [] }>()
const permissions = useUserStore()
const has = (code: string) => permissions.getPermissions.has('*:*:*') || permissions.getPermissions.has(code)
const action = computed(() => props.row.status === 'blocked' ? 'unblock' : 'block')
const title = computed(() => action.value === 'block' ? '禁止提现' : '恢复提现')
const allowed = computed(() => ['pending_settlement', 'available', 'blocked'].includes(props.row.status) && has('zsjos:cashback:finance-query') && has(`zsjos:cashback:${action.value}`))
const visible = ref(false), saving = ref(false), reason = ref(''), error = ref('')
const open = () => { reason.value = ''; error.value = ''; visible.value = true }
const close = (done: () => void) => { if (!saving.value) done() }
const submit = async () => {
  if (saving.value) return
  const value = reason.value.trim()
  if (!value || value.length > 500) { error.value = '请填写1至500字的操作原因'; return }
  saving.value = true; error.value = ''
  try { await Api.controlCashback(props.row.id, action.value, { version: props.row.version, reason: value }); visible.value = false; emit('changed') }
  catch (e) { error.value = e instanceof Error ? e.message : '操作失败，请重试' }
  finally { saving.value = false }
}
</script>
