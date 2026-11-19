<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { hasPermission } from '@/directives/permission/hasPermi'
import { formatDate } from '@/utils/formatTime'
import {
  actOnFinanceOrder,
  commentOnFinanceOrder,
  getFinanceOrderApprovalDetail,
  getFinanceOrderSignCandidates,
  type OrderApprovalAction,
  type OrderApprovalDetail,
  type OrderApprovalEntry
} from '@/api/zsjos/salesOrderApproval'
import OrderHistoryFacts from '../components/OrderHistoryFacts.vue'
import OrderProductSummary from '../components/OrderProductSummary.vue'

const props = defineProps<{
  taskId: string
  entry: OrderApprovalEntry
  done?: boolean
  processInstanceId?: string
}>()
const emit = defineEmits<{ changed: [completed: boolean] }>()
const message = useMessage()
const order = ref<OrderApprovalDetail>()
const candidateOptions = ref<Array<{ id: number; nickname: string }>>()
const action = ref<OrderApprovalAction>()
const reason = ref('')
const signType = ref<'before' | 'after'>('before')
const userIds = ref<number[]>([])
const loading = ref(false)
const candidateLoading = ref(false)
const saving = ref(false)
const completed = ref(false)
const comment = ref('')
const commenting = ref(false)
const commentError = ref('')
const error = ref('')
const actionError = ref('')
let generation = 0

const labels = { approve: '通过', reject: '驳回', sign: '加签' }
const statusLabels: Record<string, string> = {
  pending_approval: '待审批',
  revision_required: '待修改',
  effective: '已生效',
  superseded: '已被替代',
  terminated: '已终止'
}
const taskStatusLabels: Record<number, string> = {
  [-2]: '已跳过',
  [-1]: '未开始',
  0: '待审批',
  1: '审批中',
  2: '已通过',
  3: '已驳回',
  4: '已取消',
  5: '已退回',
  7: '等待后加签'
}
const facts = [
  { key: 'orderNo', label: '订单编号' },
  { key: 'status', label: '订单状态' },
  { key: 'approvalRoundNo', label: '审批轮次' },
  { key: 'formalOwnerIdentityLabel', label: '成交归属身份' },
  { key: 'buyerName', label: '付款人' },
  { key: 'studentName', label: '学员' },
  { key: 'studentMobile', label: '学员手机号' },
  { key: 'studentWechatId', label: '学员微信' },
  { key: 'provinceName', label: '省份' },
  { key: 'cityName', label: '城市' },
  { key: 'totalAmount', label: '订单金额' },
  { key: 'customerPaidAt', label: '付款时间' },
  { key: 'submittedAt', label: '提交时间' },
  { key: 'agreedExamMonth', label: '商定考试月份' },
  { key: 'classType', label: '班型' },
  { key: 'studentSpecialRequirements', label: '学员特殊要求' },
  { key: 'materialDeliveryContact', label: '资料寄送联系方式' },
  { key: 'giftShippingAddress', label: '赠品寄送地址' },
  { key: 'remark', label: '备注' },
  { key: 'repurchaseReason', label: '复购原因' },
  { key: 'decisionReason', label: '审批意见' },
  { key: 'terminationReason', label: '终止原因' }
]
const displayFact = (key: string) => {
  const value =
    key === 'agreedExamMonth'
      ? order.value?.agreedExamMonth || order.value?.agreedExamTime
      : order.value?.[key]
  if (value == null || value === '') return '—'
  if (key === 'status') return statusLabels[String(value)] || String(value)
  if (key === 'totalAmount') return `¥${Number(value).toFixed(2)}`
  if (key === 'submittedAt' || key === 'customerPaidAt') return formatDate(value as string)
  return String(value)
}
const task = computed(() => order.value?.approvalTask)
const mayAct = computed(
  () =>
    !props.done &&
    !completed.value &&
    task.value?.id === props.taskId &&
    task.value.canAct &&
    hasPermission([props.entry === 'business' ? 'zsjos:sales-order:review' : 'bpm:task:update'])
)
const maySign = computed(() => mayAct.value && task.value?.canSign)
const reasonRequired = computed(
  () => action.value !== 'approve' || task.value?.reasonRequire !== false
)

