<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// TabBar 页面路径
const tabRoutes = ['/home', '/lead/list', '/earnings', '/messages', '/profile']
const showTabBar = computed(() => tabRoutes.includes(route.path))

const activeTab = computed({
  get: () => {
    const idx = tabRoutes.indexOf(route.path)
    return idx >= 0 ? idx : 0
  },
  set: () => {} // controlled by @change
})
</script>

<template>
  <router-view v-slot="{ Component }">
    <keep-alive :include="['Home', 'LeadList', 'Earnings', 'Messages', 'Profile']">
      <component :is="Component" />
    </keep-alive>
  </router-view>

  <van-tabbar
    v-if="showTabBar"
    v-model="activeTab"
    route
    placeholder
    safe-area-inset-bottom
  >
    <van-tabbar-item icon="wap-home-o" to="/home">首页</van-tabbar-item>
    <van-tabbar-item icon="orders-o" to="/lead/list">客资</van-tabbar-item>
    <van-tabbar-item icon="gold-coin-o" to="/earnings">收益</van-tabbar-item>
    <van-tabbar-item icon="bell" to="/messages" :badge="''">消息</van-tabbar-item>
    <van-tabbar-item icon="user-o" to="/profile">我的</van-tabbar-item>
  </van-tabbar>
</template>

<style>
/* App 级全局样式已在 main.ts 导入 */
</style>
