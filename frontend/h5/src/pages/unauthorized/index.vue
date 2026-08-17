<script setup lang="ts">
import { useRouter } from 'vue-router'

defineOptions({ name: 'Unauthorized' })
const router = useRouter()
</script>

<template>
  <div class="page-container unauthorized-page">
    <van-empty image="error" description="当前账号没有访问此功能的权限">
      <van-button type="primary" round size="small" @click="router.replace('/home')">返回首页</van-button>
    </van-empty>
  </div>
</template>

<style scoped>
.unauthorized-page{display:flex;align-items:center;justify-content:center;padding-bottom:0}
</style>
