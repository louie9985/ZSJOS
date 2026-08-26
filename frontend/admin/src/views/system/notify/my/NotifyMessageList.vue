<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      class="-mb-15px"
      :model="queryParams"
      :inline="true"
      label-width="68px"
    >
      <el-form-item v-if="!unreadOnly" label="是否已读" prop="readStatus">
        <el-select
          v-model="queryParams.readStatus"
          placeholder="请选择状态"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="dict in getBoolDictOptions(DICT_TYPE.INFRA_BOOLEAN_STRING)"
            :key="String(dict.value)"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="发送时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button @click="handleUpdateList">
          <Icon icon="ep:reading" class="mr-5px" /> 标记已读
        </el-button>
        <el-button @click="handleUpdateAll">
          <Icon icon="ep:reading" class="mr-5px" /> 全部已读
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="list"
      row-key="id"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" :selectable="selectable" :reserve-selection="true" />
      <el-table-column label="发送人" align="center" prop="templateNickname" width="180" />
      <el-table-column label="标题" prop="templateTitle" min-width="180" show-overflow-tooltip />
      <el-table-column
        label="发送时间"
        align="center"
        prop="createTime"
        width="200"
        :formatter="dateFormatter"
      />
      <el-table-column label="类型" align="center" prop="templateType" width="180">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.SYSTEM_NOTIFY_TEMPLATE_TYPE" :value="scope.row.templateType" />
        </template>
      </el-table-column>
      <el-table-column label="摘要" align="center" prop="templateSummary" show-overflow-tooltip />
      <el-table-column label="是否已读" align="center" prop="readStatus" width="160">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.INFRA_BOOLEAN_STRING" :value="scope.row.readStatus" />
        </template>
      </el-table-column>
      <el-table-column
        label="阅读时间"
        align="center"
        prop="readTime"
        width="200"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="160">
        <template #default="scope">
          <el-button
            link
            :type="scope.row.readStatus ? 'primary' : 'warning'"
            @click="openDetail(scope.row)"
          >
            {{ scope.row.readStatus ? '详情' : '已读' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <MyNotifyMessageDetail ref="detailRef" />
</template>

<script lang="ts" setup>
import { DICT_TYPE, getBoolDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as NotifyMessageApi from '@/api/system/notify/message'
import { useEmitt } from '@/hooks/web/useEmitt'
import { NOTIFY_MESSAGE_CHANGED_EVENT } from '@/utils/notifyMessage'
import MyNotifyMessageDetail from './MyNotifyMessageDetail.vue'

const props = withDefaults(
  defineProps<{
    unreadOnly?: boolean
  }>(),
  {
    unreadOnly: false
  }
)

const message = useMessage()
const route = useRoute()
const loading = ref(true)
const total = ref(0)
const list = ref<NotifyMessageApi.NotifyMessageVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  readStatus: props.unreadOnly ? false : undefined,
  createTime: []
})
const queryFormRef = ref()
const tableRef = ref()
const selectedIds = ref<number[]>([])
const { emitter } = useEmitt({
  name: NOTIFY_MESSAGE_CHANGED_EVENT,
  callback: () => getList()
})

const getList = async () => {
  loading.value = true
  try {
    if (props.unreadOnly) {
      queryParams.readStatus = false
    }
    const data = await NotifyMessageApi.getMyNotifyMessagePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  tableRef.value.clearSelection()
  queryParams.readStatus = props.unreadOnly ? false : undefined
  handleQuery()
}

const detailRef = ref()
const openDetail = async (data: NotifyMessageApi.NotifyMessageVO) => {
  let detailData = data
  if (!data.readStatus) {
    await NotifyMessageApi.updateNotifyMessageRead(data.id)
    detailData = {
      ...data,
      readStatus: true,
      readTime: new Date()
    }
    emitter.emit(NOTIFY_MESSAGE_CHANGED_EVENT)
  }
  detailRef.value.open(detailData)
}

const handleUpdateAll = async () => {
  await NotifyMessageApi.updateAllNotifyMessageRead()
  message.success('全部已读成功！')
  tableRef.value.clearSelection()
  emitter.emit(NOTIFY_MESSAGE_CHANGED_EVENT)
}

const handleUpdateList = async () => {
  if (selectedIds.value.length === 0) {
    return
  }
  await NotifyMessageApi.updateNotifyMessageRead(selectedIds.value)
  message.success('批量已读成功！')
  tableRef.value.clearSelection()
  emitter.emit(NOTIFY_MESSAGE_CHANGED_EVENT)
}

const selectable = (row: NotifyMessageApi.NotifyMessageVO) => !row.readStatus

const handleSelectionChange = (array: NotifyMessageApi.NotifyMessageVO[]) => {
  selectedIds.value = array?.map((row) => row.id) ?? []
}

const openRouteMessage = async () => {
  const messageId = Number(route.query.messageId)
  if (Number.isFinite(messageId) && messageId > 0) {
    try {
      const detail = await NotifyMessageApi.getMyNotifyMessage(messageId)
      if (detail) await openDetail(detail)
    } catch {
      message.warning('消息不存在或当前账号无权查看')
    }
  }
}

watch(
  () => route.query.messageId,
  () => {
    void openRouteMessage()
  }
)

onMounted(async () => {
  await getList()
  await openRouteMessage()
})
</script>