const load = async () => {
  const current = ++generation
  loading.value = true
  error.value = ''
  order.value = undefined
  candidateOptions.value = undefined
  candidateLoading.value = false
  try {
    const result = await getFinanceOrderApprovalDetail(
      props.taskId,
      props.entry,
      Boolean(props.done || completed.value)
    )
    if (current !== generation) return
    if (props.processInstanceId && result.processInstanceId !== props.processInstanceId) {
      throw new Error('审批任务与当前流程不匹配')
    }
    order.value = result
  } catch (cause) {
    if (current === generation)
      error.value = cause instanceof Error ? cause.message : '审批详情加载失败'
  } finally {
    if (current === generation) loading.value = false
  }
}

const loadCandidates = async () => {
  const current = generation
  candidateLoading.value = true
  actionError.value = ''
  try {
    const result = await getFinanceOrderSignCandidates(props.taskId, props.entry)
    if (current === generation) candidateOptions.value = result
  } catch (cause) {
    if (current === generation) {
      actionError.value = cause instanceof Error ? cause.message : '加签人员加载失败'
    }
  } finally {
    if (current === generation) candidateLoading.value = false
  }
}

const choose = (next: OrderApprovalAction) => {
  action.value = next
  reason.value = ''
  userIds.value = []
  signType.value = 'before'
  actionError.value = ''
  if (next === 'sign' && !candidateOptions.value && !candidateLoading.value) void loadCandidates()
}

const submit = async () => {
  const currentAction = action.value
  if (
    saving.value ||
    commenting.value ||
    !currentAction ||
    !mayAct.value ||
    (currentAction === 'sign' && !maySign.value)
  )
    return
  if (reasonRequired.value && !reason.value.trim()) return message.warning('请填写审批意见')
  if (currentAction === 'sign' && !userIds.value.length) return message.warning('请选择加签人员')
  const current = generation
  saving.value = true
  actionError.value = ''
  try {
    await actOnFinanceOrder(props.entry, {
      taskId: props.taskId,
      action: currentAction,
      reason: reason.value.trim(),
      ...(currentAction === 'sign' ? { type: signType.value, userIds: userIds.value } : {})
    })
    if (current !== generation) return
    completed.value = currentAction !== 'sign'
    action.value = undefined
    message.success(`已${labels[currentAction]}`)
    emit('changed', completed.value)
    if (!completed.value || props.entry === 'bpm') await load()
  } catch (cause) {
    if (current !== generation) return
    actionError.value = cause instanceof Error ? cause.message : '操作失败，请刷新任务状态'
    action.value = undefined
    emit('changed', false)
    await load()
  } finally {
    saving.value = false
  }
}

const submitComment = async () => {
  const content = comment.value.trim()
  if (!mayAct.value || commenting.value || saving.value || !content) return
  const current = generation
  commenting.value = true
  commentError.value = ''
  try {
    await commentOnFinanceOrder(props.entry, props.taskId, content)
    if (generation !== current) return
    comment.value = ''
    action.value = undefined
    message.success('评论已发送')
    await load()
  } catch (cause) {
    if (generation === current) {
      commentError.value = cause instanceof Error ? cause.message : '评论发送失败'
    }
  } finally {
    commenting.value = false
  }
}

watch(
  () => [props.taskId, props.entry, props.done, props.processInstanceId],
  () => {
    action.value = undefined
    actionError.value = ''
    completed.value = false
    comment.value = ''
    commentError.value = ''
    void load()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  generation++
})
</script>

