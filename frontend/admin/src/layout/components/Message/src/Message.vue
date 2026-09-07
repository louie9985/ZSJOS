<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import * as NotifyMessageApi from '@/api/system/notify/message'
import { useUserStoreWithOut } from '@/store/modules/user'
import { propTypes } from '@/utils/propTypes'
import { getRefreshToken } from '@/utils/auth'
import { useWebSocket } from '@vueuse/core'
import { useEmitt } from '@/hooks/web/useEmitt'
import { NOTIFY_MESSAGE_CHANGED_EVENT } from '@/utils/notifyMessage'
import { ElNotification } from 'element-plus'
import * as LeadApi from '@/api/zsjos/leadManagement'
import * as FeedbackApi from '@/api/zsjos/feedback'

defineOptions({ name: 'Message' })

defineProps({
  color: propTypes.string.def('')
})

const router = useRouter()
const userStore = useUserStoreWithOut()
const message = useMessage()
const activeName = ref('notice')
const unreadCount = ref(0) // 未读消息数量
const list = ref<any[]>([]) // 消息列表
let unreadCountTimer: ReturnType<typeof setInterval> | undefined
const displayedMessageIds = new Set<number>()

// 获得消息列表
const getList = async () => {
  list.value = await NotifyMessageApi.getUnreadNotifyMessageList()
}

// 获得未读消息数
const getUnreadCount = async () => {
  unreadCount.value = await NotifyMessageApi.getUnreadNotifyMessageCount()
}

const refreshUnreadMessages = () => {
  getUnreadCount()
  getList()
}

const { emitter } = useEmitt({
  name: NOTIFY_MESSAGE_CHANGED_EVENT,
  callback: refreshUnreadMessages
})

const server =
  (import.meta.env.VITE_BASE_URL + '/infra/ws').replace('http', 'ws') +
  '?token=' +
  encodeURIComponent(getRefreshToken() || '')
const { data: websocketData } = useWebSocket(server, {
  autoReconnect: true,
  heartbeat: true
})

watch(websocketData, (data) => {
  if (!data || data === 'pong') return
  try {
    const event = JSON.parse(data)
    const content = typeof event.content === 'string' ? JSON.parse(event.content) : event.content
    if (event.type === 'notify-message-new') {
      emitter.emit(NOTIFY_MESSAGE_CHANGED_EVENT)
      showPersistedNotification(Number(content?.messageId))
    } else if (event.type === 'notice-push') {
      ElNotification.info({
        title: content?.title || '系统通知',
        message: content?.summary || '',
        position: 'bottom-right'
      })
    }
  } catch (error) {
    console.error('处理系统 WebSocket 消息失败', error)
  }
})

const showPersistedNotification = async (messageId: number) => {
  if (!Number.isFinite(messageId) || displayedMessageIds.has(messageId)) return
  displayedMessageIds.add(messageId)
  try {
    const detail = await NotifyMessageApi.getMyNotifyMessage(messageId)
    if (!detail) return
    const notification = ElNotification.info({
      title: detail.templateTitle || detail.templateNickname,
      message: detail.templateSummary || '',
      position: 'bottom-right',
      duration: 8000,
      onClick: async () => {
        notification.close()
        if (!detail.readStatus) await NotifyMessageApi.updateNotifyMessageRead(detail.id)
        emitter.emit(NOTIFY_MESSAGE_CHANGED_EVENT)
        if (detail.actionType === 'business_detail' && detail.bizType === 'feedback' && detail.bizId) {
          try {
            const feedback = await FeedbackApi.getFeedback(detail.bizId)
            const path = feedback.feedbackType === 'REQUIREMENT'
              ? '/zsjos/feedback/requirement'
              : feedback.feedbackType === 'BUG'
                ? '/zsjos/feedback/bug'
                : '/zsjos/feedback/support'
            await router.push({ path, query: { feedbackId: String(detail.bizId) } })
            return
          } catch {
            message.warning('当前账号无权查看该反馈，已打开消息详情')
          }
        }
        if (detail.actionType === 'business_detail' && detail.bizType === 'lead' && detail.bizId) {
          const target = { path: '/zsjos/leads/manage', query: { leadId: String(detail.bizId) } }
          if (router.resolve(target).matched.length > 0) {
            try {
              await LeadApi.getLead(detail.bizId)
              await router.push(target)
              return
            } catch {
              message.warning('当前账号无权查看该客资，已打开消息详情')
            }
          } else {
            message.warning('当前账号没有客资详情菜单权限，已打开消息详情')
          }
        }
        if (detail.actionType !== 'none') {
          // Admin 的“我的站内信”实际挂载在 /user/notify-message；旧的
          // /messages/all 不存在，导致 WebSocket 弹窗点击后无法进入消息详情。
          await router.push({ path: '/user/notify-message', query: { messageId: String(detail.id) } })
        }
      }
    })
  } catch (error) {
    displayedMessageIds.delete(messageId)
    console.error('加载 WebSocket 站内信详情失败', error)
  }
}

