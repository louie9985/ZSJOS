<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import * as Api from '@/api/zsjos/workbenchMenus'
const message = useMessage()
const open = ref(false)
const current = ref<any>()
const detail = ref<any>()
const decision = ref<'approve' | 'reject'>('approve')
const reason = ref('')
const saving = ref(false)
const detailLoading = ref(false)
let reloadList = () => {}
const show = async (row: any, fn: () => void) => {
  current.value = row
  reloadList = fn
  decision.value = 'approve'
  reason.value = ''
  open.value = true
  detailLoading.value = true
  try {
    detail.value = await Api.getSalesOrder(row.id)
  } finally {
    detailLoading.value = false
  }
}
const submit = async () => {
  if (!current.value?.taskId || !detail.value)
    return message.warning('审批并发信息缺失，请刷新详情')
  if (decision.value === 'reject' && !reason.value.trim())
    return message.warning('驳回时必须填写原因')
  saving.value = true
  try {
    await Api.decideSalesOrder(current.value.id, decision.value, {
      taskId: current.value.taskId,
      reason: reason.value.trim(),
      approvalRoundId: detail.value.currentApprovalRoundId,
      orderVersion: detail.value.version,
      roundVersion: detail.value.approvalRoundVersion,
      idempotencyKey: crypto.randomUUID()
    })
    message.success('审批结论已提交')
    open.value = false
    await reloadList()
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <WorkbenchListPage
    title="成交审批"
    endpoint="/zsjos/sales-order/approval/inbox-page"
    description="成交订单审批队列"
    :query="{ handled: false }"
    ><template #row-actions="{ row, reload }"
      ><el-button link type="primary" :disabled="!row.taskId" @click="show(row, reload)"
        >审批</el-button
      ></template
    ></WorkbenchListPage
  ><el-dialog v-model="open" title="成交审批" width="560px"
    ><div v-loading="detailLoading"
      ><el-form label-width="90px"
        ><el-form-item label="订单"
          ><span>{{ detail?.orderNo || `#${current?.id}` }}</span></el-form-item
        ><el-form-item label="结论"
          ><el-radio-group v-model="decision"
            ><el-radio value="approve">通过</el-radio
            ><el-radio value="reject">驳回</el-radio></el-radio-group
          ></el-form-item
        ><el-form-item label="审批意见"
          ><el-input
            v-model="reason"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit /></el-form-item></el-form></div
    ><template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button
        type="primary"
        :loading="saving"
        :disabled="detailLoading || !detail"
        @click="submit"
        >提交</el-button
      ></template
    ></el-dialog
  >
</template>
