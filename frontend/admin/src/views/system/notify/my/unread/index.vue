<template>
  <NotifyMessageList unread-only />
</template>

<script lang="ts" setup>
import NotifyMessageList from '../NotifyMessageList.vue'

defineOptions({ name: 'MyNotifyMessageUnread' })
</script>
