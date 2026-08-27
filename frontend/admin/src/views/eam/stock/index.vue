<template>
  <ContentWrap>
    <div class="mb-4 flex items-center justify-between">
      <span class="text-base font-medium">全公司库存余额</span>
      <el-button :loading="loading" @click="load"
        ><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button
      >
    </div>
    <el-result v-if="error" icon="error" title="库存加载失败" :sub-title="error">
      <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
    </el-result>
    <el-table v-else v-loading="loading" :data="rows" stripe>
      <el-table-column prop="name" label="库存品项" min-width="180" />
      <el-table-column label="类型" min-width="120"
        ><template #default="{ row }"
          >{{ row.deliveryMode === 1 ? '实物' : '数字' }} /
          {{ row.custodyMode === 1 ? '消耗' : '归还' }}</template
        ></el-table-column
      >
      <el-table-column label="匹配属性" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ attributeText(row) }}</template>
      </el-table-column>
      <el-table-column prop="onHandQuantity" label="在库" width="90" />
      <el-table-column prop="reservedQuantity" label="预留" width="90" />
      <el-table-column prop="frozenQuantity" label="冻结" width="90" />
      <el-table-column label="可用" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.availableQuantity < row.minimumQuantity ? 'danger' : 'success'"
            >{{ row.availableQuantity }} {{ row.unit }}</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column label="最低库存" width="150">
        <template #default="{ row }">
          <div class="flex items-center gap-6px">
            <el-input-number v-model="row.minimumQuantity" :min="0" :precision="0" size="small" />
            <el-button
              v-hasPermi="['eam:stock:update']"
              circle
              text
              :loading="savingId === row.id"
              title="保存最低库存"
              @click="saveMinimum(row)"
            >
              <Icon icon="ep:check" />
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="nextExpiryDate" label="最近到期" width="130" />
    </el-table>
    <el-empty
      v-if="!loading && !error && !rows.length"
      description="暂无库存品项，采购入库后自动建立"
    />
  </ContentWrap>
</template>
<script setup lang="ts">
import * as ProcurementApi from '@/api/eam/procurement'
defineOptions({ name: 'EamStock' })
const message = useMessage()
const loading = ref(false)
const error = ref('')
const savingId = ref<number>()
const rows = ref<ProcurementApi.StockBalanceVO[]>([])
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    rows.value = await ProcurementApi.getStockList()
  } catch (e: any) {
    error.value = e?.msg || e?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}
const attributeText = (row: ProcurementApi.StockBalanceVO) =>
  Object.values(row.extFieldLabels || {}).join(' / ') || '-'
const saveMinimum = async (row: ProcurementApi.StockBalanceVO) => {
  savingId.value = row.id
  try {
    await ProcurementApi.updateStockMinimum({ id: row.id, minimumQuantity: row.minimumQuantity })
    message.success('最低库存已更新')
  } finally {
    savingId.value = undefined
  }
}
onMounted(load)
</script>
