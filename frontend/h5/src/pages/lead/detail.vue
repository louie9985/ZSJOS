<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isAxiosError } from 'axios'
import { showToast, showConfirmDialog, showImagePreview } from 'vant'
import { getDictByType, getLeadAppeals, getLeadDetail, getPartnerLeadActivity, urgeLead, type LeadAppealItem, type LeadListItem, type LeadTimelineItem, type PartnerLeadActivity, type PartnerLeadActivityTone } from '@/api/lead'
import { formatAmount, formatDateTime, formatLeadNo, formatLeadStatus } from '@/utils/format'
import type { DictItem } from '@/stores/app'
import type { ApiDateValue } from '@/utils/format'

defineOptions({ name: 'LeadDetail' })

const route = useRoute()
const router = useRouter()
const leadId = Number(route.params.id)

const lead = ref<LeadListItem>()
const activity = ref<PartnerLeadActivity>()
const loading = ref(true)
const loadError = ref('')
const urgeLoading = ref(false)
const activityLoading = ref(false)
const activityError = ref('')
const appeals = ref<LeadAppealItem[]>([])
const appealsLoading = ref(false)
const appealsError = ref('')
const sourceChannelOptions = ref<DictItem[]>([])
const sourceChannelLoading = ref(false)
const sourceChannelError = ref('')

async function loadLead() {
  loading.value = true
  loadError.value = ''
  lead.value = undefined
  try {
    lead.value = await getLeadDetail(leadId)
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '客资详情加载失败'
    return
  } finally {
    loading.value = false
  }
  await Promise.all([loadActivity(), loadAppeals()])
}

async function loadSourceChannels() {
  sourceChannelLoading.value = true
  sourceChannelError.value = ''
  try {
    sourceChannelOptions.value = await getDictByType('zsjos_lead_source_channel')
  } catch (cause) {
    sourceChannelOptions.value = []
    sourceChannelError.value = cause instanceof Error ? cause.message : '来源渠道加载失败'
  } finally {
    sourceChannelLoading.value = false
  }
}

onMounted(() => { void loadLead(); void loadSourceChannels() })

const actions = computed(() => new Set(
  (lead.value?.availableActions || []).filter(action => action.enabled).map(action => action.code)
))
const hasAction = (...codes: string[]) => codes.some(code => actions.value.has(code))
const viewableAttachments = computed(() => (lead.value?.attachments || []).filter(file => file.fileUrl))
const detailProducts = computed(() => {
  const products = lead.value?.intendedProducts || []
  const primary = lead.value?.primaryProduct
  if (!primary || products.some(item => item.spuRef === primary.spuRef && item.skuRef === primary.skuRef)) return products
  return [...products, { ...primary, primary: true }]
})
const canViewTab = (tab: string) => !lead.value?.visibleTabs?.length || lead.value.visibleTabs.includes(tab)
const sourceChannelLabel = computed(() => {
  const value = lead.value?.sourceChannel
  if (!value) return '来源渠道未配置'
  return sourceChannelOptions.value.find(item => item.value === value)?.label || '来源渠道未配置'
})
function timelineTimestamp(value: ApiDateValue): number {
  if (Array.isArray(value)) {
    return new Date(
      value[0], (value[1] || 1) - 1, value[2] || 1,
      value[3] || 0, value[4] || 0, value[5] || 0
    ).getTime()
  }
  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? 0 : timestamp
}

