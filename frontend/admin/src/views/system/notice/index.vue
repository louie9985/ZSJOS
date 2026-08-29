<template>
  <NoticeEditor
    v-if="editorOpen"
    :key="editorId || 'new'"
    :id="editorId"
    @back="closeEditor"
    @saved="handleSaved"
  />

  <template v-else>
    <ContentWrap>
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" label-width="72px" class="-mb-15px">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="queryParams.title" clearable placeholder="请输入公告标题" class="!w-240px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="发布状态" prop="publishStatus">
          <el-select v-model="queryParams.publishStatus" clearable placeholder="全部状态" class="!w-200px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已下线" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleQuery"><Icon icon="ep:search" /> 搜索</el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" /> 重置</el-button>
          <el-button type="primary" @click="openEditor()" v-hasPermi="['system:notice:create']">
            <Icon icon="ep:plus" /> 新建公告
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="list">
        <el-table-column label="公告标题" prop="title" min-width="260" show-overflow-tooltip />
        <el-table-column label="公告类型" prop="type" width="110" align="center">
          <template #default="scope"><dict-tag :type="DICT_TYPE.SYSTEM_NOTICE_TYPE" :value="scope.row.type" /></template>
        </el-table-column>
        <el-table-column label="高亮" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.highlighted" type="warning">高亮中</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="发布状态" prop="publishStatus" width="110" align="center">
          <template #default="scope">
            <el-tag :type="statusMeta[scope.row.publishStatus].type">{{ statusMeta[scope.row.publishStatus].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="附件" width="80" align="center">
          <template #default="scope">{{ scope.row.attachments?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="发布时间" prop="publishTime" width="180" :formatter="dateFormatter" />
        <el-table-column label="创建时间" prop="createTime" width="180" :formatter="dateFormatter" />
        <el-table-column label="操作" fixed="right" width="300" align="center">
          <template #default="scope">
            <el-button v-if="scope.row.publishStatus === 'DRAFT'" link type="primary" @click="openEditor(scope.row.id)" v-hasPermi="['system:notice:update']">编辑</el-button>
            <el-button v-if="scope.row.publishStatus === 'DRAFT'" link type="primary" @click="publish(scope.row)" v-hasPermi="['system:notice:publish']">发布</el-button>
            <el-button v-if="scope.row.publishStatus === 'PUBLISHED'" link type="warning" @click="offline(scope.row)" v-hasPermi="['system:notice:offline']">下线</el-button>
            <el-button v-if="scope.row.publishStatus !== 'DRAFT'" link @click="copy(scope.row)" v-hasPermi="['system:notice:create']">复制草稿</el-button>
            <el-button v-if="scope.row.publishStatus === 'DRAFT'" link type="danger" @click="remove(scope.row)" v-hasPermi="['system:notice:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
    </ContentWrap>
  </template>
</template>

<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as NoticeApi from '@/api/system/notice'
import NoticeEditor from './NoticeEditor.vue'

defineOptions({ name: 'SystemNotice' })
const route = useRoute()
const router = useRouter()
const message = useMessage()
const loading = ref(false)
const list = ref<NoticeApi.NoticeVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive({ pageNo: 1, pageSize: 10, title: undefined as string | undefined, publishStatus: undefined as string | undefined })
const editorOpen = computed(() => route.query.action === 'edit')
const editorId = computed(() => {
  const id = Number(route.query.id)
  return Number.isFinite(id) && id > 0 ? id : undefined
})
const statusMeta = {
  DRAFT: { label: '草稿', type: 'info' as const },
  PUBLISHED: { label: '已发布', type: 'success' as const },
  OFFLINE: { label: '已下线', type: 'warning' as const }
}

const getList = async () => {
  loading.value = true
  try {
    const data = await NoticeApi.getNoticePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally { loading.value = false }
}
const handleQuery = () => { queryParams.pageNo = 1; getList() }
const resetQuery = () => { queryFormRef.value?.resetFields(); handleQuery() }
const openEditor = (id?: number) => router.push({ query: { ...route.query, action: 'edit', id: id ? String(id) : undefined } })
const closeEditor = async () => { await router.push({ query: {} }); await getList() }
const handleSaved = (id: number) => router.replace({ query: { action: 'edit', id: String(id) } })
const publish = async (row: NoticeApi.NoticeVO) => { await message.confirm(`确认发布“${row.title}”？发布后内容不可直接修改。`); await NoticeApi.publishNotice(row.id!); message.success('发布成功'); getList() }
const offline = async (row: NoticeApi.NoticeVO) => { await message.confirm(`确认下线“${row.title}”？`); await NoticeApi.offlineNotice(row.id!); message.success('已下线'); getList() }
const copy = async (row: NoticeApi.NoticeVO) => { const id = await NoticeApi.copyNotice(row.id!); message.success('已复制为草稿'); openEditor(id) }
const remove = async (row: NoticeApi.NoticeVO) => { await message.delConfirm(); await NoticeApi.deleteNotice(row.id!); message.success('删除成功'); getList() }

onMounted(() => { if (!editorOpen.value) getList() })
</script>
