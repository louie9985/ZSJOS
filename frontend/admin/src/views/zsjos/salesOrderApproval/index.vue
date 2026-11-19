<script setup lang="ts">
import OrderProductSummary from '../components/OrderProductSummary.vue'
import OrderHistoryFacts from '../components/OrderHistoryFacts.vue'
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import * as Api from '@/api/zsjos/workbenchMenus'
import { FINANCE_ORDER_PROCESS_KEY } from '@/api/zsjos/salesOrderApproval'
import FinanceOrderApprovalDetail from './FinanceOrderApprovalDetail.vue'
const message = useMessage()
const nativeFinance = computed(() => detail.value?.processDefinitionKey === Api.FINANCE_ORDER_PROCESS_KEY)
const open = ref(false)
const current = ref<any>()
const detail = ref<any>()
const decision = ref<'approve' | 'reject'>('approve')
const reason = ref('')
const saving = ref(false)
const detailLoading = ref(false)
const approvalReasonRequired = ref<boolean>()
const loadError = ref('')
const reasonRequired = computed(() => decision.value === 'reject' || approvalReasonRequired.value !== false)
const isFinance = computed(() => current.value?.processDefinitionKey === FINANCE_ORDER_PROCESS_KEY)
const load = async () => {
  detail.value = undefined
  approvalReasonRequired.value = undefined
  loadError.value = ''
  detailLoading.value = true
  try {
    const order = await Api.getSalesOrder(current.value.id, current.value.taskId)
    if (order.processDefinitionKey === Api.FINANCE_ORDER_PROCESS_KEY) {
      detail.value = order
      return
    }
    const target = await Api.getSalesOrderApprovalTaskTarget(current.value.taskId)
    if (target.orderId !== current.value.id || typeof target.approvalReasonRequired !== 'boolean')
      throw new Error('审批任务或意见配置未加载，请重试')
    detail.value = order
    approvalReasonRequired.value = target.approvalReasonRequired
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '审批详情加载失败，请重试'
  } finally { detailLoading.value = false }
}
let reloadList = () => {}
const show = async (row: any, fn: () => void) => {
  current.value = row
  reloadList = fn
  decision.value = 'approve'
  reason.value = ''
  open.value = true
  if (!isFinance.value) await load()
}
const onFinanceChanged = async (completed: boolean) => {
  if (completed) open.value = false
  await reloadList()
}
const submit = async () => {
  if (!current.value?.taskId || !detail.value || nativeFinance.value)
    return message.warning('审批并发信息缺失，请刷新详情')
  if (typeof approvalReasonRequired.value !== 'boolean') return message.warning('审批意见配置未加载，请重试')
  if (reasonRequired.value && !reason.value.trim())
    return message.warning(decision.value === 'reject' ? '请填写驳回原因' : '请填写审批意见')
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
    show-order-identity
    advanced-scene="order"
    advanced-page-key="sales_order_approval"
    advanced-search-endpoint="/zsjos/sales-order/approval/search-page"
    advanced-placeholder="订单号 / 客户信息"
    endpoint="/zsjos/sales-order/approval/inbox-page"
    description="成交订单审批队列"
    :query="{ handled: false }"
    ><template #row-actions="{ row, reload }"
      ><el-button link type="primary" :disabled="!row.taskId" @click="show(row, reload)"
        >审批</el-button
      ></template
    ></WorkbenchListPage
  ><el-dialog v-model="open" title="成交审批" :width="isFinance ? 'min(820px, calc(100vw - 32px))' : 'min(560px, calc(100vw - 32px))'"
    :close-on-click-modal="false" destroy-on-close
  >
    <FinanceOrderApprovalDetail
      v-if="open && isFinance"
      :task-id="current.taskId"
      :process-instance-id="current.processInstanceId"
      entry="business"
      @changed="onFinanceChanged"
    />
    <div v-else-if="!isFinance" v-loading="detailLoading">
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
        <el-button link type="primary" @click="load">重试</el-button>
      </el-alert>
      <OrderHistoryFacts v-if="detail" :order="detail" />
      <OrderProductSummary v-if="detail && !detailLoading" :items="detail.items" />
      <el-descriptions v-if="detail && !detailLoading" :column="1" border>
        <el-descriptions-item label="成交归属身份">{{ detail.formalOwnerIdentityLabel || '未记录' }}</el-descriptions-item>
      </el-descriptions>
      <el-form v-if="!nativeFinance" label-width="90px"
        ><el-form-item label="订单"
          ><span>{{ detail?.orderNo || `#${current?.id}` }}</span></el-form-item
        ><el-form-item label="结论" required
          ><el-radio-group v-model="decision"
            ><el-radio value="approve">通过</el-radio
            ><el-radio value="reject">驳回</el-radio></el-radio-group
          ></el-form-item
        ><el-form-item label="审批意见" :required="reasonRequired"
          ><el-input
            v-model="reason"
            type="textarea"
            :placeholder="decision === 'reject' ? '请填写驳回原因' : reasonRequired ? '请填写审批意见' : '审批意见（选填）'"
            :rows="4"
            maxlength="1000"
            show-word-limit /></el-form-item></el-form></div
    ><template v-if="!isFinance" #footer
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
