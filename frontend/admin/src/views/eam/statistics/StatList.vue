<template>
  <el-empty v-if="items.length === 0" description="暂无数据" :image-size="50" />
  <div v-else class="flex flex-col gap-3">
    <div v-for="item in items" :key="item.key">
      <div class="mb-1 flex items-center justify-between text-sm">
        <span>{{ item.name }}</span>
        <span class="text-gray-500">{{ item.count }}</span>
      </div>
      <el-progress
        :percentage="percentOf(item.count)"
        :show-text="false"
        :stroke-width="8"
        color="#409eff"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { StatisticsItemVO } from '@/api/eam/statistics'

defineOptions({ name: 'EamStatList' })

const props = defineProps<{ items: StatisticsItemVO[]; total: number }>()

const percentOf = (count: number) => {
  if (!props.total) {
    return 0
  }
  return Math.round((count / props.total) * 100)
}
</script>
