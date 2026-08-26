<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showSuccessToast } from 'vant'
import { getMessageGroups, getMessagePage, markAllRead, markRead, type MessageGroup, type MessageItem } from '@/api/message'
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
const params = computed(() => ({ ...(activeGroup.value !== 'all' ? { group: activeGroup.value } : {}), ...(unreadOnly.value ? { unreadOnly: true } : {}) }))
const { list, loading, refreshing, finished, error, loadMore, refresh } = usePageList<MessageItem>(page => getMessagePage(page), params)
const usingMock = computed(() => wasMockedEndpoint('/zsjos/messages/groups'))

async function loadGroups() {
  groupLoading.value = true; groupError.value = ''
  try { groups.value = await getMessageGroups() }
  catch (cause) { groupError.value = cause instanceof Error ? cause.message : '消息分组加载失败' }
  finally { groupLoading.value = false }
}
function goDetail(item: MessageItem) { if (!item.readStatus) markRead([item.id]).catch(() => {}); router.push(`/messages/${item.id}`) }
async function readAll() {
  try { await showConfirmDialog({ title: '全部已读', message: '将当前账号的全部消息标记为已读？' }) } catch { return }
  markingAll.value = true
  try {
    const result = await markAllRead()
    await refresh()
    showSuccessToast(result.updatedCount ? `已标记 ${result.updatedCount} 条消息` : '当前没有未读消息')
  } finally { markingAll.value = false }
}
const typeIcon: Record<string, string> = { lead: 'orders-o', cashback: 'gold-coin-o', withdrawal: 'balance-list-o', appeal: 'info-o', complaint: 'warning-o', feedback: 'comment-o' }
onMounted(loadGroups)
</script>

<template>
  <div class="page-container messages-page">
    <van-nav-bar title="消息"><template #right><van-button size="mini" plain :loading="markingAll" @click="readAll">全部已读</van-button></template></van-nav-bar>
    <van-notice-bar v-if="usingMock" color="#8a6100" background="#fff7df" left-icon="info-o">消息分组为开发环境演示能力</van-notice-bar>
    <div class="message-toolbar">
      <div v-if="groupLoading" class="group-loading"><van-loading size="18" /> 加载分组</div>
      <div v-else-if="groupError" class="group-error">{{ groupError }} <button type="button" @click="loadGroups">重试</button></div>
      <div v-else class="group-tabs">
        <button type="button" :class="{ active: activeGroup === 'all' }" @click="activeGroup = 'all'; refresh()">全部</button>
        <button v-for="group in groups" :key="group.key" type="button" :class="{ active: activeGroup === group.key }" @click="activeGroup = group.key; refresh()">{{ group.label }}</button>
      </div>
      <van-checkbox v-model="unreadOnly" icon-size="16" @change="refresh">只看未读</van-checkbox>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="refresh">
      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <van-cell v-for="item in list" :key="item.id" :title="item.templateTitle" :label="`${item.bizType || '系统'} · ${formatDateTime(item.createTime)}`" clickable @click="goDetail(item)">
          <template #icon><div class="msg-icon-wrap"><van-icon :name="typeIcon[item.bizType || ''] || 'bell'" size="20" color="var(--h5-primary)" /><div v-if="!item.readStatus" class="msg-dot" /></div></template>
          <template #value><span class="msg-preview">{{ item.templateSummary || item.templateContent }}</span></template>
        </van-cell>
        <van-empty v-if="!loading && !error && list.length === 0" description="暂无消息" image="default" />
        <van-empty v-if="!loading && error" :description="error" image="error"><van-button size="small" type="primary" @click="refresh">重新加载</van-button></van-empty>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.message-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;background:var(--h5-card-bg);border-bottom:1px solid var(--h5-divider)}.group-tabs{display:flex;min-width:0;gap:8px;overflow-x:auto;scrollbar-width:none}.group-tabs button{flex:0 0 auto;padding:6px 10px;border:0;border-radius:6px;background:var(--h5-bg);color:var(--h5-text-secondary);font-size:12px}.group-tabs button.active{background:var(--h5-primary);color:#fff}.group-loading,.group-error{display:flex;align-items:center;gap:6px;font-size:12px;color:var(--h5-text-secondary)}.group-error button{border:0;background:transparent;color:var(--h5-primary)}.message-toolbar :deep(.van-checkbox){flex:0 0 auto;font-size:12px}.msg-icon-wrap{position:relative;display:flex;align-items:center;margin-right:12px}.msg-dot{position:absolute;top:-2px;right:-2px;width:8px;height:8px;border-radius:50%;background:var(--h5-danger)}.msg-preview{display:-webkit-box;overflow:hidden;color:var(--h5-text-secondary);font-size:12px;-webkit-line-clamp:1;-webkit-box-orient:vertical}
</style>
