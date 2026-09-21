# 内容审核通知前端集成指南

> 2026-09-20 实现校正：当前受支持契约见 [编导与运营通知](../../../docs/api/director-operator-notifications.md)。以下示例保留作历史设计参考；超时场景为 `zsjos.content_review.review_timeout_reminder`，由 `MediaNotificationReminderScheduler` 按 System 规则扫描真实 BPM 待办，不使用本文旧 Quartz Job。Workbench 统一入口为 `/zsjos/material-library/content-review?batchId=…`，Vue Admin 未支持的员工业务动作回退消息详情。


## 一、通知数据结构

### 1.1 通知对象

```typescript
interface Notification {
  id: number;                    // 通知 ID
  userId: number;                // 收件人 ID
  sceneCode: string;             // 场景编码
  title: string;                 // 通知标题
  content: string;               // 通知内容
  bizType: string;               // 业务类型
  bizId: number;                 // 业务 ID
  action?: string;               // 操作类型
  params?: Record<string, any>;  // 扩展参数
  readStatus: boolean;           // 已读状态
  createdAt: string;             // 创建时间
}
```

### 1.2 内容审核通知场景

```typescript
enum ContentReviewNotifyScene {
  BATCH_SUBMITTED = 'zsjos.content_review.batch_submitted',          // 批次提交
  DIRECTOR_COMPLETED = 'zsjos.content_review.director_completed',    // 编导审核通过
  DIRECTOR_REJECTED = 'zsjos.content_review.director_rejected',      // 编导驳回
  FINAL_COMPLETED = 'zsjos.content_review.final_completed',          // 终审通过
  FINAL_REJECTED = 'zsjos.content_review.final_rejected',            // 终审驳回
  DIRECTOR_ITEM_DECISION = 'zsjos.content_review.director_item_decision',  // 编导逐条决策
  FINAL_ITEM_DECISION = 'zsjos.content_review.final_item_decision',        // 终审逐条决策
  REVIEW_TIMEOUT = 'zsjos.content_review.review_timeout',            // 审核超时提醒
}
```

---

## 二、通知跳转实现

### 2.1 跳转路由配置

```typescript
// router/content-review.ts
export const contentReviewRoutes = [
  {
    path: '/content-review/todo',
    name: 'ContentReviewTodo',
    component: () => import('@/views/content-review/TodoList.vue'),
    meta: { title: '待审核列表' }
  },
  {
    path: '/content-review/batch/:batchId',
    name: 'ContentReviewBatchDetail',
    component: () => import('@/views/content-review/BatchDetail.vue'),
    meta: { title: '批次详情' }
  }
];
```

### 2.2 通知跳转处理器

```typescript
// utils/notify-router.ts
import { Router } from 'vue-router';
import { Notification } from '@/types/notification';

export class ContentReviewNotifyRouter {
  constructor(private router: Router) {}

  /**
   * 处理内容审核通知的跳转
   */
  handleNotifyClick(notification: Notification): void {
    const { sceneCode, bizType, bizId, params } = notification;

    // 确保是内容审核业务
    if (bizType !== 'content-review-batch') {
      console.warn('Not a content review notification:', bizType);
      return;
    }

    switch (sceneCode) {
      case ContentReviewNotifyScene.BATCH_SUBMITTED:
        // 跳转到待审核列表
        this.router.push('/content-review/todo');
        break;

      case ContentReviewNotifyScene.DIRECTOR_ITEM_DECISION:
      case ContentReviewNotifyScene.FINAL_ITEM_DECISION:
        // 跳转到批次详情并定位到具体条目
        this.router.push({
          path: `/content-review/batch/${bizId}`,
          query: { itemId: params?.itemId }
        });
        break;

      case ContentReviewNotifyScene.DIRECTOR_COMPLETED:
      case ContentReviewNotifyScene.DIRECTOR_REJECTED:
      case ContentReviewNotifyScene.FINAL_COMPLETED:
      case ContentReviewNotifyScene.FINAL_REJECTED:
      case ContentReviewNotifyScene.REVIEW_TIMEOUT:
        // 跳转到批次详情
        this.router.push(`/content-review/batch/${bizId}`);
        break;

      default:
        console.warn('Unknown scene code:', sceneCode);
    }
  }

  /**
   * 获取通知的跳转路径（不执行跳转）
   */
  getNotifyPath(notification: Notification): string {
    const { sceneCode, bizId, params } = notification;

    switch (sceneCode) {
      case ContentReviewNotifyScene.BATCH_SUBMITTED:
        return '/content-review/todo';

      case ContentReviewNotifyScene.DIRECTOR_ITEM_DECISION:
      case ContentReviewNotifyScene.FINAL_ITEM_DECISION:
        return `/content-review/batch/${bizId}?itemId=${params?.itemId || ''}`;

      default:
        return `/content-review/batch/${bizId}`;
    }
  }
}
```

