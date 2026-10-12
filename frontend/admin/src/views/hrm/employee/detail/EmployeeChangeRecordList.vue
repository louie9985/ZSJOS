<template>
  <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon
    ><el-button link type="primary" @click="getList">重试</el-button></el-alert
  >
  <el-row justify="end" class="mb-12px"
    ><el-button v-hasPermi="['hrm:employee:update']" type="primary" plain @click="newPlan"
      >新增人事计划</el-button
    ></el-row
  >
  <el-tabs v-model="tab">
    <el-tab-pane label="待处理计划" name="plans" />
    <el-tab-pane label="实际异动历史" name="history" />
    <el-tab-pane label="档案纠错与已关闭计划" name="audit" />
  </el-tabs>
  <el-table v-loading="loading" :data="visibleRows" stripe>
    <el-table-column label="类别" min-width="120"
      ><template #default="{ row }">{{
        commandOptions.find((x) => x.value === row.type)?.label || row.type
      }}</template></el-table-column
    >
    <el-table-column label="处理状态" min-width="120"
      ><template #default="{ row }"
        ><el-tag :type="row.state === 'PENDING' && overdue(row) ? 'warning' : 'info'">{{
          stateLabel(row)
        }}</el-tag></template
      ></el-table-column
    >
    <el-table-column label="计划日期" min-width="120"
      ><template #default="{ row }">{{ date(row.plannedTime) }}</template></el-table-column
    >
    <el-table-column label="实际发生日期" min-width="120"
      ><template #default="{ row }">{{ date(row.actualTime) }}</template></el-table-column
    >
    <el-table-column label="原部门" prop="oldDeptName" min-width="120" />
    <el-table-column label="新部门" prop="newDeptName" min-width="120" />
    <el-table-column label="原岗位" prop="oldPostName" min-width="120" />
    <el-table-column label="新岗位" prop="newPostName" min-width="120" />
    <el-table-column label="修改原因" prop="remark" min-width="180" show-overflow-tooltip />
    <el-table-column label="操作" width="220" fixed="right"
      ><template #default="{ row }">
        <template v-if="row.state === 'PENDING'"
          ><el-button
            v-hasPermi="['hrm:employee:update']"
            link
            type="primary"
            @click="edit(row, 'PLAN')"
            >核对/改期</el-button
          ><el-button
            v-hasPermi="['hrm:employee:update']"
            link
            type="primary"
            @click="edit(row, 'BUSINESS')"
            >确认发生</el-button
          ><el-button v-hasPermi="['hrm:employee:update']" link type="danger" @click="cancel(row)"
            >取消</el-button
          ></template
        >
      </template></el-table-column
    >
  </el-table>
  <EmployeeForm ref="formRef" @success="success" />
</template>
<script lang="ts" setup>
import dayjs from 'dayjs'
import { ElMessageBox } from 'element-plus'
import type { HrmEmployeeVO } from '@/api/hrm/employee'
import * as EmployeeApi from '@/api/hrm/employee'
import * as ChangeRecordApi from '@/api/hrm/employee/change-record'
import EmployeeForm from '../EmployeeForm.vue'
defineOptions({ name: 'HrmEmployeeChangeRecordList' })
const props = defineProps<{ employee: HrmEmployeeVO; employeeId: number }>()
const emit = defineEmits<{ success: [] }>()
const loading = ref(false)
const error = ref('')
const tab = ref('plans')
const list = ref<ChangeRecordApi.HrmEmployeeChangeRecordVO[]>([])
const commandOptions = ref<Array<{ value: number; label: string }>>([])
const formRef = ref<InstanceType<typeof EmployeeForm>>()
const visibleRows = computed(() =>
  list.value.filter((row) =>
    tab.value === 'plans'
      ? row.state === 'PENDING'
      : tab.value === 'history'
        ? row.state === 'APPLIED' && row.changeMode === 'BUSINESS'
        : row.state !== 'PENDING' && !(row.state === 'APPLIED' && row.changeMode === 'BUSINESS')
  )
)
const date = (value?: number) => (value ? dayjs(value).format('YYYY-MM-DD') : '-')
const overdue = (row: ChangeRecordApi.HrmEmployeeChangeRecordVO) =>
  row.plannedTime && dayjs(row.plannedTime).isBefore(dayjs(), 'day')
