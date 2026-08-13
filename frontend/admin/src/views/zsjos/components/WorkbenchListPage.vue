<template>
  <ContentWrap>
    <div class="flex items-center justify-between mb-16px"
      ><div
        ><h3>{{ title }}</h3
        ><p class="text-gray-500">{{ description }}</p></div
      ><div class="flex gap-8px"
        ><slot name="actions" :reload="load"></slot><el-button @click="load">刷新</el-button></div
      ></div
    >
    <el-alert
      v-if="unauthorized"
      title="暂无该列表的查询权限"
      type="info"
      show-icon
      :closable="false"
      class="mb-12px"
    />
    <el-alert
      v-else-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="mb-12px"
      ><template #default
        ><el-button link type="primary" @click="load">重试</el-button></template
      ></el-alert
    >
    <el-empty v-if="!loading && !rows.length && !error && !unauthorized" description="暂无数据" />
    <el-table v-else v-loading="loading" :data="rows" row-key="id" stripe>
      <el-table-column label="编号" prop="id" width="100" />
      <el-table-column label="名称/客户" min-width="180"
        ><template #default="{ row }">{{
          row.submittedName || row.studentName || row.nickname || row.name || `记录 #${row.id}`
        }}</template></el-table-column
      >
      <el-table-column label="状态" prop="status" width="140" />
      <el-table-column label="负责人" prop="ownerUserName" width="140" />
      <el-table-column label="时间" width="180"
        ><template #default="{ row }">{{
          formatTime(row.submittedAt || row.createTime)
        }}</template></el-table-column
      >
      <el-table-column label="操作" min-width="160" fixed="right"
        ><template #default="{ row }"
          ><slot name="row-actions" :row="row" :reload="load"
            ><el-button link type="primary" @click="openDetail(row)">查看</el-button></slot
          ></template
        ></el-table-column
      >
    </el-table>
    <div class="flex justify-end mt-12px"
      ><el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
    /></div>
  </ContentWrap>
  <el-drawer v-model="detailOpen" :title="`${title}详情`" size="520px"
    ><el-descriptions v-if="selected" :column="1" border
      ><el-descriptions-item
        v-for="(value, key) in selected"
        :key="String(key)"
        :label="String(key)"
        >{{ display(value) }}</el-descriptions-item
      ></el-descriptions
    ></el-drawer
  >
</template>
<script setup lang="ts">
import * as MenuApi from '@/api/zsjos/workbenchMenus'
const props = withDefaults(
  defineProps<{
    title: string
    endpoint: string
    description?: string
    query?: Record<string, unknown>
  }>(),
  { description: '服务端数据列表', query: () => ({}) }
)
const loading = ref(false)
const error = ref('')
const unauthorized = ref(false)
const rows = ref<MenuApi.WorkbenchListItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const selected = ref<MenuApi.WorkbenchListItem>()
const detailOpen = ref(false)
const formatTime = (value: unknown) =>
  value ? new Date(Number(value)).toLocaleString('zh-CN') : '-'
const display = (value: unknown) =>
  value == null || value === ''
    ? '-'
    : typeof value === 'object'
      ? JSON.stringify(value)
      : String(value)
const load = async () => {
  loading.value = true
  error.value = ''
  unauthorized.value = false
  try {
    const result = await MenuApi.page(props.endpoint, {
      ...props.query,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
    rows.value = result.list || []
    total.value = result.total || 0
  } catch (e: any) {
    rows.value = []
    if (e?.response?.status === 403 || e?.code === 403) unauthorized.value = true
    else error.value = e?.msg || e?.message || '列表加载失败'
  } finally {
    loading.value = false
  }
}
const openDetail = (row: MenuApi.WorkbenchListItem) => {
  selected.value = row
  detailOpen.value = true
}
onMounted(load)
</script>
