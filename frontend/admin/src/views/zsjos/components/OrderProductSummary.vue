<script setup lang="ts">
import ProductSpecs from './ProductSpecs.vue'
import type { ProductSpec } from '@/utils/productSpecs'
defineProps<{
  items?: Array<{
    id?: number
    productName?: string
    skuName?: string
    categoryPath?: string[]
    specs?: ProductSpec[]
    attrValues?: Record<string, string>
    actualAmount?: number
  }>
}>()
</script>
<template>
  <div v-if="items?.length" class="order-products">
    <div v-for="(item, index) in items" :key="item.id || index" class="order-product">
      <div class="order-product-copy"
        ><strong>{{ item.productName || item.skuName || '历史产品信息缺失' }}</strong
        ><div class="order-product-category">{{ item.categoryPath?.join(' / ') }}</div
        ><ProductSpecs :product="item" />
        <div
          v-if="item.skuName && item.skuName !== item.productName"
          class="order-product-category"
          >{{ item.skuName }}</div
        >
      </div>
      <span class="order-product-amount">{{
        item.actualAmount == null ? '-' : `¥${Number(item.actualAmount).toFixed(2)}`
      }}</span>
    </div>
  </div>
</template>
<style scoped>
.order-products {
  margin-block: 12px;
}

.order-product {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-block: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.order-product-copy {
  min-width: 0;
  overflow-wrap: anywhere;
}

.order-product-category {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.order-product-amount {
  flex-shrink: 0;
  white-space: nowrap;
}

@media (width <= 600px) {
  .order-product {
    flex-direction: column;
  }
}
</style>
