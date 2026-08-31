<template>
  <ContentWrap>
    <el-alert
      v-if="!availableTabs.length"
      title="无权查看业务审计"
      type="warning"
      show-icon
      :closable="false"
    />
    <el-tabs v-else v-model="tab" @tab-change="reload">
      <el-tab-pane v-if="canViewBusiness" label="业务审计" name="business">
        <el-form :model="businessQuery" inline @submit.prevent>
          <el-form-item label="审计类别">
            <el-select v-model="businessQuery.categoryCode" clearable class="!w-160px">
              <el-option label="业务操作" value="business" />
              <el-option label="敏感读取" value="sensitive_read" />
              <el-option label="系统任务" value="system" />
            </el-select>
          </el-form-item>
          <el-form-item label="动作编码"
            ><el-input v-model="businessQuery.actionCode" clearable class="!w-220px"
          /></el-form-item>
          <el-form-item label="目标类型"
            ><el-input v-model="businessQuery.targetType" clearable class="!w-180px"
          /></el-form-item>
          <el-form-item label="来源">
            <el-select v-model="businessQuery.sourceType" clearable class="!w-170px">
              <el-option label="管理端" value="ADMIN" />
              <el-option label="合作方" value="PARTNER" />
              <el-option label="公开回调" value="PUBLIC_CALLBACK" />
              <el-option label="系统任务" value="SYSTEM" />
              <el-option label="业务明细" value="EXPLICIT" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="businessQuery.resultStatus" clearable class="!w-140px">
              <el-option label="执行中" value="STARTED" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILURE" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作人ID">
            <el-input-number v-model="businessQuery.operatorUserId" :min="1" controls-position="right" />
          </el-form-item>
          <el-form-item
            ><el-button :loading="loading" @click="loadBusiness">查询</el-button></el-form-item
          >
        </el-form>
        <el-table v-loading="loading" :data="businessList" table-layout="auto">
          <el-table-column label="操作人" prop="operatorNameSnapshot" min-width="120" />
          <el-table-column label="来源" prop="sourceType" min-width="130" />
          <el-table-column label="类别" prop="categoryCode" min-width="130" />
          <el-table-column label="动作编码" prop="actionCode" min-width="170" />
          <el-table-column label="目标" min-width="180">
            <template #default="scope">{{ scope.row.targetType }}<template v-if="scope.row.targetId"> / {{ scope.row.targetId }}</template></template>
          </el-table-column>
          <el-table-column label="结果" min-width="130">
            <template #default="scope">{{ scope.row.resultStatus }}<template v-if="scope.row.resultCode !== undefined"> / {{ scope.row.resultCode }}</template></template>
          </el-table-column>
          <el-table-column label="请求" min-width="280">
            <template #default="scope">{{ scope.row.requestMethod || '-' }} {{ scope.row.requestPath || '-' }}</template>
          </el-table-column>
          <el-table-column label="耗时(ms)" prop="durationMs" width="110" />
          <el-table-column label="来源 IP" prop="sourceIp" width="140" />
          <el-table-column label="Trace ID" prop="traceId" min-width="220" show-overflow-tooltip />
          <el-table-column label="时间" prop="occurredAt" min-width="170" />
          <template #empty><el-empty description="暂无业务审计" /></template>
        </el-table>
        <Pagination
          :total="businessTotal"
          v-model:page="businessQuery.pageNo"
          v-model:limit="businessQuery.pageSize"
          @pagination="loadBusiness"
        />
      </el-tab-pane>
      <el-tab-pane v-if="canViewImpersonation" label="借视图请求审计" name="impersonation">
        <el-form :model="impersonationQuery" inline @submit.prevent>
          <el-form-item label="会话编号"
            ><el-input-number
              v-model="impersonationQuery.sessionId"
              :min="1"
              controls-position="right"
          /></el-form-item>
          <el-form-item
            ><el-button :loading="loading" @click="loadImpersonation">查询</el-button></el-form-item
          >
        </el-form>
        <el-table v-loading="loading" :data="impersonationList">
          <el-table-column label="会话编号" prop="sessionId" width="110" />
          <el-table-column label="原管理员" prop="administratorUserId" width="110" />
          <el-table-column label="目标员工" prop="targetUserId" width="110" />
          <el-table-column label="方法" prop="httpMethod" width="90" />
          <el-table-column label="请求路径" prop="requestPath" min-width="300" />
          <el-table-column label="时间" prop="occurredAt" min-width="170" />
          <template #empty><el-empty description="暂无借视图请求审计" /></template>
        </el-table>
        <Pagination
          :total="impersonationTotal"
          v-model:page="impersonationQuery.pageNo"
          v-model:limit="impersonationQuery.pageSize"
          @pagination="loadImpersonation"
        />
      </el-tab-pane>
    </el-tabs>
    <el-alert v-if="error" :title="error" type="error" show-icon class="mt-16px">
      <template #default><el-button link @click="reload">重试</el-button></template>
    </el-alert>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as AuditApi from '@/api/zsjos/businessAudit'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'ZsjosBusinessAudit' })
const userStore = useUserStore()
const hasPermission = (permission: string) =>
  userStore.getPermissions.has('*:*:*') || userStore.getPermissions.has(permission)
const canViewBusiness = computed(() => hasPermission('zsjos:audit:query'))
const canViewImpersonation = computed(() => hasPermission('zsjos:audit:query-impersonation'))
const availableTabs = computed<Array<'business' | 'impersonation'>>(() => [
  ...(canViewBusiness.value ? ['business' as const] : []),
  ...(canViewImpersonation.value ? ['impersonation' as const] : [])
])
const tab = ref<'business' | 'impersonation'>(availableTabs.value[0] || 'business')
const loading = ref(false)
const error = ref('')
const businessList = ref<AuditApi.BusinessAuditVO[]>([])
const businessTotal = ref(0)
const impersonationList = ref<AuditApi.ImpersonationAuditVO[]>([])
const impersonationTotal = ref(0)
const businessQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  actionCode: undefined as string | undefined,
  targetType: undefined as string | undefined,
  categoryCode: undefined as string | undefined,
  sourceType: undefined as string | undefined,
  resultStatus: undefined as string | undefined,
  operatorUserId: undefined as number | undefined
})
const impersonationQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  sessionId: undefined as number | undefined
})

const loadBusiness = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await AuditApi.getBusinessAuditPage(businessQuery)
    businessList.value = data.list
    businessTotal.value = data.total
  } catch (e: any) {
    error.value = e?.msg || e?.message || '业务审计加载失败'
  } finally {
    loading.value = false
  }
}
const loadImpersonation = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await AuditApi.getImpersonationAuditPage(impersonationQuery)
    impersonationList.value = data.list
    impersonationTotal.value = data.total
  } catch (e: any) {
    error.value = e?.msg || e?.message || '借视图审计加载失败'
  } finally {
    loading.value = false
  }
}
const reload = () => (tab.value === 'business' ? loadBusiness() : loadImpersonation())
watch(
  availableTabs,
  (tabs, previousTabs) => {
    if (!tabs.length) return
    const tabChanged = !tabs.includes(tab.value)
    if (tabChanged) tab.value = tabs[0]
    if (!previousTabs?.length || tabChanged) reload()
  },
  { immediate: true }
)
</script>