### 2.3 在组件中使用

```vue
<!-- components/NotificationList.vue -->
<template>
  <div class="notification-list">
    <div
      v-for="item in notifications"
      :key="item.id"
      class="notification-item"
      :class="{ unread: !item.readStatus }"
      @click="handleNotificationClick(item)"
    >
      <div class="notification-title">{{ item.title }}</div>
      <div class="notification-content">{{ item.content }}</div>
      <div class="notification-time">{{ formatTime(item.createdAt) }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ContentReviewNotifyRouter } from '@/utils/notify-router';
import { markNotificationAsRead } from '@/api/notification';
import type { Notification } from '@/types/notification';

const router = useRouter();
const notifyRouter = new ContentReviewNotifyRouter(router);
const notifications = ref<Notification[]>([]);

const handleNotificationClick = async (notification: Notification) => {
  // 标记为已读
  if (!notification.readStatus) {
    await markNotificationAsRead(notification.id);
    notification.readStatus = true;
  }

  // 执行跳转
  notifyRouter.handleNotifyClick(notification);
};

const formatTime = (time: string) => {
  // 格式化时间显示
  return new Date(time).toLocaleString('zh-CN');
};
</script>

<style scoped>
.notification-item {
  padding: 16px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  transition: background-color 0.2s;
}

.notification-item:hover {
  background-color: #f5f5f5;
}

.notification-item.unread {
  background-color: #e6f7ff;
}

.notification-item.unread::before {
  content: '';
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #1890ff;
  margin-right: 8px;
}

.notification-title {
  font-weight: 600;
  font-size: 14px;
  color: #333;
  margin-bottom: 8px;
}

.notification-content {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
}

.notification-time {
  font-size: 12px;
  color: #999;
}
</style>
```

---

## 三、批次详情页面滚动定位

### 3.1 自动滚动到指定条目

```vue
<!-- views/content-review/BatchDetail.vue -->
<template>
  <div class="batch-detail">
    <div class="batch-header">
      <h2>批次详情</h2>
    </div>

    <div class="batch-items">
      <div
        v-for="item in batchItems"
        :key="item.id"
        :ref="el => setItemRef(el, item.id)"
        class="batch-item"
        :class="{ highlighted: item.id === highlightedItemId }"
      >
        <!-- 条目内容 -->
        <div class="item-title">{{ item.title }}</div>
        <div class="item-decision">{{ item.decision }}</div>
        <div class="item-comment">{{ item.comment }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { getBatchDetail } from '@/api/content-review';

const route = useRoute();
const batchId = ref(Number(route.params.batchId));
const highlightedItemId = ref<number | null>(null);
const batchItems = ref<any[]>([]);
const itemRefs = new Map<number, HTMLElement>();

const setItemRef = (el: any, itemId: number) => {
  if (el) {
    itemRefs.set(itemId, el);
  }
};

const scrollToItem = (itemId: number) => {
  const itemEl = itemRefs.get(itemId);
  if (itemEl) {
    itemEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
    highlightedItemId.value = itemId;

    // 3 秒后取消高亮
    setTimeout(() => {
      highlightedItemId.value = null;
    }, 3000);
  }
};

onMounted(async () => {
  // 加载批次详情
  const response = await getBatchDetail(batchId.value);
  batchItems.value = response.data.items;

  // 如果 URL 中有 itemId 参数，滚动到对应条目
  const itemIdParam = route.query.itemId;
  if (itemIdParam) {
    await nextTick();
    const itemId = Number(itemIdParam);
    scrollToItem(itemId);
  }
});
</script>

<style scoped>
.batch-item {
  padding: 16px;
  border: 1px solid #eee;
  margin-bottom: 12px;
  border-radius: 4px;
  transition: all 0.3s;
}

.batch-item.highlighted {
  background-color: #fff7e6;
  border-color: #ffa940;
  box-shadow: 0 2px 8px rgba(255, 169, 64, 0.3);
}
</style>
```

