<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import LeadCreateDialog from '../components/LeadCreateDialog.vue'
const dialog = ref<InstanceType<typeof LeadCreateDialog>>()
</script>
<template>
  <WorkbenchListPage
    title="销售自拓"
    endpoint="/zsjos/lead/page"
    description="销售自拓客资记录"
    number-field="leadNo"
    number-label="客资编号"
    ><template #actions="{ reload }"
      ><el-button type="primary" @click="dialog?.open()">新增自拓客资</el-button
      ><LeadCreateDialog ref="dialog" self-sourced @success="reload" /></template
  ></WorkbenchListPage>
</template>