function buildSnapshotTimeline(value?: LeadListItem): LeadTimelineItem[] {
  if (!value) return []
  const items: Array<LeadTimelineItem & { sequence: number }> = []
  const add = (
    id: string,
    type: string,
    title: string,
    description: string,
    occurredAt: ApiDateValue | undefined,
    tone: PartnerLeadActivityTone,
    sequence: number
  ) => {
    if (!occurredAt) return
    items.push({ id, type, title, description, occurredAt, tone, sequence })
  }

  add('lead-submitted', 'submitted', '客资已提交', '客资已进入平台处理流程', value.submittedAt, 'success', 10)
  add('lead-first-follow-up', 'first_follow_up', '销售已完成首次跟进', '销售已完成首次联系', value.currentAssignmentFirstFollowUpAt, 'success', 20)
  add('lead-qualification-started', 'qualification_started', '进入有效性判定', '客资已进入有效性判定阶段', value.qualificationStartedAt, 'primary', 30)
  if (value.qualificationStatus === 'valid') {
    add('lead-qualified-valid', 'qualified_valid', '已判定有效', value.validDescription || '客资已进入后续跟进阶段', value.qualifiedAt, 'success', 40)
  } else if (value.qualificationStatus === 'invalid') {
    const reason = value.invalidReasonLabelSnapshot || value.invalidReason
    add('lead-qualified-invalid', 'qualified_invalid', '已判定无效', reason ? `无效原因：${reason}` : '客资已判定无效', value.qualifiedAt, 'danger', 40)
  }
  add('lead-suspended', 'suspended', '处理已挂起', '当前处理已暂停', value.suspendedAt, 'warning', 50)
  add('lead-order-submitted', 'order_submitted', '成交资料已提交', '成交资料已进入审核流程', value.salesOrderSubmittedAt, 'primary', 60)
  add('lead-won', 'won', '已成交', '关联首购订单已生效', value.convertedAt, 'success', 70)
  add('lead-closed', 'closed', '客资已关闭', value.closeReason ? `关闭原因：${value.closeReason}` : '客资处理已结束', value.closedAt, 'default', 80)

  const sorted = items.sort((left, right) => {
    const timeDifference = timelineTimestamp(left.occurredAt) - timelineTimestamp(right.occurredAt)
    return timeDifference || left.sequence - right.sequence
  })
  return sorted.map(({ sequence: _sequence, ...item }, index) => ({
    ...item,
    current: index === sorted.length - 1
  }))
}

const snapshotTimeline = computed(() => buildSnapshotTimeline(lead.value))
const displayTimeline = computed(() => activity.value?.timeline?.length
  ? activity.value.timeline
  : snapshotTimeline.value)
const latestTimelineItem = computed(() => displayTimeline.value[displayTimeline.value.length - 1])
const statusTone = computed<PartnerLeadActivityTone>(() => {
  if (activity.value?.currentStatus?.tone) return activity.value.currentStatus.tone
  if (lead.value?.status === 'invalid') return 'danger'
  if (lead.value?.status === 'suspended') return 'warning'
  if (lead.value?.status === 'valid' || lead.value?.status === 'won') return 'success'
  if (lead.value?.status === 'submitted' || lead.value?.status === 'converted') return 'primary'
  return 'default'
})
const currentStatusText = computed(() => activity.value?.currentStatus?.text || formatLeadStatus(lead.value?.status || ''))
const currentStatusDescription = computed(() => activity.value?.currentStatus?.description
  || latestTimelineItem.value?.description || '请关注后续处理进度')
const currentStatusUpdatedAt = computed(() => activity.value?.currentStatus?.updatedAt
  || latestTimelineItem.value?.occurredAt || lead.value?.submittedAt)

function previewAttachment(index: number) {
  showImagePreview({
    images: viewableAttachments.value.map(file => file.fileUrl!),
    startPosition: index,
    closeable: true
  })
}

async function loadActivity() {
  activityLoading.value = true
  activityError.value = ''
  activity.value = undefined
  try {
    activity.value = await getPartnerLeadActivity(leadId)
  } catch (cause) {
    if (isAxiosError(cause) && cause.response?.status === 403) {
      activityError.value = '没有权限查看处理进度'
    } else if (isAxiosError(cause) && cause.response?.status === 401) {
      activityError.value = '登录已失效'
    } else if (isAxiosError(cause) && typeof cause.response?.data?.msg === 'string') {
      activityError.value = cause.response.data.msg
    } else {
      activityError.value = cause instanceof Error ? cause.message : '处理进度加载失败'
    }
  } finally {
    activityLoading.value = false
  }
}

