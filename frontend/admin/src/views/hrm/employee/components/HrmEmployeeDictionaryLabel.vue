<template>
  <el-tooltip v-if="usesCurrentDictionary" content="历史标签未记录，按当前字典显示" placement="top">
    <span tabindex="0">{{ currentLabel }}</span>
  </el-tooltip>
  <span v-else>{{ snapshotLabel || (value == null ? '-' : `历史代码 ${value}`) }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getDictLabel } from '@/utils/dict'

const props = defineProps<{
  dictType: string
  value?: number | string | null
  snapshotLabel?: string | null
}>()

// A missing historical snapshot may use the current dictionary for display only.
const currentLabel = computed(() =>
  !props.snapshotLabel && props.value != null ? getDictLabel(props.dictType, props.value) : ''
)
const usesCurrentDictionary = computed(() => !props.snapshotLabel && !!currentLabel.value)
</script>
