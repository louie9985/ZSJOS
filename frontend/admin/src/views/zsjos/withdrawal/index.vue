<template>
  <ContentWrap>
    <BusinessReadScope v-if="own && userStore.dataAccess.tenantReadAll" v-model="readScope" />
    <el-button
      v-if="!readOnly"
      v-hasPermi="['zsjos:withdrawal:apply']"
      type="primary"
      class="mb-16px"
      @click="openApply"
    >
      <Icon icon="ep:wallet" />申请提现
    </el-button>
    <el-button
      v-if="canPayout"
      type="primary"
      class="mb-16px"
      :disabled="loading || !selectedWithdrawals.length"
      @click="openBatchPayout"
      >批量登记打款（{{ selectedWithdrawals.length }}）</el-button
    >
    <el-form inline @submit.prevent>
      <el-form-item label="状态">
        <el-select v-model="query.status" :loading="optionsLoading" :disabled="optionsLoading || !!optionsError" clearable class="!w-160px">
          <el-option v-for="item in statuses" :key="item.value" v-bind="item" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button v-if="canExport" :loading="exporting" @click="exportCurrent">
          <Icon icon="ep:download" />导出
        </el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="optionsError" :title="optionsError" type="error" :closable="false"><el-button link @click="reloadOptions">重试</el-button></el-alert>