async function loadAppeals() {
  appealsLoading.value = true
  appealsError.value = ''
  try {
    appeals.value = await getLeadAppeals(leadId)
  } catch (cause) {
    appeals.value = []
    appealsError.value = cause instanceof Error ? cause.message : '申诉记录加载失败'
  } finally {
    appealsLoading.value = false
  }
}

function previewUrls(urls: string[], index: number) {
  showImagePreview({ images: urls, startPosition: index, closeable: true })
}

const toneColor: Record<PartnerLeadActivityTone, string> = {
  default: 'var(--h5-text-secondary)',
  primary: 'var(--h5-primary)',
  success: 'var(--h5-success)',
  warning: 'var(--h5-warning)',
  danger: 'var(--h5-danger)'
}
const appealStatusMap: Record<string, string> = {
  submitted: '已提交', sales_manager_reviewing: '平台复核中', quality_reviewing: '平台仲裁中',
  chairman_reviewing: '最终审核中', overturned: '已改判', upheld: '已维持', withdrawn: '已撤回'
}
const appealStageMap: Record<string, string> = { sales_manager: '平台复核', quality: '平台仲裁', chairman: '最终审核' }

// 催办
async function handleUrge() {
  try {
    await showConfirmDialog({
      title: '催办客资',
      message: '确定催办此客资？同一客资每天最多催办一次。',
      confirmButtonText: '确认催办'
    })
    urgeLoading.value = true
    await urgeLead(leadId, '请尽快跟进')
    showToast({ message: '催办成功', type: 'success' })
  } catch {
    // 用户取消或请求失败
  } finally {
    urgeLoading.value = false
  }
}

function goSupplement() {
  router.push(`/lead/${leadId}/supplement`)
}

function goComplaint() {
  router.push(`/lead/${leadId}/complaint`)
}

function goAppeal() {
  router.push(`/lead/${leadId}/appeal`)
}
</script>

