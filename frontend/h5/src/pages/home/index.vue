<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCashbackSummary, type CashbackSummary } from '@/api/cashback'
import { getMyLeadPage, type LeadListItem } from '@/api/lead'
import { getPartnerMe, type PartnerInfo } from '@/api/profile'
import { formatDate, formatLeadNo, formatLeadStatus } from '@/utils/format'

defineOptions({ name: 'Home' })

const router = useRouter()
const userStore = useUserStore()

const partner = ref<PartnerInfo>()
const summary = ref<CashbackSummary>()
const recentLeads = ref<LeadListItem[]>([])
const loading = ref(true)
const loadError = ref('')

async function loadHome() {
  loading.value = true
  loadError.value = ''
  try {
    const [partnerData, summaryData, leadsData] = await Promise.all([
      getPartnerMe(),
      getCashbackSummary(),
      getMyLeadPage({ pageNo: 1, pageSize: 3 })
    ])
    partner.value = partnerData
    summary.value = summaryData
    recentLeads.value = leadsData.list
  } catch (cause) {
    loadError.value = cause instanceof Error ? cause.message : '工作台加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadHome)

function goSubmit() {
  router.push('/lead/submit')
}

function goEarnings() {
  router.push('/earnings')
}

function goWithdraw() {
  router.push('/withdrawal/apply')
}

function goLeadList() {
  router.push('/lead/list')
}

function goLeadDetail(id: number) {
  router.push(`/lead/${id}`)
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
</script>

<template>
  <div class="page-container">
    <van-empty v-if="!loading && loadError" :description="loadError" image="error">
      <van-button size="small" type="primary" @click="loadHome">重新加载</van-button>
    </van-empty>
    <template v-else>
    <!-- 渐变头部 -->
    <div class="page-header-gradient home-header">
      <div class="home-header__greeting">
        Hi, {{ partner?.name || userStore.nickname || '兼职伙伴' }} 👋
      </div>
      <div class="home-header__sub">
        编号: {{ partner?.partnerNo || '--' }}
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="card home-shortcuts">
      <div class="home-shortcuts__item" @click="goSubmit">
        <van-icon name="edit" size="28" color="var(--h5-primary)" />
        <span>提交客资</span>
      </div>
      <div class="home-shortcuts__item" @click="goEarnings">
        <van-icon name="gold-coin-o" size="28" color="var(--h5-warning)" />
        <span>查看收益</span>
      </div>
    </div>

    <!-- 收益概览 -->
    <div class="card">
      <div class="section-title">收益概览</div>
      <van-skeleton :loading="loading" :row="2">
        <div class="home-earnings">
          <div class="home-earnings__item">
            <div class="home-earnings__value">¥{{ summary?.pendingAmount?.toFixed(2) || '0.00' }}</div>
            <div class="home-earnings__label">待结算</div>
          </div>
          <div class="home-earnings__item">
            <div class="home-earnings__value amount--primary">¥{{ summary?.availableAmount?.toFixed(2) || '0.00' }}</div>
            <div class="home-earnings__label">可提现</div>
          </div>
          <div class="home-earnings__item">
            <div class="home-earnings__value">¥{{ summary?.withdrawnAmount?.toFixed(2) || '0.00' }}</div>
            <div class="home-earnings__label">已提现</div>
          </div>
        </div>
        <van-button
          v-if="summary && summary.availableAmount > 0"
          type="primary"
          size="small"
          round
          class="home-withdraw-btn"
          @click="goWithdraw"
        >
          去提现 →
        </van-button>
      </van-skeleton>
    </div>

    <!-- 最近提交 -->
    <div class="card">
      <div class="section-title" style="display: flex; justify-content: space-between; align-items: center;">
        最近提交
        <span style="font-size: 12px; color: var(--h5-text-secondary);" @click="goLeadList">查看全部 ></span>
      </div>
      <van-skeleton :loading="loading" :row="3">
        <div v-if="recentLeads.length === 0" style="text-align: center; color: var(--h5-text-placeholder); padding: 20px 0;">
          暂无提交记录
        </div>
        <van-cell-group v-else :border="false">
          <van-cell
            v-for="lead in recentLeads"
            :key="lead.id"
            :title="lead.submittedName"
            :label="`${formatLeadNo(lead.leadNo)} · ${formatDate(lead.submittedAt)}`"
            is-link
            @click="goLeadDetail(lead.id)"
          >
            <template #value>
              <span :style="{ color: statusColor[lead.status] || 'var(--h5-text-secondary)', fontSize: '12px' }">
                {{ formatLeadStatus(lead.status) }}
              </span>
            </template>
          </van-cell>
        </van-cell-group>
      </van-skeleton>
    </div>
    </template>
  </div>
</template>

<style scoped>
.home-header {
  padding: 30px 20px 40px;
}
.home-header__greeting {
  font-size: 20px;
  font-weight: 600;
}
.home-header__sub {
  margin-top: 4px;
  font-size: 13px;
  opacity: 0.85;
}

.home-shortcuts {
  display: flex;
  gap: 16px;
  margin-top: -20px;
}
.home-shortcuts__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 0;
  border-radius: 10px;
  background: var(--h5-primary-opacity);
  font-size: 13px;
  color: var(--h5-text-primary);
}

.home-earnings {
  display: flex;
  justify-content: space-around;
  text-align: center;
  padding: 8px 0;
}
.home-earnings__value {
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.home-earnings__label {
  font-size: 12px;
  color: var(--h5-text-secondary);
  margin-top: 4px;
}

.home-withdraw-btn {
  display: block;
  margin: 12px auto 0;
}

.section-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--h5-text-primary);
  margin-bottom: 12px;
}
</style>
