<template>
  <ContentWrap>
    <el-form :model="query" inline @submit.prevent>
      <el-form-item label="类型"
        ><el-select v-model="query.type" :loading="optionsLoading" :disabled="optionsLoading || !!optionsError" clearable class="!w-160px"
          ><el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" /></el-select
      ></el-form-item>
      <el-form-item label="状态"
        ><el-select v-model="query.status" :loading="optionsLoading" :disabled="optionsLoading || !!optionsError" clearable class="!w-160px"
          ><el-option
            v-for="item in statuses"
            :key="item.value"
            :label="item.label"
            :value="item.value" /></el-select
      ></el-form-item>
      <el-form-item
        ><el-button :loading="loading" @click="load"
          ><Icon icon="ep:search" class="mr-5px" />查询</el-button
        ></el-form-item
      >
    </el-form>
    <el-alert v-if="optionsError" :title="optionsError" type="error" :closable="false"><el-button link @click="reloadOptions">重试</el-button></el-alert>
    <ZsjosAdvancedFilter scene="cashback" page-key="cashback" placeholder="返现编号 / 客资编号" @search="value => { query.keyword = value; query.pageNo = 1; load() }" @change="value => { query.advancedFilter = value; query.pageNo = 1; load() }" />
    <el-alert v-if="error" :title="error" type="error" show-icon
      ><template #default><el-button link @click="load">重试</el-button></template></el-alert
    >
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="返现编号" prop="cashbackNo" min-width="210"><template #default="{ row }"><el-link v-if="finance" type="primary" @click="openDetail(row.id)">{{ row.cashbackNo }}</el-link><span v-else>{{ row.cashbackNo }}</span></template></el-table-column>
      <el-table-column v-if="finance" label="返现受益人" prop="beneficiaryName" min-width="140" />
      <el-table-column v-if="finance" label="归属合作方" prop="partnerName" min-width="140" />
      <el-table-column v-if="finance" label="客户／学员" min-width="140"><template #default="{ row }">{{ row.source?.studentName || row.source?.customerName || '-' }}</template></el-table-column>
      <el-table-column v-if="finance" label="来源单据" min-width="200"><template #default="{ row }">{{ row.source?.orderNo || row.source?.leadNo || '来源不可查看' }}</template></el-table-column>
      <el-table-column v-if="finance" label="返现基数" min-width="120"><template #default="{ row }">{{ row.type === 'valid' ? '不适用' : money(row.baseAmount) }}</template></el-table-column>
      <el-table-column v-if="finance" label="比例" min-width="90"><template #default="{ row }">{{ row.type === 'valid' ? '不适用' : row.rateSnapshot == null ? '-' : `${Number((row.rateSnapshot * 100).toFixed(8))}%` }}</template></el-table-column>
      <el-table-column label="类型" width="100"
        ><template #default="scope">{{
          types.find(item => item.value === scope.row.type)?.label || '类型暂不可用'
        }}</template></el-table-column
      >
      <el-table-column label="课程" prop="productNameSnapshot" min-width="150" />
      <el-table-column label="金额" width="120"
        ><template #default="scope"
          >¥{{ Number(scope.row.amount).toFixed(2) }}</template
        ></el-table-column
      >
      <el-table-column label="状态" width="110"
        ><template #default="scope">{{ statusName(scope.row.status) }}</template></el-table-column
      >
      <el-table-column label="生成时间" prop="generatedAt" min-width="170" />
      <el-table-column label="可提现时间" prop="availableAt" min-width="170" />
      <template #empty><el-empty description="暂无返现记录" /></template>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
  <CashbackDetail v-if="finance && cashbackId" :id="cashbackId" @close="openDetail()" />
</template>

<script setup lang="ts">
import CashbackDetail from '../components/CashbackDetail.vue'
import { useFinanceFilterOptions } from '../components/useFinanceFilterOptions'
import ZsjosAdvancedFilter from '../components/ZsjosAdvancedFilter.vue'
import type { AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'
import * as CashbackApi from '@/api/zsjos/cashback'
import { useUserStore } from '@/store/modules/user'
import { cashbackDataScope } from '@/utils/zsjosDataScope'
defineOptions({ name: 'ZsjosCashback' })
const userStore = useUserStore()
const route = useRoute(), router = useRouter()
const finance = computed(() => cashbackDataScope(userStore.getPermissions) === 'all')
const cashbackId = computed(() => Number(route.query.cashbackId) || undefined)
const openDetail = (id?: number) => router.replace({ query: { ...route.query, cashbackId: id ? String(id) : undefined } })
const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`
const loading = ref(false)
const error = ref('')
const list = ref<CashbackApi.CashbackVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  type: undefined as string | undefined,
  status: undefined as string | undefined,
  keyword: undefined as string | undefined,
  advancedFilter: undefined as AdvancedFilterGroup | undefined
})
const { statuses, types, loading: optionsLoading, error: optionsError, reload: reloadOptions } = useFinanceFilterOptions('cashback')
const statusName = (value: string) => statuses.value.find((item) => item.value === value)?.label || '状态暂不可用'
let loadSequence = 0
const load = async () => {
  const sequence = ++loadSequence
  list.value = []
  total.value = 0
  loading.value = true
  error.value = ''
  try {
    const scope = cashbackDataScope(userStore.getPermissions)
    if (scope === 'unauthorized') throw new Error('暂无返现查询权限')
    const data = await (scope === 'all'
      ? (query.advancedFilter ? CashbackApi.searchFinanceCashbackPage(query) : CashbackApi.getFinanceCashbackPage(query))
      : (query.advancedFilter ? CashbackApi.searchMyCashbackPage(query) : CashbackApi.getMyCashbackPage(query)))
    if (sequence !== loadSequence) return
    list.value = data.list
    total.value = data.total
  } catch (e: any) {
    if (sequence === loadSequence) error.value = e?.msg || e?.message || '返现记录加载失败'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
onMounted(load)
</script>
