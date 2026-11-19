<script setup lang="ts">
import OrderHistoryFacts from '@/views/zsjos/components/OrderHistoryFacts.vue'
import OrderProductSummary from '@/views/zsjos/components/OrderProductSummary.vue'
import { getNativeFinanceOrderDetail } from '@/api/zsjos/workbenchMenus'

const props = defineProps<{ taskId: string; view: 'todo' | 'done' }>()
const order = ref<Awaited<ReturnType<typeof getNativeFinanceOrderDetail>>>()
const loading = ref(false)
const error = ref('')
let generation = 0

const load = async () => {
  const current = ++generation
  loading.value = true
  error.value = ''
  order.value = undefined
  try {
    const result = await getNativeFinanceOrderDetail(props.taskId, props.view)
    if (current === generation) order.value = result
  } catch (cause) {
    if (current === generation) error.value = cause instanceof Error ? cause.message : '订单信息加载失败'
  } finally {
    if (current === generation) loading.value = false
  }
}

watch(() => [props.taskId, props.view], load, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>

<template>
  <section aria-label="财务审批订单" v-loading="loading">
    <el-alert v-if="error" type="error" :title="error" :closable="false">
      <el-button link @click="load">重试</el-button>
    </el-alert>
    <template v-if="order">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单编号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="学员">{{ order.studentName || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ order.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="付款方式">{{ order.paymentMethodLabelSnapshot || '未记录' }}</el-descriptions-item>
      </el-descriptions>
      <OrderHistoryFacts :order="order" />
      <OrderProductSummary :items="order.items" />
    </template>
  </section>
</template>
