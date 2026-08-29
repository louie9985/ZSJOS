<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import LiquidTabbar, { type LiquidTabItem } from '@/components/LiquidTabbar.vue'

const route = useRoute()
const userStore = useUserStore()

const canSubmitLead = computed(() => userStore.hasPermission('zsjos:lead:submit'))
const canViewLeads = computed(() => userStore.hasPermission('zsjos:lead:query-submitted'))
const canViewEarnings = computed(() => userStore.hasPermission('zsjos:cashback:my-query'))
const tabRoutes = computed(() => [
  '/home',
  ...(canSubmitLead.value ? ['/lead/submit'] : []),
  ...(canViewLeads.value ? ['/lead/list'] : []),
  ...(canViewEarnings.value ? ['/earnings'] : []),
  '/profile'
])
const showTabBar = computed(() => tabRoutes.value.includes(route.path))
const tabItems = computed<LiquidTabItem[]>(() => [
  { path: '/home', icon: 'wap-home-o', activeIcon: 'wap-home', label: '首页' },
  ...(canSubmitLead.value ? [{ path: '/lead/submit', icon: 'description-o', activeIcon: 'description', label: '提交' }] : []),
  ...(canViewLeads.value ? [{ path: '/lead/list', icon: 'contact-o', activeIcon: 'contact', label: '客资' }] : []),
  ...(canViewEarnings.value ? [{ path: '/earnings', icon: 'gold-coin-o', activeIcon: 'gold-coin', label: '收益' }] : []),
  { path: '/profile', icon: 'user-o', activeIcon: 'user', label: '我的' }
])
</script>

<template>
  <router-view v-slot="{ Component }">
    <keep-alive :include="['Home', 'LeadSubmit', 'LeadList', 'LeadFollowUp', 'Earnings', 'Messages', 'Profile']">
      <component :is="Component" />
    </keep-alive>
  </router-view>

  <LiquidTabbar v-if="showTabBar" :items="tabItems" />
</template>

<style>
/* App 级全局样式已在 main.ts 导入 */
</style>
