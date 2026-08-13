<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import ExternalRepurchaseDialog from '../components/ExternalRepurchaseDialog.vue'
const dialog = ref<InstanceType<typeof ExternalRepurchaseDialog>>()
</script>
<template>
  <WorkbenchListPage
    title="历史客户复购"
    endpoint="/zsjos/sales-order/my-page"
    description="历史客户复购订单记录"
    ><template #actions="{ reload }"
      ><el-button type="primary" @click="dialog?.open()">新增复购</el-button
      ><ExternalRepurchaseDialog ref="dialog" @success="reload" /></template
  ></WorkbenchListPage>
</template>
