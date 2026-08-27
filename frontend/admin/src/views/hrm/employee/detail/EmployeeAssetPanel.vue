<template>
  <ContentWrap title="个人资产">
    <el-alert
      v-if="summary?.offboardingUncleared"
      class="mb-4"
      title="该员工存在未完成的离职资产结清任务"
      type="warning"
      show-icon
      :closable="false"
    />
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-result v-else-if="error" icon="error" title="个人资产加载失败" :sub-title="error">
      <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
    </el-result>
    <template v-else>
      <el-row :gutter="12" class="mb-4">
        <el-col :xs="12" :sm="6"
          ><el-statistic title="持有资产" :value="summary?.items.length || 0"
        /></el-col>
        <el-col :xs="12" :sm="6"
          ><el-statistic title="待签收" :value="summary?.pendingSignCount || 0"
        /></el-col>
        <el-col :xs="12" :sm="6"
          ><el-statistic title="待归还验收" :value="summary?.pendingReturnCount || 0"
        /></el-col>
        <el-col :xs="12" :sm="6"
          ><el-statistic title="资产任务" :value="summary?.tasks.length || 0"
        /></el-col>
      </el-row>
      <el-table v-if="summary?.items.length" :data="summary.items" stripe>
        <el-table-column label="资产" prop="name" min-width="180" />
        <el-table-column label="资产编号" prop="assetCode" min-width="140" />
        <el-table-column label="数量" min-width="100">
          <template #default="{ row }">{{ row.quantity }} {{ row.unit || '' }}</template>
        </el-table-column>
        <el-table-column label="类型" min-width="100">
          <template #default="{ row }">{{
            row.itemType === 'SERIALIZED_ASSET' ? '单件资产' : '持有明细'
          }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag v-if="row.itemType.endsWith('HOLDING')">{{
              holdingStatus[row.status] || row.status
            }}</el-tag>
            <dict-tag v-else :type="DICT_TYPE.EAM_ASSET_STATUS" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.holdingId && row.status === 2"
              v-hasPermi="['eam:employee-asset:inspect']"
              link
              type="primary"
              @click="openInspection(row)"
            >
              退还验收
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="该员工暂无个人资产" />
      <el-divider content-position="left">生命周期资产任务</el-divider>
      <el-table v-if="summary?.tasks.length" :data="summary.tasks" stripe>
        <el-table-column label="任务类型" min-width="120">
          <template #default="{ row }">{{ taskType[row.type] || '未知任务' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100"
          ><template #default="{ row }">{{
            taskStatus[row.status] || row.status
          }}</template></el-table-column
        >
        <el-table-column label="计划离职时间" prop="plannedLeaveTime" min-width="160" />
        <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0 && row.type === 1"
              v-hasPermi="['eam:employee-asset:task']"
              link
              type="primary"
              @click="openProvisioning(row)"
            >
              填写配资需求
            </el-button>
            <el-button
              v-else-if="row.status === 0 && (row.type === 2 || row.type === 3)"
              v-hasPermi="['eam:employee-asset:task']"
              link
              type="primary"
              @click="openReview(row)"
            >
              资产复核
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无入职配资、异动复核或离职结清任务" />
    </template>
  </ContentWrap>

  <DemandForm ref="demandFormRef" @success="load" />
  <Dialog
    v-model="reviewVisible"
    :title="reviewTask?.type === 3 ? '离职资产结清' : '员工资产复核'"
    width="900px"
  >
    <el-table
      v-loading="reviewLoading"
      :data="reviewItems"
      empty-text="该员工当前没有需要处理的资产"
    >
      <el-table-column label="资产" prop="assetNameSnapshot" min-width="200" />
      <el-table-column label="处理方式" min-width="150">
        <template #default="{ row }">
          <el-select v-model="row.action" placeholder="请选择">
            <el-option label="随人" :value="1" />
            <el-option label="退回" :value="2" />
            <el-option label="转交" :value="3" />
            <el-option label="不调整" :value="4" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="转交员工" min-width="220">
        <template #default="{ row }">
          <HrmEmployeeSelect
            v-if="row.action === 3"
            v-model="row.transferToEmployeeId"
            :selectable="employeeSelectable"
            placeholder="选择已绑定账号的员工"
            @change="(employee) => changeTransferEmployee(row, employee)"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="说明" min-width="180">
        <template #default="{ row }"><el-input v-model="row.remark" /></template>
      </el-table-column>
    </el-table>
    <el-input
      v-model="reviewRemark"
      class="mt-4"
      type="textarea"
      :rows="2"
      placeholder="可填写本次复核说明"
    />
    <template #footer>
      <el-button @click="reviewVisible = false">取消</el-button>
      <el-button type="primary" :loading="reviewSubmitting" @click="submitReview"
        >提交统一审批中心</el-button
      >
    </template>
  </Dialog>

  <Dialog v-model="inspectionVisible" title="员工资产退还验收" width="520px">
    <el-form label-width="90px">
      <el-form-item label="资产">
        <span>{{ inspectionItem?.name }}</span>
      </el-form-item>
      <el-form-item label="验收结果" required>
        <el-select v-model="inspectionResult" class="!w-full" placeholder="请选择验收结果">
          <el-option label="完好" :value="1" />
          <el-option label="损坏" :value="2" />
          <el-option label="缺件 / 遗失" :value="3" />
          <el-option label="不符驳回" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="验收说明">
        <el-input v-model="inspectionRemark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="inspectionVisible = false">取消</el-button>
      <el-button type="primary" :loading="inspectionSubmitting" @click="submitInspection">
        确认验收
      </el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as EmployeeAssetApi from '@/api/eam/employeeAsset'
