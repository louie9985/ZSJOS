<template>
  <ContentWrap>
    <div class="mb-4 flex items-center justify-between gap-12px">
      <span class="text-base font-medium">办公资产需求</span>
      <div>
        <el-button v-hasPermi="['eam:demand:create']" type="primary" @click="formRef.open()">
          <Icon icon="ep:plus" class="mr-5px" />新建需求
        </el-button>
        <el-button :loading="loading" @click="load">
          <Icon icon="ep:refresh" class="mr-5px" />刷新
        </el-button>
      </div>
    </div>
    <el-result v-if="error" icon="error" title="需求加载失败" :sub-title="error">
      <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
    </el-result>
    <el-table v-else v-loading="loading" :data="rows" stripe row-key="id">
      <el-table-column type="expand" width="48">
        <template #default="{ row }">
          <el-table :data="row.items" border>
            <el-table-column prop="name" label="物品" min-width="150" />
            <el-table-column label="需求 / 已分配 / 已采购 / 已关闭" min-width="240">
              <template #default="scope">
                {{ scope.row.quantity }} / {{ scope.row.fulfilledQuantity || 0 }} /
                {{ scope.row.purchasedQuantity || 0 }} / {{ scope.row.closedQuantity || 0 }}
                {{ scope.row.unit }}
              </template>
            </el-table-column>
            <el-table-column prop="custodyModeLabelSnapshot" label="持有规则" min-width="100" />
            <el-table-column label="操作" width="130" align="center">
              <template #default="scope">
                <el-button
                  v-if="row.status === 2 && remaining(scope.row) > 0"
                  v-hasPermi="['eam:stock:allocate']"
                  link
                  type="primary"
                  @click="candidateRef.open(scope.row)"
                >
                  检查库存
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="no" label="需求单号" min-width="150" />
      <el-table-column prop="employeeId" label="员工编号" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"
          ><el-tag>{{ status[row.status] || row.status }}</el-tag></template
        >
      </el-table-column>
      <el-table-column prop="reason" label="事由" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="提交时间" min-width="170" />
    </el-table>
    <el-empty v-if="!loading && !error && !rows.length" description="暂无办公资产需求" />
  </ContentWrap>
  <DemandForm ref="formRef" @success="load" />
  <StockCandidateDialog ref="candidateRef" @success="load" />
</template>

<script setup lang="ts">
import * as ProcurementApi from '@/api/eam/procurement'
import DemandForm from './DemandForm.vue'
import StockCandidateDialog from './StockCandidateDialog.vue'

defineOptions({ name: 'EamDemand' })
const loading = ref(false)
const error = ref('')
const rows = ref<ProcurementApi.DemandVO[]>([])
const formRef = ref()
const candidateRef = ref()
const status: Record<number, string> = {
  0: '草稿',
  1: '审批中',
  2: '已通过',
  3: '已驳回',
  4: '已取消',
  5: '履约中',
  6: '已完成'
}
const remaining = (item: ProcurementApi.DemandItemVO) =>
  item.quantity - (item.reservedQuantity || 0) - (item.purchasedQuantity || 0)
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    rows.value = await ProcurementApi.getDemandList()
  } catch (e: any) {
    error.value = e?.msg || e?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
