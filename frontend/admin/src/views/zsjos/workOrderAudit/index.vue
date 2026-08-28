<template>
  <ContentWrap>
    <el-form :inline="true"
      ><el-form-item label="状态"
        ><el-select v-model="query.status" clearable style="width: 180px"
          ><el-option
            v-for="(label, value) in statusLabels"
            :key="value"
            :label="label"
            :value="value" /></el-select></el-form-item
      ><el-form-item
        ><el-button type="primary" @click="load">查询</el-button></el-form-item
      ></el-form
    >
    <el-alert v-if="error" :title="error" type="error" show-icon
      ><template #default><el-button link @click="load">重试</el-button></template></el-alert
    >
    <el-table v-loading="loading" :data="list"
      ><el-table-column prop="orderNo" label="工单编号" min-width="170" /><el-table-column
        prop="sceneName"
        label="模板"
        min-width="150"
      /><el-table-column prop="processorType" label="处理器" width="150" /><el-table-column
        prop="sourceName"
        label="发起人"
        width="120"
      /><el-table-column prop="targetName" label="当前处理人" width="120" /><el-table-column
        label="状态"
        width="120"
        ><template #default="{ row }">{{
          statusLabels[row.status] || row.status
        }}</template></el-table-column
      ><el-table-column prop="currentRound" label="轮次" width="70" /><el-table-column
        label="操作"
        width="80"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="show(row.id)">查看</el-button></template
        ></el-table-column
      ></el-table
    >
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
  <el-drawer v-model="detailOpen" title="工单运行审计" size="640px"
    ><template v-if="detail"
      ><el-descriptions :column="1" border
        ><el-descriptions-item label="工单编号">{{ detail.orderNo }}</el-descriptions-item
        ><el-descriptions-item label="状态">{{
          statusLabels[detail.status] || detail.status
        }}</el-descriptions-item
        ><el-descriptions-item label="发起人">{{ detail.sourceName }}</el-descriptions-item
        ><el-descriptions-item label="处理人">{{
          detail.targetName || '候选池'
        }}</el-descriptions-item
        ><el-descriptions-item v-for="field in detail.fields || []" :key="field.key" :label="field.label"
          ><template v-if="field.type === 'attachment'"
            ><div v-for="file in fieldFiles(field.key)" :key="file.id">{{ file.name }}</div
            ><span v-if="!fieldFiles(field.key).length">-</span></template
          ><template v-else>{{ displayValue(detail.values?.[field.key]) }}</template></el-descriptions-item
        ><el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item
        ><el-descriptions-item label="完成说明">{{
          detail.completionRemark || '-'
        }}</el-descriptions-item></el-descriptions
      ><el-timeline class="mt-20px"
        ><el-timeline-item
          v-for="item in detail.timeline || []"
          :key="`${item.operation}-${item.operatedAt}`"
          :timestamp="item.operatedAt"
          ><strong>第 {{ item.roundNo || 1 }} 轮 · {{ item.operatorName || '系统' }}</strong
          ><div>{{ item.fromStatus || '创建' }} → {{ item.toStatus }}</div
          ><div v-if="item.reason">{{ item.reason }}</div></el-timeline-item
        ></el-timeline
      ></template
    ></el-drawer
  >
</template>
<script setup lang="ts">
import * as Api from '@/api/zsjos/workOrder'
defineOptions({ name: 'ZsjosWorkOrderAudit' })
const statusLabels: Record<string, string> = {
  PENDING_ACCEPT: '待接单',
  AVAILABLE: '可认领',
  IN_PROGRESS: '处理中',
  PENDING_REVIEW: '待验收',
  COMPLETED: '已完成',
  REJECTED_INVALID: '拒单失效',
  WITHDRAWN: '已撤回',
  TERMINATED_UNQUALIFIED: '不合格终止'
}
const query = reactive<{ pageNo: number; pageSize: number; status?: string }>({
  pageNo: 1,
  pageSize: 20
})
const loading = ref(false)
const error = ref('')
const total = ref(0)
const list = ref<Api.WorkOrderAudit[]>([])
const detailOpen = ref(false)
const detail = ref<Api.WorkOrderAudit>()
const fieldFiles = (key: string) => {
  const value = detail.value?.values?.[key]
  const ids = Array.isArray(value) ? value.map(Number) : []
  return (detail.value?.requestAttachments || []).filter((file) => ids.includes(file.id))
}
const displayValue = (value: unknown) => {
  if (value && typeof value === 'object' && 'label' in value) return String(value.label)
  return value === undefined || value === null || value === '' ? '-' : String(value)
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const page = await Api.getWorkOrderAuditPage(query)
    list.value = page.list || []
    total.value = page.total || 0
  } catch (e: any) {
    error.value = e?.msg || e?.message || '审计工单加载失败'
  } finally {
    loading.value = false
  }
}
const show = async (id: number) => {
  detail.value = await Api.getWorkOrderAudit(id)
  detailOpen.value = true
}
onMounted(load)
</script>
