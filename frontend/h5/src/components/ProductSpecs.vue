<script setup lang="ts">
import { computed } from 'vue'
import { productSpecs, specText, type ProductSpec } from '../utils/productSpecs'
const props = defineProps<{ product: { specs?: ProductSpec[] | null; attrValues?: Record<string, string>; selectedAttrValues?: string } }>()
const specs = computed(() => productSpecs(props.product))
</script>
<template>
  <span v-if="specs.length" class="product-specs"><span v-for="spec in specs" :key="spec.attrKey">{{ specText(spec) }}</span></span>
</template>
<style scoped>
.product-specs { display: flex; flex-direction: column; gap: 4px; min-width: 0; color: var(--van-text-color-2); font-size: 12px; white-space: normal; overflow-wrap: anywhere; }
</style>
