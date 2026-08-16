<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import { getLeadDetail, urgeLead, type LeadListItem } from '@/api/lead'
import { formatDateTime, maskMobile } from '@/utils/format'

defineOptions({ name: 'LeadDetail' })

const route = useRoute()
const router = useRouter()
const leadId = Number(route.params.id)

const lead = ref<LeadListItem>()
const loading = ref(true)
const urgeLoading = ref(false)

onMounted(async () => {
  try {
    lead.value = await getLeadDetail(leadId)
  } finally {
    loading.value = false
  }
})

const actions = computed(() => lead.value?.availableActions || [])

const statusMap: Record<string, { text: string; color: string }> = {
  submitted: { text: '已提交', color: 'var(--h5-info)' },
  valid: { text: '有效', color: 'var(--h5-success)' },
  invalid: { text: '无效', color: 'var(--h5-danger)' },
  suspended: { text: '已挂起', color: 'var(--h5-warning)' },
  converted: { text: '已转化', color: 'var(--h5-primary)' },
  closed: { text: '已关闭', color: 'var(--h5-text-secondary)' }
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
    const { value } = await showDialog({
      title: '催办客资',
      message: '确定催办此客资？同一客资每天最多催办一次。',
      showCancelButton: true,
      confirmButtonText: '确认催办'
    }).catch(() => ({ value: false })) as unknown as { value: boolean }

    // showDialog with showCancelButton doesn't return value directly
    // Instead just proceed since it resolved (didn't throw)
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
      <template v-if="lead">
        <!-- 状态卡片 -->
        <div class="card status-card">
          <div class="status-card__main">
            <span
              class="status-card__badge"
              :style="{ backgroundColor: statusMap[lead.status]?.color || 'var(--h5-info)' }"
            >
              {{ statusMap[lead.status]?.text || lead.status }}
            </span>
            <span class="status-card__assignment">{{ assignmentMap[lead.assignmentStatus] || lead.assignmentStatus }}</span>
          </div>
          <div class="status-card__no">{{ lead.leadNo }}</div>
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
          <div v-for="(product, idx) in (lead.intendedProducts as any[])" :key="idx" class="product-item">
            <span>{{ product.spuNameSnapshot || product.productNameSnapshot || '课程' }}</span>
            <van-tag v-if="product.isPrimary" type="primary" size="medium">主意向</van-tag>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div v-if="actions.length > 0" class="detail-actions safe-area-bottom">
          <van-button
            v-if="actions.includes('supplement') || actions.includes('submitter-supplement')"
            size="small"
            round
            plain
            @click="goSupplement"
          >
            补充
          </van-button>
          <van-button
            v-if="actions.includes('urge')"
            size="small"
            round
            plain
            :loading="urgeLoading"
            @click="handleUrge"
          >
            催办
          </van-button>
          <van-button
            v-if="actions.includes('complaint')"
            size="small"
            round
            plain
            @click="goComplaint"
          >
            投诉
          </van-button>
          <van-button
            v-if="actions.includes('appeal') || lead.status === 'invalid'"
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
