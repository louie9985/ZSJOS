<template>
  <section v-loading="loading">
    <el-result v-if="denied" icon="warning" title="无权查看阅读情况" sub-title="请联系管理员配置公告查询权限" />
    <el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false">
      <el-button @click="load">重试</el-button>
    </el-alert>
    <el-alert v-else-if="summary && !summary.published" title="发布后生成阅读统计" type="info" :closable="false" />
    <template v-else>
      <template v-if="summary">
        <el-alert v-if="!summary.rosterComplete" title="无法统计应读、未读及阅读率：发布时未记录名单" type="info" :closable="false" />
        <el-descriptions :column="1" size="small" border>
          <template v-if="summary.rosterComplete">
            <el-descriptions-item label="应读人数">{{ summary.expectedCount }}</el-descriptions-item>
            <el-descriptions-item label="已读人数">{{ summary.readCount }}</el-descriptions-item>
            <el-descriptions-item label="未读人数">{{ summary.unreadCount }}</el-descriptions-item>
            <el-descriptions-item label="阅读率">{{ summary.readRate == null ? '—' : (summary.readRate * 100).toFixed(1) + '%' }}</el-descriptions-item>
            <el-descriptions-item label="名单外阅读人数">{{ summary.extraReadCount }}</el-descriptions-item>
          </template>
          <el-descriptions-item v-else label="实际阅读人数">{{ summary.actualReadCount }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-tabs :model-value="summary && !summary.rosterComplete ? 'ACTUAL' : scope" @tab-change="changeScope">
        <el-tab-pane v-if="summary && !summary.rosterComplete" name="ACTUAL" label="实际阅读人员" />
        <template v-else><el-tab-pane name="EXPECTED" label="全部应读" /><el-tab-pane name="READ" label="已读" /><el-tab-pane name="UNREAD" label="未读" /><el-tab-pane name="EXTRA" label="名单外阅读" /></template>
      </el-tabs>
      <el-form inline @submit.prevent="search">
        <el-form-item><el-input v-model="nameInput" clearable placeholder="搜索姓名" /></el-form-item>
        <el-form-item><el-select v-model="deptId" clearable filterable placeholder="筛选部门" style="width: 180px" @change="search">
          <el-option v-for="dept in departments" :key="dept.id" :value="dept.id" :label="dept.name || '部门名称未记录'" />
        </el-select></el-form-item>
        <el-form-item><el-button native-type="submit">查询</el-button><el-button @click="load">刷新</el-button></el-form-item>
      </el-form>
      <el-table :data="rows" row-key="userId" empty-text="暂无人员">
        <el-table-column label="姓名" min-width="130"><template #default="{ row }">{{ row.userName || (row.accountDeleted ? '账号已删除' : '姓名未记录') }}</template></el-table-column>
        <el-table-column label="部门" min-width="150"><template #default="{ row }">{{ row.deptName || (row.deptId == null ? '未分配部门' : '部门名称未记录') }}</template></el-table-column>
        <el-table-column label="资料口径" min-width="180"><template #default="{ row }">{{ row.profileSource === 'SNAPSHOT' ? '发布时资料' : '当前资料（非历史快照）' }}</template></el-table-column>
        <el-table-column label="当前账号状态" min-width="120"><template #default="{ row }">{{ row.accountDeleted ? '已删除' : row.accountStatus === 0 ? '启用' : row.accountStatus === 1 ? '停用' : '未知' }}</template></el-table-column>
        <el-table-column label="阅读状态" min-width="100"><template #default="{ row }">{{ row.readTime == null ? '未读' : '已读' }}</template></el-table-column>
        <el-table-column label="首次阅读时间" min-width="180"><template #default="{ row }">{{ row.readTime == null ? '—' : formatDate(row.readTime) }}</template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="pageNo" v-model:page-size="pageSize" :total="total" :page-sizes="[20, 50, 100]" layout="prev, pager, next" :pager-count="5" @current-change="load" />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { formatDate } from '@/utils/formatTime'
import { getNoticeReadPage, getNoticeReadSummary, type NoticeReadPerson, type NoticeReadScope, type NoticeReadSummary } from '@/api/system/notice'
const props = defineProps<{ id: number }>()
const summary = ref<NoticeReadSummary>()
const rows = ref<NoticeReadPerson[]>([])
const scope = ref<NoticeReadScope>('EXPECTED')
const pageNo = ref(1), pageSize = ref(20), total = ref(0)
const nameInput = ref(''), name = ref(''), deptId = ref<number>()
const loading = ref(false), error = ref(''), denied = ref(false)
let generation = 0
const departments = computed(() => (scope.value === 'EXTRA' && summary.value?.rosterComplete ? summary.value.extraDepartments : summary.value?.departments) || [])
const load = async () => {
  const current = ++generation
  loading.value = true; error.value = ''; denied.value = false; rows.value = []
  try {
    const stats = await getNoticeReadSummary(props.id)
    const data = stats.published ? await getNoticeReadPage({ id: props.id, scope: stats.rosterComplete ? scope.value : 'ACTUAL', pageNo: pageNo.value, pageSize: pageSize.value, name: name.value || undefined, deptId: deptId.value || undefined }) : { list: [], total: 0 }
    if (current !== generation) return
    summary.value = stats; rows.value = data.list; total.value = data.total
  } catch (cause) {
    if (current !== generation) return
    const failure = cause as { code?: number; response?: { status?: number }; message?: string }
    denied.value = failure?.code === 403 || failure?.response?.status === 403
    summary.value = undefined; total.value = 0
    error.value = failure?.message || (typeof cause === 'string' ? cause : '阅读情况加载失败')
  } finally { if (current === generation) loading.value = false }
}
const search = () => { name.value = nameInput.value; pageNo.value = 1; void load() }
const changeScope = (key: string | number) => { scope.value = key as NoticeReadScope; deptId.value = undefined; pageNo.value = 1; void load() }
watch(() => props.id, () => { scope.value = 'EXPECTED'; pageNo.value = 1; deptId.value = undefined; name.value = ''; nameInput.value = ''; void load() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>
