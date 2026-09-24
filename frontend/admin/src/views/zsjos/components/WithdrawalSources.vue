<template>
  <h4>资金来源（共 {{ total }} 笔返现）</h4>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重试</el-button></el-alert>
  <el-table v-loading="loading" :data="rows" row-key="id">
    <el-table-column type="expand"><template #default="{ row }"><FinanceSource :source="row.cashback?.source" /></template></el-table-column>
    <el-table-column label="返现编号" min-width="200"><template #default="{ row }"><el-link v-if="row.cashback && canViewCashback" type="primary" @click="cashbackId = row.cashback.id">{{ row.cashback.cashbackNo }}</el-link><span v-else>{{ row.cashback?.cashbackNo || '历史返现信息缺失' }}</span></template></el-table-column>
    <el-table-column label="受益人" min-width="130"><template #default="{ row }">{{ row.cashback?.beneficiaryName || '-' }}</template></el-table-column>
    <el-table-column label="客户／学员" min-width="130"><template #default="{ row }">{{ row.cashback?.source?.studentName || row.cashback?.source?.customerName || '-' }}</template></el-table-column>
    <el-table-column label="来源单据" min-width="200"><template #default="{ row }">{{ row.cashback?.source?.orderNo || row.cashback?.source?.leadNo || '来源不可查看' }}</template></el-table-column>
    <el-table-column label="产品" min-width="140"><template #default="{ row }">{{ row.cashback?.productNameSnapshot || '-' }}</template></el-table-column>
    <el-table-column label="返现基数" min-width="120"><template #default="{ row }">{{ row.cashback?.type === 'valid' ? '不适用' : money(row.cashback?.baseAmount) }}</template></el-table-column>
    <el-table-column label="比例" min-width="90"><template #default="{ row }">{{ row.cashback?.type === 'valid' ? '不适用' : row.cashback?.rateSnapshot == null ? '-' : `${Number((row.cashback.rateSnapshot * 100).toFixed(8))}%` }}</template></el-table-column>
    <el-table-column label="本次提现金额" min-width="140"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
  </el-table>
  <Pagination :total="total" v-model:page="page" v-model:limit="limit" @pagination="load" />
  <CashbackDetail v-if="cashbackId" :id="cashbackId" @close="cashbackId = undefined" />
</template>
<script setup lang="ts">
import * as Api from '@/api/zsjos/withdrawal'
import FinanceSource from './FinanceSource.vue'
import CashbackDetail from './CashbackDetail.vue'
import { useUserStore } from '@/store/modules/user'
const props = defineProps<{ id: number }>()
const user = useUserStore()
const canViewCashback = computed(() => ['*:*:*', 'zsjos:cashback:finance-query'].some(p => user.getPermissions.has(p)))
const rows = ref<Api.WithdrawalSource[]>([]), total = ref(0), page = ref(1), limit = ref(10), loading = ref(false), error = ref(''), cashbackId = ref<number>()
const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`
let sequence = 0
const load = async () => {
  const current = ++sequence; rows.value = []; loading.value = true; error.value = ''
  try { const data = await Api.getSources(props.id, page.value); if (current === sequence) { rows.value = data.list; total.value = data.total } }
  catch (e: any) { if (current === sequence) error.value = e?.message || '资金来源加载失败' }
  finally { if (current === sequence) loading.value = false }
}
watch(() => props.id, () => { page.value = 1; void load() }, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>
