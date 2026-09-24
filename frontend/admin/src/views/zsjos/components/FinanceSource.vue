<template>
  <h4>客资／订单来源</h4>
  <el-alert v-if="!source" title="历史来源信息缺失" type="info" :closable="false" />
  <template v-else>
    <el-descriptions v-if="source.leadAccess === 'available'" :column="2" border>
      <el-descriptions-item label="客资编号"><el-link type="primary" @click="router.push({ path: '/zsjos/leads/manage', query: { leadId: source.leadId } })">{{ source.leadNo || '历史未记录' }}</el-link></el-descriptions-item>
      <el-descriptions-item label="客户姓名">{{ source.customerName || '未提供可见姓名' }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-else :title="`客资：${accessText(source.leadAccess)}`" type="info" :closable="false" />
    <el-descriptions v-if="source.orderAccess === 'available'" :column="2" border>
      <el-descriptions-item label="订单号"><el-link type="primary" @click="router.push({ path: '/zsjos/sales-orders', query: { orderId: source.orderId } })">{{ source.orderNo || '历史未记录' }}</el-link></el-descriptions-item>
      <el-descriptions-item label="学员">{{ source.studentName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="负责销售">{{ source.salesName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="订单类型">{{ source.orderTypeLabel || '-' }}</el-descriptions-item>
      <el-descriptions-item label="订单状态">{{ source.orderStatusLabel || '-' }}</el-descriptions-item>
      <el-descriptions-item label="整单总金额">{{ money(source.orderTotalAmount) }}</el-descriptions-item>
      <el-descriptions-item label="整单应付金额">{{ money(source.orderPayableAmount) }}</el-descriptions-item>
      <el-descriptions-item label="客户付款时间">{{ formatDate(source.customerPaidAt) || '-' }}</el-descriptions-item>
      <el-descriptions-item label="订单商品快照">{{ [source.productName, source.skuName].filter(Boolean).join(' / ') || '历史未记录' }}</el-descriptions-item>
      <el-descriptions-item label="商品数量">{{ source.quantity ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="商品单价">{{ money(source.unitPrice) }}</el-descriptions-item>
      <el-descriptions-item label="商品项优惠">{{ money(source.discountAmount) }}</el-descriptions-item>
      <el-descriptions-item label="对应商品项应付">{{ money(source.itemPayableAmount) }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-else :title="`订单：${accessText(source.orderAccess)}`" type="info" :closable="false" />
  </template>
</template>
<script setup lang="ts">
import type { FinanceSource } from '@/api/zsjos/cashback'
import { formatDate } from '@/utils/formatTime'
defineProps<{ source?: FinanceSource }>()
const router = useRouter()
const money = (value?: number) => value == null ? '-' : `¥${Number(value).toFixed(2)}`
const accessText = (value?: string) => value === 'denied' ? '无权查看来源信息' : value === 'not_applicable' ? '有效客资返现，无关联订单' : '历史来源信息缺失'
</script>
