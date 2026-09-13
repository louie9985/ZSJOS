<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { getMessageGroups, getMessagePage, getUnreadCount, markAllRead, type MessageGroup, type MessageItem } from '@/api/message'
import { usePageList } from '@/composables/usePageList'
import LiquidSegmentedControl from '@/components/LiquidSegmentedControl.vue'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'Messages' })
const router = useRouter()
const groups = ref<MessageGroup[]>([])
const groupLoading = ref(true)
const groupError = ref('')
const activeGroup = ref('all')
const unreadOnly = ref(false)
const markingAll = ref(false)
const unreadCount = ref<number>()
const unreadCountLoading = ref(true)
let refreshOnActivate = false
const params = computed(() => ({ ...(activeGroup.value !== 'all' ? { group: activeGroup.value } : {}), ...(unreadOnly.value ? { unreadOnly: true } : {}) }))
const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList<MessageItem>(page => getMessagePage(page), params)
const groupTabs = computed(() => groups.value.map(group => ({ key: group.key, label: group.label })))
const bizTypeLabels = computed(() => {
  const labels = new Map<string, string>()
  groups.value.forEach(group => {
    if (group.key === 'all') return
    group.bizTypes.forEach(bizType => labels.set(bizType, group.label))
  })
  return labels
})

async function loadGroups() {
  groupLoading.value = true; groupError.value = ''
  try { groups.value = await getMessageGroups() }
  catch (cause) { groupError.value = cause instanceof Error ? cause.message : '消息分组加载失败' }
  finally { groupLoading.value = false }
}
async function loadUnreadCount() {
  unreadCountLoading.value = true
  try {
    unreadCount.value = await getUnreadCount()
  } catch {
    unreadCount.value = undefined
  } finally {
    unreadCountLoading.value = false
  }
}
function goDetail(item: MessageItem) { router.push(`/messages/${item.id}`) }
async function readAll() {
  try { await showConfirmDialog({ title: '全部已读', message: '将当前账号的全部消息标记为已读？' }) } catch { return }
  markingAll.value = true
  try {
    const result = await markAllRead()
    await Promise.all([refresh(), loadUnreadCount()])
    showSuccessToast(result.updatedCount ? `已标记 ${result.updatedCount} 条消息` : '当前没有未读消息')
  } finally { markingAll.value = false }
}
const typeIcon: Record<string, string> = { lead: 'orders-o', cashback: 'gold-coin-o', withdrawal: 'balance-list-o', appeal: 'info-o', complaint: 'warning-o', feedback: 'comment-o' }
function bizTypeLabel(bizType?: string) { return (bizType && bizTypeLabels.value.get(bizType)) || '系统' }
function applyGroup(group: string) {
  activeGroup.value = group
  void refresh()
}
function toggleUnreadOnly() {
  void refresh()
}
onMounted(() => {
  void loadGroups()
  void loadUnreadCount()
})
onDeactivated(() => { refreshOnActivate = true })
onActivated(() => {
  if (!refreshOnActivate) return
  refreshOnActivate = false
  void refresh()
  void loadUnreadCount()
})
</script>

<template>
  <div class="page-container my-subpage-page messages-page">
    <van-nav-bar class="my-subpage__nav" title="消息中心" left-arrow @click-left="$router.back()" />

    <section class="card messages-hero hero-surface">
      <div class="messages-hero__head">
        <div>
          <div class="messages-hero__title-row">
            <div class="messages-hero__title">消息中心</div>
            <HelpPopover text="查看通知、业务提醒和系统反馈。" aria-label="消息中心说明" />
          </div>
        </div>
        <div class="messages-hero__aside">
          <span class="messages-chip" :class="{ 'messages-chip--loading': unreadCountLoading }">
            {{ unreadCountLoading ? '加载中' : unreadCount === undefined ? '未读 --' : `未读 ${unreadCount}` }}
          </span>
          <span class="messages-chip messages-chip--muted">{{ unreadOnly ? '仅未读' : '全部消息' }}</span>
        </div>
      </div>
    </section>

    <section class="messages-filter-section">
      <div v-if="groupLoading" class="messages-filter-state card"><van-loading size="18" /> 加载分组</div>
      <div v-else-if="groupError" class="messages-filter-state card">
        <span>{{ groupError }}</span>
        <button type="button" @click="loadGroups">重试</button>
      </div>
      <template v-else>
        <div class="messages-tabs-wrap">
          <LiquidSegmentedControl
            class="messages-tabs"
            :model-value="activeGroup"
            :items="groupTabs"
            ariaLabel="消息分组"
            @change="applyGroup"
          />
        </div>
        <div class="messages-filter__toggle-row">
          <div class="toggle-row__left">
            <span>只看未读</span>
            <van-switch
              v-model="unreadOnly"
              size="20"
              aria-label="只看未读"
              @change="toggleUnreadOnly"
            />
          </div>
          <button
            type="button"
            class="mark-all-btn"
            :disabled="markingAll"
            :aria-busy="markingAll"
            aria-label="全部已读"
            @click="readAll"
          >
            <van-loading v-if="markingAll" size="14" />
            <van-icon v-else name="success" />
            <span>全部已读</span>
          </button>
        </div>
      </template>
    </section>

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <button v-for="item in list" :key="item.id" type="button" class="card message-card" @click="goDetail(item)">
          <div class="message-card__icon">
            <van-icon :name="typeIcon[item.bizType || ''] || 'bell'" size="20" color="var(--h5-primary)" />
            <div v-if="!item.readStatus" class="message-card__dot" />
          </div>
          <div class="message-card__body">
            <div class="message-card__head">
              <strong>{{ item.templateTitle }}</strong>
              <time>{{ formatDateTime(item.createTime) }}</time>
            </div>
            <div class="message-card__meta">
              <span>{{ item.templateNickname || '中世健' }}</span>
              <span>{{ bizTypeLabel(item.bizType) }}</span>
            </div>
            <div class="message-card__preview">
              {{ item.templateSummary || item.templateContent }}
            </div>
          </div>
          <van-icon name="arrow" class="message-card__arrow" />
        </button>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无消息" image="default" />
        <van-empty v-if="!loading && error" :description="error" image="error"><van-button size="small" type="primary" @click="refresh">重新加载</van-button></van-empty>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.messages-page {
  min-height: 100vh;
  padding-bottom: 88px;
  background: transparent;
}

