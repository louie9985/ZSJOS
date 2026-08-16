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
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-160px">
          <el-option label="审批中" :value="0" />
          <el-option label="已报废" :value="1" />
          <el-option label="已驳回" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button v-hasPermi="['eam:scrap:create']" type="primary" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" /> 申请报废
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="单据编号" prop="no" min-width="150" fixed="left" />
      <el-table-column label="资产编号" prop="assetCode" min-width="140" />
      <el-table-column label="资产名称" prop="assetName" min-width="150" show-overflow-tooltip />
      <el-table-column label="报废原因" min-width="130">
        <template #default="{ row }">
          <dict-tag :type="'eam_scrap_reason'" :value="row.reasonType" />
        </template>
      </el-table-column>
      <el-table-column label="详细说明" prop="reason" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" min-width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="scrapStatusType(row.status)" size="small">
            {{ scrapStatusName(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请人" prop="applyUserName" min-width="100" />
      <el-table-column label="申请时间" prop="applyTime" min-width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === ScrapStatus.APPROVING">
            <el-button
              v-hasPermi="['eam:scrap:update']"
              link
              type="success"
              @click="handleApprove(row.id)"
            >
              通过
            </el-button>
            <el-button
              v-hasPermi="['eam:scrap:update']"
              link
              type="danger"
              @click="handleReject(row.id)"
            >
              驳回
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

  <ScrapForm ref="formRef" @success="getList" />
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import * as ScrapApi from '@/api/eam/scrap'
import { ScrapStatus } from '@/api/eam/scrap'
import ScrapForm from './ScrapForm.vue'

defineOptions({ name: 'EamScrap' })

const message = useMessage()

const loading = ref(false)
const total = ref(0)
const list = ref<ScrapApi.ScrapVO[]>([])
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  no: undefined,
  status: undefined,
  assetId: undefined
})

const scrapStatusName = (status: number) =>
  ({ 0: '审批中', 1: '已报废', 2: '已驳回' })[status] ?? '未知'
const scrapStatusType = (status: number) =>
  ({ 0: 'warning', 1: 'danger', 2: 'info' })[status] ?? 'info'

const getList = async () => {
  loading.value = true
  try {
    const data = await ScrapApi.getScrapPage(queryParams)
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
    await message.confirm('确认通过该报废申请？资产将进入已报废终态，不可恢复')
    await ScrapApi.approveScrap(id)
    message.success('已报废')
    await getList()
  } catch {}
}

const handleReject = async (id: number) => {
  try {
    const { value } = await message.prompt('请输入驳回原因', '驳回报废申请')
    await ScrapApi.rejectScrap(id, value)
    message.success('已驳回，资产恢复原状态')
    await getList()
  } catch {}
}

onMounted(() => {
  getList()
})
</script>