---

## 四、通知徽章与未读数

### 4.1 全局通知徽章组件

```vue
<!-- components/NotificationBadge.vue -->
<template>
  <div class="notification-badge" @click="toggleNotificationPanel">
    <el-badge :value="unreadCount" :hidden="unreadCount === 0">
      <el-icon :size="20"><Bell /></el-icon>
    </el-badge>

    <!-- 通知面板 -->
    <el-drawer
      v-model="showPanel"
      title="通知中心"
      direction="rtl"
      size="400px"
    >
      <NotificationList @notification-click="handleNotificationClick" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Bell } from '@element-plus/icons-vue';
import { getUnreadNotificationCount } from '@/api/notification';
import NotificationList from './NotificationList.vue';

const unreadCount = ref(0);
const showPanel = ref(false);

const loadUnreadCount = async () => {
  const response = await getUnreadNotificationCount();
  unreadCount.value = response.data;
};

const toggleNotificationPanel = () => {
  showPanel.value = !showPanel.value;
};

const handleNotificationClick = () => {
  // 通知点击后关闭面板
  showPanel.value = false;
  // 重新加载未读数
  loadUnreadCount();
};

onMounted(() => {
  loadUnreadCount();

  // 每 30 秒刷新一次未读数
  setInterval(loadUnreadCount, 30000);
});
</script>

<style scoped>
.notification-badge {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}
</style>
```

---

## 五、通知优先级样式

### 5.1 根据优先级显示不同样式

```vue
<template>
  <div
    class="notification-item"
    :class="[
      priorityClass(notification),
      { unread: !notification.readStatus }
    ]"
  >
    <!-- 通知内容 -->
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { Notification } from '@/types/notification';

const props = defineProps<{
  notification: Notification;
}>();

const priorityClass = (notification: Notification) => {
  const highPriority = [
    'zsjos.content_review.batch_submitted',
    'zsjos.content_review.review_timeout'
  ];

  const mediumPriority = [
    'zsjos.content_review.director_rejected',
    'zsjos.content_review.final_rejected'
  ];

  if (highPriority.includes(notification.sceneCode)) {
    return 'priority-high';
  } else if (mediumPriority.includes(notification.sceneCode)) {
    return 'priority-medium';
  }
  return 'priority-normal';
};
</script>

<style scoped>
.notification-item.priority-high {
  border-left: 4px solid #ff4d4f;
}

.notification-item.priority-high .notification-title {
  color: #ff4d4f;
}

.notification-item.priority-medium {
  border-left: 4px solid #faad14;
}

.notification-item.priority-medium .notification-title {
  color: #faad14;
}

.notification-item.priority-normal {
  border-left: 4px solid #1890ff;
}
</style>
```

---

## 六、WebSocket 实时通知

### 6.1 WebSocket 连接