const stateLabel = (row: ChangeRecordApi.HrmEmployeeChangeRecordVO) =>
  row.state === 'PENDING'
    ? overdue(row)
      ? '逾期待处理'
      : '待人工处理'
    : row.state === 'APPLIED'
      ? row.changeMode === 'CORRECTION'
        ? '档案纠错'
        : '已实际发生'
      : row.state === 'CONFIRMED'
        ? '已确认'
        : row.state === 'CANCELLED'
          ? '已取消'
          : '已被修订'
async function getList() {
  loading.value = true
  error.value = ''
  try {
    ;[list.value, commandOptions.value] = await Promise.all([
      ChangeRecordApi.getEmployeeChangeRecordList(props.employeeId),
      ChangeRecordApi.getCommandOptions()
    ])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '计划与异动记录加载失败'
  } finally {
    loading.value = false
  }
}
function newPlan() {
  formRef.value?.open('update', props.employeeId, {
    changeMode: 'PLAN',
    changeType: 12,
    changeReason: ''
  })
}
async function edit(row: ChangeRecordApi.HrmEmployeeChangeRecordVO, mode: 'PLAN' | 'BUSINESS') {
  const current = await EmployeeApi.getEmployee(props.employeeId)
  if (!row.beforeSnapshot && mode === 'BUSINESS') {
    await ElMessageBox.alert(
      '旧计划没有完整原始快照，请先选择“核对/改期”，核对并保存后再确认发生。',
      '请先核对'
    )
    return
  }
  if (row.beforeSnapshot) {
    const before = JSON.parse(row.beforeSnapshot) as Partial<HrmEmployeeVO>
    const keys = [
      'type',
      'status',
      'entryStatus',
      'probation',
      'deptId',
      'leaderEmployeeId',
      'postName',
      'postLevel',
      'workAddress'
    ] as const
    const labels = {
      type: '聘用形式',
      status: '员工状态',
      entryStatus: '入职状态',
      probation: '试用期',
      deptId: '部门',
      leaderEmployeeId: '直属上级',
      postName: '岗位',
      postLevel: '职级',
      workAddress: '工作地点'
    }
    const differences = keys.filter((key) => (before[key] ?? null) !== (current[key] ?? null))
    if (differences.length) {
      await ElMessageBox.alert(
        `计划建立后以下字段已改变：${differences.map((key) => labels[key]).join('、')}。请核对当前值与计划目标；${mode === 'BUSINESS' ? '需要先重新保存计划，不能直接覆盖。' : '保存后以本次核对结果为准。'}`,
        '档案与计划存在差异'
      )
      if (mode === 'BUSINESS') return
    }
  }
  const target: Partial<HrmEmployeeVO> = row.afterSnapshot
    ? JSON.parse(row.afterSnapshot)
    : {
        deptId: row.newDeptId,
        postName: row.newPostName,
        postLevel: row.newPostLevel,
        leaderEmployeeId: row.newLeaderEmployeeId,
        workAddress: row.newWorkAddress
      }
  for (const key of ['entryTime', 'regularTime', 'leaveTime'] as const)
    if (target[key] != null) target[key] = dayjs(target[key]).valueOf()
  if (row.type === 4) target.status = 1
  if (row.type === 11) target.entryStatus = 4
  formRef.value?.open('update', props.employeeId, {
    ...target,
    changeMode: mode,
    changeType: row.type,
    planId: row.id,
    planVersion: row.version,
    actualTime: mode === 'BUSINESS' ? dayjs().startOf('day').valueOf() : undefined,
    plannedTime: row.plannedTime,
    changeReason: row.remark || ''
  })
}
async function cancel(row: ChangeRecordApi.HrmEmployeeChangeRecordVO) {
  try {
    const { value } = await ElMessageBox.prompt('请填写取消原因', '取消计划', {
      inputValidator: (value) => !!value?.trim() || '请填写原因'
    })
    const employee = await EmployeeApi.getEmployee(props.employeeId)
    await ChangeRecordApi.cancelPlan({
      id: row.id!,
      employeeId: props.employeeId,
      version: row.version!,
      employeeVersion: employee.version!,
      operationKey: crypto.randomUUID(),
      reason: value
    })
    await success()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close')
      error.value = e instanceof Error ? e.message : '取消失败，请刷新后重试'
  }
}
async function success() {
  await getList()
  emit('success')
}
defineExpose({ getList })
onMounted(getList)
</script>
