<template>
  <ContentWrap>
    <div class="flex items-center justify-between mb-16px">
      <div><h3>今日待办</h3><p class="text-gray-500">业务待办与流程审批任务</p></div>
      <el-button @click="loadAll">刷新</el-button>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="业务待办" name="business">
        <el-alert
          v-if="business.error"
          :title="business.error"
          type="error"
          show-icon
          :closable="false"
          ><el-button link @click="loadBusiness">重试</el-button></el-alert
        >
        <el-empty
          v-if="!business.loading && !business.rows.length && !business.error"
          description="暂无业务待办"
        />
        <el-table v-else v-loading="business.loading" :data="business.rows" row-key="id" stripe>
          <el-table-column label="任务" min-width="220"
            ><template #default="{ row }">{{
              row.name || row.taskName || row.title || `任务 #${row.id}`
            }}</template></el-table-column
          >
          <el-table-column label="状态" prop="status" width="140" />
          <el-table-column label="创建时间" width="180"
            ><template #default="{ row }">{{
              formatTime(row.createTime)
            }}</template></el-table-column
          >
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="流程待办" name="bpm">
        <el-alert
          v-if="bpm.unauthorized"
          title="暂无流程任务查询权限"
          type="info"
          show-icon
          :closable="false"
        />
        <template v-else>
          <el-alert v-if="bpm.error" :title="bpm.error" type="error" show-icon :closable="false"
            ><el-button link @click="loadBpm">重试</el-button></el-alert
          >
          <el-empty
            v-if="!bpm.loading && !bpm.rows.length && !bpm.error"
            description="暂无流程待办"
          />
          <el-table v-else v-loading="bpm.loading" :data="bpm.rows" row-key="id" stripe>
            <el-table-column label="流程任务" min-width="220"
              ><template #default="{ row }">{{
                row.name || row.taskName || `任务 #${row.id}`
              }}</template></el-table-column
            >
            <el-table-column label="流程" min-width="180" prop="processInstanceName" />
            <el-table-column label="创建时间" width="180"
              ><template #default="{ row }">{{
                formatTime(row.createTime)
              }}</template></el-table-column
            >
          </el-table>
        </template>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as Api from '@/api/zsjos/workbenchMenus'

const activeTab = ref('business')
const business = reactive({ rows: [] as Api.WorkbenchListItem[], loading: false, error: '' })
const bpm = reactive({
  rows: [] as Api.WorkbenchListItem[],
  loading: false,
  error: '',
  unauthorized: false
})

const formatTime = (value: unknown) =>
  value ? new Date(Number(value)).toLocaleString('zh-CN') : '-'

const loadBusiness = async () => {
  business.loading = true
  business.error = ''
  try {
    const result = await Api.page('/zsjos/business-task/my-page', {
      bucket: 'today',
      status: 'pending',
      pageNo: 1,
      pageSize: 100
    })
    business.rows = result.list || []
  } catch (error: any) {
    business.rows = []
    business.error = error?.msg || error?.message || '业务待办加载失败'
  } finally {
    business.loading = false
  }
}
const loadBpm = async () => {
  bpm.loading = true
  bpm.error = ''
  bpm.unauthorized = false
  try {
    const result = await Api.bpmTodoPage({ pageNo: 1, pageSize: 100 })
    bpm.rows = result.list || []
  } catch (error: any) {
    bpm.rows = []
    if (error?.response?.status === 403 || error?.code === 403) bpm.unauthorized = true
    else bpm.error = error?.msg || error?.message || '流程待办加载失败'
  } finally {
    bpm.loading = false
  }
}
const loadAll = () => Promise.all([loadBusiness(), loadBpm()])
onMounted(loadAll)
</script>
