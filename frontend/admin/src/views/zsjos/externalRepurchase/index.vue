<script setup lang="ts">
import { useUserStore } from '@/store/modules/user'
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import ExternalRepurchaseDialog from '../components/ExternalRepurchaseDialog.vue'

const userStore = useUserStore()
const canQueryOwn = computed(() => userStore.getPermissions.has('*:*:*')
  || userStore.getPermissions.has('zsjos:sales-order:query-own'))
const listVersion = ref(0)
const dialog = ref<InstanceType<typeof ExternalRepurchaseDialog>>()
</script>

<template>
  <ContentWrap>
    <h3 class="mb-16px">历史客户复购</h3>
    <el-button v-hasPermi="['zsjos:sales-order:create']" type="primary" @click="dialog?.open()">
      新增复购
    </el-button>
    <ExternalRepurchaseDialog ref="dialog" @success="listVersion++" />
  </ContentWrap>
  <WorkbenchListPage
    v-if="canQueryOwn"
    :key="listVersion"
    title="我的订单"
    endpoint="/zsjos/sales-order/my-page"
    description="本人提交的订单"
    number-field="orderNo"
    number-label="订单号"
  />
</template>
