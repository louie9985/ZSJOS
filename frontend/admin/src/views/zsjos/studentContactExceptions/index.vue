<template>
  <ContentWrap>
    <div class="toolbar">
      <el-radio-group v-model="view" @change="search">
        <el-radio-button value="pending">待处理</el-radio-button>
        <el-radio-button value="history">处理记录</el-radio-button>
        <el-radio-button value="all">全部</el-radio-button>
      </el-radio-group>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
  </ContentWrap>
  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon>
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-empty v-else-if="!filteredRows.length" description="暂无延期审批记录" />
    <el-table v-else :data="filteredRows" row-key="id">
      <el-table-column prop="id" label="申请编号" width="110" />
      <el-table-column prop="reasonLabel" label="延期原因" min-width="150">
        <template #default="{ row }">{{ row.reasonLabel || row.reasonValue }}</template>
      </el-table-column>
      <el-table-column prop="description" label="申请说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="原截止时间" width="180"><template #default="{ row }">{{ formatDate(row.originalDueAt) }}</template></el-table-column>
      <el-table-column label="申请截止时间" width="180"><template #default="{ row }">{{ formatDate(row.requestedDueAt) }}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.processInstanceId" link type="primary" @click="openProcess(row.processInstanceId)">
            {{ row.status === 'pending' ? '处理审批' : '流程历史' }}
          </el-button>
          <span v-else>流程待创建</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination"><el-pagination v-model:current-page="pageNo" v-model:page-size="pageSize" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Api from '@/api/zsjos/studentContactException'
import { formatDate } from '@/utils/formatTime'
defineOptions({ name: 'ZsjosStudentContactExceptions' })
const router = useRouter()
const loading = ref(false)
const error = ref('')
const rows = ref<Api.StudentContactExtensionVO[]>([])
const view = ref<'pending' | 'history' | 'all'>('pending')
const filteredRows = computed(() => rows.value)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const load = async () => { loading.value = true; error.value = ''; try { const page = await Api.getStudentContactExtensions(pageNo.value, pageSize.value, view.value); rows.value = page.list; total.value = page.total } catch (e: any) { error.value = e?.msg || e?.message || '延期审批记录加载失败' } finally { loading.value = false } }
const search = () => { pageNo.value = 1; void load() }
const openProcess = (id: string) => router.push({ name: 'BpmProcessInstanceDetail', query: { id } })
const statusLabel = (status: string) => ({ pending: '待处理', approved: '已通过', rejected: '已驳回', withdrawn: '已撤回', cancelled: '已取消' }[status] || status)
const statusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => status === 'approved' ? 'success' : status === 'pending' ? 'warning' : status === 'rejected' ? 'danger' : 'info'
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px }
</style>
