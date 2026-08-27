<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog, showImagePreview } from 'vant'
import { getLeadAppeals, getLeadDetail, getPartnerLeadActivity, urgeLead, type LeadAppealItem, type LeadListItem, type PartnerLeadActivity } from '@/api/lead'
import { wasMockedEndpoint } from '@/api/mock'
import { formatAmount, formatDateTime, formatLeadNo, formatLeadStatus, maskMobile } from '@/utils/format'

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
const activityUsingMock = computed(() => wasMockedEndpoint(`/zsjos/lead/${leadId}/partner-activity`))

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

onMounted(loadLead)

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
    activityError.value = cause instanceof Error ? cause.message : '业务流转记录加载失败'
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

const statusColor: Record<string, string> = {
  submitted: 'var(--h5-info)',
  valid: 'var(--h5-success)',
  invalid: 'var(--h5-danger)',
  suspended: 'var(--h5-warning)',
  won: 'var(--h5-success)',
  converted: 'var(--h5-primary)',
  closed: 'var(--h5-text-secondary)'
}

const assignmentMap: Record<string, string> = {
  unassigned: '未分配',
  pending_acceptance: '待接单',
  owned: '已归属',
  public_pool: '公海',
  recycle_pending: '回收待处理',
  closed: '已结束'
}

