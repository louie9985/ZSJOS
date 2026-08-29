<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { getMessageGroups, getMessagePage, getUnreadCount, markAllRead, markRead, type MessageGroup, type MessageItem } from '@/api/message'
import { wasMockedEndpoint } from '@/api/mock'
import { usePageList } from '@/composables/usePageList'
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
const params = computed(() => ({ ...(activeGroup.value !== 'all' ? { group: activeGroup.value } : {}), ...(unreadOnly.value ? { unreadOnly: true } : {}) }))
const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList<MessageItem>(page => getMessagePage(page), params)
const usingMock = computed(() => wasMockedEndpoint('/zsjos/messages/groups'))

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
function goDetail(item: MessageItem) { if (!item.readStatus) markRead([item.id]).catch(() => {}); router.push(`/messages/${item.id}`) }
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
</script>

<template>
  <div class="page-container messages-page">
    <van-nav-bar title="消息中心">
      <template #right>
        <van-button size="mini" plain :loading="markingAll" @click="readAll">全部已读</van-button>
      </template>
    </van-nav-bar>

    <section class="card messages-hero">
      <div class="messages-hero__head">
        <div>
          <div class="messages-hero__title">消息中心</div>
          <div class="messages-hero__subtitle">查看通知、业务提醒和系统反馈。</div>
        </div>
        <div class="messages-hero__aside">
          <span class="messages-chip" :class="{ 'messages-chip--loading': unreadCountLoading }">
            {{ unreadCountLoading ? '加载中' : `未读 ${unreadCount ?? 0}` }}
          </span>
          <span class="messages-chip messages-chip--muted">{{ unreadOnly ? '仅未读' : '全部消息' }}</span>
        </div>
      </div>
      <div v-if="usingMock" class="messages-hero__hint">
        开发环境消息分组为演示能力
      </div>
    </section>

    <section class="page-section">
      <div class="page-section__head messages-section__head">
        <div class="page-section__title">消息分组</div>
      </div>
      <div class="card messages-filter">
        <div v-if="groupLoading" class="messages-filter__state"><van-loading size="18" /> 加载分组</div>
        <div v-else-if="groupError" class="messages-filter__state">
          <span>{{ groupError }}</span>
          <button type="button" @click="loadGroups">重试</button>
        </div>
        <template v-else>
          <div class="messages-tabs">
            <button type="button" :class="{ active: activeGroup === 'all' }" @click="applyGroup('all')">全部</button>
            <button v-for="group in groups" :key="group.key" type="button" :class="{ active: activeGroup === group.key }" @click="applyGroup(group.key)">
              {{ group.label }}
            </button>
          </div>
          <van-checkbox v-model="unreadOnly" icon-size="16" class="messages-filter__toggle" @change="toggleUnreadOnly">
            只看未读
          </van-checkbox>
        </template>
      </div>
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
              <span>{{ item.bizType || '系统' }}</span>
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
  background: var(--h5-bg);
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

.messages-hero__title {
  color: var(--h5-text-primary);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.messages-hero__subtitle {
  margin-top: 4px;
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.5;
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
  background: var(--h5-bg);
  color: var(--h5-text-secondary);
}

.messages-chip--loading {
  opacity: 0.75;
}

.messages-hero__hint {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--h5-primary) 6%, var(--h5-card-bg));
  color: var(--h5-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.messages-section__head {
  margin: 0 16px;
}

.messages-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
}

.messages-filter__state {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.messages-filter__state button {
  border: 0;
  background: transparent;
  color: var(--h5-primary);
}

.messages-tabs {
  display: flex;
  min-width: 0;
  flex: 1;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
}

.messages-tabs button {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  background: var(--h5-bg);
  color: var(--h5-text-secondary);
  font-size: 12px;
}

.messages-tabs button.active {
  background: var(--h5-primary);
  color: #fff;
}

.messages-filter__toggle {
  flex-shrink: 0;
  font-size: 12px;
}

.message-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 16px;
  gap: 12px;
  align-items: start;
  width: calc(100% - 32px);
  margin: 0 16px 10px;
  padding: 14px 14px 12px;
  border: 1px solid var(--h5-border);
  border-radius: 16px;
  appearance: none;
  background: var(--h5-card-bg);
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
