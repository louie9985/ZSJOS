<template>
  <WorkbenchListPage
    title="订单管理"
    show-order-identity
    endpoint="/zsjos/sales-order/management-page"
    description="按当前用户数据权限可见的成交订单"
    :query="queryParams"
    advanced-scene="order"
    advanced-search-endpoint="/zsjos/sales-order/management-search-page"
    advanced-placeholder="订单号 / 学员姓名 / 手机号"
    advanced-page-key="sales_order_management"
  >
    <template #actions="{ reload }">
      <el-input
        v-model="queryParams.keyword"
        placeholder="订单号 / 学员姓名 / 手机号"
        clearable
        class="!w-240px"
        @keyup.enter="reload"
      />
      <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-160px">
        <el-option label="待审核" value="pending_approval" />
        <el-option label="已驳回待修改" value="revision_required" />
        <el-option label="已通过" value="effective" />
        <el-option label="已被重提" value="superseded" />
        <el-option label="已终止" value="terminated" />
      </el-select>
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button @click="resetQuery(reload)">重置</el-button>
    </template>
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
        <el-descriptions-item label="成交归属身份">{{ detail.formalOwnerIdentityLabel || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="学员">{{ detail.studentName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ detail.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="审批轮次">{{ detail.approvalRoundNo }}</el-descriptions-item>
        <el-descriptions-item label="驳回/终止原因">{{
          detail.decisionReason || detail.terminationReason || '-'
        }}</el-descriptions-item>
      </el-descriptions>
      <OrderHistoryFacts v-if="detail" :order="detail" />
      <OrderProductSummary v-if="detail && !detailError" :items="detail.items" />
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
    <el-alert title="未展示字段沿用当前订单快照；补正后教务和财务将重新审批。" type="info" show-icon class="mb-12px" />
    <el-alert v-if="revision.collectionMode === 'online_link'" title="线上已到账：本次仅补正资料，原课程、规格和金额保持不变。" type="info" show-icon class="mb-12px" />
    <el-form label-width="100px">
      <template v-for="field in revisionDictionaryFields" :key="field.key">
        <el-form-item v-if="refreshedDictionaryFields.includes(field.key)" :label="field.label" required>
          <el-select v-model="revision[field.key]" placeholder="历史未记录，请重新选择">
            <el-option v-for="option in revisionOptions[field.key]" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
        </el-form-item>
      </template>
      <el-form-item label="购买人"><el-input v-model="revision.buyerName" /></el-form-item>
      <el-form-item label="学员姓名" required
        ><el-input v-model="revision.studentName"
      /></el-form-item>
      <el-form-item label="手机号"><el-input v-model="revision.studentMobile" /></el-form-item>
      <el-form-item label="微信号"><el-input v-model="revision.studentWechatId" /></el-form-item>
      <el-form-item
        v-for="(item, index) in revision.items"
        :key="index"
        :label="`课程 ${Number(index) + 1} 金额`"
      >
        <el-input-number v-model="item.actualAmount" :disabled="revision.transactionLocked !== false" :min="0" :precision="2" class="w-100%" />
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
import { getSimpleDictDataList } from '@/api/system/dict/dict.data'
import OrderProductSummary from '../components/OrderProductSummary.vue'
import OrderHistoryFacts from '../components/OrderHistoryFacts.vue'
import { reactive, ref, toRaw } from 'vue'
import * as Api from '@/api/zsjos/workbenchMenus'
import { useMessage } from '@/hooks/web/useMessage'
import WorkbenchListPage from '../components/WorkbenchListPage.vue'

const message = useMessage()
const queryParams = reactive<{ keyword: string; status: string | undefined }>({ keyword: '', status: undefined })
const resetQuery = (reload: () => void) => {
  queryParams.keyword = ''
  queryParams.status = undefined
  reload()
}
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
    detail.value = await Api.getManagementSalesOrder(selectedId.value)
  } catch (error: any) {
    detail.value = undefined
    detailError.value = error?.msg || error?.message || '订单详情加载失败'
  } finally {
    detailLoading.value = false
  }
}
const route = useRoute()
watch(() => route.query.orderId, value => {
  const id = Number(value)
  if (Number.isSafeInteger(id) && id > 0) { selectedId.value = id; detailOpen.value = true; void loadDetail() }
}, { immediate: true })
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
const revisionDictionaryFields = [
  { key: 'studentNature', type: 'zsjos_order_student_nature', label: '学员性质' },
  { key: 'servicePeriod', type: 'zsjos_order_service_period', label: '服务周期' },
  { key: 'studentSource', type: 'zsjos_order_student_source', label: '学员来源' },
  { key: 'feeMode', type: 'zsjos_order_fee_mode', label: '收费方式' },
  { key: 'paymentMethod', type: 'zsjos_order_payment_method', label: '支付方式' }
]
const refreshedDictionaryFields = ref<string[]>([])
const revisionOptions = ref<Record<string, Array<{ value: string; label: string }>>>({})
const openRevision = async () => {
  if (!detail.value || detailLoading.value || detailError.value) return
  if (!['offline_paid', 'online_link'].includes(detail.value.collectionMode)
      || typeof detail.value.transactionLocked !== 'boolean') return message.warning('订单收款状态未加载，请刷新后重试')
  if (detail.value.collectionMode === 'online_link' && detail.value.paymentStatus !== 'paid')
    return message.warning('线上支付尚未确认到账，请核实收款记录')
  Object.assign(revision, structuredClone(toRaw(detail.value)))
  refreshedDictionaryFields.value = revisionDictionaryFields.filter(field => !detail.value[field.key + 'LabelSnapshot']).map(field => field.key)
  const dictionaries = await getSimpleDictDataList()
  for (const field of revisionDictionaryFields) {
    revisionOptions.value[field.key] = dictionaries.filter(item => item.dictType === field.type)
      .map(item => ({ value: item.value, label: item.label }))
    if (refreshedDictionaryFields.value.includes(field.key)) revision[field.key] = undefined
  }
  revisionOpen.value = true
}
const resubmit = async () => {
  if (!detail.value || !revision.studentName?.trim()) return message.warning('请填写学员姓名')
  if (refreshedDictionaryFields.value.some(key => !revision[key])) return message.warning('请重新选择历史未记录的字典项')
  revising.value = true
  try {
    await Api.resubmitSalesOrder(detail.value.id, {
      refreshedDictionaryFields: refreshedDictionaryFields.value,
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