<template>
  <div class="page-container">
    <van-nav-bar title="客资详情" left-arrow @click-left="$router.back()" />
    <van-skeleton :loading="loading" :row="8" style="padding: 16px;">
      <van-empty v-if="loadError" :description="loadError" image="error">
        <van-button size="small" type="primary" @click="loadLead">重新加载</van-button>
      </van-empty>
      <template v-if="lead">
        <div class="card status-card">
          <div class="status-card__top">
            <span class="status-card__no">{{ formatLeadNo(lead.leadNo) }}</span>
            <span class="status-card__badge" :style="{ color: toneColor[statusTone] }">
              {{ currentStatusText }}
            </span>
          </div>
          <span class="status-card__eyebrow">当前处理进度</span>
          <strong class="status-card__headline">{{ currentStatusText }}</strong>
          <p class="status-card__description">{{ currentStatusDescription }}</p>
          <time v-if="currentStatusUpdatedAt" class="status-card__time">更新于 {{ formatDateTime(currentStatusUpdatedAt) }}</time>
        </div>

        <div class="card progress-card">
          <div class="section-title">状态时间线</div>
          <van-empty v-if="displayTimeline.length === 0" description="暂无状态记录" :image-size="64" />
          <div v-else class="progress-list">
            <div
              v-for="item in displayTimeline"
              :key="item.id"
              class="progress-item"
              :class="[`progress-item--${item.tone || 'default'}`, { 'is-current': item.current }]"
            >
              <div class="progress-item__rail" aria-hidden="true"><span class="progress-item__dot" /></div>
              <div class="progress-item__content">
                <div class="progress-item__head">
                  <strong>{{ item.title }}</strong>
                  <span v-if="item.current" class="progress-item__current">当前</span>
                </div>
                <p v-if="item.description">{{ item.description }}</p>
                <time>{{ formatDateTime(item.occurredAt) }}</time>
              </div>
            </div>
          </div>
        </div>

        <!-- 客户信息 -->
        <div class="card">
          <div class="section-title">客户信息</div>
          <van-cell-group :border="false">
            <van-cell title="姓名" :value="lead.submittedName" />
            <van-cell v-if="lead.submittedMobile" title="手机号" :value="lead.submittedMobile" />
            <van-cell v-if="lead.submittedWechatId" title="微信号" :value="lead.submittedWechatId" />
            <van-cell title="提交时间" :value="formatDateTime(lead.submittedAt)" />
            <van-cell v-if="lead.sourceUserName" title="来源人" :value="lead.sourceUserName" />
            <van-cell v-if="lead.providerOwnerNameSnapshot" title="提供方" :value="lead.providerOwnerNameSnapshot" />
            <van-cell v-if="lead.ownerUserName" title="负责销售" :value="lead.ownerUserName" />
            <van-cell v-if="lead.pendingAssigneeUserName" title="待接销售" :value="lead.pendingAssigneeUserName" />
            <van-cell v-if="lead.assignmentStatus === 'pending_acceptance'" title="处理状态" value="等待平台处理" />
            <van-cell title="来源类型" :value="lead.sourceLabel || '来源未配置'" />
            <van-cell title="来源渠道">
              <template #value>
                <span v-if="sourceChannelLoading">加载中...</span>
                <span v-else>{{ sourceChannelLabel }}</span>
                <van-button v-if="sourceChannelError" size="mini" type="primary" plain @click="loadSourceChannels">重试</van-button>
              </template>
            </van-cell>
            <van-cell title="客资分类" :value="lead.leadCategoryLabelSnapshot || lead.leadCategory" />
            <van-cell v-if="lead.closedAt" title="关闭时间" :value="formatDateTime(lead.closedAt)" />
            <van-cell v-if="lead.closeReason" title="关闭原因" :value="lead.closeReason" />
          </van-cell-group>
        </div>

        <!-- 意向课程 -->
        <div v-if="detailProducts.length > 0" class="card">
          <div class="section-title">意向课程</div>
          <div v-for="product in detailProducts" :key="`${product.spuRef}-${product.skuRef || ''}`" class="product-item">
            <div><strong>{{ product.spuName || '课程' }}</strong><p v-if="product.categoryName">{{ product.categoryName }}</p><p v-if="product.skuName">规格：{{ product.skuName }}</p><p v-if="product.selectedAttrValues">属性：{{ product.selectedAttrValues }}</p></div>
            <div><van-tag v-if="product.primary" type="primary" size="medium">主意向</van-tag><span v-if="product.price != null" class="product-price">¥{{ product.price }}</span></div>
          </div>
        </div>

        <div v-if="lead.validDescription" class="card"><div class="section-title">有效说明</div><p class="detail-text">{{ lead.validDescription }}</p></div>

        <div v-if="lead.qualificationDeadlineAt || lead.suspendedAt || lead.appealDeadlineAt || lead.currentAssignmentFirstFollowUpDeadlineAt" class="card">
          <div class="section-title">处理时限</div>
          <van-cell-group :border="false">
            <van-cell v-if="lead.currentAssignmentFirstFollowUpDeadlineAt" title="首次跟进截止" :value="formatDateTime(lead.currentAssignmentFirstFollowUpDeadlineAt)" />
            <van-cell v-if="lead.qualificationDeadlineAt" title="有效判定截止" :value="formatDateTime(lead.qualificationDeadlineAt)" />
            <van-cell v-if="lead.suspendedAt" title="挂起时间" :value="formatDateTime(lead.suspendedAt)" />
            <van-cell v-if="lead.appealDeadlineAt" title="申诉截止" :value="formatDateTime(lead.appealDeadlineAt)" />
          </van-cell-group>
        </div>

        <div v-if="lead.attachments?.length" class="card">
          <div class="section-title">图片附件</div>
          <div v-if="viewableAttachments.length" class="attachment-grid">
            <button
              v-for="(file, index) in viewableAttachments"
              :key="file.id"
              type="button"
              class="attachment-item"
              @click="previewAttachment(index)"
            >
              <img :src="file.fileUrl || ''" :alt="file.originalName" />
              <span>{{ file.originalName }}</span>
            </button>
          </div>
          <van-empty v-else description="附件暂时无法预览" :image-size="64" />
        </div>

        <div v-if="lead.invalidReason || lead.invalidDescription || lead.invalidEvidence?.length" class="card">
          <div class="section-title">无效说明</div>
          <van-cell-group :border="false">
            <van-cell v-if="lead.invalidReason" title="无效原因" :value="lead.invalidReasonLabelSnapshot || lead.invalidReason" />
            <van-cell v-if="lead.invalidDescription" title="补充说明" :label="lead.invalidDescription" />
          </van-cell-group>
          <div v-if="lead.invalidEvidence?.length" class="attachment-grid record-images">
            <button v-for="(file, index) in lead.invalidEvidence" :key="file.infraFileId" type="button" class="attachment-item" @click="previewUrls(lead.invalidEvidence!.map(item => item.fileUrl || '').filter(Boolean), index)">
              <img v-if="file.fileUrl" :src="file.fileUrl" :alt="file.originalName" />
            </button>
          </div>
        </div>

        <template v-else-if="activity">
        <div v-if="canViewTab('follow-ups')" class="card">
          <div class="section-title">跟进记录</div>
          <van-empty v-if="activity.followUps.length === 0" description="暂无跟进记录" :image-size="64" />
            <div v-else>
              <div v-for="item in activity.followUps" :key="item.id" class="record-item">
                <div class="record-head"><strong>{{ item.resultLabel || item.result }}</strong><time>{{ formatDateTime(item.occurredAt) }}</time></div>
                <p>跟进方式：{{ item.methodLabel || item.method }}</p>
                <p v-if="item.categoryAfterLabel && item.categoryAfter !== item.categoryBefore">客资分类：{{ item.categoryBeforeLabel || item.categoryBefore || '-' }} → {{ item.categoryAfterLabel }}</p>
                <p v-if="item.nextFollowUpAt">下次跟进：{{ formatDateTime(item.nextFollowUpAt) }}</p>
                <div v-if="item.images?.some(image => image.url)" class="attachment-grid record-images">
                  <button v-for="(image, index) in item.images.filter(image => image.url)" :key="image.infraFileId" type="button" class="attachment-item" @click="previewUrls(item.images.map(image => image.url || '').filter(Boolean), index)">
                    <img :src="image.url" :alt="image.originalName" />
                  </button>
                </div>
              </div>
            </div>
        </div>

        <div v-if="canViewTab('flow-history')" class="card">
          <div class="section-title">收益明细</div>
          <van-empty v-if="activity.cashbackItems.length === 0" description="暂无收益记录" :image-size="64" />
          <div v-else>
            <div v-for="item in activity.cashbackItems" :key="item.id" class="record-item amount-row">
              <div><strong>{{ item.typeText }}</strong><p>{{ item.statusText }}<template v-if="item.availableAt"> · {{ formatDateTime(item.availableAt) }}</template></p></div>
              <b>¥{{ formatAmount(item.amount) }}</b>
            </div>
          </div>
        </div>

        <div v-if="canViewTab('complaints')" class="card">
          <div class="section-title">投诉记录</div>
          <van-empty v-if="activity.complaints.length === 0" description="暂无投诉记录" :image-size="64" />
          <div v-else>
            <div v-for="item in activity.complaints" :key="item.id" class="record-item">
              <div class="record-head"><strong>{{ item.recordNo }}</strong><span>{{ item.statusText }}</span></div>
              <p>{{ item.content }}</p><p v-if="item.result">处理结果：{{ item.result }}</p><time>{{ formatDateTime(item.createdAt) }}</time>
            </div>
          </div>
        </div>

        <div v-if="canViewTab('orders')" class="card">
          <div class="section-title">订单记录</div>
          <van-empty v-if="activity.orders.length === 0" description="暂无关联订单" :image-size="64" />
          <div v-else>
            <div v-for="item in activity.orders" :key="item.id" class="record-item amount-row">
              <div><strong>{{ item.orderNo }}</strong><p>{{ item.statusText }}<template v-if="item.purchaseTypeText"> · {{ item.purchaseTypeText }}</template></p><time>{{ formatDateTime(item.createdAt) }}</time></div>
              <b>¥{{ formatAmount(item.totalAmount) }}</b>
            </div>
          </div>
        </div>
        </template>

        <div v-if="canViewTab('appeals')" class="card">
          <div class="section-title">申诉记录</div>
          <van-skeleton v-if="appealsLoading" :loading="true" :row="3" />
          <van-empty v-else-if="appealsError" :description="appealsError" image="error" :image-size="64">
            <van-button size="small" type="primary" @click="loadAppeals">重新加载</van-button>
          </van-empty>
          <van-empty v-else-if="appeals.length === 0" description="暂无申诉记录" :image-size="64" />
          <div v-else>
            <div v-for="item in appeals" :key="item.id" class="record-item">
              <div class="record-head"><strong>第 {{ item.roundNo }} 轮申诉</strong><span>{{ appealStatusMap[item.status] || item.status }}</span></div>
              <p>{{ item.reason }}</p>
              <p v-if="item.reviewStage">审核阶段：{{ appealStageMap[item.reviewStage] || item.reviewStage }}</p>
              <p v-if="item.invalidReasonSnapshot">原无效原因：{{ item.invalidReasonSnapshot }}</p>
              <p v-if="item.invalidDescriptionSnapshot">原无效说明：{{ item.invalidDescriptionSnapshot }}</p>
              <p v-if="item.decisionReason">处理结果：{{ item.decisionReason }}</p>
              <p v-if="item.evidence?.length">申诉证据：{{ item.evidence.length }} 个</p>
              <p v-if="item.invalidEvidenceSnapshot?.length">原无效证据：{{ item.invalidEvidenceSnapshot.length }} 个</p>
              <p v-if="item.decisionEvidence?.length">裁决证据：{{ item.decisionEvidence.length }} 个</p>
              <time>{{ formatDateTime(item.decidedAt || item.submittedAt) }}</time>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div v-if="actions.size > 0" class="detail-actions safe-area-bottom">
          <van-button
            v-if="actions.has('SUBMITTER_SUPPLEMENT')"
            size="small"
            round
            plain
            @click="goSupplement"
          >
            补充
          </van-button>
          <van-button
            v-if="hasAction('URGE', 'SUBMITTER_URGE')"
            size="small"
            round
            plain
            :loading="urgeLoading"
            @click="handleUrge"
          >
            催办
          </van-button>
          <van-button
            v-if="hasAction('CREATE_COMPLAINT', 'SUBMITTER_COMPLAINT')"
            size="small"
            round
            plain
            @click="goComplaint"
          >
            投诉
          </van-button>
          <van-button
            v-if="actions.has('CREATE_APPEAL')"
            size="small"
            round
            type="primary"
            @click="goAppeal"
          >
            申诉
          </van-button>
        </div>
      </template>
    </van-skeleton>
  </div>
