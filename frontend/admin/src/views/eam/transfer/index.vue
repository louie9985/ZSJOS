<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="80px"
    >
      <el-form-item label="单据编号" prop="no">
        <el-input
          v-model="queryParams.no"
          placeholder="请输入单据编号"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="流转类型" prop="type">
        <el-select v-model="queryParams.type" placeholder="全部" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions('eam_transfer_type')"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions('eam_transfer_status')"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button v-hasPermi="['eam:transfer:create']" type="primary" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" /> 发起流转
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="单据编号" prop="no" min-width="150" fixed="left" />
      <el-table-column label="类型" min-width="90" align="center">
        <template #default="{ row }">
          <dict-tag :type="'eam_transfer_type'" :value="row.type" />
        </template>
      </el-table-column>
      <el-table-column label="资产编号" prop="assetCode" min-width="140" />
      <el-table-column label="资产名称" prop="assetName" min-width="160" show-overflow-tooltip />
      <el-table-column label="转出员工" prop="fromEmployeeName" min-width="100" />
      <el-table-column label="接收员工" prop="toEmployeeName" min-width="100" />
      <el-table-column label="状态" min-width="90" align="center">
        <template #default="{ row }">
          <dict-tag :type="'eam_transfer_status'" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="申请人" prop="applyUserName" min-width="100" />
      <el-table-column
        label="申请时间"
        prop="applyTime"
        min-width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="流程实例" prop="processInstanceId" min-width="190" show-overflow-tooltip />
      <el-table-column label="验收结果" min-width="100" align="center">
        <template #default="{ row }">{{ inspectionLabels[row.inspectionResult] || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === TransferStatus.APPROVING">
            <el-button link type="primary" @click="openApprovalCenter">审批中心</el-button>
            <el-button
              v-hasPermi="['eam:transfer:cancel']"
              link
              type="info"
              @click="handleCancel(row.id)"
            >
              取消
            </el-button>
          </template>
          <el-button
            v-else-if="row.status === TransferStatus.PENDING_INSPECTION"
            v-hasPermi="['eam:transfer:inspect']"
            link type="primary" @click="handleInspect(row.id)"
          >验收</el-button>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <TransferForm ref="formRef" @success="getList" />
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import { getIntDictOptions } from '@/utils/dict'
import * as TransferApi from '@/api/eam/transfer'
import { TransferStatus } from '@/api/eam/transfer'
import TransferForm from './TransferForm.vue'

defineOptions({ name: 'EamTransfer' })

const message = useMessage()
const router = useRouter()
const inspectionLabels: Record<number, string> = { 1: '完好', 2: '损坏', 3: '缺件/遗失', 4: '不符驳回' }

const loading = ref(false)
const total = ref(0)
const list = ref<TransferApi.TransferVO[]>([])
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  no: undefined,
  type: undefined,
  status: undefined,
  assetId: undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await TransferApi.getTransferPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const formRef = ref()
const openForm = () => {
  formRef.value.open()
}

const openApprovalCenter = () => router.push('/bpm/task/todo')

const handleInspect = async (id: number) => {
  try {
    const result = await ElMessageBox.prompt('请输入验收结果：1 完好，2 损坏，3 缺件/遗失，4 不符驳回', '资产验收', {
      inputPattern: /^[1-4]$/,
      inputErrorMessage: '请输入 1 至 4'
    })
    const remark = await message.prompt('请输入验收备注（可选）', '验收备注')
    await TransferApi.inspectTransfer(id, { result: Number(result.value), remark: remark.value })
    message.success('验收结果已提交')
    await getList()
  } catch {}
}

const handleCancel = async (id: number) => {
  try {
    await message.confirm('确认取消该流转单？')
    await TransferApi.cancelTransfer(id)
    message.success('已取消')
    await getList()
  } catch {}
}

onMounted(() => {
  getList()
})
</script>