<ZsjosAdvancedFilter scene="withdrawal" page-key="withdrawal" placeholder="提现单号 / 银行流水号" @search="value => { query.keyword = value; query.pageNo = 1; load() }" @change="value => { query.advancedFilter = value; query.pageNo = 1; load() }" />
    <el-alert v-if="error" :title="error" type="error" show-icon
      ><el-button link @click="load">重试</el-button></el-alert
    >
  </ContentWrap>
  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="list"
      row-key="id"
      @selection-change="selectedWithdrawals = $event"
    >
      <el-table-column
        v-if="canPayout"
        type="selection"
        width="50"
        :selectable="(row: Api.WithdrawalVO) => row.status === 'approved'"
      />
      <el-table-column prop="withdrawalNo" label="提现单号" min-width="210" />
      <el-table-column v-if="!own" prop="applicantName" label="申请人" min-width="150" />
      <el-table-column v-if="!own" prop="partnerName" label="归属合作方" min-width="150" />
      <el-table-column v-if="!own" prop="cashbackCount" label="来源返现笔数" width="120" />
      <el-table-column label="金额" width="120"
        ><template #default="scope"
          >¥{{ money(scope.row.applicationAmount) }}</template
        ></el-table-column
      >
      <el-table-column label="状态" width="110"
        ><template #default="scope">{{ statusName(scope.row.status) }}</template></el-table-column
      >
      <el-table-column prop="cardNumber" label="银行卡" min-width="180">
        <template #default="{ row }">{{ row.cardNumber || row.maskedCardNumber || '-' }}</template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="申请时间" min-width="170" />
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="scope">
          <el-button link @click="openDetail(scope.row.id)">详情</el-button>
          <el-button
            v-if="
              !readOnly && scope.row.status === 'pending_review' &&
              scope.row.applicantUserId === userStore.getUser.id
            "
            v-hasPermi="['zsjos:withdrawal:apply']"
            link
            type="danger"
            @click="cancelOwn(scope.row.id)"
            >撤销</el-button
          >
          <el-button
            v-if="!readOnly && scope.row.status === 'approved'"
            v-hasPermi="['zsjos:withdrawal:review']"
            link
            type="danger"
            @click="openReject(scope.row.id)"
            >驳回</el-button
          >
          <el-button
            v-if="!readOnly && scope.row.status === 'approved'"
            v-hasPermi="['zsjos:withdrawal:payout']"
            link
            type="primary"
            @click="openPayout(scope.row.id)"
            >登记打款</el-button
          >
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无提现记录" /></template>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
  <Dialog v-model="detailVisible" title="提现详情" width="620px">
    <el-button v-if="detail && userStore.dataAccess.tenantReadAll && !detail.cardNumber" @click="viewFullCard">查看完整收款信息</el-button>
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="状态">{{ statusName(detail.status) }}</el-descriptions-item>
      <el-descriptions-item label="审核人">{{ detail.reviewedByName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审核时间">{{ formatDate(detail.reviewedAt) || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审核意见">{{ detail.reviewReason || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="!own" label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="!own" label="合作方">{{ detail.partnerName || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="!own" label="申请时可用余额">{{ detail.availableBalanceSnapshot == null ? '-' : money(detail.availableBalanceSnapshot) }}</el-descriptions-item>
      <el-descriptions-item v-if="!own" label="批准金额">{{ detail.approvedAmount == null ? '-' : money(detail.approvedAmount) }}</el-descriptions-item>
      <el-descriptions-item v-if="!own" label="打款登记人">{{ detail.paidByName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="提现单号">{{ detail.withdrawalNo }}</el-descriptions-item>
      <el-descriptions-item label="金额"
        >¥{{ money(detail.applicationAmount) }}</el-descriptions-item
      >
      <el-descriptions-item label="开户名">{{ detail.accountNameSnapshot }}</el-descriptions-item>
      <el-descriptions-item label="银行卡">{{ detail.cardNumber || detail.maskedCardNumber }}</el-descriptions-item>
      <el-descriptions-item label="开户行"
        >{{ detail.bankNameSnapshot }} {{ detail.branchNameSnapshot }}</el-descriptions-item
      >
      <el-descriptions-item v-if="detail.rejectionReason" label="驳回原因">{{
        detail.rejectionReason
      }}</el-descriptions-item>
      <el-descriptions-item v-if="detail.bankTransactionNo" label="银行流水">{{
        detail.bankTransactionNo
      }}</el-descriptions-item>
      <el-descriptions-item v-if="detail.proofUrl" label="打款凭证"
        ><el-link :href="detail.proofUrl" target="_blank">查看凭证</el-link></el-descriptions-item
      >
      <el-descriptions-item v-if="detail.status === 'paid' && canViewFinance" label="打款时间">{{
        formatDate(detail.paidAt) || '-'
      }}</el-descriptions-item>
      <el-descriptions-item v-if="detail.status === 'paid' && canViewFinance" label="备注">{{
        detail.payoutRemark || '-'
      }}</el-descriptions-item>
    </el-descriptions>
    <WithdrawalSources v-if="detail && !own" :id="detail.id" />
    <el-alert v-if="detail?.status === 'pending_review' && detail.reviewUnavailableReason" :title="detail.reviewUnavailableReason" type="warning" :closable="false" />
    <template #footer>
      <template v-if="detail && !own">
        <el-button v-if="detail.availableActions?.includes('approve')" v-hasPermi="['zsjos:withdrawal:review']" type="primary" @click="openReview(true)">通过</el-button>
        <el-button v-if="detail.availableActions?.includes('reject')" v-hasPermi="['zsjos:withdrawal:review']" type="danger" @click="openReview(false)">驳回</el-button>
        <el-button v-if="detail.status === 'approved'" v-hasPermi="['zsjos:withdrawal:review']" type="danger" @click="openReject(detail.id)">驳回已通过申请</el-button>
        <el-button v-if="detail.status === 'approved'" v-hasPermi="['zsjos:withdrawal:payout']" type="primary" @click="openPayout(detail.id)">登记打款</el-button>
      </template>
    </template>
  </Dialog>
  <Dialog v-model="reviewVisible" :title="reviewApprove ? '通过提现审核' : '驳回提现申请'" width="min(480px, calc(100vw - 32px))" :before-close="(done: () => void) => { if (!saving) done() }">
    <el-form label-position="top" :disabled="saving">
      <el-form-item :label="reviewApprove ? '通过理由（选填）' : '驳回原因'" :required="!reviewApprove">
        <el-input v-model="reviewReason" type="textarea" :rows="4" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="reviewVisible = false">取消</el-button>
      <el-button :type="reviewApprove ? 'primary' : 'danger'" :loading="saving" @click="submitReview">{{ reviewApprove ? '确认通过' : '确认驳回' }}</el-button>
    </template>
  </Dialog>
  <Dialog v-model="applyVisible" title="申请提现" width="720px">
    <el-alert
      title="按完整返现金额提现，不支持拆分；最低金额由系统配置。"
      type="info"
      :closable="false"
      class="mb-12px"
    />
    <el-table :data="availableCashbacks" @selection-change="selectedCashbacks = $event">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="cashbackNo" label="返现编号" min-width="190" />
      <el-table-column prop="productNameSnapshot" label="课程" min-width="140" />
      <el-table-column label="金额" width="110"
        ><template #default="scope">¥{{ money(scope.row.amount) }}</template></el-table-column
      >
    </el-table>
    <el-form class="mt-16px" label-width="90px">
      <el-form-item label="开户名" required
        ><el-input v-model="applyForm.accountName" maxlength="100"
      /></el-form-item>
      <el-form-item label="银行卡号" required
        ><el-input v-model="applyForm.cardNumber" maxlength="40"
      /></el-form-item>
      <el-form-item label="开户行" required
        ><el-input v-model="applyForm.bankName" maxlength="100"
      /></el-form-item>
      <el-form-item label="支行"
        ><el-input v-model="applyForm.branchName" maxlength="100"
      /></el-form-item>
      <el-form-item
        ><el-checkbox v-model="applyForm.saveCard">存为常用卡</el-checkbox></el-form-item
      >
    </el-form>
    <template #footer
      ><el-button @click="applyVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submitApply"
        >提交财务审批</el-button
      ></template
    >
  </Dialog>
  <Dialog v-model="rejectVisible" title="驳回待打款提现" width="480px">
    <el-form label-position="top"
      ><el-form-item label="驳回原因" required
        ><el-input
          v-model="rejectReason"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="rejectVisible = false">取消</el-button
      ><el-button type="danger" :loading="saving" @click="submitReject"
        >确认驳回</el-button
      ></template
    >
  </Dialog>
  <Dialog
    v-model="payoutVisible"
    :title="batchPayout ? '批量登记打款' : '登记打款'"
    width="min(520px, calc(100vw - 32px))"
    :before-close="
      (done: () => void) => {
        if (!saving) done()
      }
    "
  >
    <el-alert
      v-if="batchPayout"
      :title="`已选择 ${payoutIds.length} 条待打款记录，打款时间和备注将统一应用。`"
      type="info"
      :closable="false"
      class="mb-16px"
    />
    <el-form label-position="top" :disabled="saving">
      <el-form-item label="打款时间（选填）">
        <el-date-picker
          v-model="payoutForm.paidAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="请选择打款时间"
          class="!w-full"
          clearable
        />
      </el-form-item>
      <el-form-item label="备注（选填）"
        ><el-input v-model="payoutForm.remark" type="textarea" maxlength="500"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="payoutVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submitPayout"
        >确认已线下打款</el-button
      ></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import WithdrawalSources from '../components/WithdrawalSources.vue'
import { useFinanceFilterOptions } from '../components/useFinanceFilterOptions'
import ZsjosAdvancedFilter from '../components/ZsjosAdvancedFilter.vue'
import type { AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'
import BusinessReadScope from '@/components/BusinessReadScope/index.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as Api from '@/api/zsjos/withdrawal'
import * as CashbackApi from '@/api/zsjos/cashback'
import * as ExportTaskApi from '@/api/zsjos/exportTask'
import { useUserStore } from '@/store/modules/user'
import { withdrawalDataScope } from '@/utils/zsjosDataScope'
import { formatDate } from '@/utils/formatTime'
defineOptions({ name: 'ZsjosWithdrawal' })
const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const reviewVisible = ref(false), reviewApprove = ref(true), reviewReason = ref('')
const own = computed(() => withdrawalDataScope(userStore.getPermissions) === 'own')
const readScope = ref<{ readScope: 'SELF' | 'ALL' | 'USER'; targetUserId?: number }>({ readScope: 'SELF' })
const readOnly = computed(() => own.value && readScope.value.readScope !== 'SELF')
const loading = ref(false),
  saving = ref(false),
  exporting = ref(false),
  error = ref(''),
  total = ref(0)
const list = ref<Api.WithdrawalVO[]>([]),
  detail = ref<Api.WithdrawalVO>(),
  detailVisible = ref(false)
const rejectVisible = ref(false),
  payoutVisible = ref(false),
  currentId = ref(0),
  rejectReason = ref('')
const applyVisible = ref(false),
  availableCashbacks = ref<CashbackApi.CashbackVO[]>([]),
  selectedCashbacks = ref<CashbackApi.CashbackVO[]>([])
const applyForm = reactive({
  accountName: '',
  cardNumber: '',
  bankName: '',
  branchName: '',
  saveCard: false
})
const selectedWithdrawals = ref<Api.WithdrawalVO[]>([])
const payoutIds = ref<number[]>([])
const batchPayout = ref(false)
const canPayout = computed(
  () =>
    withdrawalDataScope(userStore.getPermissions) === 'all' &&
    (userStore.getPermissions.has('*:*:*') ||
      userStore.getPermissions.has('zsjos:withdrawal:payout'))
)
const canViewFinance = computed(
  () =>
    userStore.getPermissions.has('*:*:*') ||
    userStore.getPermissions.has('zsjos:withdrawal:finance-query') || userStore.getPermissions.has('zsjos:withdrawal:admin-query')
)
const query = reactive({ pageNo: 1, pageSize: 10, status: undefined as string | undefined, keyword: undefined as string | undefined, advancedFilter: undefined as AdvancedFilterGroup | undefined })
const canExport = computed(
  () =>
    (userStore.getPermissions.has('*:*:*') ||
      userStore.getPermissions.has('zsjos:export:withdrawal')) &&
    withdrawalDataScope(userStore.getPermissions) === 'all'
)
const exportCurrent = async () => {
  try {
    await ElMessageBox.confirm('将导出符合当前筛选条件的全部记录。', '导出提现记录', {
      confirmButtonText: '加入导出队列',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (action) {
    if (action === 'cancel' || action === 'close') return
    throw action
  }
  exporting.value = true
  try {
    await ExportTaskApi.createExportTask('withdrawal', JSON.stringify({ status: query.status, keyword: query.keyword, advancedFilter: query.advancedFilter }))
    ElMessageBox.confirm('已加入导出队列', '导出任务', {
      confirmButtonText: '查看导出任务',
      cancelButtonText: '关闭',
      type: 'success'
    })
      .then(() => router.push('/zsjos/export-task'))
      .catch(() => undefined)
  } finally {
    exporting.value = false
  }
}
const payoutForm = reactive({
  paidAt: undefined as string | undefined,
  remark: ''
})
const { statuses, loading: optionsLoading, error: optionsError, reload: reloadOptions } = useFinanceFilterOptions('withdrawal')
const statusName = (v: string) => statuses.value.find((i) => i.value === v)?.label
    || (optionsLoading.value ? '状态加载中' : '状态暂不可用'),
  money = (v: number) => Number(v).toFixed(2)
let loadSequence = 0
const load = async () => {
  const sequence = ++loadSequence
  selectedWithdrawals.value = []
  list.value = []
  loading.value = true
  error.value = ''
  try {
    const scope = withdrawalDataScope(userStore.getPermissions)
    if (scope === 'unauthorized') throw new Error('暂无提现查询权限')
    if (own.value && readScope.value.readScope === 'USER' && !readScope.value.targetUserId) { total.value = 0; return }
    const params = { ...query, ...(scope === 'own' ? readScope.value : {}) }; const data = await (scope === 'own' ? (query.advancedFilter ? Api.searchMyPage(params) : Api.getMyPage(params)) : (query.advancedFilter ? Api.searchPage(params) : Api.getPage(params)))
    if (sequence !== loadSequence) return
    list.value = data.list
    total.value = data.total
  } catch (e: any) {
    if (sequence === loadSequence) error.value = e?.msg || e?.message || '提现记录加载失败'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
watch(readScope, () => { detailVisible.value = false; applyVisible.value = false; rejectVisible.value = false; payoutVisible.value = false; query.pageNo = 1; void load() }, { deep: true })
const viewFullCard = async () => {
  if (!detail.value) return
  try { detail.value = await Api.getFinanceDetail(detail.value.id) }
  catch (cause: any) { error.value = cause?.msg || cause?.message || '完整收款信息加载失败' }
}
const openDetail = async (id: number) => {
  const scope = withdrawalDataScope(userStore.getPermissions)
  detail.value = await (scope === 'all'
    ? canViewFinance.value
      ? Api.getFinanceDetail(id)
      : Api.getDetail(id)
    : Api.getMyDetail(id))
  detailVisible.value = true
}
const openReview = (approve: boolean) => {
  reviewApprove.value = approve; reviewReason.value = ''; reviewVisible.value = true
}
const submitReview = async () => {
  if (saving.value || !detail.value || detail.value.version == null) return
  const reason = reviewReason.value.trim()
  if (!reviewApprove.value && !reason) { ElMessage.warning('请填写驳回原因'); return }
  saving.value = true
  try {
    await Api.review(detail.value.id, reviewApprove.value, { version: detail.value.version, reason: reason || undefined })
    reviewVisible.value = false
    ElMessage.success(reviewApprove.value ? '审核通过，待打款' : '已驳回提现')
    await openDetail(detail.value.id)
    await load()
  } catch { /* The request client reports the stable business error; keep the entered reason. */ }
  finally { saving.value = false }
}
watch(() => route.query.withdrawalId, value => {
  const id = Number(value)
  if (Number.isSafeInteger(id) && id > 0) void openDetail(id).catch(() => { error.value = '提现详情加载失败，请重试' })
}, { immediate: true })
const openReject = (id: number) => {
  currentId.value = id
  rejectReason.value = ''
  rejectVisible.value = true
}
const openApply = async () => {
  availableCashbacks.value = (
    await CashbackApi.getMyCashbackPage({ pageNo: 1, pageSize: 100, status: 'available' })
  ).list
  selectedCashbacks.value = []
  applyVisible.value = true
}
const submitApply = async () => {
  if (
    !selectedCashbacks.value.length ||
    !applyForm.accountName.trim() ||
    !applyForm.cardNumber.trim() ||
    !applyForm.bankName.trim()
  )
    return
  saving.value = true
  try {
    await Api.apply({ ...applyForm, cashbackIds: selectedCashbacks.value.map((i) => i.id) })
    applyVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const cancelOwn = async (id: number) => {
  await Api.cancel(id)
  await load()
}
const submitReject = async () => {
  if (saving.value || !rejectReason.value.trim()) return
  saving.value = true
  try {
    await Api.rejectApproved(currentId.value, rejectReason.value)
    rejectVisible.value = false
    if (detailVisible.value && detail.value) await openDetail(detail.value.id)
    await load()
  } finally {
    saving.value = false
  }
}
const openPayout = (id: number) => {
  payoutIds.value = [id]
  batchPayout.value = false
  Object.assign(payoutForm, { paidAt: undefined, remark: '' })
  payoutVisible.value = true
}
const openBatchPayout = () => {
  if (!canPayout.value || !selectedWithdrawals.value.length) return
  payoutIds.value = selectedWithdrawals.value.map((row) => row.id)
  batchPayout.value = true
  Object.assign(payoutForm, { paidAt: undefined, remark: '' })
  payoutVisible.value = true
}
const submitPayout = async () => {
  if (saving.value || !payoutIds.value.length) return
  saving.value = true
  try {
    const data = {
      paidAt: payoutForm.paidAt || undefined,
      remark: payoutForm.remark.trim() || undefined
    }
    if (batchPayout.value) await Api.batchPayout({ ...data, ids: payoutIds.value })
    else await Api.payout(payoutIds.value[0], data)
    payoutVisible.value = false
    if (detailVisible.value && detail.value) await openDetail(detail.value.id)
    await load()
  } catch {
    // The request client displays the business error; retain this selection and form for retry.
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
