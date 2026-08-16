<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px">
      <el-form-item>
        <el-button v-hasPermi="['eam:repair:create']" type="primary" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" /> 送修登记
        </el-button>
        <el-button @click="getList"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="资产编号" prop="assetCode" min-width="140" fixed="left" />
      <el-table-column label="资产名称" prop="assetName" min-width="150" show-overflow-tooltip />
      <el-table-column label="故障描述" prop="faultDesc" min-width="200" show-overflow-tooltip />
      <el-table-column label="维修方" prop="repairVendor" min-width="140" show-overflow-tooltip />
      <el-table-column label="费用" prop="cost" min-width="100" align="right">
        <template #default="{ row }">{{ row.cost != null ? `¥${row.cost}` : '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.endTime ? 'success' : 'warning'" size="small">
            {{ row.endTime ? '已完成' : '维修中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="送修时间" prop="startTime" min-width="170" :formatter="dateFormatter" />
      <el-table-column label="完成时间" prop="endTime" min-width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.endTime"
            v-hasPermi="['eam:repair:update']"
            link
            type="success"
            @click="openFinish(row)"
          >
            维修完成
          </el-button>
          <el-button
            v-hasPermi="['eam:repair:delete']"
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

  <RepairForm ref="formRef" @success="getList" />

  <!-- 维修完成 -->
  <Dialog v-model="finishVisible" title="维修完成" width="520px">
    <el-form ref="finishFormRef" :model="finishForm" label-width="100px">
      <el-form-item label="资产">
        <span>{{ current.assetCode }} {{ current.assetName }}</span>
      </el-form-item>
      <el-form-item label="完成时间" prop="endTime">
        <el-date-picker
          v-model="finishForm.endTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="!w-full"
          placeholder="留空则取当前时间"
        />
      </el-form-item>
      <el-form-item label="维修费用" prop="cost">
        <el-input-number
          v-model="finishForm.cost"
          :min="0"
          :precision="2"
          :controls="false"
          class="!w-full"
          placeholder="请输入维修费用"
        />
      </el-form-item>
      <el-form-item label="维修结果" prop="result">
        <el-input
          v-model="finishForm.result"
          type="textarea"
          :rows="2"
          placeholder="如 已更换屏幕"
        />
      </el-form-item>
      <el-alert
        title="确认后资产将恢复到送修前的状态"
        type="info"
        :closable="false"
        show-icon
      />
    </el-form>
    <template #footer>
      <el-button :disabled="finishLoading" type="primary" @click="submitFinish">确 定</el-button>
      <el-button @click="finishVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import * as RepairApi from '@/api/eam/repair'
import RepairForm from './RepairForm.vue'

defineOptions({ name: 'EamRepair' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(false)
const total = ref(0)
const list = ref<RepairApi.RepairVO[]>([])
const queryFormRef = ref()
const queryParams = reactive({ pageNo: 1, pageSize: 10, assetId: undefined })

const getList = async () => {
  loading.value = true
  try {
    const data = await RepairApi.getRepairPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const formRef = ref()
const openForm = () => {
  formRef.value.open()
}

const finishVisible = ref(false)
const finishLoading = ref(false)
const finishFormRef = ref()
const current = ref<RepairApi.RepairVO>({} as RepairApi.RepairVO)
const finishForm = ref<RepairApi.RepairFinishVO>({ id: 0 })

const openFinish = (row: RepairApi.RepairVO) => {
  current.value = row
  finishForm.value = { id: row.id!, cost: row.cost, result: '' }
  finishVisible.value = true
}

const submitFinish = async () => {
  finishLoading.value = true
  try {
    await RepairApi.finishRepair(finishForm.value)
    message.success('维修已完成')
    finishVisible.value = false
    await getList()
  } finally {
    finishLoading.value = false
  }
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await RepairApi.deleteRepair(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(() => {
  getList()
})
</script>
