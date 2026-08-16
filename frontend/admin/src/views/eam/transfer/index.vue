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
      <el-table-column label="转出人" prop="fromUserName" min-width="100" />
      <el-table-column label="接收人" prop="toUserName" min-width="100" />
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
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <!-- 仅审批中的单据可审批或取消，其余状态为终态 -->
          <template v-if="row.status === TransferStatus.APPROVING">
            <el-button
              v-hasPermi="['eam:transfer:update']"
              link
              type="success"
              @click="handleApprove(row.id)"
            >
              通过
            </el-button>
            <el-button
              v-hasPermi="['eam:transfer:update']"
              link
              type="danger"
              @click="handleReject(row.id)"
            >
              驳回
            </el-button>
            <el-button
              v-hasPermi="['eam:transfer:update']"
              link
              type="info"
              @click="handleCancel(row.id)"
            >
              取消
            </el-button>
          </template>
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

const handleApprove = async (id: number) => {
  try {
    await message.confirm('确认审批通过该流转单？通过后资产状态与归属将立即变更')
    await TransferApi.approveTransfer(id)
    message.success('审批通过')
    await getList()
  } catch {}
}

const handleReject = async (id: number) => {
  try {
    const { value } = await message.prompt('请输入驳回原因', '驳回流转单')
    await TransferApi.rejectTransfer(id, value)
    message.success('已驳回')
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
