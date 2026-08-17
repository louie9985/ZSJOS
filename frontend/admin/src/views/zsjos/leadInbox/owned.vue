<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
</script>
<template>
  <WorkbenchListPage
    title="我负责的"
    endpoint="/zsjos/lead/inbox/owned/page"
    description="当前用户负责的客资"
    number-field="leadNo"
    number-label="客资编号"
  />
</template>
