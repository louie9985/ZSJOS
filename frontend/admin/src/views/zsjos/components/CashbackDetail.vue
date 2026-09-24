<template>
  <el-drawer :model-value="!!id" title="返现详情" size="min(900px, 95vw)" @close="emit('close')">
    <div v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重试</el-button></el-alert>
      <template v-if="detail">
        <el-alert v-if="optionsError" :title="optionsError" type="error" :closable="false"><el-button link @click="reloadOptions">重试</el-button></el-alert>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="返现编号">{{ detail.cashbackNo }}</el-descriptions-item>
          <el-descriptions-item label="返现受益人">{{ detail.beneficiaryName || '历史归属信息缺失' }}</el-descriptions-item>
          <el-descriptions-item label="合作方">{{ detail.partnerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="返现产品快照">{{ detail.productNameSnapshot || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ types.find(x => x.value === detail?.type)?.label || '类型暂不可用' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statuses.find(x => x.value === detail?.status)?.label || '状态暂不可用' }}</el-descriptions-item>
          <el-descriptions-item label="返现基数">{{ detail.type === 'valid' ? '固定金额返现，不适用' : money(detail.baseAmount) }}</el-descriptions-item>
          <el-descriptions-item label="比例">{{ detail.type === 'valid' ? '不适用' : detail.rateSnapshot == null ? '-' : `${Number((detail.rateSnapshot * 100).toFixed(8))}%` }}</el-descriptions-item>
          <el-descriptions-item label="返现金额">{{ money(detail.amount) }}</el-descriptions-item>
          <el-descriptions-item label="观察期（天）">{{ detail.observationDaysSnapshot ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ formatDate(detail.generatedAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="可提现时间">{{ formatDate(detail.availableAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结算时间">{{ formatDate(detail.settledAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="取消时间">{{ formatDate(detail.cancelledAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="取消原因">{{ detail.cancelReason || '-' }}</el-descriptions-item>
        </el-descriptions>
        <FinanceSource :source="detail.source" />
        <h4>提现记录</h4>
        <el-alert v-if="!canViewWithdrawals" title="无权查看提现记录" type="info" :closable="false" />
        <template v-else>
          <el-alert v-if="historyError" :title="historyError" type="error" :closable="false"><el-button link @click="loadHistory">重试</el-button></el-alert>
          <el-table v-loading="historyLoading" :data="history">
            <el-table-column label="提现单号" min-width="190"><template #default="{ row }"><el-link type="primary" @click="router.push({ path: '/zsjos/withdrawal', query: { withdrawalId: row.id } })">{{ row.withdrawalNo }}</el-link></template></el-table-column>
            <el-table-column label="本次提现金额"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column label="状态"><template #default="{ row }">{{ withdrawalOptions.statuses.value.find(x => x.value === row.status)?.label || '状态暂不可用' }}</template></el-table-column>
            <el-table-column label="申请时间"><template #default="{ row }">{{ formatDate(row.submittedAt) || '-' }}</template></el-table-column>
          </el-table>
          <Pagination :total="historyTotal" v-model:page="historyPage" v-model:limit="historyLimit" @pagination="loadHistory" />
        </template>
      </template>
    </div>
  </el-drawer>
</template>
<script setup lang="ts">
import * as Api from '@/api/zsjos/cashback'
import FinanceSource from './FinanceSource.vue'
import { useFinanceFilterOptions } from './useFinanceFilterOptions'
import { useUserStore } from '@/store/modules/user'
import { formatDate } from '@/utils/formatTime'
const props = defineProps<{ id?: number }>()
const emit = defineEmits<{ close: [] }>()
const router = useRouter(), user = useUserStore()
const canViewWithdrawals = computed(() => ['*:*:*', 'zsjos:withdrawal:finance-query', 'zsjos:withdrawal:admin-query'].some(p => user.getPermissions.has(p)))
const detail = ref<Api.CashbackVO>(), loading = ref(false), error = ref('')
const history = ref<Api.CashbackWithdrawal[]>([]), historyPage = ref(1), historyLimit = ref(10), historyTotal = ref(0), historyError = ref(''), historyLoading = ref(false)
const { types, statuses, error: optionsError, reload: reloadOptions } = useFinanceFilterOptions('cashback')
const withdrawalOptions = useFinanceFilterOptions('withdrawal')
const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`
let sequence = 0, historySequence = 0
const loadHistory = async () => {
  const current = ++historySequence; history.value = []; historyError.value = ''
  if (!props.id || !canViewWithdrawals.value) return
  historyLoading.value = true
  try { const data = await Api.getWithdrawals(props.id, historyPage.value); if (current === historySequence) { history.value = data.list; historyTotal.value = data.total } }
  catch (e: any) { if (current === historySequence) historyError.value = e?.message || '提现记录加载失败' }
  finally { if (current === historySequence) historyLoading.value = false }
}
const load = async () => {
  const current = ++sequence; detail.value = undefined; error.value = ''
  if (!props.id) return
  loading.value = true
  try { const data = await Api.getDetail(props.id); if (current === sequence) detail.value = data }
  catch (e: any) { if (current === sequence) error.value = e?.message || '返现详情加载失败' }
  finally { if (current === sequence) loading.value = false }
}
watch(() => props.id, () => { historyPage.value = 1; void load(); void loadHistory() }, { immediate: true })
onBeforeUnmount(() => { sequence++; historySequence++ })
</script>
