<script setup lang="ts">
import { computed } from 'vue'
import { productSpecs, specText, type ProductSpec } from '@/utils/productSpecs'
const props = defineProps<{
  product: {
    specs?: ProductSpec[] | null
    attrValues?: Record<string, string>
    selectedAttrValues?: string
  }
}>()
const specs = computed(() => productSpecs(props.product))
</script>
<template>
  <span v-if="specs.length" class="product-specs"
    ><span v-for="spec in specs" :key="spec.attrKey">{{ specText(spec) }}</span></span
  >
</template>
<style scoped>
.product-specs {
  display: flex;
  min-width: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: normal;
  flex-wrap: wrap;
  gap: 4px 12px;
  overflow-wrap: anywhere;
}

@media (width <= 600px) {
  .product-specs {
    flex-direction: column;
  }
}
</style>
