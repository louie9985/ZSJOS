<template>
  <ContentWrap>
    <el-form :model="query" inline @submit.prevent>
      <el-form-item label="类型"
        ><el-select v-model="query.type" clearable class="!w-160px"
          ><el-option label="有效返现" value="valid" /><el-option
            label="成交返现"
            value="deal" /></el-select
      ></el-form-item>
      <el-form-item label="状态"
        ><el-select v-model="query.status" clearable class="!w-160px"
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
    <el-alert v-if="error" :title="error" type="error" show-icon
      ><template #default><el-button link @click="load">重试</el-button></template></el-alert
    >
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="返现编号" prop="cashbackNo" min-width="210" />
      <el-table-column label="类型" width="100"
        ><template #default="scope">{{
          scope.row.type === 'valid' ? '有效返现' : '成交返现'
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
</template>

<script setup lang="ts">
import * as CashbackApi from '@/api/zsjos/cashback'
import { useUserStore } from '@/store/modules/user'
import { cashbackDataScope } from '@/utils/zsjosDataScope'
defineOptions({ name: 'ZsjosCashback' })
const userStore = useUserStore()
const loading = ref(false)
const error = ref('')
const list = ref<CashbackApi.CashbackVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  type: undefined as string | undefined,
  status: undefined as string | undefined
})
const statuses = [
  { value: 'pending_settlement', label: '待结算' },
  { value: 'available', label: '可提现' },
  { value: 'withdrawing', label: '提现中' },
  { value: 'withdrawn', label: '已提现' },
  { value: 'cancelled', label: '已取消' }
]
const statusName = (value: string) => statuses.find((item) => item.value === value)?.label || value
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const scope = cashbackDataScope(userStore.getPermissions)
    if (scope === 'unauthorized') throw new Error('暂无返现查询权限')
    const data = await (scope === 'all'
      ? CashbackApi.getFinanceCashbackPage(query)
      : CashbackApi.getMyCashbackPage(query))
    list.value = data.list
    total.value = data.total
  } catch (e: any) {
    error.value = e?.msg || e?.message || '返现记录加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