<template>
  <section v-loading="loading" aria-label="成交订单审批详情">
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <el-alert v-if="actionError" :title="actionError" type="error" :closable="false" />
    <template v-if="order">
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="fact in facts" :key="fact.key" :label="fact.label">
          {{ displayFact(fact.key) }}
        </el-descriptions-item>
        <el-descriptions-item label="赠品">
          {{
            order.giftItemSnapshots
              ?.map((item) => item.path?.join(' / ') || item.name)
              .join('、') || '—'
          }}
        </el-descriptions-item>
      </el-descriptions>
      <OrderHistoryFacts :order="order" />
      <h4>成交产品</h4>
      <OrderProductSummary :items="order.items" />
      <p v-if="!order.items?.length">暂无产品明细</p>
      <h4>付款凭证</h4>
      <ul v-if="order.paymentVouchers?.length">
        <li v-for="file in order.paymentVouchers" :key="file.infraFileId">
          <el-link
            v-if="file.fileUrl"
            :href="file.fileUrl"
            target="_blank"
            rel="noopener noreferrer"
            type="primary"
          >
            {{ file.originalName || '查看凭证' }}
          </el-link>
          <span v-else>{{ file.originalName || '付款凭证' }}（暂不可用）</span>
        </li>
      </ul>
      <p v-else>暂无付款凭证</p>
      <h4>审批记录</h4>
      <el-timeline v-if="order.approvalProgress?.nodes?.length">
        <el-timeline-item v-for="node in order.approvalProgress.nodes" :key="node.id">
          <strong>{{ node.name }} · {{ taskStatusLabels[node.status] || '—' }}</strong>
          <div v-for="record in node.tasks" :key="record.id" class="approval-record">
            {{ record.assigneeName || record.ownerName || '未分配' }} ·
            {{ taskStatusLabels[record.status] || '—' }}
            <span v-if="record.endTime || record.createTime">
              · {{ formatDate(record.endTime || record.createTime) }}</span
            >
            <div v-if="record.reason">{{ record.reason }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <p v-else>暂无审批记录</p>
      <h4>流程评论</h4>
      <ul v-if="order.approvalComments?.length">
        <li v-for="item in order.approvalComments" :key="item.id" class="approval-record">
          <strong>{{ item.user?.nickname || '系统' }}</strong>
          <span v-if="item.createTime"> · {{ formatDate(item.createTime) }}</span>
          <div class="approval-comment-text">{{ item.message }}</div>
        </li>
      </ul>
      <p v-else>暂无流程评论</p>
      <el-form v-if="mayAct" @submit.prevent="submitComment">
        <el-alert v-if="commentError" :title="commentError" type="error" :closable="false" />
        <el-form-item label="评论">
          <el-input
            v-model="comment"
            type="textarea"
            maxlength="500"
            show-word-limit
            :disabled="saving || commenting"
          />
        </el-form-item>
        <el-button
          type="primary"
          plain
          native-type="submit"
          :loading="commenting"
          :disabled="saving || !comment.trim()"
        >
          发送评论
        </el-button>
      </el-form>

      <template v-if="mayAct">
        <div class="approval-actions">
          <el-button type="primary" :disabled="saving || commenting" @click="choose('approve')"
            >通过</el-button
          >
          <el-button type="danger" plain :disabled="saving || commenting" @click="choose('reject')"
            >驳回</el-button
          >
          <el-button v-if="maySign" :disabled="saving || commenting" @click="choose('sign')"
            >加签</el-button
          >
        </div>
        <el-form
          v-if="action"
          label-position="top"
          class="approval-actions"
          @submit.prevent="submit"
        >
          <template v-if="action === 'sign' && maySign">
            <el-form-item label="加签方式" required>
              <el-radio-group v-model="signType" :disabled="saving">
                <el-radio value="before">前加签</el-radio>
                <el-radio value="after">后加签</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="加签人员" required>
              <el-select
                v-model="userIds"
                multiple
                filterable
                :loading="candidateLoading"
                :disabled="saving"
              >
                <el-option
                  v-for="user in candidateOptions"
                  :key="user.id"
                  :value="user.id"
                  :label="user.nickname"
                />
              </el-select>
              <el-button
                v-if="actionError && !candidateOptions"
                link
                type="primary"
                @click="loadCandidates"
                >重试</el-button
              >
            </el-form-item>
          </template>
          <el-form-item label="审批意见" :required="reasonRequired">
            <el-input
              v-model="reason"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              :disabled="saving"
            />
          </el-form-item>
          <el-button :disabled="saving" @click="action = undefined">取消</el-button>
          <el-button
            native-type="submit"
            type="primary"
            :loading="saving"
            :disabled="commenting || (action === 'sign' && (!maySign || !candidateOptions?.length))"
          >
            确认{{ labels[action] }}
          </el-button>
        </el-form>
      </template>
      <el-alert
        v-else-if="!done && !completed"
        title="当前任务不可操作"
        type="info"
        :closable="false"
      />
    </template>
  </section>
</template>

<style scoped>
.approval-actions {
  margin-top: 12px;
}
.approval-record {
  margin-top: 4px;
  overflow-wrap: anywhere;
}
.approval-comment-text {
  white-space: pre-wrap;
}
</style>
