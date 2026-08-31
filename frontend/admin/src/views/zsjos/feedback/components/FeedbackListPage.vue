<template>
  <ContentWrap>
    <el-form :model="query" inline @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          class="!w-240px"
          clearable
          placeholder="反馈编号或标题"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" class="!w-160px" clearable placeholder="全部状态">
          <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="canAssign" label="处理人">
        <el-select
          v-model="query.assigneeUserId"
          class="!w-180px"
          clearable
          filterable
          placeholder="全部处理人"
        >
          <el-option
            v-for="item in candidates"
            :key="item.id"
            :label="item.nickname"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" />查询
        </el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <el-button link type="primary" @click="load">重新加载</el-button>
      </template>
    </el-alert>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" row-key="id" stripe>
      <el-table-column label="编号" prop="feedbackNo" min-width="170" fixed="left">
        <template #default="{ row }">
          <el-badge :is-dot="row.unread" class="feedback-unread-badge">
            <el-button v-if="canRead" link type="primary" @click="openDetail(row.id)">
              {{ row.feedbackNo }}
            </el-button>
            <span v-else>{{ row.feedbackNo }}</span>
          </el-badge>
        </template>
      </el-table-column>
      <el-table-column label="标题" prop="title" min-width="240" show-overflow-tooltip />
      <el-table-column label="提交人" prop="submitterName" min-width="120" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前处理人" min-width="130">
        <template #default="{ row }">{{ row.assigneeName || '未分派' }}</template>
      </el-table-column>
      <el-table-column
        label="最新回复"
        prop="latestReplySummary"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{ row.latestReplySummary || '-' }}</template>
      </el-table-column>
      <el-table-column label="最后更新" min-width="170">
        <template #default="{ row }">{{ formatDate(row.lastActivityAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canRead" link type="primary" @click="openDetail(row.id)">查看</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty :description="`暂无${title}`" /></template>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <el-drawer v-model="detailOpen" :title="detail?.feedbackNo || `${title}详情`" size="760px">
    <div v-loading="detailLoading" class="feedback-detail">
      <el-alert
        v-if="detailError"
        :title="detailError"
        type="error"
        show-icon
        :closable="false"
        class="mb-16px"
      >
        <template #default
          ><el-button link type="primary" @click="reloadDetail">重试</el-button></template
        >
      </el-alert>
      <template v-else-if="detail">
        <div class="feedback-detail-heading">
          <div>
            <h3>{{ detail.title }}</h3>
            <span>
              {{ detail.submitterName || '未知提交人' }} · {{ formatDate(detail.createTime) }}
            </span>
          </div>
          <el-tag :type="statusTag(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="反馈编号">{{ detail.feedbackNo }}</el-descriptions-item>
          <el-descriptions-item label="当前处理人">{{
            detail.assigneeName || '未分派'
          }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.supportTypeLabel" label="支持类型">
            {{ detail.supportTypeLabel }}
          </el-descriptions-item>
          <el-descriptions-item label="最后更新">
            {{ formatDate(detail.lastActivityAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.rejectReason"
          :title="`审批驳回：${detail.rejectReason}`"
          type="error"
          show-icon
          :closable="false"
          class="mt-16px"
        />

        <h4 class="feedback-section-title">提交内容</h4>
        <el-descriptions :column="1" border>
          <el-descriptions-item
            v-for="field in detail.fields || []"
            :key="field.key"
            :label="field.label"
          >
            <template v-if="fileValues(detail.values?.[field.key]).length">
              <div v-for="file in fileValues(detail.values?.[field.key])" :key="file.id">
                <el-link v-if="file.url" :href="file.url" target="_blank" type="primary">
                  {{ file.name || `附件 ${file.id}` }}
                </el-link>
                <span v-else>{{ file.name || `附件 ${file.id}` }}</span>
              </div>
            </template>
            <span v-else>{{ displayValue(detail.values?.[field.key]) }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.completedResult">
          <h4 class="feedback-section-title">处理结果</h4>
          <p class="feedback-result">{{ detail.completedResult }}</p>
          <AttachmentLinks :items="detail.resultAttachments || []" />
        </template>

        <h4 class="feedback-section-title">沟通记录</h4>
        <el-timeline v-if="detail.replies?.length">
          <el-timeline-item
            v-for="replyItem in detail.replies"
            :key="replyItem.id"
            :timestamp="formatDate(replyItem.createTime)"
            placement="top"
          >
            <strong>{{ replyItem.authorName || '未知用户' }}</strong>
            <el-tag class="ml-8px" size="small" type="info">
              {{ replyItem.authorType === 'EMPLOYEE' ? '员工' : '处理人员' }}
            </el-tag>
            <p class="feedback-reply-content">{{ replyItem.content }}</p>
            <AttachmentLinks :items="replyItem.attachments || []" />
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无沟通记录" :image-size="72" />

        <template v-if="detail.survey">
          <h4 class="feedback-section-title">满意度</h4>
          <el-alert
            v-if="detail.survey.status === 'PENDING'"
            title="已发起，等待员工评价"
            type="info"
            show-icon
            :closable="false"
          />
          <el-descriptions v-else :column="1" border>
            <el-descriptions-item
              v-for="field in detail.survey.fields"
              :key="field.key"
              :label="field.label"
            >
              {{ displayValue(detail.survey.values?.[field.key]) }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
    </div>

    <template #footer>
      <div class="feedback-drawer-actions">
        <el-button @click="detailOpen = false">关闭</el-button>
        <el-button v-if="canAssign && assignable" @click="assignOpen = true">
          {{ detail?.assigneeUserId ? '改派' : '分派' }}
        </el-button>
        <el-button v-if="canReply && detail?.canReply" @click="replyOpen = true">回复</el-button>
        <el-button v-if="canSurvey && detail?.canSurvey" @click="requestSurvey"
          >发起满意度</el-button
        >
        <el-button
          v-if="canComplete && detail?.canComplete"
          type="primary"
          @click="completeOpen = true"
        >
          完成
        </el-button>
      </div>
    </template>
  </el-drawer>

  <el-dialog
    v-model="assignOpen"
    :title="detail?.assigneeUserId ? '改派反馈' : '分派反馈'"
    width="420px"
  >
    <el-form label-position="top">
      <el-form-item label="处理人" required>
        <el-select v-model="assigneeUserId" filterable class="w-full" placeholder="请选择处理人">
          <el-option
            v-for="item in candidates"
            :key="item.id"
            :label="item.nickname"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="assignOpen = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="assign">确认</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="replyOpen" title="回复反馈" width="560px">
    <el-form label-position="top">
      <el-form-item label="回复内容" required>
        <el-input
          v-model="replyContent"
          type="textarea"
          :rows="5"
          maxlength="5000"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="附件">
        <FeedbackUpload v-model="replyAttachments" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="replyOpen = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="reply">发送</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="completeOpen" title="完成反馈" width="560px">
    <el-form label-position="top">
      <el-form-item label="处理结果" required>
        <el-input
          v-model="completeResult"
          type="textarea"
          :rows="5"
          maxlength="5000"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="附件">
        <FeedbackUpload v-model="completeAttachments" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="completeOpen = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="complete">确认完成</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { computed, defineComponent, h, onMounted, reactive, ref, resolveComponent } from 'vue'
import * as FeedbackApi from '@/api/zsjos/feedback'
import { useMessage } from '@/hooks/web/useMessage'
import { useUserStore } from '@/store/modules/user'
import { formatDate } from '@/utils/formatTime'

const props = defineProps<{
  feedbackType: Exclude<FeedbackApi.FeedbackType, 'SURVEY'>
  title: string
}>()

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const error = ref('')
const list = ref<FeedbackApi.FeedbackRecord[]>([])
const total = ref(0)
const query = reactive<FeedbackApi.FeedbackPageParams>({ pageNo: 1, pageSize: 10 })
const candidates = ref<FeedbackApi.FeedbackUserOption[]>([])
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref<number>()
const detail = ref<FeedbackApi.FeedbackRecord>()
const saving = ref(false)
const assignOpen = ref(false)
const assigneeUserId = ref<number>()
const replyOpen = ref(false)
const replyContent = ref('')
const replyAttachments = ref<FeedbackApi.FeedbackAttachment[]>([])
const completeOpen = ref(false)
const completeResult = ref('')
const completeAttachments = ref<FeedbackApi.FeedbackAttachment[]>([])

const statusOptions = [
  { value: 'APPROVING', label: '审批中' },
  { value: 'APPROVAL_REJECTED', label: '审批驳回' },
  { value: 'WAITING', label: '待处理' },
  { value: 'IN_PROGRESS', label: '处理中' },
  { value: 'COMPLETED', label: '已完成' }
]

const permission = (value: string) =>
  userStore.getPermissions.has('*:*:*') || userStore.getPermissions.has(value)
const canRead = computed(() => permission('zsjos:feedback:query-admin'))
const canAssign = computed(() => permission('zsjos:feedback:assign'))
const canReply = computed(() => permission('zsjos:feedback:reply'))
const canComplete = computed(() => permission('zsjos:feedback:complete'))
const canSurvey = computed(() => permission('zsjos:feedback:survey'))
const assignable = computed(() =>
  detail.value ? ['WAITING', 'IN_PROGRESS'].includes(detail.value.status) : false
)
const statusLabel = (status: string) =>
  statusOptions.find((item) => item.value === status)?.label || status
const statusTag = (status: string): 'success' | 'info' | 'warning' | 'danger' =>
  status === 'COMPLETED'
    ? 'success'
    : status === 'APPROVAL_REJECTED'
      ? 'danger'
      : status === 'IN_PROGRESS'
        ? 'warning'
        : 'info'

const fileValues = (value: unknown): FeedbackApi.FeedbackAttachment[] =>
  Array.isArray(value) && value.every((item) => item && typeof item === 'object' && 'id' in item)
    ? (value as FeedbackApi.FeedbackAttachment[])
    : []

const displayValue = (value: unknown) => {
  if (value === undefined || value === null || value === '') return '-'
  if (typeof value === 'object' && !Array.isArray(value)) {
    const snapshot = value as Record<string, unknown>
    return String(snapshot.label ?? snapshot.value ?? '-')
  }
  if (Array.isArray(value)) return value.map((item) => displayValue(item)).join('、')
  return String(value)
}

const AttachmentLinks = defineComponent({
  props: { items: { type: Array as () => FeedbackApi.FeedbackAttachment[], default: () => [] } },
  setup(componentProps) {
    return () =>
      h(
        'div',
        { class: 'feedback-attachment-links' },
        componentProps.items.map((item) =>
          item.url
            ? h(
                'a',
                { href: item.url, target: '_blank', rel: 'noreferrer' },
                item.name || `附件 ${item.id}`
              )
            : h('span', item.name || `附件 ${item.id}`)
        )
      )
  }
})

const FeedbackUpload = defineComponent({
  props: { modelValue: { type: Array as () => FeedbackApi.FeedbackAttachment[], required: true } },
  emits: ['update:modelValue'],
  setup(componentProps, { emit }) {
    const upload = async (options: any) => {
      try {
        const file = await FeedbackApi.uploadFeedbackFile(options.file as File)
        emit('update:modelValue', [...componentProps.modelValue, file])
        options.onSuccess?.(file)
      } catch (cause) {
        options.onError?.(cause)
      }
    }
    const remove = (id: number) =>
      emit(
        'update:modelValue',
        componentProps.modelValue.filter((item) => item.id !== id)
      )
    return () =>
      h('div', { class: 'feedback-upload' }, [
        h(
          resolveComponent('el-upload') as any,
          {
            httpRequest: upload,
            showFileList: false,
            disabled: componentProps.modelValue.length >= 20
          },
          () => h(resolveComponent('el-button') as any, null, () => '上传附件')
        ),
        ...componentProps.modelValue.map((item) =>
          h('div', { class: 'feedback-upload-item', key: item.id }, [
            h('span', item.name || `附件 ${item.id}`),
            h(
              resolveComponent('el-button') as any,
              { link: true, type: 'danger', onClick: () => remove(item.id) },
              () => '移除'
            )
          ])
        )
      ])
  }
})

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await FeedbackApi.getFeedbackPage(props.feedbackType, query)
    list.value = data.list
    total.value = data.total
  } catch (cause: any) {
    list.value = []
    total.value = 0
    error.value = cause?.message || '反馈列表加载失败'
  } finally {
    loading.value = false
  }
}

const loadCandidates = async () => {
  if (!canAssign.value) return
  candidates.value = await FeedbackApi.getFeedbackCandidates(props.feedbackType)
}

const handleQuery = () => {
  query.pageNo = 1
  load()
}
const resetQuery = () => {
  query.keyword = undefined
  query.status = undefined
  query.assigneeUserId = undefined
  handleQuery()
}

const reloadDetail = async () => {
  if (!detailId.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await FeedbackApi.getFeedback(detailId.value)
    assigneeUserId.value = detail.value.assigneeUserId
  } catch (cause: any) {
    detail.value = undefined
    detailError.value = cause?.message || '反馈详情加载失败'
  } finally {
    detailLoading.value = false
  }
}
const openDetail = async (id: number) => {
  detailId.value = id
  detailOpen.value = true
  await reloadDetail()
}

const afterAction = async (successMessage: string) => {
  message.success(successMessage)
  await Promise.all([reloadDetail(), load()])
}
const assign = async () => {
  if (!detail.value || !assigneeUserId.value) return message.warning('请选择处理人')
  saving.value = true
  try {
    await FeedbackApi.assignFeedback(detail.value.id, assigneeUserId.value, detail.value.version)
    assignOpen.value = false
    await afterAction(detail.value.assigneeUserId ? '反馈已改派' : '反馈已分派')
  } finally {
    saving.value = false
  }
}
const reply = async () => {
  if (!detail.value || !replyContent.value.trim()) return message.warning('请填写回复内容')
  saving.value = true
  try {
    await FeedbackApi.replyFeedback(
      detail.value.id,
      replyContent.value.trim(),
      replyAttachments.value.map((item) => item.id),
      detail.value.version
    )
    replyOpen.value = false
    replyContent.value = ''
    replyAttachments.value = []
    await afterAction('回复已发送')
  } finally {
    saving.value = false
  }
}
const complete = async () => {
  if (!detail.value || !completeResult.value.trim()) return message.warning('请填写处理结果')
  saving.value = true
  try {
    await FeedbackApi.completeFeedback(
      detail.value.id,
      completeResult.value.trim(),
      completeAttachments.value.map((item) => item.id),
      detail.value.version
    )
    completeOpen.value = false
    completeResult.value = ''
    completeAttachments.value = []
    await afterAction('反馈已完成')
  } finally {
    saving.value = false
  }
}
const requestSurvey = async () => {
  if (!detail.value) return
  await message.confirm('每条反馈只能发起一次满意度调研，确认发起吗？')
  saving.value = true
  try {
    await FeedbackApi.requestFeedbackSurvey(detail.value.id, detail.value.version)
    await afterAction('满意度调研已发起')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([load(), loadCandidates()])
})
</script>

<style scoped>
.feedback-unread-badge :deep(.el-badge__content.is-fixed) {
  top: 8px;
}

.feedback-detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.feedback-detail-heading h3 {
  margin: 0 0 6px;
  font-size: 18px;
}

.feedback-detail-heading span {
  color: var(--el-text-color-secondary);
}

.feedback-section-title {
  margin: 24px 0 12px;
  font-size: 15px;
}

.feedback-result,
.feedback-reply-content {
  margin: 8px 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.feedback-attachment-links,
.feedback-upload {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.feedback-upload-item {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
}

.feedback-drawer-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

@media (width <= 768px) {
  .feedback-detail-heading {
    flex-direction: column;
  }
}
</style>
