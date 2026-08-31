<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resolveWecomClickTicket, type WecomClickTarget } from '@/api/wecom'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'WecomClick' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const error = ref('')

function normalizeInternalPath(path?: string) {
  if (!path || !path.startsWith('/') || path.startsWith('//')) return undefined
  return path
}

function resolveTarget(target: WecomClickTarget) {
  return normalizeInternalPath(target.targetPath)
    || normalizeInternalPath(target.fallbackPath)
    || '/messages'
}

onMounted(async () => {
  const ticket = (route.query.ticket as string | undefined)?.trim()
  if (!ticket) {
    error.value = '企业微信消息链接缺少票据'
    return
  }
  try {
    const target = await resolveWecomClickTicket(ticket)
    if (target.audience !== 'PARTNER') {
      error.value = '该企业微信消息不属于兼职端'
      return
    }
    const targetPath = resolveTarget(target)
    if (!userStore.isLoggedIn) {
      await router.replace({ name: 'Login', query: { redirect: targetPath, wecom: '1' } })
      return
    }
    await router.replace(targetPath)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '企业微信消息链接解析失败'
  }
})
</script>

<template>
  <div class="page-container wecom-click-page">
    <van-nav-bar title="企业微信消息" />
    <van-empty v-if="error" :description="error" image="error">
      <van-button type="primary" round size="small" @click="router.replace('/messages')">查看消息中心</van-button>
    </van-empty>
    <div v-else class="card wecom-click-loading">
      <van-loading size="20">正在打开消息...</van-loading>
    </div>
  </div>
</template>

<style scoped>
.wecom-click-page {
  min-height: 100vh;
  background: var(--h5-bg);
}

.wecom-click-loading {
  margin-top: 12px;
  padding: 28px 16px;
  text-align: center;
}
</style>
