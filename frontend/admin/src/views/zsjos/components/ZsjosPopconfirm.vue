<template>
  <el-popover
    :trigger="trigger"
    placement="top"
    width="280"
    :visible="resolvedVisible"
    @update:visible="handleVisibleChange"
  >
    <div class="zsjos-popconfirm-title">{{ irreversibleConfirmTitle(action) }}</div>
    <div class="zsjos-popconfirm-content">{{ IRREVERSIBLE_CONFIRM_DESCRIPTION }}</div>
    <div class="zsjos-popconfirm-actions">
      <el-button size="small" @click="cancel">取消</el-button>
      <el-button size="small" :type="danger ? 'danger' : 'primary'" @click="confirm">
        确认执行
      </el-button>
    </div>
    <template #reference><slot></slot></template>
  </el-popover>
</template>

<script lang="ts" setup>
import { irreversibleConfirmTitle, IRREVERSIBLE_CONFIRM_DESCRIPTION } from './irreversibleConfirm'

const props = defineProps<{
  action: string
  danger?: boolean
  visible?: boolean
}>()

const emit = defineEmits<{
  confirm: []
  'update:visible': [visible: boolean]
}>()

const internalVisible = ref(false)
const resolvedVisible = computed(() => props.visible ?? internalVisible.value)
// Controlled callers open the confirmation after validation; avoid Popover's click toggle racing that state update.
const trigger = computed(() => (props.visible === undefined ? ('click' as const) : []))

const handleVisibleChange = (nextVisible: boolean) => {
  if (props.visible === undefined) internalVisible.value = nextVisible
  else if (!nextVisible) emit('update:visible', false)
}

const close = () => {
  internalVisible.value = false
  emit('update:visible', false)
}

const cancel = () => close()
const confirm = () => {
  close()
  emit('confirm')
}
</script>

<style scoped>
.zsjos-popconfirm-content {
  margin: 8px 0 12px;
  color: var(--el-text-color-secondary);
}

.zsjos-popconfirm-title {
  font-weight: 600;
  line-height: 20px;
  color: var(--el-text-color-primary);
}

.zsjos-popconfirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
