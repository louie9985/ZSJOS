import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

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
      component: () => import('@/pages/lead/submit.vue')
    },
    {
      path: '/lead/list',
      name: 'LeadList',
      component: () => import('@/pages/lead/list.vue')
    },
    {
      path: '/lead/:id',
      name: 'LeadDetail',
      component: () => import('@/pages/lead/detail.vue')
    },
    {
      path: '/lead/:id/supplement',
      name: 'LeadSupplement',
      component: () => import('@/pages/lead/supplement.vue')
    },
    {
      path: '/lead/:id/complaint',
      name: 'LeadComplaint',
      component: () => import('@/pages/lead/complaint.vue')
    },
    {
      path: '/lead/:id/appeal',
      name: 'LeadAppeal',
      component: () => import('@/pages/lead/appeal.vue')
    },
    // --- 收益 ---
    {
      path: '/earnings',
      name: 'Earnings',
      component: () => import('@/pages/earnings/index.vue')
    },
    // --- 提现 ---
    {
      path: '/withdrawal',
      name: 'Withdrawal',
      component: () => import('@/pages/withdrawal/index.vue')
    },
    {
      path: '/withdrawal/apply',
      name: 'WithdrawalApply',
      component: () => import('@/pages/withdrawal/apply.vue')
    },
    {
      path: '/withdrawal/:id',
      name: 'WithdrawalDetail',
      component: () => import('@/pages/withdrawal/detail.vue')
    },
    // --- 消息 ---
    {
      path: '/messages',
      name: 'Messages',
      component: () => import('@/pages/messages/index.vue')
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
      component: () => import('@/pages/profile/bank-cards.vue')
    },
    {
      path: '/profile/theme',
      name: 'ThemeSwitch',
      component: () => import('@/pages/profile/theme.vue')
    }
  ]
})

// 是否已初始化认证（仅首次进入时尝试企微 OAuth）
let authInitialized = false

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (!requiresAuth) return

  // 首次进入：如果在企微环境且 URL 有 code，尝试自动登录
  if (!authInitialized) {
    authInitialized = true
    if (userStore.isLoggedIn) {
      // token 存在，放行（实际有效性由 API 层 401 兜底）
      return
    }
    // 检查 URL 中是否带有企微 OAuth code
    const code = new URLSearchParams(window.location.search).get('code')
    if (code) {
      // 有 code 先放行到目标页，由页面 onMounted 里的 initAuth 处理
      return
    }
  }

  if (!userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
})

export default router