.messages-hero {
  margin-top: 12px;
  padding: 14px 16px;
}

.messages-hero__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.messages-hero__title-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.messages-hero__title {
  color: var(--h5-text-primary);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.messages-hero__title-row :deep(.help-popover__button) {
  margin: -8px -4px;
}

.messages-hero__aside {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.messages-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 11px;
  font-weight: 600;
}

.messages-chip--muted {
  background: var(--h5-glass-sunken);
  color: var(--h5-text-secondary);
}

.messages-chip--loading {
  opacity: 0.75;
}

.messages-filter-section {
  position: sticky;
  top: 46px;
  z-index: 9;
  padding: 8px 16px 10px;
  border-bottom: 1px solid var(--h5-glass-divider);
  background: var(--h5-glass-surface);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
  -webkit-backdrop-filter: saturate(160%) blur(var(--h5-glass-blur));
}

.messages-filter-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  margin: 0;
  padding: 0 14px;
  border-radius: 14px;
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.messages-filter-state button {
  border: 0;
  background: transparent;
  color: var(--h5-primary);
}

.messages-tabs-wrap {
  width: 100%;
}

.messages-tabs :deep(.liquid-segmented) {
  border-color: var(--h5-glass-border);
  background: color-mix(in srgb, var(--h5-glass-sunken) 78%, transparent);
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 42%, transparent);
}

.messages-tabs :deep(.liquid-segmented__indicator) {
  border: 1px solid color-mix(in srgb, var(--h5-primary) 42%, var(--h5-glass-border));
  background: color-mix(in srgb, var(--h5-primary) 25%, var(--h5-card-bg));
  box-shadow: inset 0 1px 0 color-mix(in srgb, #fff 62%, transparent), 0 4px 12px color-mix(in srgb, var(--h5-primary) 18%, transparent);
}

.messages-filter__toggle-row {
  display: flex;
  min-height: 30px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 6px;
  padding: 0 4px;
  color: var(--h5-text-secondary);
  font-size: 11px;
}

.toggle-row__left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mark-all-btn {
  position: relative;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 32%, transparent);
  border-radius: 999px;
  appearance: none;
  background: var(--h5-primary-opacity);
  color: var(--h5-primary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: background 0.2s ease, opacity 0.2s ease;
}

.mark-all-btn::after {
  content: '';
  position: absolute;
  inset: -7px;
}

.mark-all-btn .van-icon {
  font-size: 14px;
}

.mark-all-btn:not(:disabled):active {
  background: color-mix(in srgb, var(--h5-primary) 26%, transparent);
}

.mark-all-btn:disabled {
  cursor: default;
  opacity: 0.7;
}

@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .messages-filter-section {
    background: var(--h5-glass-surface-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .messages-tabs :deep(.liquid-segmented) {
    background: color-mix(in srgb, var(--h5-primary-light) 52%, var(--h5-card-bg));
  }

  .messages-tabs :deep(.liquid-segmented__indicator) {
    background: color-mix(in srgb, var(--h5-primary) 22%, var(--h5-card-bg));
  }
}

@media (prefers-reduced-transparency: reduce) {
  .messages-filter-section {
    background: var(--h5-glass-surface-strong-fallback);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .messages-tabs :deep(.liquid-segmented) {
    background: color-mix(in srgb, var(--h5-primary-light) 52%, var(--h5-card-bg));
  }

  .messages-tabs :deep(.liquid-segmented__indicator) {
    background: color-mix(in srgb, var(--h5-primary) 22%, var(--h5-card-bg));
  }
}

.message-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 16px;
  gap: 12px;
  align-items: start;
  width: calc(100% - 32px);
  margin: 0 16px 10px;
  padding: 14px 14px 12px;
  border: 1px solid var(--h5-glass-border);
  border-radius: 16px;
  appearance: none;
  background: var(--h5-content-surface);
  box-shadow: var(--h5-glass-shadow);
  color: inherit;
  font: inherit;
  text-align: left;
}

.message-card:active {
  transform: scale(0.995);
}

.message-card__icon {
  position: relative;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--h5-primary-opacity);
}

.message-card__dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--h5-danger);
  box-shadow: 0 0 0 2px var(--h5-card-bg);
}

.message-card__body {
  min-width: 0;
}

.message-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.message-card__head strong {
  overflow: hidden;
  color: var(--h5-text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-card__head time {
  flex-shrink: 0;
  color: var(--h5-text-placeholder);
  font-size: 11px;
  line-height: 1.4;
}

.message-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  margin-top: 4px;
  color: var(--h5-text-secondary);
  font-size: 11px;
  line-height: 1.4;
}

.message-card__preview {
  margin-top: 8px;
  display: -webkit-box;
  overflow: hidden;
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.55;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.message-card__arrow {
  align-self: center;
  color: var(--h5-text-placeholder);
}

.message-card + .message-card {
  margin-top: 10px;
}
</style>