</template>

<style scoped>
.status-card {
  border-top: 3px solid var(--h5-primary);
}
.status-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
.status-card__badge {
  flex: 0 0 auto;
  max-width: 46%;
  overflow: hidden;
  padding: 4px 10px;
  border: 1px solid currentColor;
  border-radius: 12px;
  background: var(--h5-bg);
  font-size: 12px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-card__no {
  min-width: 0;
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-card__eyebrow {
  display: block;
  margin-bottom: 4px;
  color: var(--h5-text-placeholder);
  font-size: 11px;
  line-height: 16px;
}
.status-card__headline {
  display: block;
  color: var(--h5-text-primary);
  font-size: 22px;
  line-height: 30px;
}
.status-card__description {
  margin-top: 8px;
  color: var(--h5-text-secondary);
  font-size: 13px;
  line-height: 20px;
  overflow-wrap: anywhere;
}
.status-card__time {
  display: block;
  margin-top: 12px;
  color: var(--h5-text-placeholder);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
}

.section-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--h5-text-primary);
  margin-bottom: 8px;
}
.progress-card {
  min-height: 154px;
}
.progress-list {
  padding-top: 4px;
}
.progress-item {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 10px;
  min-height: 72px;
}
.progress-item:last-child {
  min-height: 0;
}
.progress-item__rail {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 6px;
}
.progress-item:not(:last-child) .progress-item__rail::after {
  position: absolute;
  top: 18px;
  bottom: -6px;
  width: 2px;
  background: var(--h5-divider);
  content: '';
}
.progress-item__dot {
  position: relative;
  z-index: 1;
  width: 10px;
  height: 10px;
  border: 2px solid var(--h5-card-bg);
  border-radius: 50%;
  background: var(--h5-text-placeholder);
  box-shadow: 0 0 0 2px var(--h5-divider);
}
.progress-item--primary .progress-item__dot { background: var(--h5-primary); box-shadow: 0 0 0 3px var(--h5-primary-opacity); }
.progress-item--success .progress-item__dot { background: var(--h5-success); }
.progress-item--warning .progress-item__dot { background: var(--h5-warning); }
.progress-item--danger .progress-item__dot { background: var(--h5-danger); }
.progress-item__content {
  min-width: 0;
  padding: 0 0 18px;
}
.progress-item.is-current .progress-item__content {
  margin: -4px 0 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--h5-primary-opacity);
}
.progress-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-item__head strong {
  min-width: 0;
  color: var(--h5-text-primary);
  font-size: 14px;
  line-height: 20px;
  overflow-wrap: anywhere;
}
.progress-item__current {
  flex: 0 0 auto;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--h5-primary);
  color: #fff;
  font-size: 10px;
  line-height: 14px;
}
.progress-item__content p {
  margin: 4px 0 0;
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 18px;
  overflow-wrap: anywhere;
}
.progress-item__content time {
  display: block;
  margin-top: 5px;
  color: var(--h5-text-placeholder);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
}

