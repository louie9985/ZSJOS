<template>
  <ContentWrap>
    <div class="page-head">
      <div><h3>主管确认</h3><p>处理成交审批人发起的直属主管确认事项</p></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-tabs v-model="tab" @tab-change="changeTab">
      <el-tab-pane label="待处理" name="todo" />
      <el-tab-pane label="已处理" name="done" />
    </el-tabs>
    <el-form inline @submit.prevent>
      <el-form-item label="关键字">
        <el-input v-model="keyword" clearable placeholder="订单号 / 学员姓名 / 手机号" @keyup.enter="search" />
      </el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
    </el-form>
    <ZsjosAdvancedFilter
      scene="order"
      placeholder="订单号 / 学员姓名 / 手机号"
      :keyword="keyword"
      @search="(value) => { keyword = value; search() }"
      @change="(value) => { advancedFilter = value; search() }"
    />
    <el-alert v-if="unauthorized" title="暂无主管确认权限" type="warning" show-icon :closable="false" />
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-table v-loading="loading" :data="items" row-key="id" stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="160" />
      <el-table-column prop="studentName" label="学员" min-width="120" />
      <el-table-column label="确认环节" min-width="130">
        <template #default="{ row }">{{ taskLabels[row.taskDefinitionKey] }}</template>
      </el-table-column>
      <el-table-column prop="requesterUserName" label="申请人" min-width="110" />
      <el-table-column prop="requestReason" label="申请原因" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><el-tag :type="statusTypes[row.status]">{{ statusLabels[row.status] }}</el-tag></template>
      </el-table-column>
      <el-table-column label="申请时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.requestedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">{{ row.status === 'pending' ? '处理' : '查看' }}</el-button></template>
      </el-table-column>
      <template #empty><el-empty description="暂无主管确认事项" /></template>
    </el-table>
    <div class="pagination"><el-pagination v-model:current-page="pageNo" v-model:page-size="pageSize" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
  </ContentWrap>

  <el-drawer v-model="drawerOpen" title="主管确认详情" size="min(720px, 100%)">
    <el-alert v-if="detailError" :title="detailError" type="error" show-icon><template #default><el-button link @click="reloadDetail">重试</el-button></template></el-alert>
    <div v-loading="detailLoading">
      <el-alert v-if="current" :title="`${current.requesterUserName || '审批人'}申请主管确认`" :description="current.requestReason" type="info" show-icon :closable="false" />
      <el-descriptions v-if="detail" :column="1" border class="detail-block">
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="学员">{{ detail.studentName }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.studentMobile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="订单状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ amount(detail.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="审批轮次">{{ detail.approvalRoundNo || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="current?.decisionReason" label="主管意见">{{ current.decisionReason }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="current?.status === 'pending' && detail" class="detail-actions">
        <el-button type="primary" @click="openDecision('confirm')">确认</el-button>
        <el-button type="danger" @click="openDecision('reject')">不确认</el-button>
      </div>
    </div>
  </el-drawer>

  <el-dialog v-model="decisionOpen" :title="decision === 'confirm' ? '确认订单事项' : '不确认并退回销售'" width="520px">
    <el-form label-width="90px"><el-form-item label="主管意见" required><el-input v-model="reason" type="textarea" :rows="5" maxlength="1000" show-word-limit /></el-form-item></el-form>
    <template #footer><el-button @click="decisionOpen = false">取消</el-button><el-button :type="decision === 'confirm' ? 'primary' : 'danger'" :loading="saving" :disabled="!reason.trim()" @click="submit">提交</el-button></template>
  </el-dialog>
</template>

<script lang="ts" setup>
import type { TabsPaneContext } from 'element-plus'
import * as Api from '@/api/zsjos/salesOrderSupervisorConfirmation'
import type { AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'
import ZsjosAdvancedFilter from '../components/ZsjosAdvancedFilter.vue'

defineOptions({ name: 'ZsjosSalesOrderSupervisorConfirmation' })
const message = useMessage()
const tab = ref('todo')
const keyword = ref('')
const advancedFilter = ref<AdvancedFilterGroup>()
const items = ref<Api.SalesOrderSupervisorInboxVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const error = ref('')
const unauthorized = ref(false)
const drawerOpen = ref(false)
const current = ref<Api.SalesOrderSupervisorInboxVO>()
const detail = ref<Api.SalesOrderDetailVO>()
const detailLoading = ref(false)
const detailError = ref('')
const decisionOpen = ref(false)
const decision = ref<'confirm' | 'reject'>('confirm')
const reason = ref('')
const saving = ref(false)
const taskLabels = { registrationReview: '报名审核', financeReview: '财务审核' }
const statusLabels = { pending: '待确认', confirmed: '已确认', rejected: '不确认', cancelled: '已取消' }
const statusTypes = { pending: 'warning', confirmed: 'success', rejected: 'danger', cancelled: 'info' } as const
const formatTime = (value?: number) => value ? new Date(value).toLocaleString('zh-CN') : '-'
const amount = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`

const load = async () => {
  loading.value = true
  error.value = ''
  unauthorized.value = false
  try {
    const result = await Api.getInboxPage({ pageNo: pageNo.value, pageSize: pageSize.value, handled: tab.value === 'done', keyword: keyword.value.trim() || undefined, advancedFilter: advancedFilter.value })
    items.value = result.list
    total.value = result.total
  } catch (e: any) {
    items.value = []
    if (e?.response?.status === 403 || e?.code === 403) unauthorized.value = true
    else error.value = e?.msg || e?.message || '主管确认列表加载失败'
  } finally {
    loading.value = false
  }
}
const search = () => { pageNo.value = 1; load() }
const changeTab = (_?: string | number | TabsPaneContext) => search()
const reloadDetail = async () => {
  if (!current.value) return
  detailLoading.value = true
  detailError.value = ''
  try { detail.value = await Api.getSalesOrder(current.value.orderId) }
  catch (e: any) { detail.value = undefined; detailError.value = e?.msg || e?.message || '订单详情加载失败' }
  finally { detailLoading.value = false }
}
const openDetail = (row: Api.SalesOrderSupervisorInboxVO) => { current.value = row; drawerOpen.value = true; reloadDetail() }
const openDecision = (value: 'confirm' | 'reject') => { decision.value = value; reason.value = ''; decisionOpen.value = true }
const submit = async () => {
  if (!current.value || !reason.value.trim()) return
  saving.value = true
  try {
    await Api.decide(current.value.orderId, decision.value, { confirmationId: current.value.id, taskId: current.value.taskId, reason: reason.value.trim(), approvalRoundId: current.value.approvalRoundId, orderVersion: current.value.orderVersion, roundVersion: current.value.roundVersion, confirmationVersion: current.value.version, idempotencyKey: crypto.randomUUID() })
    message.success(decision.value === 'confirm' ? '已确认订单事项' : '已不确认并退回销售')
    decisionOpen.value = false
    drawerOpen.value = false
    await load()
  } catch (e: any) { message.error(e?.msg || e?.message || '主管确认提交失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-head h3, .page-head p { margin: 0; }
.page-head p { margin-top: 4px; color: var(--el-text-color-secondary); }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
.detail-block, .detail-actions { margin-top: 16px; }
@media (max-width: 768px) { .page-head { flex-direction: column; } .pagination { overflow-x: auto; justify-content: flex-start; } }
</style>