```typescript
// utils/websocket-notify.ts
import { ref } from 'vue';
import type { Notification } from '@/types/notification';

class WebSocketNotifyService {
  private ws: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private heartbeatTimer: number | null = null;

  public onNotification = ref<((notification: Notification) => void) | null>(null);

  connect(userId: number, token: string) {
    const wsUrl = `ws://your-domain/ws/notify?userId=${userId}&token=${token}`;
    this.ws = new WebSocket(wsUrl);

    this.ws.onopen = () => {
      console.log('WebSocket connected');
      this.startHeartbeat();
    };

    this.ws.onmessage = (event) => {
      const notification: Notification = JSON.parse(event.data);
      if (this.onNotification.value) {
        this.onNotification.value(notification);
      }
    };

    this.ws.onclose = () => {
      console.log('WebSocket closed, reconnecting...');
      this.reconnect(userId, token);
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }

  private startHeartbeat() {
    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'ping' }));
      }
    }, 30000);
  }

  private reconnect(userId: number, token: string) {
    if (this.reconnectTimer) return;

    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.connect(userId, token);
    }, 5000);
  }

  disconnect() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    this.ws?.close();
  }
}

export const wsNotifyService = new WebSocketNotifyService();
```

### 6.2 在 App 中集成

```vue
<!-- App.vue -->
<template>
  <div id="app">
    <router-view />

    <!-- 通知提示 -->
    <el-notification
      v-for="notify in activeNotifications"
      :key="notify.id"
      :title="notify.title"
      :message="notify.content"
      :type="getNotificationType(notify)"
      @click="handleNotificationClick(notify)"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { wsNotifyService } from '@/utils/websocket-notify';
import { ContentReviewNotifyRouter } from '@/utils/notify-router';
import type { Notification } from '@/types/notification';

const router = useRouter();
const userStore = useUserStore();
const notifyRouter = new ContentReviewNotifyRouter(router);
const activeNotifications = ref<Notification[]>([]);

const handleRealtimeNotification = (notification: Notification) => {
  // 添加到活动通知列表
  activeNotifications.value.push(notification);

  // 3 秒后自动移除
  setTimeout(() => {
    const index = activeNotifications.value.findIndex(n => n.id === notification.id);
    if (index > -1) {
      activeNotifications.value.splice(index, 1);
    }
  }, 3000);
};

const handleNotificationClick = (notification: Notification) => {
  notifyRouter.handleNotifyClick(notification);
};

const getNotificationType = (notification: Notification): 'success' | 'warning' | 'info' | 'error' => {
  if (notification.sceneCode.includes('rejected')) return 'warning';
  if (notification.sceneCode.includes('completed')) return 'success';
  if (notification.sceneCode.includes('timeout')) return 'error';
  return 'info';
};

onMounted(() => {
  if (userStore.isLoggedIn) {
    wsNotifyService.onNotification.value = handleRealtimeNotification;
    wsNotifyService.connect(userStore.userId, userStore.token);
  }
});

onUnmounted(() => {
  wsNotifyService.disconnect();
});
</script>
```

---

## 七、API 接口

### 7.1 通知相关接口

```typescript
// api/notification.ts
import request from '@/utils/request';
import type { Notification } from '@/types/notification';

/**
 * 获取通知列表
 */
export function getNotificationList(params: {
  page: number;
  pageSize: number;
  readStatus?: boolean;
  sceneCode?: string;
}) {
  return request<{ list: Notification[]; total: number }>({
    url: '/system/notify-message/page',
    method: 'get',
    params
  });
}

/**
 * 获取未读通知数量
 */
export function getUnreadNotificationCount() {
  return request<number>({
    url: '/system/notify-message/unread-count',
    method: 'get'
  });
}

/**
 * 标记通知为已读
 */
export function markNotificationAsRead(id: number) {
  return request({
    url: `/system/notify-message/mark-read/${id}`,
    method: 'put'
  });
}

/**
 * 批量标记为已读
 */
export function markAllAsRead() {
  return request({
    url: '/system/notify-message/mark-all-read',
    method: 'put'
  });
}
```

---

## 八、总结

通过以上实现，前端可以：

✅ **接收并显示**内容审核通知  
✅ **点击通知跳转**到对应的审核页面  
✅ **自动滚动定位**到具体的审核条目  
✅ **显示未读徽章**提醒用户处理  
✅ **实时接收 WebSocket 通知**无需刷新页面  
✅ **区分通知优先级**用不同样式展示

建议按照以上示例代码进行集成，并根据项目实际情况调整样式和交互细节。