const handlingStageMap: Record<string, string> = {
  first_follow_pending: '待首次跟进', qualification_pending: '待判定', following: '跟进中',
  deal_pending_approval: '成交待审核', won: '已成交', suspended: '已挂起'
}
const qualificationMap: Record<string, string> = { pending: '待判定', valid: '已判有效', invalid: '已判无效' }
const followUpMap: Record<string, string> = {
  first_follow_pending: '待首次跟进', following: '跟进中', deal_pending_approval: '成交待审核', won: '已成交'
}
const operationalMap: Record<string, string> = { active: '正常', suspended: '已挂起' }
const orderStatusMap: Record<string, string> = { pending_approval: '待审核', revision_required: '待修改', approved: '已通过', rejected: '已驳回' }
const appealStatusMap: Record<string, string> = {
  submitted: '已提交', sales_manager_reviewing: '主管复核中', quality_reviewing: '质控仲裁中',
  chairman_reviewing: '最终裁定中', overturned: '已改判', upheld: '已维持', withdrawn: '已撤回'
}
const appealStageMap: Record<string, string> = { sales_manager: '销售主管复核', quality: '质控仲裁', chairman: '最终裁定' }

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
    <van-notice-bar v-if="activityUsingMock" color="#8a6100" background="#fff7df" left-icon="info-o">
      客资业务流转为开发环境演示数据
    </van-notice-bar>
    <van-skeleton :loading="loading" :row="8" style="padding: 16px;">
      <van-empty v-if="loadError" :description="loadError" image="error">
        <van-button size="small" type="primary" @click="loadLead">重新加载</van-button>
      </van-empty>
      <template v-if="lead">
        <!-- 状态卡片 -->
        <div class="card status-card">
          <div class="status-card__main">
            <span
              class="status-card__badge"
              :style="{ backgroundColor: statusColor[lead.status] || 'var(--h5-info)' }"
            >
              {{ formatLeadStatus(lead.status) }}
            </span>
            <span class="status-card__assignment">{{ assignmentMap[lead.assignmentStatus] || lead.assignmentStatus }}</span>
          </div>
          <div class="status-card__no">{{ formatLeadNo(lead.leadNo) }}</div>
        </div>

        <div v-if="lead.handlingStage || lead.qualificationStatus || lead.followUpStatus || lead.operationalStatus || lead.lastActivityAt || lead.nextFollowUpAt || lead.activeSalesOrderId || lead.pendingExpiresAt || lead.publicPoolAt || lead.qualificationStartedAt || lead.qualifiedByUserName || lead.recycleSourceOwnerUserName || lead.opportunity" class="card">
          <div class="section-title">处理概况</div>
          <van-cell-group :border="false">
            <van-cell v-if="lead.handlingStage" title="业务环节" :value="handlingStageMap[lead.handlingStage] || lead.handlingStage" />
            <van-cell v-if="lead.qualificationStatus" title="有效判定" :value="qualificationMap[lead.qualificationStatus] || lead.qualificationStatus" />
            <van-cell v-if="lead.followUpStatus" title="跟进状态" :value="followUpMap[lead.followUpStatus] || lead.followUpStatus" />
            <van-cell v-if="lead.operationalStatus" title="运行状态" :value="operationalMap[lead.operationalStatus] || lead.operationalStatus" />
            <van-cell v-if="lead.lastActivityAt" title="最近动态" :value="formatDateTime(lead.lastActivityAt)" />
            <van-cell v-if="lead.nextFollowUpAt" title="下次跟进" :value="formatDateTime(lead.nextFollowUpAt)" />
            <van-cell v-if="lead.pendingExpiresAt" title="待接截止" :value="formatDateTime(lead.pendingExpiresAt)" />
            <van-cell v-if="lead.publicPoolAt" title="进入公海" :value="formatDateTime(lead.publicPoolAt)" />
            <van-cell v-if="lead.qualificationStartedAt" title="判定开始" :value="formatDateTime(lead.qualificationStartedAt)" />
            <van-cell v-if="lead.qualifiedAt" title="判定时间" :value="formatDateTime(lead.qualifiedAt)" />
            <van-cell v-if="lead.qualifiedByUserName" title="判定人" :value="lead.qualifiedByUserName" />
            <van-cell v-if="lead.recycleSourceOwnerUserName" title="回收前销售" :value="lead.recycleSourceOwnerUserName" />
            <van-cell v-if="lead.salesOrderSubmittedAt" title="订单提交" :value="formatDateTime(lead.salesOrderSubmittedAt)" />
            <van-cell v-if="lead.convertedAt" title="成交时间" :value="formatDateTime(lead.convertedAt)" />
            <van-cell v-if="lead.activeSalesOrderId" title="当前订单" :value="orderStatusMap[lead.activeSalesOrderStatus || ''] || lead.activeSalesOrderStatus || '处理中'" />
            <van-cell v-if="lead.opportunity" title="商机状态" :value="lead.opportunity.status" />
            <van-cell v-if="lead.opportunity?.nextFollowUpAt" title="商机下次跟进" :value="formatDateTime(lead.opportunity.nextFollowUpAt)" />
          </van-cell-group>
        </div>

        <!-- 客户信息 -->
        <div class="card">
          <div class="section-title">客户信息</div>
          <van-cell-group :border="false">
            <van-cell title="姓名" :value="lead.submittedName" />
            <van-cell v-if="lead.submittedMobile" title="手机号" :value="maskMobile(lead.submittedMobile)" />
            <van-cell v-if="lead.submittedWechatId" title="微信号" :value="lead.submittedWechatId" />
            <van-cell title="提交时间" :value="formatDateTime(lead.submittedAt)" />
            <van-cell v-if="lead.sourceUserName" title="来源人" :value="lead.sourceUserName" />
            <van-cell v-if="lead.providerOwnerNameSnapshot" title="提供方" :value="lead.providerOwnerNameSnapshot" />
            <van-cell v-if="lead.ownerUserName" title="负责销售" :value="lead.ownerUserName" />
            <van-cell v-if="lead.pendingAssigneeUserName" title="待接销售" :value="lead.pendingAssigneeUserName" />
            <van-cell title="来源渠道" :value="lead.sourceLabel || lead.sourceChannel" />
            <van-cell title="客资分类" :value="lead.leadCategoryLabelSnapshot || lead.leadCategory" />
            <van-cell v-if="lead.dispatchMode" title="派单方式" :value="lead.dispatchMode" />
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

        <div v-if="activityLoading" class="card">
          <div class="section-title">业务流转记录</div>
          <van-skeleton :loading="true" :row="6" />
        </div>

        <div v-else-if="activityError" class="card activity-unavailable">
          <div class="section-title">业务流转记录</div>
          <van-empty :description="activityError" image="error" :image-size="64">
            <p v-if="activityUsingMock">后端尚未提供兼职端聚合接口，以下记录当前不可用。</p>
            <p v-else>当前账号暂时无法读取业务流转记录，请根据提示重试或联系管理员。</p>
            <van-button size="small" type="primary" @click="loadActivity">重新加载</van-button>
          </van-empty>
          <div class="unavailable-sections">
            <span>跟进记录</span><span>状态时间线</span><span>收益明细</span>
            <span>投诉记录</span><span>订单记录</span>
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
                <p v-if="item.remark">{{ item.remark }}</p>
                <div v-if="item.images?.some(image => image.url)" class="attachment-grid record-images">
                  <button v-for="(image, index) in item.images.filter(image => image.url)" :key="image.infraFileId" type="button" class="attachment-item" @click="previewUrls(item.images.map(image => image.url || '').filter(Boolean), index)">
                    <img :src="image.url" :alt="image.originalName" />
                  </button>
                </div>
              </div>
            </div>
        </div>

        <div v-if="canViewTab('flow-history')" class="card">
          <div class="section-title">状态时间线</div>
          <van-empty v-if="activity.timeline.length === 0" description="暂无状态流转" :image-size="64" />
          <div v-else>
            <div v-for="item in activity.timeline" :key="item.id" class="record-item timeline-item">
              <div class="record-head"><strong>{{ item.title }}</strong><time>{{ formatDateTime(item.occurredAt) }}</time></div>
              <p v-if="item.description">{{ item.description }}</p>
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
              <p v-if="item.reviewerUserName">处理人：{{ item.reviewerUserName }}</p>
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
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.status-card__main {
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-card__badge {
  color: #fff;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
}
.status-card__assignment {
  font-size: 13px;
  color: var(--h5-text-secondary);
}
.status-card__no {
  font-size: 12px;
  color: var(--h5-text-placeholder);
}

.section-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--h5-text-primary);
  margin-bottom: 8px;
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
.activity-unavailable p{margin:-8px 0 12px;color:var(--h5-text-secondary);font-size:12px}.unavailable-sections{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-top:12px}.unavailable-sections span{padding:8px;border-radius:6px;background:var(--h5-bg);color:var(--h5-text-placeholder);font-size:12px;text-align:center}.amount-row{display:flex;align-items:center;justify-content:space-between;gap:12px}.amount-row>div{min-width:0}.amount-row strong{color:var(--h5-text-primary)}.amount-row>b{flex:0 0 auto;color:var(--h5-primary);font-size:15px;font-variant-numeric:tabular-nums}.timeline-item{padding-left:14px;border-left:2px solid var(--h5-primary-opacity)}

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
