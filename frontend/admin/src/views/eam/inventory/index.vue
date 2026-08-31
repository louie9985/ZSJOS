<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="80px"
    >
      <el-form-item label="盘点名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入盘点名称"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-160px">
          <el-option label="进行中" :value="0" />
          <el-option label="已完成" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button v-hasPermi="['eam:inventory:create']" type="primary" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" /> 发起盘点
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="盘点单号" prop="no" min-width="150" fixed="left" />
      <el-table-column label="盘点名称" prop="name" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" min-width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
            {{ row.status === 1 ? '已完成' : '进行中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="progressOf(row)"
            :status="row.status === 1 ? 'success' : undefined"
          />
          <span class="text-xs text-gray-500">
            {{ row.checkedCount }} / {{ row.totalCount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="正常" prop="normalCount" width="80" align="center" />
      <el-table-column label="异常" width="80" align="center">
        <template #default="{ row }">
          <span :class="row.abnormalCount > 0 ? 'text-red-500 font-medium' : ''">
            {{ row.abnormalCount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="开始时间"
        prop="startTime"
        min-width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['eam:inventory:query']"
            link
            type="primary"
            @click="openDetail(row)"
          >
            盘点明细
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-hasPermi="['eam:inventory:update']"
            link
            type="success"
            @click="handleFinish(row.id)"
          >
            完成
          </el-button>
          <el-button
            v-hasPermi="['eam:inventory:delete']"
            link
            type="danger"
            @click="handleDelete(row.id)"
          >
            删除
          </el-button>
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

  <InventoryForm ref="formRef" @success="getList" />
  <InventoryDetail ref="detailRef" @success="getList" />
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import * as InventoryApi from '@/api/eam/inventory'
import InventoryForm from './InventoryForm.vue'
import InventoryDetail from './InventoryDetail.vue'

defineOptions({ name: 'EamInventory' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(false)
const total = ref(0)
const list = ref<InventoryApi.InventoryVO[]>([])
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  status: undefined
})

const progressOf = (row: InventoryApi.InventoryVO) => {
  if (!row.totalCount) {
    return 0
  }
  return Math.round(((row.checkedCount ?? 0) / row.totalCount) * 100)
}

const getList = async () => {
  loading.value = true
  try {
    const data = await InventoryApi.getInventoryPage(queryParams)
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

const detailRef = ref()
const openDetail = (row: InventoryApi.InventoryVO) => {
  detailRef.value.open(row)
}

const handleFinish = async (id: number) => {
  try {
    await message.confirm('确认完成该盘点？完成后不可再录入结果')
    await InventoryApi.finishInventory(id)
    message.success('盘点已完成')
    await getList()
  } catch {}
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await InventoryApi.deleteInventory(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(() => {
  getList()
})
</script>
