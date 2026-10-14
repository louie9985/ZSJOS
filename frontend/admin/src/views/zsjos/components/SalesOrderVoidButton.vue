<script setup lang="ts">
import { ref } from 'vue'
import { voidSalesOrder } from '@/api/zsjos/workbenchMenus'
const props = defineProps<{ order: { id: number; orderNo: string; version: number; status: string; canVoid?: boolean } }>()
const emit = defineEmits<{ success: [] }>()
const open = ref(false)
const reason = ref('')
const error = ref('')
const saving = ref(false)
const key = ref('')
const resetKey = () => { key.value = crypto.randomUUID(); error.value = '' }
const show = () => { reason.value = ''; error.value = ''; key.value = crypto.randomUUID(); open.value = true }
const submit = async () => {
  if (saving.value) return
  if (!reason.value.trim()) { error.value = '请填写作废原因'; return }
  saving.value = true
  error.value = ''
  try {
    await voidSalesOrder(props.order.id, { reason: reason.value.trim(), orderVersion: props.order.version, idempotencyKey: key.value })
    open.value = false
    emit('success')
  } catch (failure) { error.value = failure instanceof Error ? failure.message : '作废失败，请重试' }
  finally { saving.value = false }
}
</script>
<template>
  <el-button v-if="order.canVoid && order.status === 'effective'" v-hasPermi="['zsjos:sales-order:void']" type="danger" @click="show">作废订单</el-button>
  <el-dialog v-model="open" :title="`作废订单 ${order.orderNo}`" width="min(520px, calc(100vw - 32px))" :show-close="!saving" :close-on-click-modal="!saving" :close-on-press-escape="!saving">
    <el-alert type="warning" show-icon :closable="false" title="原单不再计入业绩，返现、礼品采购和资金分配失效。" description="已收付款及提现历史保留，不自动退款或追回款项。原报名和服务不变，新单正常审批并生成报名履约任务。" />
    <el-form label-position="top"><el-form-item label="作废原因" required>
      <el-input v-model="reason" type="textarea" :rows="4" :maxlength="1000" show-word-limit :disabled="saving" @input="resetKey" />
    </el-form-item></el-form>
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <template #footer><el-button :disabled="saving" @click="open = false">取消</el-button><el-button type="danger" :loading="saving" @click="submit">确认作废</el-button></template>
  </el-dialog>
</template>
