<template>
  <ContentWrap>
    <div class="mb-4 flex items-center justify-between gap-12px">
      <span class="text-base font-medium">轻量办公采购</span>
      <div>
        <el-button v-hasPermi="['eam:purchase:create']" type="primary" @click="formRef.open()">
          <Icon icon="ep:plus" class="mr-5px" />新建采购单
        </el-button>
        <el-button :loading="loading" @click="load"
          ><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button
        >
      </div>
    </div>
    <el-alert
      v-if="stockError"
      type="warning"
      :closable="false"
      class="mb-12px"
      :title="stockError"
    />
    <el-result v-if="error" icon="error" title="采购单加载失败" :sub-title="error">
      <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
    </el-result>
    <el-table v-else v-loading="loading" :data="rows" stripe row-key="id">
      <el-table-column type="expand" width="48">
        <template #default="{ row }">
          <el-table :data="row.items" border>
            <el-table-column prop="name" label="物品" min-width="150" />
            <el-table-column label="采购 / 已到 / 退货 / 关闭" min-width="230">
              <template #default="scope">
                {{ scope.row.quantity }} / {{ scope.row.receivedQuantity || 0 }} /
                {{ scope.row.returnedQuantity || 0 }} / {{ scope.row.shortClosedQuantity || 0 }}
                {{ scope.row.unit }}
              </template>
            </el-table-column>
            <el-table-column prop="deliveryModeLabelSnapshot" label="交付" min-width="100" />
            <el-table-column prop="custodyModeLabelSnapshot" label="持有" min-width="100" />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="no" label="采购单号" min-width="150" />
      <el-table-column prop="supplierNameSnapshot" label="供应商" min-width="140" />
      <el-table-column prop="paymentModeLabelSnapshot" label="付款方式" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"
          ><el-tag>{{ status[row.status] || row.status }}</el-tag></template
        >
      </el-table-column>
      <el-table-column prop="estimatedAmount" label="预计金额" width="110" />
      <el-table-column prop="actualAmount" label="实际金额" width="110" />
      <el-table-column prop="expectedArrivalDate" label="预计到货" width="120" />
      <el-table-column label="操作" min-width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="[2, 5].includes(row.status)"
            v-hasPermi="['eam:purchase:receive']"
            link
            type="primary"
            @click="actionRef.open('receive', row, stocks)"
            >入库/交付</el-button
          >
          <el-button
            v-if="[5, 6].includes(row.status)"
            v-hasPermi="['eam:purchase:return']"
            link
            type="primary"
            @click="actionRef.open('return', row, stocks)"
            >供应商退货</el-button
          >
          <el-button
            v-if="[2, 5].includes(row.status)"
            v-hasPermi="['eam:purchase:close']"
            link
            type="warning"
            @click="actionRef.open('close', row, stocks)"
            >少到关闭</el-button
          >
          <el-button
            v-if="row.expenseStatus === 0"
            v-hasPermi="['eam:purchase:expense']"
            link
            type="success"
            @click="actionRef.open('expense', row, stocks)"
            >费用审批</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !error && !rows.length" description="暂无办公采购单" />
  </ContentWrap>
  <PurchaseForm ref="formRef" @success="load" />
  <PurchaseActionDialog ref="actionRef" @success="load" />
</template>

<script setup lang="ts">
import * as ProcurementApi from '@/api/eam/procurement'
import PurchaseForm from './PurchaseForm.vue'
import PurchaseActionDialog from './PurchaseActionDialog.vue'

defineOptions({ name: 'EamPurchase' })
const loading = ref(false)
const error = ref('')
const stockError = ref('')
const rows = ref<ProcurementApi.PurchaseVO[]>([])
const stocks = ref<ProcurementApi.StockBalanceVO[]>([])
const formRef = ref()
const actionRef = ref()
const status: Record<number, string> = {
  0: '草稿',
  1: '审批中',
  2: '待入库',
  3: '已驳回',
  4: '已取消',
  5: '部分入库',
  6: '已完成'
}
const load = async () => {
  loading.value = true
  error.value = ''
  stockError.value = ''
  try {
    const [purchaseResult, stockResult] = await Promise.allSettled([
      ProcurementApi.getPurchaseList(),
      ProcurementApi.getStockList()
    ])
    if (purchaseResult.status === 'rejected') {
      throw purchaseResult.reason
    }
    rows.value = purchaseResult.value
    if (stockResult.status === 'fulfilled') {
      stocks.value = stockResult.value
    } else {
      stocks.value = []
      const e: any = stockResult.reason
      stockError.value = e?.msg || e?.message || '库存品项加载失败；批量物品退货暂不可用'
    }
  } catch (e: any) {
    error.value = e?.msg || e?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
