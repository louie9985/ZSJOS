<template>
  <ContentWrap>
    <div class="toolbar">
      <el-tabs v-model="exceptionType" @tab-change="loadList">
        <el-tab-pane label="挂起客资" name="suspended" />
        <el-tab-pane label="回收待处理" name="recycle_pending" />
      </el-tabs>
      <el-button :icon="Refresh" @click="loadList">刷新</el-button>
    </div>
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="mb-12px">
      <template #default><el-button link type="primary" @click="loadList">重试</el-button></template>
    </el-alert>
    <el-table v-loading="loading" :data="list" empty-text="暂无异常客资">
      <el-table-column label="客户" min-width="150">
        <template #default="scope"><strong>{{ scope.row.submittedName }}</strong><div>{{ scope.row.submittedMobile || '无手机号' }}</div></template>
      </el-table-column>
      <el-table-column label="当前阶段" width="130"><template #default="scope"><el-tag type="warning">{{ stageLabel(scope.row.handlingStage) }}</el-tag></template></el-table-column>
      <el-table-column :label="exceptionType === 'suspended' ? '当前销售' : '回收来源销售'" min-width="140">
        <template #default="scope">{{ ownerText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="判定截止" min-width="165"><template #default="scope">{{ formatZsjosTimestamp(scope.row.qualificationDeadlineAt) }}</template></el-table-column>
      <el-table-column label="挂起时间" min-width="165"><template #default="scope">{{ formatZsjosTimestamp(scope.row.suspendedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="scope">
          <el-button v-if="exceptionType === 'suspended'" link type="primary" v-hasPermi="['zsjos:lead:qualification:manage']" @click="openAction(scope.row, 'restore')">恢复</el-button>
          <el-button link type="primary" v-hasPermi="['zsjos:lead:qualification:manage']" @click="openAction(scope.row, 'transfer')">转派</el-button>
          <el-button v-if="exceptionType === 'suspended'" link type="warning" v-hasPermi="['zsjos:lead:qualification:manage']" @click="openAction(scope.row, 'recycle')">回收</el-button>
          <el-button link type="success" v-hasPermi="['zsjos:lead:qualification:manage']" @click="openAction(scope.row, 'release')">释放</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="pageNo" v-model:limit="pageSize" @pagination="loadList" />
  </ContentWrap>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
    <el-form label-position="top">
      <el-form-item v-if="action === 'transfer'" label="目标销售" required>
        <el-select v-model="salesUserId" filterable placeholder="选择目标销售" class="w-100%" :loading="candidateLoading">
          <el-option v-for="item in candidates" :key="item.id" :label="`${item.nickname}${item.deptName ? ` · ${item.deptName}` : ''}`" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="处置理由" required><el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
    </el-form>
    <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitAction">确认处理</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import * as LeadApi from '@/api/zsjos/leadManagement'
import { formatZsjosTimestamp } from '@/utils/zsjosTime'

defineOptions({ name: 'ZsjosLeadQualification' })
type ExceptionType = 'suspended' | 'recycle_pending'
type Action = 'restore' | 'transfer' | 'recycle' | 'release'
const message = useMessage()
const exceptionType = ref<ExceptionType>('suspended')
const loading = ref(false)
const error = ref('')
const list = ref<LeadApi.LeadQualificationExceptionVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const dialogVisible = ref(false)
const action = ref<Action>('restore')
const selected = ref<LeadApi.LeadQualificationExceptionVO>()
const reason = ref('')
const salesUserId = ref<number>()
const candidates = ref<LeadApi.LeadTransferCandidateVO[]>([])
const candidateLoading = ref(false)
const saving = ref(false)
const dialogTitle = computed(() => ({ restore: '恢复原销售', transfer: '转派客资', recycle: '回收客资', release: '释放到抢单池' })[action.value])

const loadList = async () => {
  loading.value = true; error.value = ''
  try {
    const data = await LeadApi.getQualificationExceptionPage({ type: exceptionType.value, pageNo: pageNo.value, pageSize: pageSize.value })
    list.value = data.list || []; total.value = data.total || 0
  } catch (e: any) { error.value = e?.msg || e?.message || '异常客资加载失败'; list.value = []; total.value = 0 }
  finally { loading.value = false }
}
const openAction = async (row: LeadApi.LeadQualificationExceptionVO, nextAction: Action) => {
  selected.value = row; action.value = nextAction; reason.value = ''; salesUserId.value = undefined; candidates.value = []; dialogVisible.value = true
  if (nextAction !== 'transfer') return
  candidateLoading.value = true
  try { candidates.value = await LeadApi.getTransferCandidates(row.id) as LeadApi.LeadTransferCandidateVO[] }
  catch (e: any) { message.error(e?.msg || e?.message || '目标销售加载失败') }
  finally { candidateLoading.value = false }
}
const submitAction = async () => {
  if (!selected.value || !reason.value.trim()) return message.warning('请填写处置理由')
  if (action.value === 'transfer' && !salesUserId.value) return message.warning('请选择目标销售')
  saving.value = true
  const command = { reason: reason.value.trim(), idempotencyKey: crypto.randomUUID() }
  try {
    if (action.value === 'restore') await LeadApi.restoreLead(selected.value.id, command)
    if (action.value === 'transfer') await LeadApi.transferLead(selected.value.id, { ...command, salesUserId: salesUserId.value! })
    if (action.value === 'recycle') await LeadApi.recycleLead(selected.value.id, command)
    if (action.value === 'release') await LeadApi.releaseLead(selected.value.id, command)
    message.success('异常客资已处理'); dialogVisible.value = false; await loadList()
  } finally { saving.value = false }
}
const stageLabel = (value: string) => ({ suspended: '已挂起', recycle_pending: '回收待处理' })[value] || value
const ownerText = (row: LeadApi.LeadQualificationExceptionVO) => exceptionType.value === 'suspended'
  ? row.ownerUserName || (row.ownerUserId ? `用户 #${row.ownerUserId}` : '未分配')
  : row.recycleSourceOwnerUserName || (row.recycleSourceOwnerUserId ? `用户 #${row.recycleSourceOwnerUserId}` : '-')
onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.toolbar :deep(.el-tabs__header) { margin-bottom: 12px; }
@media (width <= 768px) { .toolbar { align-items: stretch; flex-direction: column; } }
</style>
