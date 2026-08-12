<template>
  <ContentWrap>
    <el-form :inline="true" :model="queryParams" class="mb-12px" @submit.prevent>
      <el-form-item label="关键词"><el-input v-model="queryParams.keyword" clearable placeholder="姓名 / 手机号 / 微信号" @keyup.enter="search" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 180px">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="reset"><Icon icon="ep:refresh" />重置</el-button></el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="mb-16px">
      <template #default><el-button link type="primary" @click="getList">重试</el-button></template>
    </el-alert>
    <el-table v-loading="loading" :data="list" stripe table-layout="fixed" empty-text="暂无超期公海客资">
      <el-table-column label="客资" min-width="150" fixed="left"><template #default="scope"><strong>{{ scope.row.submittedName }}</strong><div class="muted">#{{ scope.row.leadId }}</div></template></el-table-column>
      <el-table-column label="联系方式" min-width="190"><template #default="scope"><div>{{ scope.row.submittedMobile || '-' }}</div><div>{{ scope.row.submittedWechatId || '-' }}</div></template></el-table-column>
      <el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="原销售A" prop="originalOwnerUserName" min-width="130" />
      <el-table-column label="协同销售B" min-width="130"><template #default="scope">{{ scope.row.collaboratorUserName || '待指派' }}</template></el-table-column>
      <el-table-column label="冻结部门" prop="frozenDeptName" min-width="140" />
      <el-table-column label="持有起点" min-width="170"><template #default="scope">{{ formatDate(scope.row.ownershipStartedAt) }}</template></el-table-column>
      <el-table-column label="到期时间" min-width="170"><template #default="scope">{{ formatDate(scope.row.dueAt) }}</template></el-table-column>
      <el-table-column label="进入公海" min-width="170"><template #default="scope">{{ formatDate(scope.row.enteredAt) }}</template></el-table-column>
      <el-table-column label="最近跟进" min-width="170"><template #default="scope">{{ formatDate(scope.row.lastFollowUpAt) }}</template></el-table-column>
      <el-table-column label="下次跟进" min-width="170"><template #default="scope">{{ formatDate(scope.row.nextFollowUpAt) }}</template></el-table-column>
      <el-table-column label="操作" width="180" fixed="right"><template #default="scope">
        <el-button v-if="scope.row.availableActions.includes('ASSIGN')" link type="primary" v-hasPermi="['zsjos:lead-aging-pool:manage','zsjos:lead-aging-pool:manage-all']" @click="openAssign(scope.row)">{{ scope.row.collaboratorUserId ? '换派' : '指派' }}</el-button>
        <el-button v-if="scope.row.availableActions.includes('EXIT')" link type="danger" v-hasPermi="['zsjos:lead-aging-pool:manage','zsjos:lead-aging-pool:manage-all']" @click="openExit(scope.row)">退出</el-button>
      </template></el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>
  <el-dialog v-model="assignVisible" :title="current?.collaboratorUserId ? '更换协同销售B' : '指派协同销售B'" width="480px">
    <el-select v-model="salesUserId" filterable placeholder="选择同部门启用销售" style="width:100%"><el-option v-for="item in candidates" :key="item.id" :label="item.nickname" :value="item.id" /></el-select>
    <template #footer><el-button @click="assignVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitAssign">确认</el-button></template>
  </el-dialog>
  <el-dialog v-model="exitVisible" title="退出超期公海" width="520px">
    <el-input v-model="exitReason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="填写退出原因；退出后由A独占推进并重新计时" />
    <template #footer><el-button @click="exitVisible=false">取消</el-button><el-button type="danger" :loading="saving" @click="submitExit">确认退出</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import * as AgingPoolApi from '@/api/zsjos/leadAgingPool'

defineOptions({ name: 'ZsjosLeadAgingPool' })
const message = useMessage()
const loading = ref(false); const saving = ref(false); const error = ref(''); const list = ref<AgingPoolApi.LeadAgingPoolVO[]>([]); const total = ref(0)
const queryParams = reactive<AgingPoolApi.LeadAgingPoolPageReqVO>({ pageNo: 1, pageSize: 20 })
const current = ref<AgingPoolApi.LeadAgingPoolVO>(); const candidates = ref<Array<{id:number;nickname:string}>>([]); const salesUserId = ref<number>()
const assignVisible = ref(false); const exitVisible = ref(false); const exitReason = ref('')
const statusOptions = [{ value: 'waiting_assignment', label: '待指派' }, { value: 'assigned', label: '协同跟进中' }, { value: 'deal_pending', label: '成交审批中' }] as const
const statusLabel = (status: AgingPoolApi.LeadAgingPoolStatus) => statusOptions.find(item => item.value === status)?.label || status
const statusType = (status: AgingPoolApi.LeadAgingPoolStatus) => status === 'waiting_assignment' ? 'warning' : status === 'deal_pending' ? 'info' : 'success'
const getList = async () => { loading.value=true; error.value=''; try { const data=await AgingPoolApi.getPage(queryParams); list.value=data.list||[]; total.value=data.total||0 } catch(e:any){ error.value=e?.msg||e?.message||'超期公海加载失败'; list.value=[]; total.value=0 } finally { loading.value=false } }
const search = () => { queryParams.pageNo=1; void getList() }; const reset = () => { queryParams.keyword=undefined; queryParams.status=undefined; search() }
const openAssign = async (row:AgingPoolApi.LeadAgingPoolVO) => { current.value=row; try{candidates.value=await AgingPoolApi.getCandidates(row.cycleId);salesUserId.value=row.collaboratorUserId;assignVisible.value=true}catch(e:any){message.error(e?.msg||e?.message||'候选销售加载失败')} }
const submitAssign = async () => { if(!current.value||!salesUserId.value){message.warning('请选择协同销售');return} saving.value=true; try{await AgingPoolApi.assign(current.value.cycleId,salesUserId.value);message.success('协同销售已更新');assignVisible.value=false;await getList()}catch(e:any){message.error(e?.msg||e?.message||'协同销售更新失败')}finally{saving.value=false} }
const openExit = (row:AgingPoolApi.LeadAgingPoolVO) => { current.value=row; exitReason.value=''; exitVisible.value=true }
const submitExit = async () => { if(!current.value||!exitReason.value.trim()){message.warning('请填写退出原因');return} saving.value=true; try{await AgingPoolApi.exit(current.value.cycleId,exitReason.value.trim());message.success('客资已退出超期公海');exitVisible.value=false;await getList()}catch(e:any){message.error(e?.msg||e?.message||'退出超期公海失败')}finally{saving.value=false} }
onMounted(getList)
</script>

<style scoped>.muted{margin-top:3px;color:var(--el-text-color-secondary);font-size:12px}</style>
