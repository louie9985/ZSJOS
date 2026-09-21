<template>
  <div class="resource-link-input">
    <el-input
      ref="inputRef"
      clearable
      placeholder="请输入链接"
      v-bind="$attrs"
      :model-value="modelValue"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <template v-for="(_, name) in $slots" #[name]="scope">
        <slot :name="name" v-bind="scope || {}"></slot>
      </template>
    </el-input>
    <el-link v-if="safeHref" :href="safeHref" target="_blank" rel="noopener noreferrer" type="primary">
      打开链接（新标签页）
    </el-link>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { InputInstance } from 'element-plus'

defineOptions({ name: 'ResourceLinkInput', inheritAttrs: false })
const props = defineProps<{ modelValue?: string | number | null; previewValue?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const inputRef = ref<InputInstance>()
// Preview never changes the stored value or the owning form's validation rules.
const safeHref = computed(() => {
  const raw = String(props.previewValue ?? props.modelValue ?? '').trim()
  if (/[\u0000-\u0020\\]/.test(raw)) return undefined
  try {
    const url = new URL(raw)
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password
      ? url.href
      : undefined
  } catch {
    return undefined
  }
})
defineExpose({ focus: () => inputRef.value?.focus(), blur: () => inputRef.value?.blur() })
</script>

<style scoped>
.resource-link-input {
  width: 100%;
  min-width: 0;
}
</style>
