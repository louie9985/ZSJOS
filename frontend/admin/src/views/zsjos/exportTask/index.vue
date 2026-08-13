<template>
  <ContentWrap>
    <el-form ref="queryRef" :model="query" inline @submit.prevent>
      <el-form-item label="导出类型" prop="exportType">
        <el-select v-model="query.exportType" clearable placeholder="全部" class="!w-180px">
          <el-option
            v-for="item in types"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="load"
          ><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button
        >
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" show-icon>
      <template #default><el-button link @click="load">重试</el-button></template>
    </el-alert>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="任务编号" prop="taskNo" min-width="210" />
      <el-table-column label="类型" width="100">
        <template #default="scope">{{ typeName(scope.row.exportType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)">{{ statusName(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="尝试次数" prop="attemptCount" width="90" />
      <el-table-column label="创建时间" prop="createTime" min-width="170" />
      <el-table-column label="结果" min-width="220">
        <template #default="scope">
          <span v-if="scope.row.status === 'failed'">{{
            scope.row.failureMessage || scope.row.failureCode
          }}</span>
          <span v-else>{{ scope.row.resultFileName || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="150">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === 'ready'"
            link
            type="primary"
            @click="download(scope.row.id)"
          >
            下载
          </el-button>
          <el-button
            v-if="activeStatuses.includes(scope.row.status)"
            link
            type="danger"
            @click="cancel(scope.row.id)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无导出任务" /></template>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as ExportApi from '@/api/zsjos/exportTask'

defineOptions({ name: 'ZsjosExportTask' })
const message = useMessage()
const loading = ref(false)
const error = ref('')
const list = ref<ExportApi.ExportTaskVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, exportType: undefined as string | undefined })
const types = [
  { value: 'lead', label: '客资' },
  { value: 'order', label: '订单' },
  { value: 'cashback', label: '返现' },
  { value: 'withdrawal', label: '提现' }
]
const activeStatuses = ['queued', 'prechecking', 'generating']
const typeName = (value: string) => types.find((item) => item.value === value)?.label || value
const statusName = (value: string) =>
  ({
    queued: '排队中',
    prechecking: '数据预检中',
    generating: '文件生成中',
    ready: '文件已就绪',
    failed: '生成失败',
    cancelled: '已取消',
    expired: '已过期'
  })[value] || value
const statusType = (value: string) =>
  ({ ready: 'success', failed: 'danger', cancelled: 'info', expired: 'info' })[value] || 'warning'

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await ExportApi.getExportTaskPage(query)
    list.value = data.list
    total.value = data.total
  } catch (e: any) {
    error.value = e?.msg || e?.message || '导出任务加载失败'
  } finally {
    loading.value = false
  }
}
const cancel = async (id: number) => {
  await message.confirm('确认取消该导出任务？')
  await ExportApi.cancelExportTask(id)
  message.success('导出任务已取消')
  await load()
}
const download = async (id: number) => {
  const url = await ExportApi.getExportDownloadUrl(id)
  window.open(url, '_blank', 'noopener,noreferrer')
}
onMounted(load)
</script>
