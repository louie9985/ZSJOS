<template>
  <ContentWrap>
    <el-descriptions :column="4">
      <el-descriptions-item label="工号">{{ employee.jobNumber || '-' }}</el-descriptions-item>
      <el-descriptions-item label="所属部门">{{ employee.deptName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="职位名称">{{ employee.postName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="岗位职级">{{ employee.postLevel || '-' }}</el-descriptions-item>
      <el-descriptions-item label="直属上级">
        {{ employee.leaderEmployeeName || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="入职状态">
        <span>{{
          employee.entryStatusLabel ||
          (employee.entryStatus == null ? '-' : `历史代码 ${employee.entryStatus}`)
        }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="员工状态">
        <span>{{
          employee.statusLabel || (employee.status == null ? '-' : `历史代码 ${employee.status}`)
        }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="聘用形式">
        <span>{{
          employee.typeLabel || (employee.type == null ? '-' : `历史代码 ${employee.type}`)
        }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="入职时间">
        {{ formatHrmDateTime(employee.entryTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="试用期">
        {{ employee.probation != null ? `${employee.probation} 个月` : '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="计划转正日期">{{
        formatHrmDateTime(employee.plannedRegularTime)
      }}</el-descriptions-item>
      <el-descriptions-item label="计划离职日期">{{
        formatHrmDateTime(employee.plannedLeaveTime)
      }}</el-descriptions-item>
      <el-descriptions-item v-if="employee.legacyDateSource" label="日期来源">{{
        employee.legacyDateSource
      }}</el-descriptions-item>
      <el-descriptions-item label="实际转正日期">
        {{ formatHrmDateTime(employee.regularTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="实际离职日期">
        {{ formatHrmDateTime(employee.leaveTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="工作城市">{{ employee.workCity || '-' }}</el-descriptions-item>
      <el-descriptions-item label="工作地点">
        {{ employee.workAddress || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="详细地址" :span="2">
        {{ employee.workDetailAddress || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="招聘渠道">
        {{ employee.channelName || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="司龄起算时间">
        {{ formatHrmDateTime(employee.companyAgeStartTime) }}
      </el-descriptions-item>
      <el-descriptions-item label="司龄">
        {{ employee.companyAge != null ? `${employee.companyAge} 年` : '-' }}
      </el-descriptions-item>
    </el-descriptions>
  </ContentWrap>

  <ContentWrap title="异动记录">
    <EmployeeChangeRecordList
      ref="changeRecordListRef"
      :employee="employee"
      :employee-id="employeeId"
      @success="emit('refresh')"
    />
  </ContentWrap>
  <EmployeeQuitInfo ref="quitInfoRef" :employee-id="employeeId" @edit="emit('edit-quit')" />
</template>

<script lang="ts" setup>
import type { HrmEmployeeVO } from '@/api/hrm/employee'
import { formatHrmDateTime } from '@/views/hrm/utils/format'
import EmployeeChangeRecordList from './EmployeeChangeRecordList.vue'
import EmployeeQuitInfo from './EmployeeQuitInfo.vue'

defineOptions({ name: 'HrmEmployeePostInfo' })

defineProps<{
  employee: HrmEmployeeVO
  employeeId: number
}>()
const emit = defineEmits<{
  'edit-quit': []
  refresh: []
}>() // 定义离职编辑与刷新事件

const changeRecordListRef = ref<InstanceType<typeof EmployeeChangeRecordList>>() // 员工异动列表 Ref
const quitInfoRef = ref<InstanceType<typeof EmployeeQuitInfo>>() // 员工离职信息 Ref

/** 刷新员工异动记录 */
function refreshChangeRecordList() {
  return changeRecordListRef.value?.getList()
}

/** 刷新员工离职信息 */
function refreshQuitInfo() {
  return quitInfoRef.value?.getQuitInfo()
}
defineExpose({ refreshChangeRecordList, refreshQuitInfo }) // 提供员工异动和离职信息刷新方法
</script>
