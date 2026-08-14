<template>
  <ContentWrap>
    <el-form :model="query" inline>
      <el-form-item label="模板"><el-select v-model="query.templateId" clearable filterable><el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="周期"><el-select v-model="query.periodType" clearable><el-option v-for="(label, value) in periodLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable><el-option v-for="(label, value) in planStatusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="负责人"><el-select v-model="query.ownerUserId" clearable filterable><el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id" /></el-select></el-form-item>
      <el-form-item label="部门"><el-select v-model="query.ownerDeptId" clearable filterable><el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button><el-button v-hasPermi="['zsjos:work-plan:export']" @click="exportTasks">导出任务明细</el-button></el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon><template #default><el-button link type="primary" @click="loadPlans">重试</el-button></template></el-alert>
    <el-empty v-else-if="!plans.length" description="当前范围内暂无工作计划" />
    <el-table v-else :data="plans" row-key="id" @row-click="openDetail">
      <el-table-column prop="title" label="计划名称" min-width="220" />
      <el-table-column label="模板" min-width="150"><template #default="{ row }">{{ templateNames.get(row.templateId) || '-' }}</template></el-table-column>
      <el-table-column label="周期" width="190"><template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column>
      <el-table-column label="负责人" width="120"><template #default="{ row }">{{ userName(row.ownerUserId) }}</template></el-table-column>
      <el-table-column label="任务进度" width="120"><template #default="{ row }">{{ completedCount(row) }}/{{ row.tasks?.length || 0 }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ displayPlanStatus(row) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">详情</el-button></template></el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="loadPlans" />
  </ContentWrap>
  <el-drawer v-model="detailOpen" title="计划监管详情" size="760px">
    <div v-if="detail" v-loading="detailLoading">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="计划名称">{{ detail.title }}</el-descriptions-item><el-descriptions-item label="状态">{{ displayPlanStatus(detail) }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ userName(detail.ownerUserId) }}</el-descriptions-item><el-descriptions-item label="责任部门">{{ deptName(detail.ownerDeptId) }}</el-descriptions-item>
        <el-descriptions-item label="计划目标" :span="2">{{ detail.objective || '-' }}</el-descriptions-item><el-descriptions-item label="重点要求" :span="2">{{ detail.keyRequirements || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">任务责任树</el-divider>
      <el-tree :data="taskTree" node-key="id" default-expand-all :props="{ label: 'title', children: 'children' }">
        <template #default="{ data }"><div class="task-node"><span>{{ data.title }}</span><el-tag size="small" :type="statusType(data.status)">{{ taskStatusLabels[data.status] || data.status }}</el-tag><span>责任人：{{ userName(data.assigneeUserId) }}</span><el-button v-hasPermi="['zsjos:work-plan:assign']" link @click.stop="openTransfer(data)">转派</el-button><el-button v-hasPermi="['zsjos:work-plan:cancel']" link type="danger" @click.stop="cancelTask(data)">取消</el-button></div></template>
      </el-tree>
      <el-divider content-position="left">完成汇报与确认</el-divider>
      <el-collapse><el-collapse-item v-for="task in detail.tasks || []" :key="task.id" :title="task.title"><el-timeline v-if="task.reports?.length"><el-timeline-item v-for="report in task.reports" :key="report.id" :timestamp="report.submittedAt">第 {{ report.revisionNo }} 次汇报：{{ report.completionSummary }}<div v-if="report.confirmationDecision">确认结果：{{ report.confirmationDecision }} {{ report.confirmationComment || '' }}</div></el-timeline-item></el-timeline><el-empty v-else description="暂无完成汇报" :image-size="60" /></el-collapse-item></el-collapse>
      <el-divider content-position="left">计划总结</el-divider><div>{{ detail.summary?.summary || '尚未提交计划总结' }}</div>
      <el-divider content-position="left">变更记录</el-divider><el-timeline><el-timeline-item v-for="change in detail.changes || []" :key="change.id" :timestamp="change.changedAt">{{ change.changeType }}：{{ change.reason }}</el-timeline-item></el-timeline>
      <div class="drawer-footer"><el-button v-if="['draft', 'active'].includes(detail.status)" v-hasPermi="['zsjos:work-plan:cancel']" type="danger" @click="cancelPlan">取消计划</el-button></div>
    </div>
  </el-drawer>
  <el-dialog v-model="transferOpen" title="转派工作任务" width="480px"><el-form label-width="90px"><el-form-item label="新责任人" required><el-select v-model="transferUserId" filterable><el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id" /></el-select></el-form-item><el-form-item label="调整原因" required><el-input v-model="transferReason" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="transferOpen = false">取消</el-button><el-button type="primary" @click="transfer">确认转派</el-button></template></el-dialog>
</template>

<script lang="ts" setup>
import * as DeptApi from '@/api/system/dept'
import * as UserApi from '@/api/system/user'
import * as WorkPlanApi from '@/api/zsjos/workPlan'
import * as ConfigApi from '@/api/zsjos/workPlanConfig'
import { checkPermi } from '@/utils/permission'
import { ElMessageBox } from 'element-plus'
defineOptions({ name: 'ZsjosWorkPlan' })
const message = useMessage(); const query = reactive<WorkPlanApi.WorkPlanPageReqVO>({ pageNo: 1, pageSize: 10 }); const plans = ref<WorkPlanApi.WorkPlanVO[]>([]); const total = ref(0); const loading = ref(false); const error = ref(''); const templates = ref<ConfigApi.WorkPlanTemplateVO[]>([]); const users = ref<UserApi.UserVO[]>([]); const departments = ref<DeptApi.DeptVO[]>([]); const detail = ref<WorkPlanApi.WorkPlanVO>(); const detailOpen = ref(false); const detailLoading = ref(false); const transferOpen = ref(false); const transferTask = ref<WorkPlanApi.WorkTaskVO>(); const transferUserId = ref<number>(); const transferReason = ref('')
const periodLabels: Record<string, string> = { day: '日', week: '周', month: '月', quarter: '季度', year: '年度', custom: '自定义' }; const planStatusLabels: Record<string, string> = { draft: '草稿', active: '进行中', completed: '已完成', cancelled: '已取消' }; const taskStatusLabels: Record<string, string> = { draft: '草稿', pending: '待完成', awaiting_confirmation: '待确认', completed: '已完成', cancelled: '已取消' }
const userNames = computed(() => new Map(users.value.map((item) => [item.id, item.nickname]))); const deptNames = computed(() => new Map(departments.value.map((item) => [item.id, item.name]))); const templateNames = computed(() => new Map(templates.value.map((item) => [item.id, item.name]))); const userName = (id?: number) => (id ? userNames.value.get(id) || `#${id}` : '-'); const deptName = (id?: number) => (id ? deptNames.value.get(id) || `#${id}` : '-'); const completedCount = (plan: WorkPlanApi.WorkPlanVO) => plan.tasks?.filter((task) => ['completed', 'cancelled'].includes(task.status)).length || 0; const displayPlanStatus = (plan: WorkPlanApi.WorkPlanVO) => plan.status === 'active' && plan.summaryReady ? '待总结' : planStatusLabels[plan.status] || plan.status; const statusType = (status: string): 'success' | 'warning' | 'info' | 'primary' => status === 'completed' ? 'success' : status === 'awaiting_confirmation' ? 'warning' : status === 'cancelled' ? 'info' : 'primary'
const taskTree = computed(() => { const nodes = new Map<number, WorkPlanApi.WorkTaskVO & { children: WorkPlanApi.WorkTaskVO[] }>(); (detail.value?.tasks || []).forEach((task) => nodes.set(task.id, { ...task, children: [] })); const roots: Array<WorkPlanApi.WorkTaskVO & { children: WorkPlanApi.WorkTaskVO[] }> = []; nodes.forEach((node) => { const parent = node.parentTaskId ? nodes.get(node.parentTaskId) : undefined; parent ? parent.children.push(node) : roots.push(node) }); return roots })
const loadPlans = async () => { loading.value = true; error.value = ''; try { const page = await WorkPlanApi.getWorkPlanPage(query); plans.value = page.list; total.value = page.total } catch (e: any) { error.value = e?.msg || e?.message || '计划列表加载失败' } finally { loading.value = false } }; const search = () => { query.pageNo = 1; loadPlans() }; const reset = () => { Object.assign(query, { pageNo: 1, pageSize: query.pageSize, templateId: undefined, periodType: undefined, status: undefined, ownerUserId: undefined, ownerDeptId: undefined }); loadPlans() }
const openDetail = async (row: WorkPlanApi.WorkPlanVO) => { detailOpen.value = true; detailLoading.value = true; try { detail.value = await WorkPlanApi.getWorkPlan(row.id) } finally { detailLoading.value = false } }; const askReason = async (title: string) => (await ElMessageBox.prompt('请填写原因，操作将被记录', title, { inputValidator: (value) => Boolean(value?.trim()) || '原因不能为空' })).value.trim(); const cancelPlan = async () => { if (!detail.value) return; await WorkPlanApi.cancelWorkPlan(detail.value.id, detail.value.version, await askReason('取消计划')); message.success('计划已取消'); await openDetail(detail.value); await loadPlans() }; const cancelTask = async (task: WorkPlanApi.WorkTaskVO) => { const reason = await askReason('取消工作任务'); let cascadeChildren = false; if (taskTree.value.some((node) => node.id === task.id && node.children.length)) { try { await ElMessageBox.confirm('是否同时取消所有未完成下级任务？选择“仅当前任务”不会影响下级任务。', '选择取消范围', { confirmButtonText: '同时取消下级任务', cancelButtonText: '仅当前任务', distinguishCancelAndClose: true }); cascadeChildren = true } catch (action) { if (action === 'close') return } } await WorkPlanApi.cancelWorkTask(task.id, task.version, reason, cascadeChildren); message.success(cascadeChildren ? '当前任务及未完成下级任务已取消' : '当前任务已取消'); if (detail.value) await openDetail(detail.value); await loadPlans() }
const openTransfer = (task: WorkPlanApi.WorkTaskVO) => { transferTask.value = task; transferUserId.value = task.assigneeUserId; transferReason.value = ''; transferOpen.value = true }; const transfer = async () => { if (!transferTask.value || !transferUserId.value || !transferReason.value.trim()) return message.warning('请选择责任人并填写原因'); await WorkPlanApi.adjustWorkTask(transferTask.value, transferUserId.value, transferReason.value.trim()); message.success('工作任务已转派'); transferOpen.value = false; if (detail.value) await openDetail(detail.value); await loadPlans() }; const exportTasks = async () => { await WorkPlanApi.exportWorkPlans(query); message.success('导出任务已提交') }
const loadOptions = async () => {
  const requests = [
    checkPermi(['zsjos:work-plan-config:query']) ? ConfigApi.getTemplates() : Promise.resolve([]),
    UserApi.getSimpleUserList(),
    DeptApi.getSimpleDeptList()
  ] as const
  const [templateResult, userResult, deptResult] = await Promise.allSettled(requests)
  if (templateResult.status === 'fulfilled') templates.value = templateResult.value
  if (userResult.status === 'fulfilled') users.value = userResult.value
  if (deptResult.status === 'fulfilled') departments.value = deptResult.value
  const failedOptions = [templateResult, userResult, deptResult].filter((result) => result.status === 'rejected').length
  if (failedOptions) message.warning('部分筛选或操作选项加载失败，不影响查看工作计划')
}
onMounted(() => { void loadPlans(); void loadOptions() })
</script>
<style scoped>.task-node{display:flex;align-items:center;gap:10px;min-width:0}.drawer-footer{display:flex;justify-content:flex-end;margin-top:16px}</style>
