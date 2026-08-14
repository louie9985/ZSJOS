<template>
  <WorkbenchListPage
    title="我的订单"
    endpoint="/zsjos/sales-order/my-page"
    description="当前用户成交订单"
  >
    <template #row-actions="{ row, reload }">
      <el-button link type="primary" @click="showDetail(row, reload)">查看</el-button>
    </template>
  </WorkbenchListPage>
  <el-drawer v-model="detailOpen" title="订单详情" size="560px">
    <div v-loading="detailLoading">
      <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false"
        ><el-button link @click="loadDetail">重试</el-button></el-alert
      >
      <el-descriptions v-else-if="detail" :column="1" border>
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="学员">{{ detail.studentName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ detail.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="审批轮次">{{ detail.approvalRoundNo }}</el-descriptions-item>
        <el-descriptions-item label="驳回/终止原因">{{
          detail.decisionReason || detail.terminationReason || '-'
        }}</el-descriptions-item>
      </el-descriptions>
    </div>
    <template #footer>
      <el-button @click="detailOpen = false">关闭</el-button>
      <el-button
        v-if="detail?.canRevise"
        v-hasPermi="['zsjos:sales-order:create']"
        type="primary"
        @click="openRevision"
        >补正并重新提交</el-button
      >
      <el-button
        v-if="detail?.canTerminate"
        v-hasPermi="['zsjos:sales-order:create']"
        type="danger"
        @click="terminateOpen = true"
        >终止审批</el-button
      >
    </template>
  </el-drawer>
  <el-dialog v-model="terminateOpen" title="终止订单审批" width="520px">
    <el-alert
      title="终止后当前审批流程将结束，请确认业务状态后操作。"
      type="warning"
      show-icon
      class="mb-12px"
    />
    <el-form label-position="top"><el-form-item label="终止原因" required><el-input
      v-model="terminationReason"
      type="textarea"
      :rows="4"
      maxlength="1000"
      show-word-limit
      placeholder="填写终止原因"
    /></el-form-item></el-form>
    <template #footer
      ><el-button @click="terminateOpen = false">取消</el-button
      ><el-button type="danger" :loading="terminating" @click="terminate"
        >确认终止</el-button
      ></template
    >
  </el-dialog>
  <el-dialog v-model="revisionOpen" title="补正并重新提交" width="620px">
    <el-alert title="未展示字段会沿用当前订单快照。" type="info" show-icon class="mb-12px" />
    <el-form label-width="100px">
      <el-form-item label="购买人"><el-input v-model="revision.buyerName" /></el-form-item>
      <el-form-item label="学员姓名" required
        ><el-input v-model="revision.studentName"
      /></el-form-item>
      <el-form-item label="手机号"><el-input v-model="revision.studentMobile" /></el-form-item>
      <el-form-item label="微信号"><el-input v-model="revision.studentWechatId" /></el-form-item>
      <el-form-item
        v-for="(item, index) in revision.items"
        :key="index"
        :label="`课程 ${index + 1} 金额`"
      >
        <el-input-number v-model="item.actualAmount" :min="0" :precision="2" class="w-100%" />
      </el-form-item>
      <el-form-item label="备注"
        ><el-input
          v-model="revision.remark"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="revisionOpen = false">取消</el-button
      ><el-button type="primary" :loading="revising" @click="resubmit"
        >重新提交</el-button
      ></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import * as Api from '@/api/zsjos/workbenchMenus'
import { useMessage } from '@/hooks/web/useMessage'
import WorkbenchListPage from '../components/WorkbenchListPage.vue'

const message = useMessage()
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<any>()
const selectedId = ref<number>()
const terminateOpen = ref(false)
const terminationReason = ref('')
const terminating = ref(false)
const revisionOpen = ref(false)
const revising = ref(false)
const revision = reactive<any>({ items: [] })
let reloadList = async () => {}
const loadDetail = async () => {
  if (!selectedId.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await Api.getMySalesOrder(selectedId.value)
  } catch (error: any) {
    detail.value = undefined
    detailError.value = error?.msg || error?.message || '订单详情加载失败'
  } finally {
    detailLoading.value = false
  }
}
const showDetail = (row: Api.WorkbenchListItem, reloadListPage: () => Promise<void>) => {
  selectedId.value = row.id
  reloadList = reloadListPage
  detailOpen.value = true
  void loadDetail()
}
const terminate = async () => {
  if (!detail.value || !terminationReason.value.trim()) return message.warning('请填写终止原因')
  terminating.value = true
  try {
    await Api.terminateSalesOrder(detail.value.id, {
      approvalRoundId: detail.value.currentApprovalRoundId,
      orderVersion: detail.value.version,
      roundVersion: detail.value.approvalRoundVersion,
      reason: terminationReason.value.trim(),
      idempotencyKey: crypto.randomUUID()
    })
    message.success('订单审批已终止')
    terminateOpen.value = false
    detailOpen.value = false
    terminationReason.value = ''
    await reloadList()
  } finally {
    terminating.value = false
  }
}
const openRevision = () => {
  if (!detail.value) return
  Object.assign(revision, structuredClone(detail.value))
  revisionOpen.value = true
}
const resubmit = async () => {
  if (!detail.value || !revision.studentName?.trim()) return message.warning('请填写学员姓名')
  revising.value = true
  try {
    await Api.resubmitSalesOrder(detail.value.id, {
      buyerName: revision.buyerName?.trim() || undefined,
      studentName: revision.studentName.trim(),
      studentNature: revision.studentNature,
      studentMobile: revision.studentMobile?.trim() || undefined,
      studentWechatId: revision.studentWechatId?.trim() || undefined,
      provinceCode: revision.provinceCode,
      provinceName: revision.provinceName,
      cityCode: revision.cityCode,
      cityName: revision.cityName,
      agreedExamTime: revision.agreedExamTime,
      classType: revision.classType,
      servicePeriod: revision.servicePeriod,
      studentSource: revision.studentSource,
      customerPaidAt: revision.customerPaidAt,
      feeMode: revision.feeMode,
      paymentMethod: revision.paymentMethod,
      remark: revision.remark?.trim() || undefined,
      studentSpecialRequirements: revision.studentSpecialRequirements,
      materialDeliveryContact: revision.materialDeliveryContact,
      items: revision.items.map((item: any) => ({
        spuRef: item.productRef,
        skuRef: item.skuRef,
        actualAmount: item.actualAmount
      })),
      paymentVouchers: (revision.paymentVouchers || []).map((item: any) => ({
        infraFileId: item.infraFileId
      })),
      idempotencyKey: crypto.randomUUID()
    })
    message.success('订单已重新提交')
    revisionOpen.value = false
    detailOpen.value = false
    await reloadList()
  } finally {
    revising.value = false
  }
}
</script>