import type { HrmEmployeeVO } from '@/api/hrm/employee'
import DemandForm from '@/views/eam/demand/DemandForm.vue'
import HrmEmployeeSelect from '@/views/hrm/employee/components/HrmEmployeeSelect.vue'
import { DICT_TYPE } from '@/utils/dict'

const props = defineProps<{ employeeId: number }>()
const message = useMessage()
const loading = ref(false)
const error = ref('')
const summary = ref<EmployeeAssetApi.EmployeeAssetSummaryVO>()
const demandFormRef = ref<InstanceType<typeof DemandForm>>()
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const reviewSubmitting = ref(false)
const reviewTask = ref<EmployeeAssetApi.EmployeeAssetTaskVO>()
type ReviewItem = EmployeeAssetApi.EmployeeAssetTaskItemVO
const reviewItems = ref<ReviewItem[]>([])
const reviewRemark = ref('')
const inspectionVisible = ref(false)
const inspectionSubmitting = ref(false)
const inspectionItem = ref<EmployeeAssetApi.EmployeeAssetItemVO>()
const inspectionResult = ref<number>()
const inspectionRemark = ref('')
const taskType: Record<number, string> = {
  1: '入职配资',
  2: '异动复核',
  3: '离职结清',
  4: '取消记录'
}
const taskStatus: Record<number, string> = {
  0: '草稿',
  1: '审批中',
  2: '已通过',
  3: '已驳回',
  4: '已取消',
  5: '履行中',
  6: '已完成'
}
const holdingStatus: Record<number, string> = {
  0: '待签收',
  1: '持有中',
  2: '待退还验收',
  3: '已退还',
  4: '遗失'
}

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    summary.value = await EmployeeAssetApi.getByEmployee(props.employeeId)
  } catch (e: any) {
    error.value = e?.msg || e?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

const openProvisioning = (task: EmployeeAssetApi.EmployeeAssetTaskVO) => {
  demandFormRef.value?.open({ employeeId: props.employeeId, taskId: task.id })
}

const openReview = async (task: EmployeeAssetApi.EmployeeAssetTaskVO) => {
  reviewVisible.value = true
  reviewLoading.value = true
  reviewTask.value = task
  reviewItems.value = []
  reviewRemark.value = ''
  try {
    const detail = await EmployeeAssetApi.getTask(task.id)
    reviewTask.value = detail
    reviewItems.value = (detail.items || []).map((item) => ({ ...item }))
  } catch (e: any) {
    message.error(e?.msg || e?.message || '资产任务加载失败')
    reviewVisible.value = false
  } finally {
    reviewLoading.value = false
  }
}

const employeeSelectable = (employee: HrmEmployeeVO) => Boolean(employee.userId)
const changeTransferEmployee = (item: ReviewItem, employee?: HrmEmployeeVO | HrmEmployeeVO[]) => {
  const selected = Array.isArray(employee) ? employee[0] : employee
  item.transferToEmployeeId = selected?.id
}

const submitReview = async () => {
  if (!reviewTask.value) return
  if (reviewItems.value.some((item) => !item.action)) {
    message.warning('请为每项资产选择处理方式')
    return
  }
  if (reviewItems.value.some((item) => item.action === 3 && !item.transferToEmployeeId)) {
    message.warning('请选择资产转交员工')
    return
  }
  reviewSubmitting.value = true
  try {
    await EmployeeAssetApi.submitReview(reviewTask.value.id, {
      items: reviewItems.value.map((item) => ({
        id: item.id,
        action: item.action!,
        transferToEmployeeId: item.action === 3 ? item.transferToEmployeeId : undefined,
        remark: item.remark
      })),
      remark: reviewRemark.value
    })
    message.success('资产复核已提交统一审批中心')
    reviewVisible.value = false
    await load()
  } finally {
    reviewSubmitting.value = false
  }
}

const openInspection = (item: EmployeeAssetApi.EmployeeAssetItemVO) => {
  inspectionItem.value = item
  inspectionResult.value = undefined
  inspectionRemark.value = ''
  inspectionVisible.value = true
}

const submitInspection = async () => {
  if (!inspectionItem.value?.holdingId || !inspectionResult.value) {
    message.warning('请选择验收结果')
    return
  }
  inspectionSubmitting.value = true
  try {
    await EmployeeAssetApi.inspectReturn(inspectionItem.value.holdingId, {
      result: inspectionResult.value,
      remark: inspectionRemark.value
    })
    message.success('退还验收已完成')
    inspectionVisible.value = false
    await load()
  } finally {
    inspectionSubmitting.value = false
  }
}

onMounted(load)
watch(() => props.employeeId, load)
</script>