// 跳转我的站内信
const goMyList = () => {
  router.push('/messages/all')
}

// ========== 初始化 =========
onMounted(() => {
  // 首次加载小红点
  getUnreadCount()
  // 轮询刷新小红点
  unreadCountTimer = setInterval(
    () => {
      if (userStore.getIsSetUser) {
        getUnreadCount()
      } else {
        unreadCount.value = 0
      }
    },
    1000 * 60 * 2
  )
})

onBeforeUnmount(() => {
  if (unreadCountTimer) {
    clearInterval(unreadCountTimer)
    unreadCountTimer = undefined
  }
})
</script>
<template>
  <div class="message">
    <ElPopover :width="400" placement="bottom" trigger="click" @show="getList">
      <template #reference>
        <ElBadge :value="unreadCount" :max="99" :hidden="unreadCount === 0" class="item">
          <Icon :size="18" class="cursor-pointer" icon="ep:bell" :color="color" />
        </ElBadge>
      </template>
      <ElTabs v-model="activeName">
        <ElTabPane label="未读消息" name="notice">
          <el-scrollbar class="message-list">
            <div v-if="list.length === 0" class="message-empty">
              <Icon :size="40" icon="ep:message" />
              <span>暂无未读消息</span>
            </div>
            <template v-for="item in list" :key="item.id">
              <div class="message-item">
                <img alt="" class="message-icon" src="@/assets/imgs/avatar.gif" />
                <div class="message-content">
                  <span class="message-title">
                    {{ item.templateTitle || item.templateNickname }}
                  </span>
                  <span class="message-summary">{{ item.templateSummary }}</span>
                  <span class="message-date">
                    {{ formatDate(item.createTime) }}
                  </span>
                </div>
              </div>
            </template>
          </el-scrollbar>
        </ElTabPane>
      </ElTabs>
      <!-- 更多 -->
      <div style="margin-top: 10px; text-align: right">
        <el-button type="primary" @click="goMyList">
          <Icon icon="ep:view" class="mr-1px" /> 查看全部
        </el-button>
      </div>
    </ElPopover>
  </div>
</template>
<style lang="scss" scoped>
// 铃铛对齐修复：
// DOM 链路 div.message > ElPopover > ElBadge(inline-block) > Icon
// 默认 ElBadge 是 inline-block，在父 flex 容器里按 baseline 对齐，导致图标比其他 ElIcon 视觉偏下 1-2px
// 解法：让 .message 本身变 flex-center 容器，且穿透设置内部 .el-badge 也为 flex-center
// 这样从父到子整条链路都走几何中心对齐，不再受 inline-block baseline 影响
.message {
  display: flex;
  align-items: center;
  height: 100%;

  :deep(.el-badge) {
    display: flex;
    align-items: center;
  }
}

.message-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 260px;
  line-height: 45px;
}

.message-list {
  display: flex;
  height: 400px;
  flex-direction: column;

  .message-item {
    display: flex;
    align-items: center;
    padding: 20px 0;
    border-bottom: 1px solid var(--el-border-color-light);

    &:last-child {
      border: none;
    }

    .message-icon {
      width: 40px;
      height: 40px;
      margin: 0 20px 0 5px;
    }

    .message-content {
      display: flex;
      flex-direction: column;

      .message-title {
        margin-bottom: 5px;
      }

      .message-date {
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }

      .message-summary {
        display: -webkit-box;
        overflow: hidden;
        color: var(--el-text-color-regular);
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
      }
    }
  }
}
</style>
