<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import LeadCreateDialog from '../components/LeadCreateDialog.vue'
const dialog = ref<InstanceType<typeof LeadCreateDialog>>()
</script>
<template>
  <WorkbenchListPage title="提交客资" endpoint="/zsjos/lead/page" description="提交客资记录与详情"
    ><template #actions="{ reload }"
      ><el-button type="primary" @click="dialog?.open()">提交客资</el-button
      ><LeadCreateDialog ref="dialog" @success="reload" /></template
  ></WorkbenchListPage>
</template>
