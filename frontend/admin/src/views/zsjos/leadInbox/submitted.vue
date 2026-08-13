<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
</script>
<template>
  <WorkbenchListPage
    title="我提交的"
    endpoint="/zsjos/lead/inbox/submitted/page"
    description="当前用户提交的客资"
  />
</template>
