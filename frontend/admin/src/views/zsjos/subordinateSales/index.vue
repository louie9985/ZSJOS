<template>
  <WorkbenchListPage
    title="下属销售"
    endpoint="/zsjos/subordinate-sales/page"
    description="下属销售与业绩概览"
  >
    <template #row-actions="{ row, reload }">
      <el-button link type="primary" @click="showLeads(row)">查看客资</el-button>
      <el-button
        link
        v-hasPermi="['zsjos:subordinate-sales:account-status']"
        @click="changeAccount(row, reload)"
        >{{ row.accountStatus === 0 ? '停用账号' : '启用账号' }}</el-button
      >
      <el-button
        link
        v-hasPermi="['zsjos:subordinate-sales:dispatch-mode']"
        @click="changeDispatch(row, reload)"
        >{{ row.accepting ? '暂停接单' : '开启接单' }}</el-button
      >
    </template>
  </WorkbenchListPage>
  <el-drawer v-model="leadOpen" :title="`${currentSales?.nickname || '下属'}的客资`" size="760px">
    <el-alert v-if="leadError" :title="leadError" type="error" show-icon :closable="false"
      ><el-button link @click="loadLeads">重试</el-button></el-alert
    >
    <el-table
      v-loading="leadLoading"
      :data="leads"
      row-key="id"
      @selection-change="selected = $event"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column label="客资编号" prop="leadNo" width="220" />
      <el-table-column label="客资" min-width="180"
        ><template #default="{ row }">{{
          row.submittedName || row.name || '未命名客资'
        }}</template></el-table-column
      >
      <el-table-column label="状态" prop="status" width="140" />
      <el-table-column label="更新时间" prop="updateTime" width="180" />
    </el-table>
    <el-pagination
      v-model:current-page="leadPageNo"
      :total="leadTotal"
      :page-size="20"
      layout="total, prev, pager, next"
      class="mt-12px justify-end"
      @current-change="loadLeads"
    />
    <template #footer>
      <el-button @click="leadOpen = false">关闭</el-button>
      <el-button
        v-hasPermi="['zsjos:subordinate-sales:batch-public-sea']"
        :disabled="!selected.length"
        @click="openBatch('public-sea')"
        >释放公海</el-button
      >
      <el-button
        v-hasPermi="['zsjos:subordinate-sales:batch-transfer']"
        type="primary"
        :disabled="!selected.length"
        @click="openBatch('transfer')"
        >批量转派</el-button
      >
    </template>
  </el-drawer>
  <el-dialog
    v-model="batchOpen"
    :title="batchMode === 'transfer' ? '批量转派客资' : '批量释放公海'"
    width="520px"
  >
    <el-alert :title="`已选择 ${selected.length} 条客资`" type="info" show-icon class="mb-12px" />
    <el-form label-width="90px">
      <el-form-item v-if="batchMode === 'transfer'" label="目标销售" required
        ><el-select v-model="targetUserId" filterable class="w-100%"
          ><el-option
            v-for="item in candidates"
            :key="item.id"
            :label="item.nickname"
            :value="item.id" /></el-select
      ></el-form-item>
      <el-form-item label="操作原因" required
        ><el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="batchOpen = false">取消</el-button
      ><el-button type="primary" :loading="batchSaving" @click="submitBatch"
        >确认</el-button
      ></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import * as Api from '@/api/zsjos/workbenchMenus'
import { useMessage } from '@/hooks/web/useMessage'
import WorkbenchListPage from '../components/WorkbenchListPage.vue'

const message = useMessage()
const leadOpen = ref(false)
const leadLoading = ref(false)
const leadError = ref('')
const currentSales = ref<any>()
const leads = ref<Api.WorkbenchListItem[]>([])
const selected = ref<Api.WorkbenchListItem[]>([])
const leadPageNo = ref(1)
const leadTotal = ref(0)
const batchOpen = ref(false)
const batchSaving = ref(false)
const batchMode = ref<'transfer' | 'public-sea'>('transfer')
const candidates = ref<Array<{ id: number; nickname: string }>>([])
const targetUserId = ref<number>()
const reason = ref('')

const changeAccount = async (row: any, reload: () => Promise<void>) => {
  const next = row.accountStatus === 0 ? 1 : 0
  await Api.updateSubordinateAccount(
    row.userId || row.id,
    next,
    next === 0 ? '恢复下属账号' : '停用下属账号'
  )
  message.success('账号状态已更新')
  await reload()
}
const changeDispatch = async (row: any, reload: () => Promise<void>) => {
  await Api.updateSubordinateDispatch(row.userId || row.id, !row.accepting, '调整下属接单状态')
  message.success('接单状态已更新')
  await reload()
}
const loadLeads = async () => {
  if (!currentSales.value) return
  leadLoading.value = true
  leadError.value = ''
  try {
    const result = await Api.subordinateLeads(currentSales.value.userId || currentSales.value.id, {
      pageNo: leadPageNo.value,
      pageSize: 20
    })
    leads.value = result.list || []
    leadTotal.value = result.total || 0
    selected.value = []
  } catch (error: any) {
    leads.value = []
    leadError.value = error?.msg || error?.message || '下属客资加载失败'
  } finally {
    leadLoading.value = false
  }
}
const showLeads = (row: any) => {
  currentSales.value = row
  leadPageNo.value = 1
  leadOpen.value = true
  void loadLeads()
}
const openBatch = async (mode: 'transfer' | 'public-sea') => {
  batchMode.value = mode
  targetUserId.value = undefined
  reason.value = ''
  batchOpen.value = true
  if (mode === 'transfer' && !candidates.value.length) {
    try {
      candidates.value = await Api.subordinateTransferCandidates()
    } catch (error: any) {
      message.error(error?.msg || error?.message || '目标销售加载失败')
    }
  }
}
const submitBatch = async () => {
  if (!reason.value.trim()) return message.warning('请填写操作原因')
  if (batchMode.value === 'transfer' && !targetUserId.value)
    return message.warning('请选择目标销售')
  batchSaving.value = true
  try {
    const leadIds = selected.value.map((item) => item.id)
    if (batchMode.value === 'transfer')
      await Api.batchTransferSubordinateLeads({
        leadIds,
        targetUserId: targetUserId.value,
        reason: reason.value.trim()
      })
    else await Api.batchReleaseSubordinateLeads({ leadIds, reason: reason.value.trim() })
    message.success('批量操作已完成')
    batchOpen.value = false
    await loadLeads()
  } finally {
    batchSaving.value = false
  }
}
</script>
