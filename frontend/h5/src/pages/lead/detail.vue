<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getLeadDetail, urgeLead, type LeadListItem } from '@/api/lead'
import { formatDateTime, formatLeadNo, formatLeadStatus, maskMobile } from '@/utils/format'

defineOptions({ name: 'LeadDetail' })

const route = useRoute()
const router = useRouter()
const leadId = Number(route.params.id)

const lead = ref<LeadListItem>()
const loading = ref(true)
const loadError = ref('')
const urgeLoading = ref(false)

async function loadLead() {
  loading.value = true
  loadError.value = ''
  try {
    lead.value = await getLeadDetail(leadId)
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '客资详情加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadLead)

const actions = computed(() => new Set(
  (lead.value?.availableActions || []).filter(action => action.enabled).map(action => action.code)
))

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

        <!-- 客户信息 -->
        <div class="card">
          <div class="section-title">客户信息</div>
          <van-cell-group :border="false">
            <van-cell title="姓名" :value="lead.submittedName" />
            <van-cell v-if="lead.submittedMobile" title="手机号" :value="maskMobile(lead.submittedMobile)" />
            <van-cell title="提交时间" :value="formatDateTime(lead.submittedAt)" />
            <van-cell v-if="lead.ownerUserName" title="负责销售" :value="lead.ownerUserName" />
            <van-cell title="来源渠道" :value="lead.sourceChannel" />
            <van-cell title="客资分类" :value="lead.leadCategory" />
          </van-cell-group>
        </div>

        <!-- 意向课程 -->
        <div v-if="lead.intendedProducts && (lead.intendedProducts as unknown[]).length > 0" class="card">
          <div class="section-title">意向课程</div>
          <div v-for="product in lead.intendedProducts" :key="`${product.spuRef}-${product.skuRef || ''}`" class="product-item">
            <span>{{ product.spuName || '课程' }}</span>
            <van-tag v-if="product.primary" type="primary" size="medium">主意向</van-tag>
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
            v-if="actions.has('URGE')"
            size="small"
            round
            plain
            :loading="urgeLoading"
            @click="handleUrge"
          >
            催办
          </van-button>
          <van-button
            v-if="actions.has('CREATE_COMPLAINT')"
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