.product-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
  border-bottom: 1px solid var(--h5-divider);
}
.product-item:last-child {
  border-bottom: none;
}
.attachment-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.attachment-item {
  min-width: 0;
  padding: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
}
.attachment-item img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 6px;
}
.attachment-item span {
  display: block;
  overflow: hidden;
  margin-top: 4px;
  color: var(--h5-text-secondary);
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.record-images{margin-top:10px}.record-item{padding:10px 0;border-bottom:1px solid var(--h5-divider);font-size:13px;color:var(--h5-text-secondary)}.record-item:last-child{border-bottom:0}.record-item p{margin:5px 0;line-height:1.5;overflow-wrap:anywhere}.record-item time{font-size:11px;color:var(--h5-text-placeholder)}.record-head{display:flex;align-items:center;justify-content:space-between;gap:10px;color:var(--h5-text-primary)}.record-head strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.amount-row{display:flex;align-items:center;justify-content:space-between;gap:12px}.amount-row>div{min-width:0}.amount-row strong{color:var(--h5-text-primary)}.amount-row>b{flex:0 0 auto;color:var(--h5-primary);font-size:15px;font-variant-numeric:tabular-nums}

.detail-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  background: var(--h5-card-bg);
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
  z-index: 10;
}
.detail-actions .van-button {
  flex: 1;
}
</style>
