import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getPermissionInfo } from '@/api/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/pages/login/index.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/pay/:paymentIntentNo',
      name: 'PublicPayment',
      component: () => import('@/pages/payment/index.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/payment-result',
      name: 'PaymentResult',
      component: () => import('@/pages/payment/result.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/eam/asset',
      name: 'PublicEamAsset',
      component: () => import('@/pages/eam/asset.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/wecom/click',
      name: 'WecomClick',
      component: () => import('@/pages/wecom/click.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      redirect: '/home'
    },
    {
      path: '/home',
      name: 'Home',
      component: () => import('@/pages/home/index.vue')
    },
    // --- 客资 ---
    {
      path: '/lead/submit',
      name: 'LeadSubmit',
      component: () => import('@/pages/lead/submit.vue'),
      meta: { permission: 'zsjos:lead:submit' }
    },
    {
      path: '/lead/list',
      name: 'LeadList',
      component: () => import('@/pages/lead/list.vue'),
      meta: { permission: 'zsjos:lead:query-submitted' }
    },
    {
      path: '/lead/follow-up',
      name: 'LeadFollowUp',
      component: () => import('@/pages/lead/follow-up.vue'),
      meta: { permission: 'zsjos:lead:query-submitted' }
    },
    {
      path: '/lead/:id',
      name: 'LeadDetail',
      component: () => import('@/pages/lead/detail.vue'),
      meta: { permission: 'zsjos:lead:query-submitted' }
    },
    {
      path: '/lead/:id/supplement',
      name: 'LeadSupplement',
      component: () => import('@/pages/lead/supplement.vue'),
      meta: { permission: 'zsjos:lead:submitter-supplement' }
    },
    {
      path: '/lead/:id/complaint',
      name: 'LeadComplaint',
      component: () => import('@/pages/lead/complaint.vue'),
      meta: { permission: 'zsjos:lead-complaint:create' }
    },
    {
      path: '/complaints',
      name: 'ComplaintHistory',
      component: () => import('@/pages/lead/complaints.vue'),
      meta: { permission: 'zsjos:lead-complaint:create' }
    },
    {
      path: '/lead/:id/appeal',
      name: 'LeadAppeal',
      component: () => import('@/pages/lead/appeal.vue'),
      meta: { permission: 'zsjos:lead:appeal:create' }
    },
    // --- 收益 ---
    {
      path: '/earnings',
      name: 'Earnings',
      component: () => import('@/pages/earnings/index.vue'),
      meta: { permission: 'zsjos:cashback:my-query' }
    },
    {
      path: '/leaderboard',
      name: 'Leaderboard',
      component: () => import('@/pages/leaderboard/index.vue')
    },
    // --- 提现 ---
    {
      path: '/withdrawal',
      name: 'Withdrawal',
      component: () => import('@/pages/withdrawal/index.vue'),
      meta: { permission: 'zsjos:withdrawal:my-query' }
    },
    {
      path: '/withdrawal/apply',
      name: 'WithdrawalApply',
      component: () => import('@/pages/withdrawal/apply.vue'),
      meta: { permission: 'zsjos:withdrawal:apply' }
    },
    {
      path: '/withdrawal/:id',
      name: 'WithdrawalDetail',
      component: () => import('@/pages/withdrawal/detail.vue'),
      meta: { permission: 'zsjos:withdrawal:my-query' }
    },
    // --- 消息 ---
    {
      path: '/messages',
      name: 'Messages',
      component: () => import('@/pages/messages/index.vue')
    },
    {
      path: '/positioning/share',
      name: 'PositioningConfirmation',
      component: () => import('@/pages/positioning/confirmation.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/messages/:id',
      name: 'MessageDetail',
      component: () => import('@/pages/messages/detail.vue')
    },
    // --- 系统反馈 ---
    {
      path: '/feedback',
      name: 'FeedbackList',
      component: () => import('@/pages/feedback/index.vue')
    },
    {
      path: '/feedback/create',
      name: 'FeedbackCreate',
      component: () => import('@/pages/feedback/create.vue')
    },
    {
      path: '/feedback/:id',
      name: 'FeedbackDetail',
      component: () => import('@/pages/feedback/detail.vue')
    },
    // --- 个人 ---
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/pages/profile/index.vue')
    },
    {
      path: '/profile/edit',
      name: 'ProfileEdit',
      component: () => import('@/pages/profile/edit.vue')
    },
    {
      path: '/profile/password',
      name: 'ProfilePassword',
      component: () => import('@/pages/profile/password.vue')
    },
    {
      path: '/profile/bank-cards',
      name: 'BankCards',
      component: () => import('@/pages/profile/bank-cards.vue'),
      meta: { permission: 'zsjos:withdrawal:apply' }
    },
    {
      path: '/profile/bank-cards/:id/edit',
      name: 'BankCardEdit',
      component: () => import('@/pages/profile/bank-card-edit.vue'),
      meta: { permission: 'zsjos:withdrawal:apply' }
    },
    {
      path: '/profile/theme',
      name: 'ThemeSwitch',
      component: () => import('@/pages/profile/theme.vue')
    },
    {
      path: '/unauthorized',
      name: 'Unauthorized',
      component: () => import('@/pages/unauthorized/index.vue')
    }
  ]
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (!requiresAuth) return

  if (!userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  if (!userStore.userId) {
    try {
      const info = await getPermissionInfo()
      userStore.setUserInfo({
        userId: info.user.id,
        nickname: info.user.nickname,
        avatar: info.user.avatar,
        permissions: info.permissions
      })
    } catch {
      userStore.logout()
      return { name: 'Login', query: { redirect: to.fullPath } }
    }
  }

  const permission = to.meta.permission as string | undefined
  if (permission && !userStore.hasPermission(permission)) {
    return { name: 'Unauthorized', query: { from: to.fullPath } }
  }
})

export default router
